import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    emptyOutDir: true,
    rollupOptions: {
      // print.html is the resume print entry loaded by the renderer service (docs/phase2/03 §5.4).
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        print: fileURLToPath(new URL('./print.html', import.meta.url)),
      },
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        // The isolated local JobProof backend runs on 18081. Keep the
        // override for CI/legacy setups, but do not silently proxy this app
        // to the unrelated service that commonly occupies 8080.
        target: process.env.JOBPROOF_API_TARGET || 'http://127.0.0.1:18081',
        changeOrigin: true,
      },
      '/internal': {
        target: process.env.JOBPROOF_API_TARGET || 'http://127.0.0.1:18081',
        changeOrigin: true,
      },
    },
  },
})
