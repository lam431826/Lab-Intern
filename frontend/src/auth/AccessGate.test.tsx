import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiProvider } from '@/api/ApiProvider'
import { createQueryClient } from '@/api/queryClient'
import { AccessGate } from './AccessGate'
import { RequireRole } from './RequireRole'

vi.mock('@clerk/react', () => ({
  useUser: () => ({ user: { primaryEmailAddress: { emailAddress: 'nguoi.la@gmail.com' } } }),
  useClerk: () => ({ signOut: vi.fn<() => Promise<void>>() }),
}))

const ME = { id: 1, email: 'm@dxlab.vn', fullName: 'Thành viên A', role: 'MEMBER', leadTrackCodes: [] }

function mockMe(status: number, body: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn<typeof globalThis.fetch>(
      async () =>
        new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }),
    ),
  )
}

function renderAt(path: string) {
  const router = createMemoryRouter(
    [
      {
        element: <AccessGate />,
        children: [
          { index: true, element: <p>Trang chính</p> },
          {
            path: 'admin/users',
            element: (
              <RequireRole roles={['ORGANIZER', 'LECTURER']}>
                <p>Quản lý tài khoản</p>
              </RequireRole>
            ),
          },
        ],
      },
    ],
    { initialEntries: [path] },
  )
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <ApiProvider getToken={async () => 'token'}>
        <RouterProvider router={router} />
      </ApiProvider>
    </QueryClientProvider>,
  )
}

describe('AccessGate', () => {
  beforeEach(() => vi.unstubAllGlobals())
  afterEach(() => vi.unstubAllGlobals())

  it('có tài khoản thì vào ứng dụng', async () => {
    mockMe(200, ME)
    renderAt('/')
    expect(await screen.findByText('Trang chính')).toBeInTheDocument()
  })

  it('403: đăng nhập Clerk được nhưng chưa được cấp quyền, hiện email đang dùng', async () => {
    mockMe(403, { code: 'FORBIDDEN', message: 'Bạn không có quyền thực hiện thao tác này' })
    renderAt('/')
    expect(await screen.findByText('Chưa được cấp quyền')).toBeInTheDocument()
    expect(screen.getByText('nguoi.la@gmail.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Đăng xuất/ })).toBeInTheDocument()
  })

  it('401: báo máy chủ từ chối phiên thay vì tự đăng xuất lặp vòng', async () => {
    mockMe(401, { code: 'UNAUTHORIZED', message: 'Phiên đăng nhập không hợp lệ' })
    renderAt('/')
    expect(await screen.findByText('Máy chủ không chấp nhận phiên đăng nhập')).toBeInTheDocument()
  })

  it('vai trò không đủ thì thấy trang không có quyền', async () => {
    mockMe(200, ME)
    renderAt('/admin/users')
    expect(await screen.findByText('Bạn không có quyền xem trang này')).toBeInTheDocument()
    expect(screen.queryByText('Quản lý tài khoản')).not.toBeInTheDocument()
  })

  it('đủ vai trò thì vào được', async () => {
    mockMe(200, { ...ME, role: 'ORGANIZER' })
    renderAt('/admin/users')
    expect(await screen.findByText('Quản lý tài khoản')).toBeInTheDocument()
  })
})
