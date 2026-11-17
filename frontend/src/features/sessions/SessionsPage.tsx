import { AlertTriangle, Download, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { saveFile } from '@/api/client'
import { useApi } from '@/api/useApi'
import { useCan } from '@/auth/useCan'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryState } from '@/components/common/QueryState'
import { TrackSelect } from '@/components/common/TrackSelect'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useTracks } from '@/features/admin/api'
import { toastError } from '@/lib/errors'
import { formatDate, formatTime } from '@/lib/format'
import { useSessions, type Session } from './api'
import { SessionFormDialog } from './SessionFormDialog'

const WEEKDAYS = ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy']

function dayHeading(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  return `${WEEKDAYS[new Date(y, m - 1, d).getDay()]}, ${formatDate(isoDate)}`
}

export function SessionsPage() {
  const api = useApi()
  const can = useCan()
  const tracks = useTracks()
  const [trackId, setTrackId] = useState('ALL')
  const [date, setDate] = useState('')
  const [creating, setCreating] = useState(false)
  const sessions = useSessions({
    trackId: trackId === 'ALL' ? undefined : Number(trackId),
    date: date || undefined,
  })
  const canExport = can.isOrganizer || can.isLecturer

  async function exportExcel() {
    try {
      saveFile(
        await api.download('/api/sessions/export', 'lich-phong-van.xlsx', {
          query: { trackId: trackId === 'ALL' ? undefined : trackId },
        }),
      )
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        title="Lịch phỏng vấn"
        description={
          can.me.role === 'MEMBER'
            ? 'Các ca bạn có tên trong hội đồng.'
            : 'Ca phỏng vấn theo ngạch, hội đồng và lịch của từng ứng viên.'
        }
        actions={
          <>
            {canExport && (
              <Button variant="outline" onClick={exportExcel}>
                <Download aria-hidden /> Xuất Excel
              </Button>
            )}
            {can.isOrganizer && (
              <Button onClick={() => setCreating(true)}>
                <Plus aria-hidden /> Tạo ca
              </Button>
            )}
          </>
        }
      />
      <div className="mb-4 grid gap-2 sm:grid-cols-[13rem_11rem_auto]">
        <TrackSelect value={trackId} onChange={setTrackId} options={tracks.data ?? []} allLabel="Mọi ngạch" />
        <Input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          aria-label="Lọc theo ngày"
        />
        {date && (
          <Button variant="ghost" onClick={() => setDate('')} className="justify-self-start">
            Mọi ngày
          </Button>
        )}
      </div>
      <QueryState query={sessions}>{(list) => <SessionList sessions={list} />}</QueryState>
      <SessionFormDialog open={creating} onOpenChange={setCreating} session={null} />
    </div>
  )
}

function SessionList({ sessions }: { sessions: Session[] }) {
  const byDay = useMemo(() => {
    const map = new Map<string, Session[]>()
    for (const s of sessions) map.set(s.sessionDate, [...(map.get(s.sessionDate) ?? []), s])
    return [...map.entries()]
  }, [sessions])

  if (sessions.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        Chưa có ca phỏng vấn nào.
      </p>
    )
  }
  return (
    <div className="space-y-6">
      {byDay.map(([day, list]) => (
        <section key={day} className="space-y-2">
          <h3 className="text-sm font-semibold text-muted-foreground">{dayHeading(day)}</h3>
          <div className="grid gap-2 md:grid-cols-2">
            {list.map((s) => {
              const active = s.slots.filter((x) => x.active)
              const attended = active.filter((x) => x.attendance === 'ATTENDED').length
              return (
                <Link
                  key={s.id}
                  to={`/sessions/${s.id}`}
                  className="rounded-xl focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Card size="sm" className="h-full transition-colors hover:bg-muted/50">
                    <CardContent className="space-y-2">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-medium">
                          {formatTime(s.startTime)}–{formatTime(s.endTime)} · {s.trackName}
                        </span>
                        {s.room && <Badge variant="outline">Phòng {s.room}</Badge>}
                      </div>
                      <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
                        <span>{active.length} ứng viên</span>
                        <span>{attended} có mặt</span>
                        <span>Hội đồng {s.panel.length} người</span>
                      </div>
                      {s.warnings.length > 0 && (
                        <div className="flex items-center gap-1 text-xs text-amber-700 dark:text-amber-400">
                          <AlertTriangle className="size-3.5" aria-hidden />
                          {s.warnings.length} cảnh báo
                        </div>
                      )}
                    </CardContent>
                  </Card>
                </Link>
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}
