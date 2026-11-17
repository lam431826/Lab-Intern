import { screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithApp } from '@/test/renderWithApp'
import type { User } from './api'
import { UserFormDialog } from './UserFormDialog'

const LEAD: User = {
  id: 7,
  email: 'lead@dxlab.vn',
  fullName: 'Lead A',
  role: 'LEAD',
  active: true,
  linked: false,
}

afterEach(() => vi.unstubAllGlobals())

describe('UserFormDialog (sửa)', () => {
  it('ô email bị khóa vẫn cho lưu và gửi đúng nội dung', async () => {
    const { user, calls } = renderWithApp(<UserFormDialog open user={LEAD} onOpenChange={() => {}} />, {
      routes: { 'PUT /api/users/7': (body) => [200, { ...LEAD, ...(body as object) }] },
    })
    const dialog = await screen.findByRole('dialog')
    const name = within(dialog).getByLabelText('Họ và tên')
    await user.clear(name)
    await user.type(name, 'Lead B')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu' }))

    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      fullName: 'Lead B',
      role: 'LEAD',
      active: true,
    })
  })

  it('lỗi nghiệp vụ hiện thông báo', async () => {
    const { user } = renderWithApp(<UserFormDialog open user={LEAD} onOpenChange={() => {}} />, {
      routes: {
        'PUT /api/users/7': () => [
          422,
          { code: 'CANNOT_CHANGE_SELF', message: 'Không tự đổi vai trò của mình được' },
        ],
      },
    })
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByText('Không tự đổi vai trò của mình được')).toBeInTheDocument()
  })
})
