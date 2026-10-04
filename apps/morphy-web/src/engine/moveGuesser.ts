import { Chess, type Square } from 'chess.js';
import { ENGINES } from './engines';
import { UciEngine } from './uci';

// Guessing which piece is meant to go to a square, for moves made by clicking the square they go
// to: the best of the moves there, by a short search of the lite engine. It has a worker of its
// own, single-threaded, so it answers at once whatever the analysis is doing.

/** How long a guess searches, in ms */
const GUESS_MS = 100;
// The guesses made, by position and square, as the same square is often clicked again
const CACHE_SIZE = 64;

let engine: Promise<UciEngine> | null = null;
const cache = new Map<string, string>();

function load(): Promise<UciEngine> {
  engine ??= (async () => {
    const uci = new UciEngine(ENGINES.stockfishLiteSingle.createTransport());
    uci.onError(() => (engine = null));
    await uci.init();
    await uci.isReady();
    return uci;
  })().catch((err: unknown) => {
    // Tried again with the next guess
    engine = null;
    throw err;
  });
  return engine;
}

/** Starts the engine, so the first guess doesn't wait for it to load. */
export function warmMoveGuesser() {
  load().catch(() => {});
}

/**
 * Which of the pieces on the squares given is the one to move to a square, in the position: the
 * square it's on, or null if the engine can't tell.
 */
export async function guessMove(fen: string, to: string, froms: string[]): Promise<string | null> {
  const key = `${fen}|${to}|${froms.join(',')}`;
  const known = cache.get(key);
  if (known) return known;
  let chess: Chess;
  try {
    chess = new Chess(fen);
  } catch {
    return null;
  }
  // A pawn promoting is searched as promoting to a queen
  const moves = froms.map((from) => {
    const promotes = chess.get(from as Square)?.type === 'p' && (to[1] === '8' || to[1] === '1');
    return `${from}${to}${promotes ? 'q' : ''}`;
  });
  try {
    const best = await (await load()).bestMove(fen, GUESS_MS, moves);
    const from = best?.slice(0, 2);
    if (!from || !froms.includes(from)) return null;
    if (cache.size >= CACHE_SIZE) cache.delete(cache.keys().next().value!);
    cache.set(key, from);
    return from;
  } catch {
    return null;
  }
}
