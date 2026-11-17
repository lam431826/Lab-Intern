import { screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME_ORGANIZER, renderWithApp } from '@/test/renderWithApp'
import type { Settings } from './api'
import { SettingsPage, settingsSchema } from './SettingsPage'

const SETTINGS: Settings = {
  weightGeneral: 0.4,
  weightProfessional: 0.6,
  passThreshold: 7,
  slaHours: 36,
  slotMinutes: 15,
}

afterEach(() => vi.unstubAllGlobals())

describe('settingsSchema', () => {
  it('hai trọng số phải cộng bằng 1 (chấp nhận sai số dấu phẩy động)', () => {
    expect(
      settingsSchema.safeParse({ ...SETTINGS, weightGeneral: 0.7, weightProfessional: 0.3 }).success,
    ).toBe(true)
    expect(
      settingsSchema.safeParse({ ...SETTINGS, weightGeneral: 0.1 + 0.2, weightProfessional: 0.7 }).success,
    ).toBe(true)
    expect(
      settingsSchema.safeParse({ ...SETTINGS, weightGeneral: 0.5, weightProfessional: 0.6 }).success,
    ).toBe(false)
  })
})

describe('SettingsPage', () => {
  it('sửa trọng số chung thì trọng số chuyên môn tự đổi, rồi lưu đúng giá trị', async () => {
    const { user, calls } = renderWithApp(<SettingsPage />, {
      routes: {
        'GET /api/settings': () => [200, SETTINGS],
        'PUT /api/settings': (body) => [200, body],
      },
    })
    const wg = await screen.findByLabelText('Trọng số điểm chung')
    await user.clear(wg)
    await user.type(wg, '0.3')
    expect(screen.getByLabelText('Trọng số điểm chuyên môn')).toHaveValue(0.7)
    expect(screen.getByText(/tổng 7,30/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Lưu tham số' }))
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      ...SETTINGS,
      weightGeneral: 0.3,
      weightProfessional: 0.7,
    })
    expect(await screen.findByText('Đã lưu tham số chấm')).toBeInTheDocument()
  })

  it('Lead chỉ xem, không sửa được', async () => {
    renderWithApp(<SettingsPage />, {
      me: { ...ME_ORGANIZER, role: 'LEAD' },
      routes: { 'GET /api/settings': () => [200, SETTINGS] },
    })
    expect(await screen.findByLabelText('Điểm sàn')).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Lưu tham số' })).not.toBeInTheDocument()
  })
})
