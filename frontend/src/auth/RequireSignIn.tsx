import { useAuth } from '@clerk/react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { FullPageLoading } from '@/app/pages/StatusPages'

/**
 * Chưa đăng nhập thì về trang đăng nhập. Link mời trong email Clerk mở trang gốc kèm
 * __clerk_ticket: chuyển sang trang đăng ký để người được mời tạo tài khoản bằng ticket đó.
 */
export function RequireSignIn() {
  const { isLoaded, isSignedIn } = useAuth()
  const location = useLocation()
  if (!isLoaded) return <FullPageLoading />
  if (!isSignedIn) {
    const params = new URLSearchParams(location.search)
    if (params.has('__clerk_ticket')) {
      return <Navigate to={`/sign-up${location.search}`} replace />
    }
    const back = location.pathname + location.search
    return (
      <Navigate
        to={back === '/' ? '/sign-in' : `/sign-in?redirect_url=${encodeURIComponent(back)}`}
        replace
      />
    )
  }
  return <Outlet />
}
