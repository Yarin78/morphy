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
import { showToast } from './toastStore';
import { SaveToDatabaseDialog } from './SaveToDatabaseDialog';
import { registerSaver, type SaveMode, setUnsaved } from './unsavedStore';

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
  const { databases, gameState, selectedGame, gameInfoServices, onGameReady, canSave, saving, save, saveAs } =
    useGameDocument(databaseId, doc.gameId ?? null, {
      onCreated: (createdIn, gameId) =>
        dispatch({ type: 'updateBoard', id: doc.id, changes: { databaseId: createdIn, gameId } }),
      onError: showError,
      onSaved: showToast,
    });

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
          showToast(`No game ${white ?? '?'} – ${black ?? '?'} in this database`);
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
    [databaseId, dispatch]
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

  // The game's moves and header, to tell whether they've changed since it was loaded or saved;
  // moving through the game doesn't change them
  const { game, version } = view;
  const content = useMemo(
    () => JSON.stringify({ moves: game.toMoves(), tags: game.tagValues() }),
    // version: the game is changed in place
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [game, version]
  );
  // A game just loaded is as it was saved
  const [savedContent, setSavedContent] = useState<{ game: GameTree; content: string } | null>(null);
  if (savedContent?.game !== game) setSavedContent({ game, content });
  const unsaved = savedContent?.game === game && savedContent.content !== content;
  useEffect(() => setUnsaved(doc.id, unsaved), [doc.id, unsaved]);
  useEffect(() => () => setUnsaved(doc.id, false), [doc.id]);

  // A game in no database is saved to one picked in a dialog; Save As always picks one, maybe
  // the game's own for a copy there
  const [picking, setPicking] = useState<{ mode: SaveMode; pick: (id: string | null) => void } | null>(null);
  const pickDatabase = (mode: SaveMode) =>
    new Promise<string | null>((resolve) =>
      setPicking({
        mode,
        pick: (id) => {
          setPicking(null);
          resolve(id);
        },
      })
    );

  const database = databases?.find((db) => db.id === databaseId);
  const writable = !database?.readOnly;

  // Saves the game, picking the database first when it needs one; once saved, what was saved is
  // what the game is compared with
  const saveGame = async (mode: SaveMode): Promise<boolean> => {
    const before = { game, content };
    const targetId = mode === 'save' && databaseId ? databaseId : await pickDatabase(mode);
    if (!targetId) return false;
    const saved = mode === 'saveAs' ? await saveAs(targetId) : await save(databaseId ? undefined : targetId);
    if (saved) setSavedContent(before);
    return saved;
  };
  // For closing the board to save it with
  useEffect(() => registerSaver(doc.id, { canSave: writable, save: saveGame }));
  useEffect(() => () => registerSaver(doc.id, null), [doc.id]);

  const databaseName = database?.displayName ?? databaseId;
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
          save={() => void saveGame('save')}
          saveAs={() => void saveGame('saveAs')}
          unsaved={unsaved}
          savePicksDatabase={!databaseId}
          readOnlyDatabase={database?.readOnly ? database.displayName : null}
          canSave={canSave}
          saving={saving}
          status={status}
          error={gameState.kind === 'error' ? gameState.message : null}
        />
        <div className="board-grid">{children}</div>
      </div>
      <GameDialogs view={view} />
      {picking && (
        <SaveToDatabaseDialog
          title={picking.mode === 'saveAs' ? 'Save a copy of the game to' : 'Save the game to'}
          databases={databases}
          currentDatabaseId={databaseId}
          onSave={picking.pick}
          onCancel={() => picking.pick(null)}
        />
      )}
    </BoardViewContext.Provider>
  );
}
