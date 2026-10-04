import { useCallback, useMemo, useState } from 'react';
import { GameView, quotedPosition } from 'game-view';
import type { GameTree, QuotationLink } from 'game-view';
import { findGameByPlayers } from '../game/gameServices';
import { useGameDocument } from '../game/useGameDocument';
import { useDbGameParams } from './hooks/useDbGameParams';
import './App.css';

/** The position to open a quoted game at, from the fen and index params, if they're given. */
function readQuoteTarget(): { gameId: number; fen: string; index: number } | null {
  const params = new URLSearchParams(window.location.search);
  const gameId = Number(params.get('game'));
  const fen = params.get('fen');
  if (!fen || !Number.isFinite(gameId)) return null;
  return { gameId, fen, index: Number(params.get('index')) || 0 };
}

function App() {
  const { databaseId, gameId, setLoadedGame } = useDbGameParams();
  const {
    databases,
    gameState,
    selectedGame,
    gameInfoServices,
    onGameReady,
    canSave,
    saving,
    save,
    message,
    showMessage,
  } = useGameDocument(databaseId, gameId, setLoadedGame);

  // A quoted game refers to another game in the same database by its players, as a repertoire
  // refers to its other chapters. It's opened in a new tab, at the position the quotation links
  // to, given by the fen and index params
  const [quoteTarget] = useState(readQuoteTarget);
  const initialMoveToShow = useMemo(
    () =>
      quoteTarget && quoteTarget.gameId === gameId
        ? (game: GameTree) => quotedPosition(game, quoteTarget.fen, quoteTarget.index)
        : undefined,
    [quoteTarget, gameId]
  );

  const handleQuotationClick = useCallback(
    async (link: QuotationLink) => {
      if (!databaseId) return;
      const { white, black } = link.quote.header;
      // Opened now, while the click still counts as one, as browsers block opening it later
      const tab = window.open('', '_blank');
      try {
        const target = await findGameByPlayers(databaseId, white, black);
        if (target == null) {
          tab?.close();
          showMessage(`No game ${white ?? '?'} – ${black ?? '?'} in this database`);
          return;
        }
        const params = new URLSearchParams({
          db: databaseId,
          game: String(target),
          fen: link.fen,
          index: String(link.quote.unknown ?? 0),
        });
        // Absolute, as the new tab is about:blank so far
        const url = new URL(`${window.location.pathname}?${params}`, window.location.href).href;
        if (tab) {
          tab.location.href = url;
        } else {
          window.location.href = url;
        }
      } catch (err) {
        tab?.close();
        showMessage(err instanceof Error ? err.message : String(err));
      }
    },
    [databaseId, showMessage]
  );

  const databaseName = databases?.find((db) => db.id === databaseId)?.displayName ?? databaseId;
  const statusText = !databaseId
    ? 'No database (unbound)'
    : gameState.kind === 'loaded'
      ? `${databaseName} — game ${gameState.game.id}`
      : `${databaseName} — new game`;

  return (
    <div className="app">
      <header className="topbar">
        <h1>Board Tester</h1>
        <span className="status">{statusText}</span>
        <button onClick={save} disabled={!canSave} title={!databaseId ? 'No database to save to' : undefined}>
          {saving ? 'Saving…' : 'Save'}
        </button>
        {message && <span className="save-message">{message}</span>}
        {gameState.kind === 'error' && <span className="error-message">{gameState.message}</span>}
      </header>
      <main>
        <GameView
          selectedGame={selectedGame}
          initialOrientation="white"
          onGameReady={onGameReady}
          gameInfoServices={gameInfoServices}
          initialMoveToShow={initialMoveToShow}
          onQuotationClick={handleQuotationClick}
        />
      </main>
    </div>
  );
}

export default App;
