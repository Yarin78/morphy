# Morphy Apps

Node.js/React applications for working with the morphy-service API.

This is an npm workspace: run `npm install` once from here (`apps/`), not from
inside an individual app or package - it installs and hoists dependencies for
everything below. Each app still has its own `package.json` and its own
`dev`/`build`/`lint`/`preview` scripts, so `cd apps/<app> && npm run dev` works
exactly as if it were standalone.

## Apps

- **morphy-web** — The main Morphy app at `/`, plus
  two multi-page test pages that exercise the morphy-service API and the
  `game-view` package in isolation:
  - `/search-tester.html` — Debug and test the game/entity search API.
    Clicking a game's id opens it in the board tester, in a new tab.
  - `/board-tester.html` — A single chess board + header + move-notation
    view, driven by `?db=<databaseId>&game=<gameId>` query params.

## Packages

Shared, reusable code lives under `packages/`, consumed by apps via the
workspace (e.g. `"game-view": "*"` in an app's `package.json`).

- **packages/game-view** — The chess board + header + move-notation UI
  (`GameView`), ported from `~/src/yarin-chess`. No build step: apps compile
  its TypeScript source directly as part of their own Vite/tsc pipeline.
