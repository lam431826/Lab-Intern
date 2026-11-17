import { screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME_ORGANIZER, renderWithApp } from '@/test/renderWithApp'
import type { User } from './api'
import { UsersPage } from './UsersPage'

const USERS: User[] = [
  { id: 1, email: 'org@dxlab.vn', fullName: 'Người tổ chức', role: 'ORGANIZER', active: true, linked: true },
  { id: 2, email: 'an@dxlab.vn', fullName: 'Nguyễn Văn An', role: 'LEAD', active: true, linked: false },
  { id: 3, email: 'binh@dxlab.vn', fullName: 'Trần Bình', role: 'MEMBER', active: false, linked: true },
]

afterEach(() => vi.unstubAllGlobals())

describe('UsersPage', () => {
  it('Người tổ chức thấy danh sách, trạng thái và chỉ gửi lại lời mời cho người chưa đăng nhập', async () => {
    renderWithApp(<UsersPage />, { routes: { 'GET /api/users': () => [200, USERS] } })

    const an = (await screen.findByText('Nguyễn Văn An')).closest('tr')!
    expect(within(an).getByText('Chưa đăng nhập')).toBeInTheDocument()
    expect(within(an).getByRole('button', { name: /Gửi lại lời mời/ })).toBeInTheDocument()

    const binh = screen.getByText('Trần Bình').closest('tr')!
    expect(within(binh).getByText('Đã khóa')).toBeInTheDocument()
    expect(within(binh).queryByRole('button', { name: /Gửi lại lời mời/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Thêm tài khoản/ })).toBeInTheDocument()
  })

  it('tìm không cần dấu', async () => {
    const { user } = renderWithApp(<UsersPage />, { routes: { 'GET /api/users': () => [200, USERS] } })
    await screen.findByText('Nguyễn Văn An')

    await user.type(screen.getByLabelText('Tìm tài khoản'), 'nguyen van')
    await waitFor(() => expect(screen.queryByText('Trần Bình')).not.toBeInTheDocument())
    expect(screen.getByText('Nguyễn Văn An')).toBeInTheDocument()
  })

  it('Giảng viên chỉ xem, không có nút thêm hay sửa', async () => {
    renderWithApp(<UsersPage />, {
      me: { ...ME_ORGANIZER, role: 'LECTURER' },
      routes: { 'GET /api/users': () => [200, USERS] },
    })
    await screen.findByText('Nguyễn Văn An')
    expect(screen.queryByRole('button', { name: /Thêm tài khoản/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Sửa/ })).not.toBeInTheDocument()
  })

  it('tạo tài khoản: lỗi email trùng hiện thông báo của backend, nội dung đã nhập được giữ', async () => {
    const { user, calls } = renderWithApp(<UsersPage />, {
      routes: {
        'GET /api/users': () => [200, USERS],
        'POST /api/users': () => [
          409,
          { code: 'USER_EMAIL_EXISTS', message: 'Email an@dxlab.vn đã có tài khoản' },
        ],
      },
    })
    await screen.findByText('Nguyễn Văn An')
    await user.click(screen.getByRole('button', { name: /Thêm tài khoản/ }))

    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Email'), 'an@dxlab.vn')
    await user.type(within(dialog).getByLabelText('Họ và tên'), 'An mới')
    await user.click(within(dialog).getByRole('button', { name: 'Tạo tài khoản' }))

    expect(await screen.findByText('Email an@dxlab.vn đã có tài khoản')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Email')).toHaveValue('an@dxlab.vn')
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      email: 'an@dxlab.vn',
      fullName: 'An mới',
      role: 'MEMBER',
      sendInvite: true,
    })
  })

  it('kiểm tra email phía client trước khi gửi', async () => {
    const { user, calls } = renderWithApp(<UsersPage />, { routes: { 'GET /api/users': () => [200, USERS] } })
    await screen.findByText('Nguyễn Văn An')
    await user.click(screen.getByRole('button', { name: /Thêm tài khoản/ }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Email'), 'khong-phai-email')
    await user.click(within(dialog).getByRole('button', { name: 'Tạo tài khoản' }))

    expect(await within(dialog).findByText('Email không đúng định dạng')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })
})
