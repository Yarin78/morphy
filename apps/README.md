# Morphy Apps

Node.js/React applications for debugging and working with the morphy-service API.

This is an npm workspace: run `npm install` once from here (`apps/`), not from
inside an individual app - it installs and hoists dependencies for every app
and package below. Each app still has its own `package.json` and its own
`dev`/`build`/`lint`/`preview` scripts, so `cd apps/<app> && npm run dev` works
exactly as if it were standalone.

## Apps

- **search-tester** — Debug and test the game search API
- **board-tester** — A single chess board + header + move-notation view, for
  testing the `game-view` package and morphy-service's game read/write API in
  isolation. Driven by `?db=<databaseId>&game=<gameId>` query params.

## Packages

Shared, reusable code lives under `packages/`, consumed by apps via the
workspace (e.g. `"game-view": "*"` in an app's `package.json`).

- **packages/game-view** — The chess board + header + move-notation UI
  (`GameView`), ported from `~/src/yarin-chess`. No build step: apps compile
  its TypeScript source directly as part of their own Vite/tsc pipeline.
