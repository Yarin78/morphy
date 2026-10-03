import type { ReactNode } from 'react';
import { TbArrowUp, TbScissors, TbTrash } from 'react-icons/tb';
import { GameTree } from '../model/GameTree';
import type { MoveNode } from '../model/GameTree';

/** Something to do to the moves of a game from a move, for its context menu and the bar below the notation. */
export interface MoveAction {
  label: string;
  icon: ReactNode;
  /** Whether it can't be done from the move. */
  disabled: boolean;
  run: () => void;
}

/**
 * The changes to the moves of a game that can be made from a move: promoting or deleting its
 * variation, and deleting the moves after or before it.
 *
 * @param move the move, or null at the start position, where none can be made
 * @param onChange called after the moves were changed
 */
export function moveActions(game: GameTree, move: MoveNode | null, onChange: () => void): MoveAction[] {
  const inVariation = !!move && !!GameTree.variationStart(move);
  return [
    {
      label: 'Promote Variation',
      icon: <TbArrowUp />,
      disabled: !inVariation,
      run: () => {
        if (move && GameTree.promoteVariation(move)) onChange();
      },
    },
    {
      label: 'Delete Variation',
      icon: <TbTrash />,
      disabled: !inVariation,
      run: () => {
        if (move && game.deleteVariation(move)) onChange();
      },
    },
    {
      label: 'Delete Remaining Moves',
      icon: <TbScissors />,
      disabled: !move || move.children.length === 0,
      run: () => {
        if (!move) return;
        game.deleteRemainingMoves(move);
        onChange();
      },
    },
    {
      label: 'Delete Previous Moves',
      // Mirrored, cutting to the left
      icon: <TbScissors style={{ transform: 'scaleX(-1)' }} />,
      disabled: !move || !GameTree.previous(move),
      run: () => {
        if (move && game.deletePreviousMoves(move)) onChange();
      },
    },
  ];
}
