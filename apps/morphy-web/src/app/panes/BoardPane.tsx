import { useCallback, useEffect, useMemo } from 'react';
import { GameView, quotedPosition } from 'game-view';
import type { GameTree, QuotationLink } from 'game-view';
import { findGameByPlayers } from '../../game/gameServices';
import { gameTitle, useGameDocument } from '../../game/useGameDocument';
import { useDocument, useDocuments } from '../documentsStore';

/** A board document's game: loaded from its database (or new), played through, edited and saved. */
export function BoardPane() {
  const doc = useDocument('board');
  const { dispatch } = useDocuments();
  const databaseId = doc.databaseId ?? null;
  const { databases, gameState, selectedGame, gameInfoServices, onGameReady, canSave, saving, save, message, showMessage } =
    useGameDocument(databaseId, doc.gameId ?? null, (_, gameId) =>
      dispatch({ type: 'updateBoard', id: doc.id, changes: { gameId } })
    );

  // The board is titled by the game's players, once it's loaded or saved
  const title = gameState.kind === 'loaded' ? (gameTitle(gameState.game) ?? undefined) : doc.title;
  useEffect(() => {
    if (title !== doc.title) dispatch({ type: 'updateBoard', id: doc.id, changes: { title } });
  }, [title, doc.title, doc.id, dispatch]);

  const quote = doc.quote;
  const initialMoveToShow = useMemo(
    () => (quote ? (game: GameTree) => quotedPosition(game, quote.fen, quote.index) : undefined),
    [quote]
  );

  // A quoted game refers to another game in the same database by its players, as a repertoire
  // refers to its other chapters; it opens on a new board, at the quoted position
  const onQuotationClick = useCallback(
    async (link: QuotationLink) => {
      if (!databaseId) return;
      const { white, black } = link.quote.header;
      try {
        const target = await findGameByPlayers(databaseId, white, black);
        if (target == null) {
          showMessage(`No game ${white ?? '?'} – ${black ?? '?'} in this database`);
          return;
        }
        dispatch({
          type: 'openBoard',
          databaseId,
          gameId: target,
          quote: { fen: link.fen, index: link.quote.unknown ?? 0 },
        });
      } catch (err) {
        showMessage(err instanceof Error ? err.message : String(err));
      }
    },
    [databaseId, dispatch, showMessage]
  );

  const databaseName = databases?.find((db) => db.id === databaseId)?.displayName ?? databaseId;
  const status = !databaseId
    ? 'Not in a database'
    : gameState.kind === 'loaded'
      ? `${databaseName} — game ${gameState.game.id}`
      : `${databaseName} — new game`;

  return (
    <div className="board-pane">
      <div className="board-toolbar">
        <span className="board-status">{status}</span>
        <button onClick={save} disabled={!canSave} title={!databaseId ? 'No database to save to' : undefined}>
          {saving ? 'Saving…' : 'Save'}
        </button>
        {message && <span className="board-message">{message}</span>}
        {gameState.kind === 'error' && <span className="board-error">{gameState.message}</span>}
      </div>
      <div className="board-game">
        <GameView
          selectedGame={selectedGame}
          initialOrientation="white"
          onGameReady={onGameReady}
          gameInfoServices={gameInfoServices}
          initialMoveToShow={initialMoveToShow}
          onQuotationClick={onQuotationClick}
        />
      </div>
    </div>
  );
}
