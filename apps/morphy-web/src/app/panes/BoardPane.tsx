import { GameBoard } from 'game-view';
import { useSettings } from '../settings';
import { useBoardView } from '../boardStore';

/** The board of a board document, with the buttons to move through the game. */
export function BoardPane() {
  const { board } = useSettings();
  return (
    <GameBoard
      view={useBoardView()}
      coordinates={board.coordinates}
      animation={board.animation}
      lastMove={board.lastMove}
      navigation={board.navigation}
    />
  );
}
