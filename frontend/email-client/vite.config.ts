import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import sveltePreprocess from 'svelte-preprocess'

export default defineConfig({
  plugins: [svelte({ preprocess: sveltePreprocess() })],
  base: '/app/email-client/',
  build: {
    outDir: '../../src/main/resources/static/app/email-client',
    assetsDir: 'assets',
    sourcemap: true,
    rollupOptions: {
      output: {
        entryFileNames: 'email-client.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]'
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
