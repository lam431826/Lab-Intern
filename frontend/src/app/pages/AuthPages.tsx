import { SignIn, SignUp } from '@clerk/react'
import type { ReactNode } from 'react'

function AuthShell({ children }: { children: ReactNode }) {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-6 bg-muted/40 p-4">
      <div className="text-center">
        <p className="text-lg font-semibold">Hệ thống Tuyển dụng DX Lab</p>
        <p className="text-sm text-muted-foreground">
          Dành cho Giảng viên, Người tổ chức, Lead và hội đồng phỏng vấn
        </p>
      </div>
      {children}
    </main>
  )
}

export function SignInPage() {
  return (
    <AuthShell>
      <SignIn routing="path" path="/sign-in" signUpUrl="/sign-up" />
    </AuthShell>
  )
}

/** Chỉ người được mời đăng ký được (Sign-up mode: Restricted); link mời mang __clerk_ticket. */
export function SignUpPage() {
  return (
    <AuthShell>
      <SignUp routing="path" path="/sign-up" signInUrl="/sign-in" />
    </AuthShell>
  )
}
