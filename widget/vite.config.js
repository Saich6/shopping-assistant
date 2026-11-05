import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// `npm run build` produces ONE self-contained script (React bundled inside) in the Spring Boot static folder,
// because the widget must be injectable with a single <script> tag (like the Adobe Target script).
export default defineConfig(({ command }) => ({
  plugins: [react()],
  // Libraries built for the browser must replace this Node-only variable, or React fails with "process is not defined".
  define: command === 'build' ? { 'process.env.NODE_ENV': JSON.stringify('production') } : {},
  build: {
    lib: {
      entry: 'src/main.jsx',
      name: 'ShoppingAssistantWidget',
      formats: ['iife'],
      fileName: () => 'shopping-assistant.js'
    },
    outDir: '../src/main/resources/static/widget',
    emptyOutDir: true,
    target: 'es2018'
  },
  server: { proxy: { '/api': 'http://localhost:8080' } }, // `npm run dev` talks to the running Spring Boot app
  test: { environment: 'jsdom', globals: true, setupFiles: './src/test/setup.js' }
}));
