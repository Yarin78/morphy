import { GameBoard } from 'game-view';
import { useBoardSettings } from '../boardSettings';
import { useBoardView } from '../boardStore';

/** The board of a board document, with the buttons to move through the game. */
export function BoardPane() {
  const [settings] = useBoardSettings();
  return <GameBoard view={useBoardView()} coordinates={settings.coordinates} animation={settings.animation} />;
}
