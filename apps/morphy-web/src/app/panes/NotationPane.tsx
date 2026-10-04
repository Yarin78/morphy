import { GameNotationPanel } from 'game-view';
import { useBoardView } from '../boardStore';

/** The notation of a board document's game, with its header and the bar to annotate moves. */
export function NotationPane() {
  return <GameNotationPanel view={useBoardView()} />;
}
