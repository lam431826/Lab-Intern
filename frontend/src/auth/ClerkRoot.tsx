import { ClerkProvider, useAuth, useClerk } from '@clerk/react'
import { viVN } from '@clerk/localizations'
import { shadcn } from '@clerk/ui/themes'
import { useCallback, type ReactNode } from 'react'
import { Outlet, useNavigate } from 'react-router'
import { ApiProvider } from '@/api/ApiProvider'
import { config } from '@/lib/config'
import { useUserCacheReset } from './useUserCacheReset'
import { AuthLoadErrorPage, ConfigErrorPage } from '@/app/pages/StatusPages'

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
  const { status } = useClerk()
  useUserCacheReset(userId)

  const tokenProvider = useCallback(() => getToken(), [getToken])
  // Không tải được script Clerk (mạng chập chờn, bị chặn): báo rõ thay vì kẹt ở "Đang tải…"
  if (status === 'error') return <AuthLoadErrorPage />
  return <ApiProvider getToken={tokenProvider}>{children}</ApiProvider>
}
