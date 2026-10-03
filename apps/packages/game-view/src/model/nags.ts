import type { Annotation } from './annotations';
import { findAnnotation, replaceAnnotation } from './annotations';

/**
 * The NAGs (numeric annotation glyphs) the notation shows, and how they're edited.
 *
 * Like se.yarin.chess.NAGType, each NAG has a type: a comment on the move (like '!'), an
 * evaluation of the position (like '±'), or a prefix shown before the move (like '⌓', better
 * is). A move has at most one NAG of each type, which is how ChessBase stores them.
 */

export type NagType = 'moveComment' | 'lineEvaluation' | 'movePrefix';

export interface NagInfo {
  nag: number;
  symbol: string;
  name: string;
  type: NagType;
}

// The symbols follow se.yarin.chess.NAG, with the usual chess book glyphs where they differ
const NAGS: NagInfo[] = [
  { nag: 1, symbol: '!', name: 'Good move', type: 'moveComment' },
  { nag: 2, symbol: '?', name: 'Mistake', type: 'moveComment' },
  { nag: 3, symbol: '!!', name: 'Brilliant move', type: 'moveComment' },
  { nag: 4, symbol: '??', name: 'Blunder', type: 'moveComment' },
  { nag: 5, symbol: '!?', name: 'Interesting move', type: 'moveComment' },
  { nag: 6, symbol: '?!', name: 'Dubious move', type: 'moveComment' },
  { nag: 7, symbol: '□', name: 'Only move', type: 'moveComment' },
  { nag: 8, symbol: '□', name: 'Only move', type: 'moveComment' },
  { nag: 22, symbol: '⨀', name: 'Zugzwang', type: 'moveComment' },
  { nag: 23, symbol: '⨀', name: 'Zugzwang', type: 'moveComment' },

  { nag: 10, symbol: '=', name: 'Equal', type: 'lineEvaluation' },
  { nag: 11, symbol: '=', name: 'Equal', type: 'lineEvaluation' },
  { nag: 12, symbol: '=', name: 'Equal', type: 'lineEvaluation' },
  { nag: 13, symbol: '∞', name: 'Unclear', type: 'lineEvaluation' },
  { nag: 14, symbol: '⩲', name: 'White is slightly better', type: 'lineEvaluation' },
  { nag: 15, symbol: '⩱', name: 'Black is slightly better', type: 'lineEvaluation' },
  { nag: 16, symbol: '±', name: 'White is better', type: 'lineEvaluation' },
  { nag: 17, symbol: '∓', name: 'Black is better', type: 'lineEvaluation' },
  { nag: 18, symbol: '+−', name: 'White is winning', type: 'lineEvaluation' },
  { nag: 19, symbol: '−+', name: 'Black is winning', type: 'lineEvaluation' },
  { nag: 20, symbol: '+−', name: 'White is winning', type: 'lineEvaluation' },
  { nag: 21, symbol: '−+', name: 'Black is winning', type: 'lineEvaluation' },
  { nag: 26, symbol: '○', name: 'Space advantage', type: 'lineEvaluation' },
  { nag: 27, symbol: '○', name: 'Space advantage', type: 'lineEvaluation' },
  { nag: 32, symbol: '⟳', name: 'Development advantage', type: 'lineEvaluation' },
  { nag: 33, symbol: '⟳', name: 'Development advantage', type: 'lineEvaluation' },
  { nag: 36, symbol: '↑', name: 'With initiative', type: 'lineEvaluation' },
  { nag: 37, symbol: '↑', name: 'With initiative', type: 'lineEvaluation' },
  { nag: 40, symbol: '→', name: 'With attack', type: 'lineEvaluation' },
  { nag: 41, symbol: '→', name: 'With attack', type: 'lineEvaluation' },
  { nag: 44, symbol: '=∞', name: 'With compensation', type: 'lineEvaluation' },
  { nag: 45, symbol: '=∞', name: 'With compensation', type: 'lineEvaluation' },
  { nag: 132, symbol: '⇆', name: 'With counterplay', type: 'lineEvaluation' },
  { nag: 133, symbol: '⇆', name: 'With counterplay', type: 'lineEvaluation' },
  { nag: 138, symbol: '⊕', name: 'Time trouble', type: 'lineEvaluation' },
  { nag: 139, symbol: '⊕', name: 'Time trouble', type: 'lineEvaluation' },
  { nag: 146, symbol: 'N', name: 'Novelty', type: 'lineEvaluation' },

  { nag: 140, symbol: 'Δ', name: 'With the idea', type: 'movePrefix' },
  { nag: 141, symbol: '∇', name: 'Directed against', type: 'movePrefix' },
  { nag: 142, symbol: '⌓', name: 'Better is', type: 'movePrefix' },
  { nag: 143, symbol: '≤', name: 'Worse is', type: 'movePrefix' },
  { nag: 144, symbol: '=', name: 'Equivalent is', type: 'movePrefix' },
  { nag: 145, symbol: 'RR', name: 'Editorial comment', type: 'movePrefix' },
];

const NAG_INFO: ReadonlyMap<number, NagInfo> = new Map(NAGS.map((info) => [info.nag, info]));

const TYPE_ORDER: readonly NagType[] = ['moveComment', 'lineEvaluation', 'movePrefix'];

/**
 * The NAGs offered for editing, by type, in the order ChessBase offers them. Of the NAGs that come
 * in a white and a black version, ChessBase uses the white one.
 */
export const NAG_PALETTE: readonly { type: NagType; nags: readonly number[] }[] = [
  { type: 'moveComment', nags: [1, 2, 3, 4, 5, 6, 8, 22] },
  { type: 'lineEvaluation', nags: [18, 16, 14, 10, 15, 17, 19, 13, 44, 36, 40, 132, 32, 26, 138, 146] },
  { type: 'movePrefix', nags: [140, 141, 142, 143, 144, 145] },
];

/** What is known about a NAG, or undefined if it isn't shown. */
export function nagInfo(nag: number): NagInfo | undefined {
  return NAG_INFO.get(nag);
}

/** Whether a NAG is shown before the move rather than after it. */
export function isPrefixNag(nag: number): boolean {
  return NAG_INFO.get(nag)?.type === 'movePrefix';
}

/** The NAGs of a move. */
export function nagsOf(annotations: readonly Annotation[]): readonly number[] {
  return findAnnotation(annotations, 'symbols')?.nags ?? [];
}

/**
 * The annotations of a move with a NAG added, or removed if the move has it. Adding one replaces
 * the NAG of the same type the move has, if any.
 */
export function toggleNag(annotations: readonly Annotation[], nag: number): Annotation[] {
  const current = nagsOf(annotations);
  const type = NAG_INFO.get(nag)?.type;
  const nags = current.includes(nag)
    ? current.filter((n) => n !== nag)
    : [...current.filter((n) => type === undefined || NAG_INFO.get(n)?.type !== type), nag];
  // In the order ChessBase keeps them: the move comment, the evaluation, the prefix
  const rank = (n: number) => {
    const t = NAG_INFO.get(n)?.type;
    return t ? TYPE_ORDER.indexOf(t) : TYPE_ORDER.length;
  };
  const sorted = [...nags].sort((a, b) => rank(a) - rank(b));
  return replaceAnnotation(annotations, 'symbols', sorted.length > 0 ? { type: 'symbols', nags: sorted } : null);
}

/** The symbols of good and bad moves as they're typed, one key or two, and their NAGs. */
const TYPED_MOVE_COMMENTS: Readonly<Record<string, number>> = {
  '!': 1,
  '?': 2,
  '!!': 3,
  '??': 4,
  '!?': 5,
  '?!': 6,
};

/** The NAG of the good or bad move a move is, if it has one. */
function moveCommentOf(annotations: readonly Annotation[]): number | undefined {
  return nagsOf(annotations).find((n) => NAG_INFO.get(n)?.type === 'moveComment');
}

/**
 * Typing the symbol of a good or bad move, '!' or '?', as a key on its own or after another: '!'
 * then '?' is '!?', and so on, and a third key takes the symbol away. A key on its own gives the
 * move its symbol, or takes it away if the move has it.
 *
 * @param typed what was typed just before on the same move, if it's to be typed on: what this
 *     returned for the key before
 * @returns the annotations, and what has been typed so far, or null when nothing more can be typed
 *     on it
 */
export function typeMoveComment(
  annotations: readonly Annotation[],
  key: '!' | '?',
  typed: string | null
): { annotations: Annotation[]; typed: string | null } {
  const current = moveCommentOf(annotations);
  const withoutCurrent = current === undefined ? [...annotations] : toggleNag(annotations, current);
  if (typed !== null && TYPED_MOVE_COMMENTS[typed] === current) {
    const nag = TYPED_MOVE_COMMENTS[typed + key];
    return nag === undefined
      ? { annotations: withoutCurrent, typed: null }
      : { annotations: toggleNag(withoutCurrent, nag), typed: typed + key };
  }
  const nag = TYPED_MOVE_COMMENTS[key];
  return current === nag
    ? { annotations: withoutCurrent, typed: null }
    : { annotations: toggleNag(annotations, nag), typed: key };
}

/** The evaluations of the position, from Black winning to White winning. */
const EVALUATION_SCALE: readonly number[] = [19, 17, 15, 10, 14, 16, 18];

// The NAG of an equal position, of which ChessBase has three
const EQUAL = 10;
const EQUALS = new Set([10, 11, 12]);

/**
 * The annotations of a move with the evaluation of the position a step better for White, or for
 * Black: '−+ ∓ ⩱ = ⩲ ± +−', stopping at the ends. An evaluation off the scale, like unclear, or
 * none counts as equal.
 *
 * @param step 1 for a step better for White, -1 for Black
 */
export function stepEvaluation(annotations: readonly Annotation[], step: 1 | -1): Annotation[] {
  const current = nagsOf(annotations).find((n) => NAG_INFO.get(n)?.type === 'lineEvaluation');
  const at = current === undefined || EQUALS.has(current) ? -1 : EVALUATION_SCALE.indexOf(current);
  const from = at < 0 ? EVALUATION_SCALE.indexOf(EQUAL) : at;
  const nag = EVALUATION_SCALE[Math.min(EVALUATION_SCALE.length - 1, Math.max(0, from + step))];
  return current === nag ? [...annotations] : toggleNag(annotations, nag);
}
