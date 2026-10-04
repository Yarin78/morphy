/// <reference types="vitest/config" />
import { createReadStream, copyFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { createRequire } from 'node:module'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

// The multi-threaded engine needs SharedArrayBuffer, which browsers only allow on a page that
// is cross-origin isolated. Whatever serves the built app must send these headers too.
const CROSS_ORIGIN_ISOLATION = {
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Embedder-Policy': 'require-corp',
}

// The chess engines that run in the browser: Stockfish compiled to WebAssembly, from the
// stockfish package. Each is a worker script and its .wasm, which the script finds next to
// itself by name, so they're served as they are at /engines/ rather than bundled.
const ENGINE_FILES = ['stockfish-19', 'stockfish-19-single', 'stockfish-19-lite', 'stockfish-19-lite-single'].flatMap(
  (engine) => [`${engine}.js`, `${engine}.wasm`]
)
const engineDir = resolve(dirname(createRequire(import.meta.url).resolve('stockfish/package.json')), 'bin')

function engines(): Plugin {
  return {
    name: 'morphy-engines',
    configureServer(server) {
      server.middlewares.use('/engines/', (req, res, next) => {
        const file = req.url?.split('?')[0].replace(/^\//, '') ?? ''
        if (!ENGINE_FILES.includes(file)) return next()
        res.setHeader('Content-Type', file.endsWith('.wasm') ? 'application/wasm' : 'text/javascript')
        // The worker runs in its own context, which needs the isolation headers as well
        for (const [name, value] of Object.entries(CROSS_ORIGIN_ISOLATION)) res.setHeader(name, value)
        createReadStream(resolve(engineDir, file)).pipe(res)
      })
    },
    writeBundle(options) {
      const out = resolve(options.dir ?? 'dist', 'engines')
      mkdirSync(out, { recursive: true })
      for (const file of ENGINE_FILES) copyFileSync(resolve(engineDir, file), resolve(out, file))
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), engines()],
  server: {
    headers: CROSS_ORIGIN_ISOLATION,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    headers: CROSS_ORIGIN_ISOLATION,
  },
  test: {
    // The unit tests of this app and of the packages it uses
    dir: resolve(__dirname, '..'),
    include: ['morphy-web/src/**/*.test.ts', 'packages/*/src/**/*.test.ts'],
    // The board isn't drawn in the tests; see the stub
    alias: [{ find: /^react-chessground$/, replacement: resolve(__dirname, 'src/test/chessgroundStub.ts') }],
  },
  build: {
    // Multi-page app: the dev server serves any .html file at its path automatically,
    // but a production build needs every entry point listed explicitly.
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        searchTester: resolve(__dirname, 'search-tester.html'),
        boardTester: resolve(__dirname, 'board-tester.html'),
      },
    },
  },
})
