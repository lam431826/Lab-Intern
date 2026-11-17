/** Cấu hình lúc chạy (public/config.js, image nginx ghi đè) ưu tiên hơn biến build-time VITE_*. */
interface RuntimeConfig {
  clerkPublishableKey?: string
}

declare global {
  interface Window {
    __APP_CONFIG__?: RuntimeConfig
  }
}

const runtime: RuntimeConfig = (typeof window !== 'undefined' && window.__APP_CONFIG__) || {}

export const config = {
  clerkPublishableKey: runtime.clerkPublishableKey || import.meta.env.VITE_CLERK_PUBLISHABLE_KEY || '',
  timeZone: 'Asia/Ho_Chi_Minh',
} as const
