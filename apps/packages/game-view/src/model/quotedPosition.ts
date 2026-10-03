import type { GameNode, GameTree, MoveNode } from './GameTree';

/**
 * Where to open a game a quotation refers to. A ChessBase quotation of a game in the same
 * database, as a repertoire has them, links to a position in it: it keeps the index of the
 * position, counted from the start of the game depth-first, a line's own moves before the lines
 * branching off it (the order ChessBase stores moves in), and the position is the one the quoting
 * move leads to.
 *
 * The position at the index is taken if it is the quoted one. The index goes stale when the game
 * is edited, so otherwise the first position that is the quoted one is taken. An index of 0 is the
 * start of the game.
 *
 * @param fen the position the quoting move leads to
 * @param index the index of the position in the quoted game
 * @returns the move leading to the position, or null for the start of the game
 */
export function quotedPosition(game: GameTree, fen: string, index: number): MoveNode | null {
  if (index <= 0) return null;
  const nodes = depthFirst(game.root);
  const target = samePosition(fen);
  const atIndex = nodes[index];
  if (atIndex && target(atIndex.fen)) return asMove(atIndex);
  const found = nodes.find((node) => target(node.fen));
  return found ? asMove(found) : null;
}

function depthFirst(root: GameNode): GameNode[] {
  const nodes: GameNode[] = [];
  const visit = (node: GameNode) => {
    nodes.push(node);
    node.children.forEach(visit);
  };
  visit(root);
  return nodes;
}

function asMove(node: GameNode): MoveNode | null {
  return 'parent' in node ? (node as MoveNode) : null;
}

/** Whether a FEN is of the same position: the same board, side to move and castling rights. */
function samePosition(fen: string): (other: string) => boolean {
  const key = (f: string) => f.split(' ').slice(0, 3).join(' ');
  const wanted = key(fen);
  return (other) => key(other) === wanted;
}
