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
