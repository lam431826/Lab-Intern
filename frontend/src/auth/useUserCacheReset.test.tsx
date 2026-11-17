import { QueryClientProvider, useQuery } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { createQueryClient } from '@/api/queryClient'
import { useUserCacheReset } from './useUserCacheReset'

function setup(initialUser: string | null | undefined) {
  const client = createQueryClient()
  let n = 0
  const fetchMe = vi.fn<() => Promise<string>>(async () => `me-${++n}`)
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  const hook = renderHook(
    ({ userId }: { userId: string | null | undefined }) => {
      useUserCacheReset(userId)
      return useQuery({ queryKey: ['me'], queryFn: fetchMe })
    },
    { wrapper, initialProps: { userId: initialUser } },
  )
  return { client, fetchMe, hook }
}

describe('useUserCacheReset', () => {
  it('lần tải đầu (Clerk vừa có userId) không xóa query đang chạy', async () => {
    const { client, fetchMe, hook } = setup(undefined)
    hook.rerender({ userId: 'user_1' })
    await waitFor(() => expect(hook.result.current.data).toBe('me-1'))
    expect(fetchMe).toHaveBeenCalledTimes(1)
    expect(client.getQueryCache().find({ queryKey: ['me'] })).toBeDefined()
  })

  it('đổi sang người dùng khác thì bỏ dữ liệu cũ và tải lại cho người mới', async () => {
    const { client, fetchMe, hook } = setup('user_1')
    await waitFor(() => expect(hook.result.current.data).toBe('me-1'))
    client.setQueryData(['other-user-data'], 'bí mật của user_1')

    hook.rerender({ userId: 'user_2' })
    await waitFor(() => expect(hook.result.current.data).toBe('me-2'))
    expect(fetchMe).toHaveBeenCalledTimes(2)
    expect(client.getQueryData(['other-user-data'])).toBeUndefined()
  })

  it('đăng xuất cũng bỏ dữ liệu của người vừa thoát', async () => {
    const { client, hook } = setup('user_1')
    await waitFor(() => expect(hook.result.current.data).toBe('me-1'))
    client.setQueryData(['candidates'], ['dữ liệu cũ'])
    hook.rerender({ userId: null })
    await waitFor(() => expect(client.getQueryData(['candidates'])).toBeUndefined())
  })
})
