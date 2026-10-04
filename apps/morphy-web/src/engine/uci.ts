import type { UciTransport } from './transport';

// The UCI protocol, over any transport: the handshake, options, and analysing a position.

/** A score from the side to move's point of view, as UCI gives it */
export type UciScore = { kind: 'cp'; value: number } | { kind: 'mate'; value: number };

/** What an `info` line says about the search, for one of the lines (multipv) it's following */
export interface EngineInfo {
  depth?: number;
  seldepth?: number;
  /** Which line, 1 for the best */
  multipv: number;
  score?: UciScore;
  /** Whether the score is only a bound so far */
  bound?: 'lower' | 'upper';
  nodes?: number;
  nps?: number;
  /** In ms */
  time?: number;
  /** The line, as UCI moves (e2e4, e7e8q) */
  pv?: string[];
}

export interface UciOption {
  name: string;
  type: string;
  default?: string;
  min?: number;
  max?: number;
}

export interface EngineIdentity {
  name: string;
  author: string;
  options: UciOption[];
}

const INFO_NUMBERS = ['depth', 'seldepth', 'multipv', 'nodes', 'nps', 'time'] as const;

/** An `info` line about the search, or null for any other (as `info string ...`). */
export function parseInfo(line: string): EngineInfo | null {
  const words = line.trim().split(/\s+/);
  if (words[0] !== 'info' || words[1] === 'string') return null;
  const info: EngineInfo = { multipv: 1 };
  let found = false;
  for (let i = 1; i < words.length; i++) {
    const word = words[i];
    if ((INFO_NUMBERS as readonly string[]).includes(word)) {
      info[word as (typeof INFO_NUMBERS)[number]] = Number(words[++i]);
      found = true;
    } else if (word === 'score') {
      const kind = words[++i];
      const value = Number(words[++i]);
      if (kind === 'cp' || kind === 'mate') info.score = { kind, value };
      if (words[i + 1] === 'lowerbound' || words[i + 1] === 'upperbound') {
        info.bound = words[++i] === 'lowerbound' ? 'lower' : 'upper';
      }
    } else if (word === 'pv') {
      info.pv = words.slice(i + 1);
      break;
    }
  }
  return found || info.score || info.pv ? info : null;
}

/** An `option` line of the handshake, or null for any other. */
export function parseOption(line: string): UciOption | null {
  const m = /^option name (.+?) type (\S+)(.*)$/.exec(line.trim());
  if (!m) return null;
  const option: UciOption = { name: m[1], type: m[2] };
  const rest = m[3];
  const value = (key: string) => new RegExp(`\\b${key} (\\S+)`).exec(rest)?.[1];
  const def = value('default');
  if (def !== undefined) option.default = def;
  const min = value('min');
  const max = value('max');
  if (min !== undefined) option.min = Number(min);
  if (max !== undefined) option.max = Number(max);
  return option;
}

/**
 * An engine spoken to over UCI. Analysing a position stops whatever it was searching first;
 * the info about the search is handed to the listener until the next analysis or stop.
 */
export class UciEngine {
  private readonly transport: UciTransport;
  private infoListener: ((info: EngineInfo) => void) | null = null;
  private searching = false;
  // The next position to search, while the last search is still being stopped
  private pending: (() => void) | null = null;

  constructor(transport: UciTransport) {
    this.transport = transport;
    transport.onLine((line) => this.handleLine(line));
  }

  /** Starts the engine: its name and options. */
  init(): Promise<EngineIdentity> {
    return new Promise((resolve, reject) => {
      const identity: EngineIdentity = { name: 'Engine', author: '', options: [] };
      const stopErrors = this.transport.onError((message) => reject(new Error(message)));
      const stopLines = this.transport.onLine((line) => {
        if (line.startsWith('id name ')) identity.name = line.slice(8);
        else if (line.startsWith('id author ')) identity.author = line.slice(10);
        else if (line.startsWith('option ')) {
          const option = parseOption(line);
          if (option) identity.options.push(option);
        } else if (line === 'uciok') {
          stopLines();
          stopErrors();
          resolve(identity);
        }
      });
      this.transport.send('uci');
    });
  }

  setOption(name: string, value: string | number) {
    this.transport.send(`setoption name ${name} value ${value}`);
  }

  /** Waits for the engine to be done with what it was told. */
  isReady(): Promise<void> {
    return new Promise((resolve) => {
      const stop = this.transport.onLine((line) => {
        if (line === 'readyok') {
          stop();
          resolve();
        }
      });
      this.transport.send('isready');
    });
  }

  onError(listener: (message: string) => void): () => void {
    return this.transport.onError(listener);
  }

  /**
   * Analyses a position until stopped, telling the listener what it finds. Options to set first
   * (as MultiPV) are set once the last search has stopped, as UCI only takes them in between.
   */
  analyse(fen: string, onInfo: (info: EngineInfo) => void, options: Record<string, string | number> = {}) {
    const start = () => {
      this.infoListener = onInfo;
      this.searching = true;
      for (const [name, value] of Object.entries(options)) this.setOption(name, value);
      this.transport.send(`position fen ${fen}`);
      this.transport.send('go infinite');
    };
    if (!this.searching) {
      start();
      return;
    }
    // A search is stopped by its bestmove; the next one starts after, so its info isn't mixed up
    // with the last one's
    this.pending = start;
    this.stopSearch();
  }

  /** Stops analysing. */
  stop() {
    this.pending = null;
    this.stopSearch();
  }

  quit() {
    this.pending = null;
    this.infoListener = null;
    this.transport.send('quit');
    this.transport.close();
  }

  private stopSearch() {
    if (!this.searching) return;
    this.infoListener = null;
    this.transport.send('stop');
  }

  private handleLine(line: string) {
    if (line.startsWith('bestmove')) {
      this.searching = false;
      const next = this.pending;
      this.pending = null;
      next?.();
      return;
    }
    if (!this.infoListener) return;
    const info = parseInfo(line);
    if (info) this.infoListener(info);
  }
}
