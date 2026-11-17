import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ApiProvider } from '@/api/ApiProvider'
import { createQueryClient } from '@/api/queryClient'
import { Toaster } from '@/components/ui/sonner'
import App from './App'
import './index.css'

const queryClient = createQueryClient()
// F0 chưa đăng nhập: chưa có token. F1 thay bằng getToken của Clerk.
const anonymous = async () => null

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ApiProvider getToken={anonymous}>
      <QueryClientProvider client={queryClient}>
        <App />
        <Toaster richColors position="top-right" />
      </QueryClientProvider>
    </ApiProvider>
  </StrictMode>,
)
