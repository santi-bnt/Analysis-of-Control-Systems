import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig, loadEnv } from 'vite';
import basicSsl from '@vitejs/plugin-basic-ssl';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.BACKEND_TARGET || 'http://127.0.0.1:8002';
  const cert = env.HTTPS_CERT && env.HTTPS_KEY
    ? { cert: readFileSync(resolve(env.HTTPS_CERT)), key: readFileSync(resolve(env.HTTPS_KEY)) }
    : undefined;
  const proxy = {
    '/api': { target, changeOrigin: true },
    '/health': { target, changeOrigin: true },
    '/ws': {
      target, ws: true, changeOrigin: true,
      // Este servidor local termina HTTPS y se identifica ante el backend.
      // La conexión WebSocket sigue exigiendo la clave como primer mensaje.
      headers: { Origin: env.BACKEND_WS_ORIGIN || 'http://localhost:3000' },
    },
  };
  return {
    plugins: cert ? [] : [basicSsl({ name: 'motor-quest3', certDir: '.cert' })],
    server: { host: '0.0.0.0', port: 5173, strictPort: true, https: cert || {}, proxy },
    preview: { host: '0.0.0.0', port: 5173, strictPort: true, https: cert || {}, proxy },
    build: { target: 'es2022' },
  };
});
