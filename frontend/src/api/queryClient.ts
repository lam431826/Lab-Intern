import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './client'

/** Không thử lại lỗi 4xx (quyền, dữ liệu sai, xung đột): thử lại cũng ra cùng kết quả. */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        retry: (count, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
      },
      mutations: { retry: false },
    },
  })
}
