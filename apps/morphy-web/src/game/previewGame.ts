import { GameTree } from 'game-view';
import type { ChessGame, GameNode } from 'game-view';

/** What a game previewed shows besides its main line. */
export interface PreviewContent {
  variations: boolean;
  /** Every annotation: text, symbols, squares and arrows, clocks, evaluations and the rest */
  commentary: boolean;
}

/**
 * A game as previewed: its main line, with its variations and its commentary only if they're
 * shown. A game whose moves can't be read is left as it is, for the board to tell of.
 */
export function previewGame(game: ChessGame, { variations, commentary }: PreviewContent): ChessGame {
  if (!game.moves || (variations && commentary)) return game;
  let tree: GameTree;
  try {
    tree = GameTree.fromMoves(game.moves, game.tags);
  } catch {
    return game;
  }
  const strip = (node: GameNode) => {
    if (!variations) {
      while (node.children.length > 1) tree.deleteVariation(node.children[1]);
    }
    if (!commentary) node.annotations = [];
    node.children.forEach(strip);
  };
  strip(tree.root);
  return { ...game, moves: tree.toMoves() };
}
