import type { GameNode, MoveNode } from '../model/GameTree';
import { nagInfo, nagsOf } from '../model/nags';

// The NAGs of good and bad moves: ! ? !! ?? !? ?!
const MOVE_QUALITY_NAGS = [1, 2, 3, 4, 5, 6];

/**
 * The moves to choose between when going to the next move from a position with variations, in the
 * order the notation has them: the variations first, and the main move last.
 */
export function nextMoveChoices(node: GameNode): MoveNode[] {
  const [main, ...variations] = node.children;
  return main ? [...variations, main] : [];
}

/**
 * The start of the line a move begins, as text with move numbers and the symbols of good and bad
 * moves: the move and the main moves after it, like '1...c5 2.Nf3! d6 3.d4'.
 *
 * @param plies the number of moves at most
 */
export function lineStart(move: MoveNode, plies = 4): string {
  const parts: string[] = [];
  let m: MoveNode | undefined = move;
  for (let i = 0; m && i < plies; i++, m = m.children[0]) {
    const number = Math.floor(m.parent.ply / 2) + 1;
    const white = m.parent.ply % 2 === 0;
    const quality = nagsOf(m.annotations)
      .filter((nag) => MOVE_QUALITY_NAGS.includes(nag))
      .map((nag) => nagInfo(nag)?.symbol ?? '')
      .join('');
    const san = m.san + quality;
    parts.push(white ? `${number}.${san}` : i === 0 ? `${number}...${san}` : san);
  }
  return parts.join(' ');
}
