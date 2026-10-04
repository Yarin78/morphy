import { GameBoard } from 'game-view';
import { useBoardView } from '../boardStore';

/** The board of a board document, with the buttons to move through the game. */
export function BoardPane() {
  return <GameBoard view={useBoardView()} />;
}
