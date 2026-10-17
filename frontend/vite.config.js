import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Бэкенд: Payara Micro, контекст /spacemarine.
// Для `npm run dev` нужен туннель: ssh -p 2222 -L 61131:localhost:61131 <логин>@helios.cs.ifmo.ru
const BACKEND = 'http://localhost:61131';
const CONTEXT = '/spacemarine';

export default defineConfig({
  plugins: [react()],
  base: './', // относительные пути: приложение работает под любым контекстом war
  build: {
    outDir: '../src/main/webapp/app',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': { target: BACKEND, rewrite: (p) => CONTEXT + p },
      '/ws': { target: BACKEND, ws: true, rewrite: (p) => CONTEXT + p },
    },
  },
});
