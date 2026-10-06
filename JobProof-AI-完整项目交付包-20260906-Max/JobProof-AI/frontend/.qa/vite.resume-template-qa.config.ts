import { mergeConfig } from 'vite'

import baseConfig from '../vite.config.ts'

export default mergeConfig(baseConfig, {
  server: {
    port: 5199,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18081',
        changeOrigin: true,
      },
      '/internal': {
        target: 'http://127.0.0.1:18081',
        changeOrigin: true,
      },
    },
  },
})
