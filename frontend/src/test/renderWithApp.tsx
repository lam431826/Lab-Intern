import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import { ApiProvider } from '@/api/ApiProvider'
import { createQueryClient } from '@/api/queryClient'
import { ME_KEY, type Me } from '@/auth/useMe'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'

/** Trả [status, json] cho API JSON, hoặc một Response tùy ý (ví dụ file tải về). */
export type Handler = (body: unknown) => [status: number, json?: unknown] | Response

export interface RecordedCall {
  method: string
  path: string
  query: URLSearchParams
  body: unknown
}

export const ME_ORGANIZER: Me = {
  id: 1,
  email: 'org@dxlab.vn',
  fullName: 'Người tổ chức',
  role: 'ORGANIZER',
  leadTrackCodes: [],
}

/**
 * Render một trang trong ứng dụng đã đăng nhập: người dùng {@code me} có sẵn trong cache,
 * API giả lập theo "METHOD /đường-dẫn" (không gồm query string). Route không khai báo trả 404.
 */
export function renderWithApp(
  ui: ReactNode,
  {
    me = ME_ORGANIZER,
    routes = {},
    path = '/',
  }: { me?: Me; routes?: Record<string, Handler>; path?: string } = {},
) {
  const queryClient = createQueryClient()
  queryClient.setQueryData(ME_KEY, me)
  const calls: RecordedCall[] = []

  vi.stubGlobal(
    'fetch',
    vi.fn<typeof globalThis.fetch>(async (input, init) => {
      const url = new URL(String(input), 'http://localhost')
      const method = init?.method ?? 'GET'
      const body = typeof init?.body === 'string' ? JSON.parse(init.body) : init?.body
      calls.push({ method, path: url.pathname, query: url.searchParams, body })
      const handler = routes[`${method} ${url.pathname}`]
      const result: ReturnType<Handler> = handler
        ? handler(body)
        : [404, { code: 'NOT_FOUND', message: 'Không tìm thấy' }]
      if (result instanceof Response) return result
      const [status, json] = result
      return new Response(json === undefined ? null : JSON.stringify(json), {
        status,
        headers: { 'Content-Type': 'application/json' },
      })
    }),
  )

  const user = userEvent.setup()
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ApiProvider getToken={async () => 'test-token'}>
        <MemoryRouter initialEntries={[path]}>
          <TooltipProvider>{ui}</TooltipProvider>
        </MemoryRouter>
      </ApiProvider>
      <Toaster />
    </QueryClientProvider>,
  )
  return { ...result, user, calls, queryClient }
}
