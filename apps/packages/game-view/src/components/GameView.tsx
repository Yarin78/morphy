import { GameBoard } from './GameBoard';
import { GameDialogs } from './GameDialogs';
import { GameNotationPanel } from './GameNotationPanel';
import { useGameView } from './useGameView';
import type { GameViewOptions } from './useGameView';
import './GameView.css';

export type GameViewProps = GameViewOptions;

/** A game to view and edit: the board on the left, the notation on the right. */
export const GameView: React.FC<GameViewProps> = (props) => {
  const view = useGameView(props);
  return (
    <div className="game-view-container">
      <div className="game-view-board">
        <GameBoard view={view} />
      </div>
      <GameNotationPanel view={view} />
      <GameDialogs view={view} />
    </div>
  );
};
