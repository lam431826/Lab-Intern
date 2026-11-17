import { describe, expect, it } from 'vitest'
import { navFor } from './nav'

const labels = (role: Parameters<typeof navFor>[0]) => navFor(role).flatMap((g) => g.items.map((i) => i.to))

describe('navFor', () => {
  it('Thành viên chỉ thấy tổng quan, cần chấm, lịch; không có nhóm Quản trị', () => {
    expect(labels('MEMBER')).toEqual(['/', '/scoring', '/sessions'])
    expect(navFor('MEMBER').map((g) => g.label)).toEqual(['Công việc'])
  })

  it('Lead thấy hồ sơ, kết quả, ngạch nhưng không quản lý tài khoản và tham số', () => {
    const lead = labels('LEAD')
    expect(lead).toContain('/candidates')
    expect(lead).toContain('/admin/tracks')
    expect(lead).not.toContain('/admin/users')
    expect(lead).not.toContain('/admin/settings')
  })

  it('Người tổ chức và Giảng viên thấy đủ mục', () => {
    expect(labels('ORGANIZER')).toHaveLength(9)
    expect(labels('LECTURER')).toHaveLength(9)
  })
})
