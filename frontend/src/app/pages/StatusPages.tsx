import { useClerk, useUser } from '@clerk/react'
import { Loader2, LogOut, RefreshCw, ShieldAlert, ShieldX, WifiOff } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { errorMessage } from '@/lib/errors'

function CenteredCard({
  icon,
  title,
  description,
  children,
  footer,
}: {
  icon: ReactNode
  title: string
  description: ReactNode
  children?: ReactNode
  footer?: ReactNode
}) {
  return (
    <main className="flex min-h-svh items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-2 text-muted-foreground">{icon}</div>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        {children && <CardContent className="space-y-2 text-sm">{children}</CardContent>}
        {footer && <CardFooter className="flex flex-wrap gap-2">{footer}</CardFooter>}
      </Card>
    </main>
  )
}

export function FullPageLoading({ label = 'Đang tải…' }: { label?: string }) {
  return (
    <main
      className="flex min-h-svh items-center justify-center gap-2 text-sm text-muted-foreground"
      role="status"
    >
      <Loader2 className="size-4 animate-spin" aria-hidden />
      {label}
    </main>
  )
}

function SignOutButton() {
  const { signOut } = useClerk()
  return (
    <Button variant="outline" onClick={() => signOut({ redirectUrl: '/sign-in' })}>
      <LogOut aria-hidden /> Đăng xuất
    </Button>
  )
}

/** Đăng nhập Clerk được nhưng hệ thống chưa có tài khoản cho email này. */
export function NoAccessPage() {
  const { user } = useUser()
  const email = user?.primaryEmailAddress?.emailAddress
  return (
    <CenteredCard
      icon={<ShieldX className="size-8" aria-hidden />}
      title="Chưa được cấp quyền"
      description="Bạn đã đăng nhập, nhưng tài khoản này chưa được cấp quyền dùng hệ thống tuyển dụng."
      footer={<SignOutButton />}
    >
      {email && (
        <p>
          Đang đăng nhập bằng <strong>{email}</strong>.
        </p>
      )}
      <p className="text-muted-foreground">
        Liên hệ Người tổ chức của DX Lab để được thêm tài khoản, hoặc đăng xuất rồi đăng nhập bằng đúng email
        đã được mời. Nếu tài khoản vừa được tạo, hãy đăng xuất rồi đăng nhập lại.
      </p>
    </CenteredCard>
  )
}

/** Backend trả 401 dù Clerk đã đăng nhập: thường do cấu hình (issuer, azp) hoặc đồng hồ máy lệch. */
export function SessionRejectedPage() {
  return (
    <CenteredCard
      icon={<ShieldAlert className="size-8" aria-hidden />}
      title="Máy chủ không chấp nhận phiên đăng nhập"
      description="Đăng nhập Clerk thành công nhưng máy chủ từ chối xác thực."
      footer={
        <>
          <Button onClick={() => window.location.reload()}>
            <RefreshCw aria-hidden /> Thử lại
          </Button>
          <SignOutButton />
        </>
      }
    >
      <p className="text-muted-foreground">
        Kiểm tra đồng hồ máy tính có đúng giờ không, và mở trang đúng địa chỉ đã cấu hình. Nếu vẫn lỗi, báo
        quản trị viên kiểm tra cấu hình Clerk của máy chủ (CLERK_ISSUER, CLERK_AZP).
      </p>
    </CenteredCard>
  )
}

export function LoadErrorPage({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <CenteredCard
      icon={<WifiOff className="size-8" aria-hidden />}
      title="Không tải được dữ liệu"
      description={errorMessage(error)}
      footer={
        <Button onClick={onRetry}>
          <RefreshCw aria-hidden /> Thử lại
        </Button>
      }
    />
  )
}

/** Script đăng nhập Clerk không tải được. Không dùng hook Clerk vì Clerk chưa sẵn sàng. */
export function AuthLoadErrorPage() {
  return (
    <CenteredCard
      icon={<WifiOff className="size-8" aria-hidden />}
      title="Không tải được phần đăng nhập"
      description="Kết nối tới dịch vụ đăng nhập Clerk bị gián đoạn."
      footer={
        <Button onClick={() => window.location.reload()}>
          <RefreshCw aria-hidden /> Tải lại trang
        </Button>
      }
    >
      <p className="text-muted-foreground">
        Kiểm tra kết nối mạng rồi tải lại trang. Nếu đang dùng trình chặn quảng cáo hoặc mạng nội bộ chặn tên
        miền *.clerk.accounts.dev, hãy cho phép tên miền này.
      </p>
    </CenteredCard>
  )
}

/** Thiếu VITE_CLERK_PUBLISHABLE_KEY (dev) hoặc CLERK_PUBLISHABLE_KEY của container (prod). Không dùng hook Clerk. */
export function ConfigErrorPage() {
  return (
    <CenteredCard
      icon={<ShieldAlert className="size-8" aria-hidden />}
      title="Thiếu cấu hình đăng nhập"
      description="Chưa có Clerk publishable key nên không thể đăng nhập."
    >
      <p className="text-muted-foreground">
        Máy dev: điền <code>VITE_CLERK_PUBLISHABLE_KEY</code> vào <code>.env</code> ở gốc repo rồi chạy lại{' '}
        <code>npm run dev</code>. Xem <code>docs/clerk-setup.md</code>.
      </p>
    </CenteredCard>
  )
}

/** Trong ứng dụng: vai trò không được vào trang này. */
export function ForbiddenPage() {
  return (
    <InlineMessage
      icon={<ShieldX className="size-8" aria-hidden />}
      title="Bạn không có quyền xem trang này"
      description="Trang này dành cho vai trò khác. Nếu bạn cần quyền truy cập, hãy liên hệ Người tổ chức."
    />
  )
}

export function NotFoundPage() {
  return (
    <InlineMessage
      icon={<span className="text-3xl font-semibold">404</span>}
      title="Không tìm thấy trang"
      description="Đường dẫn không tồn tại hoặc đã thay đổi."
    />
  )
}

function InlineMessage({
  icon,
  title,
  description,
}: {
  icon: ReactNode
  title: string
  description: string
}) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-3 py-16 text-center">
      <div className="text-muted-foreground">{icon}</div>
      <h1 className="text-lg font-semibold">{title}</h1>
      <p className="text-sm text-muted-foreground">{description}</p>
      <Button variant="outline" nativeButton={false} render={<Link to="/" />}>
        Về trang tổng quan
      </Button>
    </div>
  )
}
