import type { Schemas } from '@/api/client'

export type Role = Schemas['MeDto']['role']

export const ROLE_LABELS: Record<Role, string> = {
  LECTURER: 'Giảng viên',
  ORGANIZER: 'Người tổ chức',
  LEAD: 'Lead chuyên môn',
  MEMBER: 'Thành viên hội đồng',
}

export const ALL_ROLES: readonly Role[] = ['LECTURER', 'ORGANIZER', 'LEAD', 'MEMBER']
/** Xem được hồ sơ, kết quả theo ngạch (Lead: ngạch mình). */
export const STAFF_ROLES: readonly Role[] = ['LECTURER', 'ORGANIZER', 'LEAD']
