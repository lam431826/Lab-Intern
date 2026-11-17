import { Outlet } from 'react-router'
import { ApiError } from '@/api/client'
import { FullPageLoading, LoadErrorPage, NoAccessPage, SessionRejectedPage } from '@/app/pages/StatusPages'
import { useMeQuery } from './useMe'

/**
 * Sau khi Clerk đăng nhập, hỏi backend người này là ai (/api/me).
 * 403: đăng nhập Clerk được nhưng chưa có tài khoản trong hệ thống (hoặc bị khóa, thiếu claim email).
 * 401: backend từ chối token (sai issuer/azp, lệch đồng hồ): lỗi cấu hình, không tự đăng xuất để tránh vòng lặp.
 */
export function AccessGate() {
  const me = useMeQuery()
  if (me.isPending) return <FullPageLoading />
  if (me.error) {
    if (me.error instanceof ApiError && me.error.status === 403) return <NoAccessPage />
    if (me.error instanceof ApiError && me.error.status === 401) return <SessionRejectedPage />
    return <LoadErrorPage error={me.error} onRetry={() => me.refetch()} />
  }
  return <Outlet />
}
