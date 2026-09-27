// Ambient module declaration for CSS imports, so this package type-checks
// standalone (it has no vite.config.ts of its own to supply vite/client's
// version of this). Consuming apps' own Vite tooling handles the imports
// for real at bundle time.
declare module '*.css';
