import {
  CalendarDays,
  ClipboardCheck,
  FileText,
  LayoutDashboard,
  ListChecks,
  Send,
  Settings2,
  Trophy,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { ALL_ROLES, STAFF_ROLES, type Role } from './roles'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  roles: readonly Role[]
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

/**
 * Menu theo vai trò. Chỉ để ẩn/hiện cho gọn; quyền thật do backend kiểm tra
 * (ví dụ Lead vào được Hồ sơ nhưng chỉ thấy ngạch mình).
 */
export const NAV: NavGroup[] = [
  {
    label: 'Công việc',
    items: [
      { to: '/', label: 'Tổng quan', icon: LayoutDashboard, roles: ALL_ROLES },
      { to: '/scoring', label: 'Cần chấm', icon: ClipboardCheck, roles: ALL_ROLES },
      { to: '/sessions', label: 'Lịch phỏng vấn', icon: CalendarDays, roles: ALL_ROLES },
      { to: '/candidates', label: 'Hồ sơ ứng viên', icon: FileText, roles: STAFF_ROLES },
      { to: '/results', label: 'Kết quả', icon: Trophy, roles: STAFF_ROLES },
      { to: '/submissions', label: 'Gửi nhu cầu tuyển dụng', icon: Send, roles: STAFF_ROLES },
    ],
  },
  {
    label: 'Quản trị',
    items: [
      { to: '/admin/users', label: 'Tài khoản', icon: Users, roles: ['ORGANIZER', 'LECTURER'] },
      { to: '/admin/tracks', label: 'Ngạch và tiêu chí', icon: ListChecks, roles: STAFF_ROLES },
      { to: '/admin/settings', label: 'Tham số chấm', icon: Settings2, roles: ['ORGANIZER', 'LECTURER'] },
    ],
  },
]

export function navFor(role: Role): NavGroup[] {
  return NAV.map((g) => ({ ...g, items: g.items.filter((i) => i.roles.includes(role)) })).filter(
    (g) => g.items.length > 0,
  )
}
