import { Link } from 'react-router'
import { useMe } from '@/auth/useMe'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { navFor } from '@/lib/nav'
import { ROLE_LABELS } from '@/lib/roles'

/** Trang tổng quan tạm của F1; F8 thay bằng dashboard số liệu và việc cần làm. */
export function HomePage() {
  const me = useMe()
  const shortcuts = navFor(me.role)
    .flatMap((g) => g.items)
    .filter((i) => i.to !== '/')
  const leadNote =
    me.role !== 'LEAD'
      ? ''
      : me.leadTrackCodes.length > 0
        ? ` · phụ trách ngạch ${me.leadTrackCodes.join(', ')}`
        : ' · chưa được gán ngạch'

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Xin chào, {me.fullName}</h2>
        <p className="text-sm text-muted-foreground">
          {ROLE_LABELS[me.role]}
          {leadNote}
        </p>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {shortcuts.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            className="rounded-xl focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Card className="h-full transition-colors hover:bg-muted/50">
              <CardHeader>
                <item.icon className="size-5 text-muted-foreground" aria-hidden />
                <CardTitle className="text-base">{item.label}</CardTitle>
              </CardHeader>
            </Card>
          </Link>
        ))}
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Đang phát triển</CardTitle>
          <CardDescription>Số liệu tổng quan và việc cần làm sẽ có ở mốc F8.</CardDescription>
        </CardHeader>
      </Card>
    </div>
  )
}
