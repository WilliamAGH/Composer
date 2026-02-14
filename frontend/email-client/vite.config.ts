import { defineConfig, type Plugin } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { resolve, sep } from 'node:path';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { IncomingMessage, ServerResponse } from 'node:http';

// ESM equivalent of __dirname
const __filename = fileURLToPath(import.meta.url);
const __dirname = resolve(__filename, '..');

/**
 * Logs incoming requests to Vite dev server for debugging.
 * Only active in dev mode (serve command).
 */
function requestLogger(): Plugin {
  return {
    name: 'request-logger',
    configureServer(server) {
      server.middlewares.use((req: IncomingMessage, _res: ServerResponse, next: () => void) => {
        const url = req.url ?? '';
        // Skip noisy asset/HMR requests; log pages and API calls
        if (!url.includes('/@') && !url.includes('node_modules') && !url.endsWith('.ts') && !url.endsWith('.svelte')) {
          console.log(`[vite] ${req.method} ${url}`);
        }
        next();
      });
    },
  };
}

/**
 * Serves Spring Boot static resources directly during Vite dev.
 * Ensures feature parity with production by serving /js/* and /css/* from
 * src/main/resources/static without requiring Spring Boot to be running.
 */
function serveSpringBootStatic(): Plugin {
  const staticRoot = resolve(__dirname, '../../src/main/resources/static');
  const basePrefix = '/app/email-client';
  return {
    name: 'serve-spring-boot-static',
    configureServer(server) {
      server.middlewares.use((req: IncomingMessage, res: ServerResponse, next: () => void) => {
        let url = req.url ?? '';
        // Strip base prefix if present (Vite may prepend it in dev mode)
        if (url.startsWith(basePrefix)) {
          url = url.slice(basePrefix.length);
        }
        // Serve /js/* and /css/* from Spring Boot static resources
        if (url.startsWith('/js/') || url.startsWith('/css/')) {
          const filePath = resolve(staticRoot, url.slice(1));
          // Guard against path traversal (e.g., /js/../../../etc/passwd)
          if (!filePath.startsWith(staticRoot + sep)) {
            next();
            return;
          }
          if (existsSync(filePath)) {
            const ext = url.split('.').pop() ?? '';
            const mimeTypes: Record<string, string> = {
              js: 'application/javascript',
              css: 'text/css',
              json: 'application/json',
            };
            res.setHeader('Content-Type', mimeTypes[ext] || 'application/octet-stream');
            res.end(readFileSync(filePath));
            return;
          }
        }
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [
    svelte(),
    requestLogger(),
    serveSpringBootStatic(),
  ],
  base: '/app/email-client/',
  build: {
    outDir: '../../src/main/resources/static/app/email-client',
    assetsDir: 'assets',
    sourcemap: true,
    rollupOptions: {
      external: ['/js/email-renderer.js'],
      output: {
        entryFileNames: 'email-client.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]',
      },
    },
  },
  server: {
    port: 5183,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        cookieDomainRewrite: 'localhost',
        configure: (proxy) => {
          proxy.on('proxyReq', (_proxyReq, req) => {
            console.log(`[vite→api] ${req.method} ${req.url}`);
          });
        },
      },
      '/ui': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        cookieDomainRewrite: 'localhost',
        configure: (proxy) => {
          proxy.on('proxyReq', (_proxyReq, req) => {
            console.log(`[vite→api] ${req.method} ${req.url}`);
          });
        },
      },
    },
  },
});
