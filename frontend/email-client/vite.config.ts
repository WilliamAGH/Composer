import { defineConfig, type Plugin } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import sveltePreprocess from 'svelte-preprocess'

/**
 * Logs incoming requests to Vite dev server for debugging.
 * Only active in dev mode (serve command).
 */
function requestLogger(): Plugin {
  return {
    name: 'request-logger',
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        const url = req.url ?? ''
        // Skip noisy asset/HMR requests; log pages and API calls
        if (!url.includes('/@') && !url.includes('node_modules') && !url.endsWith('.ts') && !url.endsWith('.svelte')) {
          console.log(`[vite] ${req.method} ${url}`)
        }
        next()
      })
    }
  }
}

export default defineConfig({
  plugins: [
    svelte({ preprocess: sveltePreprocess() }),
    requestLogger()
  ],
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
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: 'localhost',
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            console.log(`[vite→api] ${req.method} ${req.url}`)
          })
        }
      },
      '/ui': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: 'localhost',
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            console.log(`[vite→api] ${req.method} ${req.url}`)
          })
        }
      }
    }
  }
})
