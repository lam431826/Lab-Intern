import type { ReactNode } from 'react'
import { ForbiddenPage } from '@/app/pages/StatusPages'
import type { Role } from '@/lib/roles'
import { useMe } from './useMe'

/** Chặn route theo vai trò cho dễ dùng; backend vẫn kiểm tra lại mọi request. */
export function RequireRole({ roles, children }: { roles: readonly Role[]; children: ReactNode }) {
  const me = useMe()
  return roles.includes(me.role) ? children : <ForbiddenPage />
}
