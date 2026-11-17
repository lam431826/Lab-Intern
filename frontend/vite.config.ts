/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Dùng chung .env ở gốc repo với backend. Vite chỉ nhúng biến có tiền tố VITE_ vào bundle,
  // nên CLERK_SECRET_KEY, DB_PASSWORD... không bao giờ lọt ra trình duyệt.
  envDir: '..',
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // CLERK_AZP của backend là http://localhost:5173: đổi cổng thì token bị từ chối
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
      '/actuator/health': { target: backend },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
