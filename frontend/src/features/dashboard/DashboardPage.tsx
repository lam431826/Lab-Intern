import { useQuery } from '@tanstack/react-query'
import { CheckCheck, ClipboardCheck, FileSearch, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import type { Schemas } from '@/api/client'
import { useApi } from '@/api/useApi'
import { useMe } from '@/auth/useMe'
import { QueryState } from '@/components/common/QueryState'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ROLE_LABELS } from '@/lib/roles'
import { cn } from '@/lib/utils'

export type Dashboard = Schemas['DashboardDto']
type TrackStats = Schemas['TrackStats']

export function useDashboard() {
  const api = useApi()
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: ({ signal }) => api.get<Dashboard>('/api/dashboard', { signal }),
  })
}

/** Việc cần làm của người đang đăng nhập; chỉ hiện thẻ phù hợp vai trò. */
export function taskCards(d: Dashboard, role: Schemas['MeDto']['role']) {
  const cards: { key: string; label: string; value: number; to: string; icon: LucideIcon; hint: string }[] = [
    {
      key: 'score',
      label: 'Phiếu cần chấm',
      value: d.myTasks.sheetsToScore,
      to: '/scoring',
      icon: ClipboardCheck,
      hint: 'Ứng viên đã có mặt trong ca bạn ngồi mà bạn chưa nộp phiếu',
    },
  ]
  if (role === 'ORGANIZER' || role === 'LEAD') {
    cards.push({
      key: 'screen',
      label: 'Hồ sơ chờ duyệt CV',
      value: d.myTasks.screeningToDo,
      to: '/candidates',
      icon: FileSearch,
      hint: role === 'LEAD' ? 'Hồ sơ của ngạch bạn phụ trách' : 'Hồ sơ mọi ngạch',
    })
  }
  if (role === 'LECTURER') {
    cards.push({
      key: 'approve',
      label: 'Kết luận chờ phê duyệt',
      value: d.myTasks.toApprove,
      to: '/results',
      icon: CheckCheck,
      hint: 'Kết luận đã chốt, cần Giảng viên phê duyệt',
    })
  }
  return cards
}

export function DashboardPage() {
  const me = useMe()
  const dashboard = useDashboard()
  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Xin chào, {me.fullName}</h2>
        <p className="text-sm text-muted-foreground">
          {ROLE_LABELS[me.role]}
          {me.role === 'LEAD' &&
            me.leadTrackCodes.length > 0 &&
            ` · phụ trách ngạch ${me.leadTrackCodes.join(', ')}`}
        </p>
      </div>
      <QueryState query={dashboard} skeletonRows={3}>
        {(d) => (
          <>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {taskCards(d, me.role).map((c) => (
                <Link
                  key={c.key}
                  to={c.to}
                  className="rounded-xl focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Card
                    className={cn(
                      'h-full transition-colors hover:bg-muted/50',
                      c.value > 0 && 'ring-1 ring-primary/30',
                    )}
                  >
                    <CardContent className="flex items-start gap-3">
                      <c.icon className="mt-1 size-5 text-muted-foreground" aria-hidden />
                      <div>
                        <div
                          className={cn(
                            'text-3xl font-semibold tabular-nums',
                            c.value === 0 && 'text-muted-foreground',
                          )}
                        >
                          {c.value}
                        </div>
                        <div className="font-medium">{c.label}</div>
                        <div className="text-xs text-muted-foreground">
                          {c.value === 0 ? 'Không còn việc nào' : c.hint}
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              ))}
            </div>
            {d.tracks.length > 0 && <TrackProgress tracks={d.tracks} />}
          </>
        )}
      </QueryState>
    </div>
  )
}

const COLUMNS: { key: keyof TrackStats; label: string; title: string }[] = [
  { key: 'candidates', label: 'Hồ sơ', title: 'Tổng hồ sơ tiếp nhận' },
  { key: 'screeningPending', label: 'Chờ duyệt', title: 'Hồ sơ chờ sàng lọc CV' },
  { key: 'screeningPassed', label: 'Đạt CV', title: 'Đạt sàng lọc CV' },
  { key: 'scheduled', label: 'Có lịch', title: 'Đã xếp lịch phỏng vấn' },
  { key: 'attended', label: 'Có mặt', title: 'Đã phỏng vấn (điểm danh có mặt)' },
  { key: 'scoresComplete', label: 'Đủ phiếu', title: 'Hội đồng đã nộp đủ phiếu' },
  { key: 'decided', label: 'Kết luận', title: 'Đã chốt kết luận' },
  { key: 'approved', label: 'Đã duyệt', title: 'Kết luận đã được Giảng viên phê duyệt' },
]

function TrackProgress({ tracks }: { tracks: TrackStats[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Tiến độ theo ngạch</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Ngạch</TableHead>
                {COLUMNS.map((c) => (
                  <TableHead key={c.key} className="text-right" title={c.title}>
                    {c.label}
                  </TableHead>
                ))}
                <TableHead
                  className="text-right"
                  title="Trúng tuyển và chuyển vào, trừ người từ chối offer / chỉ tiêu"
                >
                  Chỉ tiêu
                </TableHead>
                <TableHead className="text-right" title="Ứng viên đã nhận offer">
                  Nhận offer
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tracks.map((t) => (
                <TableRow key={t.trackId}>
                  <TableCell className="font-medium">{t.trackName}</TableCell>
                  {COLUMNS.map((c) => (
                    <TableCell key={c.key} className="text-right tabular-nums">
                      {t[c.key] as number}
                      {c.key === 'screeningPending' && t.screeningOverdue > 0 && (
                        <span className="ml-1 text-xs text-destructive" title="Quá hạn SLA">
                          ({t.screeningOverdue} quá hạn)
                        </span>
                      )}
                      {c.key === 'attended' && t.absent > 0 && (
                        <span className="ml-1 text-xs text-muted-foreground">+{t.absent} vắng</span>
                      )}
                    </TableCell>
                  ))}
                  <TableCell
                    className={cn(
                      'text-right font-medium tabular-nums',
                      t.seatsTaken >= t.quota && 'text-emerald-700 dark:text-emerald-400',
                    )}
                  >
                    {t.seatsTaken}/{t.quota}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{t.offersAccepted}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}
