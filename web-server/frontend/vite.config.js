import { resolve } from 'node:path';
import { defineConfig } from 'vite';

export default defineConfig({
  build: {
    rollupOptions: {
      input: {
        home: resolve(import.meta.dirname, 'index.html'),
        catalog: resolve(import.meta.dirname, 'pages/catalog.html'),
        movieDetail: resolve(import.meta.dirname, 'pages/movie-detail.html'),
        admin: resolve(import.meta.dirname, 'pages/admin/index.html')
      }
    }
  }
});
