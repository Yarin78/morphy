import type { ReactNode } from 'react';
import type { IDockviewHeaderActionsProps } from 'dockview-react';
import { GameBoard, GameNotationPanel } from 'game-view';
import { TbExternalLink } from 'react-icons/tb';
import { type DatabaseView, useDatabaseView } from '../databaseStore';
import type { EntityKind } from '../documents';
import { openEntityAction } from '../openEntity';
import { useDocuments } from '../documentsStore';
import { useSettings } from '../settings';
import { entityColumns, entityLabel, entityTitle, toEntityType } from '../../search/columns';
import { SEARCH_KIND_SINGULAR } from '../../search/queries';

/**
 * The preview of what's picked in a database's search results: a game on a board with its
 * notation below, played through but not edited; or an entity's details and a way to its games.
 */
export function PreviewPane() {
  const { search } = useDatabaseView();
  return search.kind === 'games' ? <GamePreview /> : <EntityPreview />;
}

/** The game or guiding text picked in the game results, if one is. */
function pickedGame({ search }: DatabaseView): { id: number; type?: string } | undefined {
  const s = search.current;
  if (search.kind !== 'games' || s.selected == null) return undefined;
  return s.results?.rows[s.selected] as { id: number; type?: string } | undefined;
}

/** In the preview's tab row, on the right: opens the game previewed on a board. */
export function PreviewActions({ activePanel }: IDockviewHeaderActionsProps) {
  const database = useDatabaseView();
  const picked = pickedGame(database);
  if (activePanel?.id !== 'preview' || !picked || picked.type === 'text') return null;
  return (
    <div className="preview-actions">
      <button
        className="preview-open"
        onClick={() => database.openGame(picked.id)}
        title="Open the game on a board, to edit it"
      >
        <TbExternalLink /> Open on a Board
      </button>
    </div>
  );
}

function GamePreview() {
  const database = useDatabaseView();
  const { preview, view, openGame, previewKeys } = database;
  const { board } = useSettings();
  const picked = pickedGame(database);

  if (!picked) {
    return <div className="preview-empty">Pick a game in the results to preview it here.</div>;
  }
  if (picked.type === 'text') {
    return (
      <div className="preview-empty">
        <p>This is a guiding text, not a game.</p>
        <button className="preview-open" onClick={() => openGame(picked.id)}>
          <TbExternalLink /> Open on a Board
        </button>
      </div>
    );
  }

  return (
    // The preview takes the keys that move through the game once clicked, as the results do
    <div className="preview-pane" tabIndex={-1} onKeyDown={(e) => previewKeys(e)}>
      {preview.kind === 'error' ? (
        <div className="preview-empty pane-error">{preview.message}</div>
      ) : (
        <div className="preview-game">
          <div className="preview-board">
            {view.selectedGame && (
              <GameBoard
                view={view}
                coordinates={board.coordinates}
                animation={board.animation}
                lastMove={board.lastMove}
                // The results' keys move through the game; the buttons would only take room
                navigation={false}
                destinationMoves={false}
              />
            )}
          </div>
          <div className="preview-notation">{view.selectedGame && <GameNotationPanel view={view} />}</div>
        </div>
      )}
    </div>
  );
}

function EntityPreview() {
  const { search, databaseId } = useDatabaseView();
  const { dispatch } = useDocuments();
  const kind = search.kind;
  if (kind === 'games') return null;
  const s = search.current;
  const entity = s.selected != null ? (s.results?.rows[s.selected] as Record<string, unknown> | undefined) : undefined;
  if (!entity) {
    return (
      <div className="preview-empty">Pick a {SEARCH_KIND_SINGULAR[kind].toLowerCase()} in the results to see it here.</div>
    );
  }
  return (
    <EntityDetails kind={kind} entity={entity}>
      <div className="preview-entity-actions">
        <button
          className="search-button primary"
          title="Open in a document of its own, with its games"
          onClick={() => dispatch(openEntityAction(databaseId, kind, entity))}
        >
          <TbExternalLink /> Open
        </button>
        <button
          className="search-button"
          onClick={() => search.showGamesOf(kind, entity.id as number, entityLabel(kind, entity))}
        >
          Show Games
        </button>
      </div>
    </EntityDetails>
  );
}

/** An entity's details: what it is, its name or title, and its fields; and anything below them. */
export function EntityDetails({
  kind,
  entity,
  children,
}: {
  kind: EntityKind;
  entity: Record<string, unknown>;
  children?: ReactNode;
}) {
  const columns = entityColumns(toEntityType(kind)).filter((c) => c.key !== 'id');
  return (
    <div className="preview-entity">
      <div className="preview-entity-kind">{SEARCH_KIND_SINGULAR[kind]}</div>
      <h2>{entityTitle(kind, entity)}</h2>
      <dl>
        {columns.map((c) => {
          const value = c.render(entity);
          if (value === '' || value == null) return null;
          return (
            <div key={c.key} className="preview-entity-row">
              <dt>{c.label}</dt>
              <dd>{value}</dd>
            </div>
          );
        })}
      </dl>
      {children}
    </div>
  );
}
