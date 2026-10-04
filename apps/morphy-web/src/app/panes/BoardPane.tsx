import { useEffect } from 'react';
import { GameBoard } from 'game-view';
import { guessMove, warmMoveGuesser } from '../../engine/moveGuesser';
import { useSettings } from '../settings';
import { useBoardView } from '../boardStore';

/** The board of a board document, with the buttons to move through the game. */
export function BoardPane() {
  const { board } = useSettings();
  // The engine that guesses the piece meant, loaded before the first guess
  useEffect(() => {
    if (board.destinationMoves) warmMoveGuesser();
  }, [board.destinationMoves]);
  return (
    <GameBoard
      view={useBoardView()}
      coordinates={board.coordinates}
      animation={board.animation}
      lastMove={board.lastMove}
      navigation={board.navigation}
      destinationMoves={board.destinationMoves}
      guessMove={guessMove}
    />
  );
}
