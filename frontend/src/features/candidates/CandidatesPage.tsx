import { FileSpreadsheet, Plus, Search } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { Link } from 'react-router'
import { useCan } from '@/auth/useCan'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryState } from '@/components/common/QueryState'
import { TrackSelect } from '@/components/common/TrackSelect'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDateTime, formatScore } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  STATUS_LABELS,
  useCandidates,
  useCandidateSummary,
  type CandidateFilter,
  type ScreeningStatus,
  type TrackSummary,
} from './api'
import { CandidateFormDialog } from './CandidateFormDialog'
import { FlagBadges, ScheduledMark, ScreeningBadge } from './parts'

const STATUS_ITEMS = [
  { value: 'ALL', label: 'Mọi trạng thái' },
  ...(Object.keys(STATUS_LABELS) as ScreeningStatus[]).map((s) => ({ value: s, label: STATUS_LABELS[s] })),
]

export function CandidatesPage() {
  const can = useCan()
  const summary = useCandidateSummary()
  const [trackId, setTrackId] = useState('ALL')
  const [status, setStatus] = useState('ALL')
  const [overdue, setOverdue] = useState(false)
  const [q, setQ] = useState('')
  const [creating, setCreating] = useState(false)
  const query = useDeferredValue(q.trim())

  const filter: CandidateFilter = {
    trackId: trackId === 'ALL' ? undefined : Number(trackId),
    status: status === 'ALL' ? undefined : (status as ScreeningStatus),
    q: query || undefined,
    overdue,
  }
  const candidates = useCandidates(filter)

  /** Bấm vào một ô của bộ đếm thì lọc theo ngạch và trạng thái tương ứng. */
  function pick(t: TrackSummary, s: ScreeningStatus | 'ALL' | 'OVERDUE') {
    setTrackId(String(t.trackId))
    setStatus(s === 'OVERDUE' ? 'PENDING' : s)
    setOverdue(s === 'OVERDUE')
  }

  return (
    <div className="mx-auto max-w-7xl">
      <PageHeader
        title="Hồ sơ ứng viên"
        description={
          can.me.role === 'LEAD'
            ? 'Hồ sơ của ngạch bạn phụ trách. Duyệt CV trong hạn SLA để không làm chậm lịch phỏng vấn.'
            : 'Tiếp nhận và sàng lọc CV theo ngạch.'
        }
        actions={
          can.isOrganizer && (
            <>
              <Button variant="outline" nativeButton={false} render={<Link to="/candidates/import" />}>
                <FileSpreadsheet aria-hidden /> Nhập Excel
              </Button>
              <Button onClick={() => setCreating(true)}>
                <Plus aria-hidden /> Thêm hồ sơ
              </Button>
            </>
          )
        }
      />

      <QueryState query={summary} skeletonRows={1}>
        {(tracks) => (
          <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {tracks.map((t) => (
              <Card
                key={t.trackId}
                size="sm"
                className={cn(String(t.trackId) === trackId && 'ring-2 ring-primary')}
              >
                <CardContent className="space-y-2">
                  <button
                    type="button"
                    className="font-medium hover:underline"
                    onClick={() => pick(t, 'ALL')}
                  >
                    {t.trackName}
                  </button>
                  <div className="grid grid-cols-5 gap-1 text-center text-xs">
                    {(
                      [
                        ['ALL', 'Tổng', t.total, ''],
                        ['PENDING', 'Chờ', t.pending, ''],
                        ['PASSED', 'Đạt', t.passed, 'text-emerald-700 dark:text-emerald-400'],
                        ['REJECTED', 'Loại', t.rejected, 'text-muted-foreground'],
                        [
                          'OVERDUE',
                          'Quá hạn',
                          t.overdue,
                          t.overdue > 0 ? 'text-destructive' : 'text-muted-foreground',
                        ],
                      ] as const
                    ).map(([key, label, value, color]) => (
                      <button
                        key={key}
                        type="button"
                        onClick={() => pick(t, key)}
                        className="rounded-md py-1 hover:bg-muted"
                        aria-label={`${t.trackName}: ${label} ${value}`}
                      >
                        <div className={cn('text-base font-semibold tabular-nums', color)}>{value}</div>
                        <div className="text-muted-foreground">{label}</div>
                      </button>
                    ))}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </QueryState>

      <div className="mb-3 grid items-center gap-2 sm:grid-cols-[1fr_13rem_11rem_auto]">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="Tìm theo tên, Mã SV, email (không cần dấu)"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Tìm hồ sơ"
          />
        </div>
        <TrackSelect
          value={trackId}
          onChange={setTrackId}
          options={summary.data?.map((t) => ({ id: t.trackId, name: t.trackName })) ?? []}
          allLabel="Mọi ngạch"
        />
        <Select items={STATUS_ITEMS} value={status} onValueChange={(v) => setStatus(v ?? 'ALL')}>
          <SelectTrigger className="w-full" aria-label="Lọc theo trạng thái">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATUS_ITEMS.map((i) => (
              <SelectItem key={i.value} value={i.value}>
                {i.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <label className="flex items-center gap-2 text-sm">
          <Switch checked={overdue} onCheckedChange={setOverdue} aria-label="Chỉ hồ sơ quá hạn" />
          Quá hạn
        </label>
      </div>

      <QueryState query={candidates}>
        {(list) => (
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Mã SV</TableHead>
                  <TableHead>Họ tên</TableHead>
                  <TableHead>Ngạch</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="text-right">Điểm CV</TableHead>
                  <TableHead>Cần lưu ý</TableHead>
                  <TableHead>Hạn duyệt</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                      Không có hồ sơ phù hợp.
                    </TableCell>
                  </TableRow>
                ) : (
                  list.map((c) => (
                    <TableRow key={c.id}>
                      <TableCell className="font-mono text-xs">{c.studentCode}</TableCell>
                      <TableCell>
                        <Link to={`/candidates/${c.id}`} className="font-medium hover:underline">
                          {c.fullName}
                        </Link>
                        {c.scheduled && (
                          <div>
                            <ScheduledMark />
                          </div>
                        )}
                      </TableCell>
                      <TableCell>{c.trackName}</TableCell>
                      <TableCell>
                        <ScreeningBadge status={c.screeningStatus} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {c.cvAverage === null ? '—' : formatScore(c.cvAverage)}
                      </TableCell>
                      <TableCell>
                        <FlagBadges flags={c.flags} />
                      </TableCell>
                      <TableCell className={cn('text-sm', c.overdue && 'font-medium text-destructive')}>
                        {c.screeningStatus === 'PENDING' ? (
                          <>
                            {formatDateTime(c.slaDeadline)}
                            {c.overdue && <span className="block text-xs">Quá hạn</span>}
                          </>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
            <p className="border-t px-3 py-2 text-xs text-muted-foreground">{list.length} hồ sơ</p>
          </div>
        )}
      </QueryState>
      <CandidateFormDialog open={creating} onOpenChange={setCreating} candidate={null} />
    </div>
  )
}
