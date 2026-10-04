/// <reference types="vitest/config" />
import { resolve } from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
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
