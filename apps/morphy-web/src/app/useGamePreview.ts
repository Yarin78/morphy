import { useEffect, useMemo, useState } from 'react';
import { type ChessGame, type GameViewState, useGameView } from 'game-view';
import { fetchGame } from '../api/client';
import { gameDtoToChessGame } from '../game/gameDtoAdapter';
import { previewGame as previewed } from '../game/previewGame';
import type { DatabaseSearch } from '../search/useDatabaseSearch';
import type { PreviewGame } from './databaseStore';
import { useSettings } from './settings';

// How long a game is picked before it's fetched for the preview, so that holding a key down
// through the results doesn't fetch every game passed
const PREVIEW_DELAY_MS = 120;

export interface GamePreview {
  preview: PreviewGame;
  /** The previewed game, played through on the preview's board */
  view: GameViewState;
  /** Handles the keys that move through the previewed game; whether it did */
  previewKeys: (e: React.KeyboardEvent) => boolean;
}

/**
 * The game picked in a search's game results, fetched and played through for its preview: in a
 * database's document, or in an entity's.
 */
export function useGamePreview(databaseId: string, search: DatabaseSearch): GamePreview {
  // The game picked in the game results, fetched for the preview
  const gameResults = search.kind === 'games' ? search.current : null;
  const pickedRow =
    gameResults?.selected != null ? (gameResults.results?.rows[gameResults.selected] as { id: number; type?: string }) : null;
  const pickedId = pickedRow && pickedRow.type !== 'text' ? pickedRow.id : null;
  const [preview, setPreview] = useState<PreviewGame>({ kind: 'none' });
  useEffect(() => {
    if (pickedId == null) return;
    let cancelled = false;
    // The game shown before stays, as it is, until this one is fetched, so the board isn't taken
    // away and put back, or faded, on every game picked
    const timer = setTimeout(() => {
      fetchGame(databaseId, pickedId)
        .then((game) => !cancelled && setPreview({ kind: 'loaded', game }))
        .catch(
          (err: unknown) =>
            !cancelled &&
            setPreview({ kind: 'error', gameId: pickedId, message: err instanceof Error ? err.message : String(err) })
        );
    }, PREVIEW_DELAY_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [databaseId, pickedId]);

  const previewGame = preview.kind === 'loaded' ? preview.game : null;
  const { notation: notationSettings, search: searchSettings } = useSettings();
  // Only the main line's moves, unless the settings show the variations or the commentary
  const { previewVariations: variations, previewCommentary: commentary } = searchSettings;
  const selectedGame = useMemo<ChessGame | null>(
    () => (previewGame ? previewed(gameDtoToChessGame(previewGame), { variations, commentary }) : null),
    [previewGame, variations, commentary]
  );
  // The preview takes no keys of its own: the results and the preview pane pass them on, so the
  // arrows up and down pick a game and left and right move through it
  const view = useGameView({
    selectedGame,
    initialOrientation: 'white',
    readOnly: true,
    keysEnabled: false,
    notation: notationSettings.moveNotation,
  });

  // The game shown until the one picked is fetched is the one picked before: it's loading
  const shown: PreviewGame =
    pickedId == null
      ? { kind: 'none' }
      : (preview.kind === 'loaded' && preview.game.id !== pickedId) ||
          (preview.kind === 'error' && preview.gameId !== pickedId) ||
          preview.kind === 'none'
        ? { kind: 'loading', gameId: pickedId }
        : preview;

  return {
    preview: shown,
    view,
    previewKeys: (e) => {
      // Not while the game picked is loading, as the keys would move through the one before
      if (shown.kind !== 'loaded' || e.altKey || e.ctrlKey || e.metaKey) return false;
      const step: Record<string, () => void> = {
        ArrowLeft: () => view.canGoBack() && view.goToPreviousMove(),
        ArrowRight: () => view.canGoForward() && view.handleNextMove(),
        Home: () => view.canGoBack() && view.goToStart(),
        End: () => view.canGoForward() && view.goToEnd(),
      };
      const action = step[e.key];
      if (!action) return false;
      e.preventDefault();
      action();
      return true;
    },
  };
}
