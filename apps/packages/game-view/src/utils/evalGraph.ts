import type { AnnotationOf } from '../model/annotations';
import type { GameNode, MoveNode } from '../model/GameTree';
import { formatEval } from './moveInfo';

/**
 * The evaluations of the main line, as ChessBase stores them on the game, as bars of a graph: one
 * for each position, above the middle when White is better and below when Black is.
 */

/** A bar of the graph. */
export interface EvalBar {
  /** The position, the start position or one after a move of the main line. */
  node: GameNode;
  /** From -1, Black winning, to 1, White winning, or null if the position has no evaluation. */
  value: number | null;
  /** The move and its evaluation, like '12...Nxe5 +0.13/1'. */
  label: string;
}

// The evaluation in pawns of a full bar; the bars grow with the square root of the evaluation, so
// the small ones of a quiet game still show
const FULL_PAWNS = 5;

/** An evaluation from -1 to 1, or null if it's not one the graph can show. */
export function evalValue(evaluation: { eval: number; evalType: number }): number | null {
  switch (evaluation.evalType) {
    case 0: {
      const pawns = evaluation.eval / 100;
      return Math.sign(pawns) * Math.min(1, Math.sqrt(Math.abs(pawns) / FULL_PAWNS));
    }
    case 1:
      // A mate, by Black when it's negative
      return evaluation.eval < 0 ? -1 : 1;
    default:
      return null;
  }
}

function moveName(node: GameNode): string {
  if (!('san' in node)) return 'Start';
  const move = node as MoveNode;
  const number = Math.ceil(move.ply / 2);
  return `${number}${move.ply % 2 === 1 ? '.' : '...'}${move.san}`;
}

/**
 * The bars of the graph: one for each position of the main line that there is an evaluation of.
 *
 * @param root the start position
 * @param annotation the evaluations of the game
 */
export function evalBars(root: GameNode, annotation: AnnotationOf<'evaluations'>): EvalBar[] {
  const bars: EvalBar[] = [];
  let node: GameNode | undefined = root;
  for (const evaluation of annotation.evaluations) {
    if (!node) break;
    const value = evalValue(evaluation);
    const text = value === null ? null : formatEval({ type: 'eval', ...evaluation });
    bars.push({ node, value, label: text === null ? moveName(node) : `${moveName(node)} ${text}` });
    node = node.children[0];
  }
  return bars;
}
