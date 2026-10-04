import { describe, expect, it } from 'vitest';
import type { UciTransport } from './transport';
import { UciEngine } from './uci';

/** A transport that keeps what's sent, and writes the engine's lines when told to. */
class FakeTransport implements UciTransport {
  sent: string[] = [];
  private listeners = new Set<(line: string) => void>();

  send(line: string) {
    this.sent.push(line);
  }

  onLine(listener: (line: string) => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  onError() {
    return () => {};
  }

  close() {}

  write(line: string) {
    this.listeners.forEach((l) => l(line));
  }
}

const FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

describe('UciEngine.bestMove', () => {
  it('searches the moves given for a while, and resolves to the best', async () => {
    const transport = new FakeTransport();
    const engine = new UciEngine(transport);
    const best = engine.bestMove(FEN, 100, ['b1c3', 'b1d2']);
    expect(transport.sent).toEqual([`position fen ${FEN}`, 'go movetime 100 searchmoves b1c3 b1d2']);
    transport.write('bestmove b1c3 ponder d7d5');
    await expect(best).resolves.toBe('b1c3');
  });

  it('stops a search going on first, and resolves to null for one replaced before it started', async () => {
    const transport = new FakeTransport();
    const engine = new UciEngine(transport);
    engine.analyse(FEN, () => {});
    const replaced = engine.bestMove(FEN, 100, ['b1c3']);
    const best = engine.bestMove(FEN, 100, ['g1f3']);
    expect(transport.sent.slice(-1)).toEqual(['stop']);
    await expect(replaced).resolves.toBeNull();
    // The analysis's bestmove, as it stops; then the search waited for starts
    transport.write('bestmove e2e4');
    expect(transport.sent.slice(-1)).toEqual(['go movetime 100 searchmoves g1f3']);
    transport.write('bestmove g1f3');
    await expect(best).resolves.toBe('g1f3');
  });

  it('resolves to null when stopped for another search', async () => {
    const transport = new FakeTransport();
    const engine = new UciEngine(transport);
    const stopped = engine.bestMove(FEN, 100);
    engine.analyse(FEN, () => {});
    transport.write('bestmove e2e4');
    await expect(stopped).resolves.toBeNull();
    expect(transport.sent.slice(-1)).toEqual(['go infinite']);
  });
});
