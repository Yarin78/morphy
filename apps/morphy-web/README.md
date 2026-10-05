# Morphy Web

A multi-page Vite app: the main Morphy app plus two test pages that exercise
the morphy-service API and the `game-view` package in isolation.

## Prerequisites

- morphy-service running on `http://localhost:8080`
- Node.js 18+

## Run

```bash
# From the apps/ workspace root
cd apps
npm install
cd morphy-web
npm run dev
```

The app runs on `http://localhost:5173` and proxies `/api` requests to the
morphy-service.

## Chess engine

The Engine pane runs Stockfish 19 in the browser: the WebAssembly build from the
`stockfish` package (GPL-3.0), served at `/engines/` by a small Vite plugin in
`vite.config.ts` and copied into `dist/engines/` by a build. The multi-threaded
engine needs the page to be cross-origin isolated, so the dev and preview servers
send `Cross-Origin-Opener-Policy: same-origin` and
`Cross-Origin-Embedder-Policy: require-corp`; whatever serves the built app must
send them too. The engine code (`src/engine`) speaks UCI over a transport, so a
remote engine can be added as another transport.

A second engine, the lite single-threaded build in a worker of its own, guesses
the piece meant when a move is made by pressing the square it goes to
(`src/engine/moveGuesser.ts`): the best of the moves to that square, by a 0.1 s
search.

## Pages

- `/` — the Morphy app (`src/app`). A navigator on the left lists the open
  documents: Home, All Databases, Logs, and any opened databases and
  boards; its Settings opens the settings dialog (`src/app/settings.ts`),
  whose settings apply to every document. Each document has its own Dockview grid of panes, which fills the
  rest of the screen while the document is active. A database document has a
  Search pane (`src/search`) on the left: tabs for games, players, events
  and the other entities, a form with the common filters, searched as it's
  changed, more filters a click away, or the query typed in full; the results below, in columns picked from
  those of the search tester and resized by dragging their edges (kept in
  localStorage, the same in every database). The Preview pane on the
  right shows the game picked on a board with its notation (↑↓ pick a game, ←→
  move through it), or the entity picked, with a way to its games.
  Double-clicking a game opens it in a board document (`src/game`, shared with
  the board tester). The open documents and their layouts are kept in
  localStorage.
- `/search-tester` (or `/search-tester.html`) — debug and test the game/entity
  search API, with the query plans and the raw request and response.
  1. Select a database from the dropdown (e.g. `world-ch` when using the test database)
  2. Use the filter query language or typed parameters to build your search
  3. Click **Search** to execute
  4. View results and the debug panel for raw request/response
  5. Click a game's id to open it in the board tester, in a new tab
- `/board-tester` (or `/board-tester.html`) — a single chess board + header + move-notation view,
  driven by `?db=<databaseId>&game=<gameId>` query params:
  - no params → an empty, unbound board
  - `?db=X` → an empty board bound to `X`; saving creates a new game
  - `?db=X&game=Y` → loads that game; saving replaces it
  - `?game=Y` with no `db` → invalid, redirected to the empty state

## Filter Query Language

Examples (from morphy-service docs):

- `result:1-0` — White wins
- `rating:2600..2800,mode=any` — Either player 2600–2800
- `player.name:Carlsen,position=white` — White player name contains "Carlsen"
- `result:1-0 AND eco:B90` — Combined filters
