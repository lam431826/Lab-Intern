import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { ApiError } from '@/api/client'
import { useApi } from '@/api/useApi'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { config } from '@/lib/config'

/**
 * Trang tạm của F0: kiểm tra proxy /api tới backend. Chưa đăng nhập nên /api/me phải trả 401.
 * F1 thay bằng luồng đăng nhập Clerk và khung ứng dụng.
 */
export function BootstrapCheck() {
  const api = useApi()
  const health = useQuery({
    queryKey: ['health'],
    queryFn: () => api.get<{ status: string }>('/actuator/health'),
  })
  const me = useQuery({ queryKey: ['me'], queryFn: () => api.get('/api/me'), retry: false })
  const meError = me.error instanceof ApiError ? me.error : null

  return (
    <main className="mx-auto flex min-h-svh max-w-xl flex-col justify-center gap-4 p-4">
      <Card>
        <CardHeader>
          <CardTitle>Hệ thống Tuyển dụng DX Lab</CardTitle>
          <CardDescription>Khung frontend (F0). Đăng nhập Clerk có ở F1.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <Row label="Backend (/actuator/health)">
            {health.isPending ? (
              <Badge variant="outline">Đang kiểm tra</Badge>
            ) : health.data?.status === 'UP' ? (
              <Badge>Hoạt động</Badge>
            ) : (
              <Badge variant="destructive">Không kết nối được</Badge>
            )}
          </Row>
          <Row label="API khi chưa đăng nhập (/api/me)">
            {me.isPending ? (
              <Badge variant="outline">Đang kiểm tra</Badge>
            ) : meError?.status === 401 ? (
              <Badge>401 như mong đợi</Badge>
            ) : (
              <Badge variant="destructive">
                {meError ? `${meError.status} ${meError.code}` : 'Bất thường'}
              </Badge>
            )}
          </Row>
          <Row label="Clerk publishable key">
            <Badge variant={config.clerkPublishableKey ? 'default' : 'destructive'}>
              {config.clerkPublishableKey ? 'Đã cấu hình' : 'Thiếu VITE_CLERK_PUBLISHABLE_KEY'}
            </Badge>
          </Row>
        </CardContent>
      </Card>
    </main>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      {children}
    </div>
  )
}
