# Morphy Web

A multi-page Vite app: the main app (currently empty - a placeholder for what
comes later) plus two test pages that exercise the morphy-service API and the
`game-view` package in isolation.

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

## Pages

- `/` — the main app. Empty for now.
- `/search-tester.html` — debug and test the game/entity search API.
  1. Select a database from the dropdown (e.g. `world-ch` when using the test database)
  2. Use the filter query language or typed parameters to build your search
  3. Click **Search** to execute
  4. View results and the debug panel for raw request/response
  5. Click a game's id to open it in the board tester, in a new tab
- `/board-tester.html` — a single chess board + header + move-notation view,
  driven by `?db=<databaseId>&game=<gameId>` query params:
  - no params → an empty, unbound board
  - `?db=X` → an empty board bound to `X`; saving creates a new game
  - `?db=X&game=Y` → loads that game; saving replaces it
  - `?game=Y` with no `db` → invalid, redirected to the empty state

- `/layout-dockview.html`, `/layout-flexlayout.html`, `/layout-mosaic.html`,
  `/layout-rcdock.html` — throwaway demos of four window-layout libraries
  (Dockview, FlexLayout, react-mosaic, rc-dock), for choosing the main app's
  windowing framework. Each has the same dummy windows, a shared menu bar
  (`src/layout-demos/shared`), split/new-tab buttons in the window headers, and
  saves its layout to localStorage. Every dummy window shows a `mounts` counter:
  if it goes up after moving a window, the library remounted it (losing state).

## Filter Query Language

Examples (from morphy-service docs):

- `result:1-0` — White wins
- `rating:2600..2800,mode=any` — Either player 2600–2800
- `player.name:Carlsen,position=white` — White player name contains "Carlsen"
- `result:1-0 AND eco:B90` — Combined filters
