import { ArrowLeft, ExternalLink, Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { useCan } from '@/auth/useCan'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { QueryState } from '@/components/common/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { toastError } from '@/lib/errors'
import { formatDate, formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  useCandidate,
  useCandidateHistory,
  useCandidateSlots,
  useDeleteCandidate,
  type Candidate,
} from './api'
import { CandidateFormDialog } from './CandidateFormDialog'
import { describe } from './history'
import { FlagBadges, ScreeningBadge } from './parts'
import { ScreeningCard } from './ScreeningCard'

const ATTENDANCE: Record<string, string> = {
  PENDING: 'Chưa điểm danh',
  ATTENDED: 'Có mặt',
  ABSENT: 'Vắng mặt',
}

export function CandidateDetailPage() {
  const { id } = useParams()
  const candidateId = Number(id)
  const candidate = useCandidate(candidateId)
  if (!Number.isInteger(candidateId) || candidateId <= 0) return <NotFoundPage />
  return (
    <div className="mx-auto max-w-6xl">
      <Button
        variant="ghost"
        size="sm"
        className="mb-2 -ml-2"
        nativeButton={false}
        render={<Link to="/candidates" />}
      >
        <ArrowLeft aria-hidden /> Danh sách hồ sơ
      </Button>
      <QueryState query={candidate}>
        {(c) => <CandidateDetail candidate={c} reload={() => candidate.refetch()} />}
      </QueryState>
    </div>
  )
}

function CandidateDetail({ candidate: c, reload }: { candidate: Candidate; reload: () => Promise<unknown> }) {
  const can = useCan()
  const navigate = useNavigate()
  const remove = useDeleteCandidate()
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const canScreen = can.isOrganizer || can.leads(c.trackCode)

  async function onDelete() {
    try {
      await remove.mutateAsync(c.id)
      toast.success(`Đã xóa hồ sơ ${c.studentCode}`)
      navigate('/candidates')
    } catch (e) {
      setDeleting(false)
      toastError(e)
    }
  }

  const info = [
    { label: 'Mã SV', value: c.studentCode },
    { label: 'Email', value: c.email },
    { label: 'Số điện thoại', value: c.phone },
    { label: 'Chuyên ngành', value: c.major },
    { label: 'Nhận hồ sơ', value: formatDateTime(c.assignedAt) },
    {
      label: 'Hạn duyệt (SLA)',
      value: c.screeningStatus === 'PENDING' ? formatDateTime(c.slaDeadline) : null,
      danger: c.overdue,
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="flex flex-wrap items-center gap-2 text-lg font-semibold sm:text-xl">
            {c.fullName} <ScreeningBadge status={c.screeningStatus} />
            {c.overdue && <Badge variant="destructive">Quá hạn duyệt</Badge>}
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {c.studentCode} · {c.trackName}
          </p>
        </div>
        {can.isOrganizer && (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setEditing(true)}>
              <Pencil aria-hidden /> Sửa hồ sơ
            </Button>
            <Button variant="outline" onClick={() => setDeleting(true)}>
              <Trash2 aria-hidden /> Xóa
            </Button>
          </div>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-6">
          <Card>
            <CardContent className="space-y-4">
              <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
                {info.map((i) => (
                  <div key={i.label} className="min-w-0">
                    <dt className="text-muted-foreground">{i.label}</dt>
                    <dd className={cn('truncate font-medium', i.danger && 'text-destructive')}>
                      {i.value || '—'}
                    </dd>
                  </div>
                ))}
              </dl>
              <div className="flex flex-wrap gap-2">
                <DocLink href={c.cvLink} label="Mở CV" />
                <DocLink href={c.portfolioLink} label="Mở Portfolio" />
              </div>
              <FlagBadges flags={c.flags} />
            </CardContent>
          </Card>
          <ScreeningCard candidate={c} editable={canScreen} onReload={reload} />
        </div>
        <div className="space-y-6">
          <SlotsCard candidateId={c.id} />
          <HistoryCard candidateId={c.id} />
        </div>
      </div>

      <CandidateFormDialog open={editing} onOpenChange={setEditing} candidate={c} />
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="Xóa hồ sơ?"
        description={
          <>
            Hồ sơ <strong>{c.fullName}</strong> ({c.studentCode}) sẽ bị xóa. Hồ sơ đã xếp lịch hoặc đã có điểm
            thì không xóa được; khi đó hãy đặt trạng thái Loại kèm lý do.
          </>
        }
        confirmLabel="Xóa hồ sơ"
        destructive
        pending={remove.isPending}
        onConfirm={onDelete}
      />
    </div>
  )
}

/** Link CV/Portfolio do ứng viên cung cấp: chỉ mở http(s), mở tab mới và không gửi Referer. */
function DocLink({ href, label }: { href: string | null; label: string }) {
  if (!href || !/^https?:\/\//i.test(href)) return null
  return (
    <Button
      size="sm"
      variant="outline"
      nativeButton={false}
      render={<a href={href} target="_blank" rel="noopener noreferrer" />}
    >
      <ExternalLink aria-hidden /> {label}
    </Button>
  )
}

function SlotsCard({ candidateId }: { candidateId: number }) {
  const slots = useCandidateSlots(candidateId)
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle>Lịch phỏng vấn</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState query={slots} skeletonRows={2}>
          {(list) =>
            list.length === 0 ? (
              <p className="text-sm text-muted-foreground">Chưa xếp lịch.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {list.map((s) => (
                  <li key={s.id} className={cn('rounded-md border p-2', !s.active && 'opacity-60')}>
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-medium">
                        {s.timeLabel} · {formatDate(s.sessionDate)}
                      </span>
                      <Badge variant={s.slotType === 'PV2' ? 'secondary' : 'outline'}>{s.slotType}</Badge>
                    </div>
                    <div className="text-xs text-muted-foreground">
                      {s.active ? ATTENDANCE[s.attendance] : 'Đã dời lịch'}
                      {s.room && ` · Phòng ${s.room}`}
                      {s.note && ` · ${s.note}`}
                    </div>
                  </li>
                ))}
              </ul>
            )
          }
        </QueryState>
      </CardContent>
    </Card>
  )
}

function HistoryCard({ candidateId }: { candidateId: number }) {
  const history = useCandidateHistory(candidateId)
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle>Lịch sử</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState query={history} skeletonRows={3}>
          {(list) =>
            list.length === 0 ? (
              <p className="text-sm text-muted-foreground">Chưa có thay đổi.</p>
            ) : (
              <ol className="space-y-3 border-l pl-4 text-sm">
                {list.toReversed().map((h, i) => (
                  <li key={i} className="relative">
                    <span className="absolute top-1.5 -left-[1.3rem] size-2 rounded-full bg-muted-foreground" />
                    <div>{describe(h)}</div>
                    {h.note && <div className="text-muted-foreground">“{h.note}”</div>}
                    <div className="text-xs text-muted-foreground">
                      {h.changedByName ?? 'Hệ thống'} · {formatDateTime(h.changedAt)}
                    </div>
                  </li>
                ))}
              </ol>
            )
          }
        </QueryState>
      </CardContent>
    </Card>
  )
}
