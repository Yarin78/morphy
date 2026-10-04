import { Chess } from 'chess.js';
import type { UciScore } from './uci';

// What an engine's lines look like on screen: scores from White's point of view, and moves as
// SAN with move numbers.

/** A score from White's point of view, given whose move it is in the position searched. */
export function scoreForWhite(score: UciScore, whiteToMove: boolean): UciScore {
  return whiteToMove ? score : { kind: score.kind, value: -score.value };
}

/** A score as shown: +0.34, -1.20, 0.00, or #5 / #-3 for mates (mated in 3 as #-3). */
export function formatScore(score: UciScore): string {
  if (score.kind === 'mate') return `#${score.value}`;
  const pawns = score.value / 100;
  return pawns === 0 ? '0.00' : `${pawns > 0 ? '+' : ''}${pawns.toFixed(2)}`;
}

/**
 * A line of UCI moves from a position, as SAN with move numbers: "12.Nf3 Nc6 13.Bb5", or
 * "12...Nc6 13.Bb5" from Black's move. Stops at a move that can't be played, and after maxMoves.
 */
export function lineToSan(fen: string, uciMoves: string[], maxMoves = Infinity): string {
  let chess: Chess;
  try {
    chess = new Chess(fen);
  } catch {
    return '';
  }
  const parts: string[] = [];
  for (const [i, uci] of uciMoves.slice(0, maxMoves).entries()) {
    const white = chess.turn() === 'w';
    const number = chess.moveNumber();
    let san: string;
    try {
      san = chess.move({ from: uci.slice(0, 2), to: uci.slice(2, 4), promotion: uci[4] }).san;
    } catch {
      break;
    }
    if (white) parts.push(`${number}.${san}`);
    else parts.push(i === 0 ? `${number}...${san}` : san);
  }
  return parts.join(' ');
}
