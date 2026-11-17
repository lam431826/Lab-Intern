import { ClerkProvider, useAuth } from '@clerk/react'
import { viVN } from '@clerk/localizations'
import { shadcn } from '@clerk/ui/themes'
import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, type ReactNode } from 'react'
import { Outlet, useNavigate } from 'react-router'
import { ApiProvider } from '@/api/ApiProvider'
import { config } from '@/lib/config'
import { ConfigErrorPage } from '@/app/pages/StatusPages'

/** Route gốc: Clerk dùng điều hướng của React Router; API lấy token từ phiên Clerk. */
export function ClerkRoot() {
  const navigate = useNavigate()
  if (!config.clerkPublishableKey) {
    return <ConfigErrorPage />
  }
  return (
    <ClerkProvider
      publishableKey={config.clerkPublishableKey}
      routerPush={(to) => navigate(to)}
      routerReplace={(to) => navigate(to, { replace: true })}
      signInUrl="/sign-in"
      signUpUrl="/sign-up"
      afterSignOutUrl="/sign-in"
      signInFallbackRedirectUrl="/"
      signUpFallbackRedirectUrl="/"
      localization={viVN}
      appearance={{ theme: shadcn }}
    >
      <ClerkApi>
        <Outlet />
      </ClerkApi>
    </ClerkProvider>
  )
}

function ClerkApi({ children }: { children: ReactNode }) {
  const { getToken, userId } = useAuth()
  const queryClient = useQueryClient()
  const lastUser = useRef(userId)

  // Đổi người dùng (đăng xuất, đăng nhập tài khoản khác): xóa sạch cache để không lộ dữ liệu người trước
  useEffect(() => {
    if (lastUser.current !== userId) {
      queryClient.clear()
      lastUser.current = userId
    }
  }, [userId, queryClient])

  const tokenProvider = useCallback(() => getToken(), [getToken])
  return <ApiProvider getToken={tokenProvider}>{children}</ApiProvider>
}
