import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import type { DockviewApi } from 'dockview-react';
import { GameDialogs, quotedPosition, useGameView } from 'game-view';
import type { GameTree, QuotationLink } from 'game-view';
import { findGameByPlayers } from '../game/gameServices';
import { gameTitle, useGameDocument } from '../game/useGameDocument';
import { BoardCommands } from './BoardCommands';
import { BoardViewContext } from './boardStore';
import type { BoardDocument as BoardDoc } from './documents';
import { useDocuments } from './documentsStore';
import { showError } from './errorStore';
import { SaveToDatabaseDialog } from './SaveToDatabaseDialog';

/**
 * A board document: its game, loaded from its database (or new), played through, edited and
 * saved. The game is kept here, above the document's grid, and shared with its panes.
 */
export function BoardDocument({
  doc,
  api,
  active,
  children,
}: {
  doc: BoardDoc;
  /** The document's grid, once it's ready */
  api: DockviewApi | null;
  active: boolean;
  children: ReactNode;
}) {
  const { dispatch } = useDocuments();
  const databaseId = doc.databaseId ?? null;
  const { databases, gameState, selectedGame, gameInfoServices, onGameReady, canSave, saving, save, message, showMessage } =
    useGameDocument(databaseId, doc.gameId ?? null, {
      onCreated: (createdIn, gameId) =>
        dispatch({ type: 'updateBoard', id: doc.id, changes: { databaseId: createdIn, gameId } }),
      onError: showError,
    });

  // A game in no database is saved to one picked in a dialog
  const [pickingDatabase, setPickingDatabase] = useState(false);
  const handleSave = () => {
    if (databaseId) void save();
    else setPickingDatabase(true);
  };

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
        showError('Opening the quoted game failed', err);
      }
    },
    [databaseId, dispatch, showMessage]
  );

  // Only the board shown takes the keys
  const view = useGameView({
    selectedGame,
    initialOrientation: 'white',
    initialMoveToShow,
    onGameReady,
    gameInfoServices,
    onQuotationClick,
    keysEnabled: active,
  });

  const databaseName = databases?.find((db) => db.id === databaseId)?.displayName ?? databaseId;
  const status = !databaseId
    ? 'Not in a database'
    : gameState.kind === 'loaded'
      ? `${databaseName} — game ${gameState.game.id}`
      : `${databaseName} — new game`;

  return (
    <BoardViewContext.Provider value={view}>
      <div className="board-document">
        <BoardCommands
          doc={doc}
          api={api}
          active={active}
          view={view}
          save={handleSave}
          savePicksDatabase={!databaseId}
          canSave={canSave}
          saving={saving}
          status={status}
          message={message}
          error={gameState.kind === 'error' ? gameState.message : null}
        />
        <div className="board-grid">{children}</div>
      </div>
      <GameDialogs view={view} />
      {pickingDatabase && (
        <SaveToDatabaseDialog
          databases={databases}
          onSave={(id) => {
            setPickingDatabase(false);
            void save(id);
          }}
          onCancel={() => setPickingDatabase(false)}
        />
      )}
    </BoardViewContext.Provider>
  );
}
