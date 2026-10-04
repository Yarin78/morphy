// How UCI lines get to an engine and back. The protocol itself (UciEngine) doesn't care where
// the engine runs: in a web worker in the browser (WorkerTransport), or later on another
// machine, reached over a WebSocket.

export interface UciTransport {
  /** Sends a line of UCI to the engine */
  send(line: string): void;
  /** Calls the listener with each line the engine writes; returns a function that stops it */
  onLine(listener: (line: string) => void): () => void;
  /** Calls the listener if the engine fails or goes away */
  onError(listener: (message: string) => void): () => void;
  /** Ends the engine */
  close(): void;
}

/** An engine in a web worker: Stockfish compiled to WebAssembly, served at /engines/. */
export class WorkerTransport implements UciTransport {
  private readonly worker: Worker;
  private readonly lineListeners = new Set<(line: string) => void>();
  private readonly errorListeners = new Set<(message: string) => void>();

  constructor(scriptUrl: string) {
    this.worker = new Worker(scriptUrl);
    this.worker.onmessage = (e: MessageEvent) => {
      // A message may hold several lines
      for (const line of String(e.data).split('\n')) {
        if (line.trim()) this.lineListeners.forEach((l) => l(line.trim()));
      }
    };
    this.worker.onerror = (e: ErrorEvent) => {
      this.errorListeners.forEach((l) => l(e.message || 'The engine failed to start'));
    };
  }

  send(line: string) {
    this.worker.postMessage(line);
  }

  onLine(listener: (line: string) => void) {
    this.lineListeners.add(listener);
    return () => this.lineListeners.delete(listener);
  }

  onError(listener: (message: string) => void) {
    this.errorListeners.add(listener);
    return () => this.errorListeners.delete(listener);
  }

  close() {
    this.worker.terminate();
  }
}
