import { AlertTriangle, ArrowLeft, CalendarPlus, Pencil, Trash2, Users, Wand2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { useCan } from '@/auth/useCan'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { QueryState } from '@/components/common/QueryState'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { toastError } from '@/lib/errors'
import { formatDate, formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  ATTENDANCE_LABELS,
  PANEL_ROLE_LABELS,
  useAttendance,
  useAutoSchedule,
  useCancelSlot,
  useDeleteSession,
  useSession,
  useSessionProgress,
  useUpdateLog,
  type Attendance,
  type Session,
  type Slot,
} from './api'
import { SessionFormDialog } from './SessionFormDialog'
import { PanelDialog, RescheduleDialog, ScheduleDialog } from './SlotDialogs'

export function SessionDetailPage() {
  const { id } = useParams()
  const sessionId = Number(id)
  const session = useSession(sessionId)
  if (!Number.isInteger(sessionId) || sessionId <= 0) return <NotFoundPage />
  return (
    <div className="mx-auto max-w-6xl">
      <Button
        variant="ghost"
        size="sm"
        className="mb-2 -ml-2"
        nativeButton={false}
        render={<Link to="/sessions" />}
      >
        <ArrowLeft aria-hidden /> Lịch phỏng vấn
      </Button>
      <QueryState query={session}>{(s) => <SessionDetail session={s} />}</QueryState>
    </div>
  )
}

function SessionDetail({ session: s }: { session: Session }) {
  const can = useCan()
  const navigate = useNavigate()
  const onPanel = s.panel.some((p) => p.userId === can.me.id)
  /** Điểm danh, ghi Interview Log: Người tổ chức hoặc thành viên hội đồng của ca (khớp @authz.canRunSession). */
  const canRun = can.isOrganizer || onPanel

  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [scheduling, setScheduling] = useState(false)
  const [editingPanel, setEditingPanel] = useState(false)
  const [moving, setMoving] = useState<Slot | null>(null)
  const [cancelling, setCancelling] = useState<Slot | null>(null)

  const remove = useDeleteSession()
  const auto = useAutoSchedule(s.id)
  const cancel = useCancelSlot()

  async function onDelete() {
    try {
      await remove.mutateAsync(s.id)
      toast.success('Đã xóa ca')
      navigate('/sessions')
    } catch (e) {
      setDeleting(false)
      toastError(e)
    }
  }

  async function onAuto() {
    try {
      const r = await auto.mutateAsync()
      if (r.scheduled.length === 0 && r.notScheduled.length === 0) {
        toast.info('Không còn hồ sơ Đạt nào chưa có lịch.')
      } else if (r.notScheduled.length > 0) {
        toast.warning(
          `Đã xếp ${r.scheduled.length} ứng viên. Ca hết giờ, còn ${r.notScheduled.length} chưa xếp: ${r.notScheduled.join(', ')}`,
        )
      } else {
        toast.success(`Đã xếp ${r.scheduled.length} ứng viên`)
      }
    } catch (e) {
      toastError(e)
    }
  }

  async function onCancel() {
    if (!cancelling) return
    try {
      await cancel.mutateAsync(cancelling.id)
      toast.success(`Đã hủy lịch của ${cancelling.candidateName}`)
      setCancelling(null)
    } catch (e) {
      toastError(e)
    }
  }

  const slots = s.slots
  const activeCount = slots.filter((x) => x.active).length

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold sm:text-xl">
            {s.trackName} · {formatTime(s.startTime)}–{formatTime(s.endTime)}
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {formatDate(s.sessionDate)}
            {s.room && ` · Phòng ${s.room}`} · {activeCount} ứng viên
          </p>
        </div>
        {can.isOrganizer && (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setEditing(true)}>
              <Pencil aria-hidden /> Sửa ca
            </Button>
            <Button variant="outline" onClick={() => setDeleting(true)}>
              <Trash2 aria-hidden /> Xóa ca
            </Button>
          </div>
        )}
      </div>

      {s.warnings.length > 0 && (
        <Alert>
          <AlertTriangle aria-hidden />
          <AlertTitle>Cần lưu ý</AlertTitle>
          <AlertDescription>
            <ul className="list-disc pl-4">
              {s.warnings.map((w, i) => (
                <li key={i}>{w.message}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Lịch trong ca</CardTitle>
              {can.isOrganizer && (
                <CardAction className="flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" onClick={onAuto} disabled={auto.isPending}>
                    <Wand2 aria-hidden /> Tự xếp
                  </Button>
                  <Button size="sm" onClick={() => setScheduling(true)}>
                    <CalendarPlus aria-hidden /> Xếp lịch
                  </Button>
                </CardAction>
              )}
            </CardHeader>
            <CardContent>
              {slots.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Chưa có ứng viên nào.
                  {can.isOrganizer && ' Bấm "Tự xếp" để xếp lần lượt các hồ sơ Đạt của ngạch.'}
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Giờ</TableHead>
                        <TableHead>Ứng viên</TableHead>
                        <TableHead>Điểm danh</TableHead>
                        {can.isOrganizer && <TableHead className="text-right">Thao tác</TableHead>}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {slots.map((slot) => (
                        <TableRow key={slot.id} className={cn(!slot.active && 'text-muted-foreground')}>
                          <TableCell className="whitespace-nowrap tabular-nums">{slot.timeLabel}</TableCell>
                          <TableCell>
                            <Link
                              to={`/candidates/${slot.candidateId}`}
                              className="font-medium hover:underline"
                            >
                              {slot.candidateName}
                            </Link>
                            <span className="ml-2 font-mono text-xs">{slot.studentCode}</span>
                            {slot.slotType === 'PV2' && (
                              <Badge variant="secondary" className="ml-2">
                                PV2
                              </Badge>
                            )}
                            {!slot.active && (
                              <div className="text-xs">Đã dời lịch{slot.note && `: ${slot.note}`}</div>
                            )}
                          </TableCell>
                          <TableCell>
                            {slot.active ? (
                              <AttendanceControl slot={slot} editable={canRun} />
                            ) : (
                              <span className="text-xs">—</span>
                            )}
                          </TableCell>
                          {can.isOrganizer && (
                            <TableCell className="text-right whitespace-nowrap">
                              {slot.active && slot.attendance !== 'ATTENDED' && slot.slotType === 'PV1' && (
                                <Button size="sm" variant="ghost" onClick={() => setMoving(slot)}>
                                  Dời lịch
                                </Button>
                              )}
                              {slot.active && slot.attendance === 'PENDING' && (
                                <Button size="sm" variant="ghost" onClick={() => setCancelling(slot)}>
                                  Hủy
                                </Button>
                              )}
                            </TableCell>
                          )}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
          <LogCard key={s.logSummary ?? ''} session={s} editable={canRun} />
        </div>

        <div className="space-y-6">
          <Card size="sm">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Users className="size-4" aria-hidden /> Hội đồng ({s.panel.length}/6)
              </CardTitle>
              {can.isOrganizer && (
                <CardAction>
                  <Button size="sm" variant="ghost" onClick={() => setEditingPanel(true)}>
                    Sửa
                  </Button>
                </CardAction>
              )}
            </CardHeader>
            <CardContent>
              <ul className="space-y-1.5 text-sm">
                {s.panel.map((p) => (
                  <li key={p.userId} className="flex items-center justify-between gap-2">
                    <span className={cn('truncate', p.userId === can.me.id && 'font-medium')}>
                      {p.fullName}
                    </span>
                    <Badge variant={p.panelRole === 'ROTATING' ? 'outline' : 'secondary'}>
                      {PANEL_ROLE_LABELS[p.panelRole]}
                    </Badge>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
          {activeCount > 0 && <ProgressCard sessionId={s.id} />}
        </div>
      </div>

      <SessionFormDialog open={editing} onOpenChange={setEditing} session={s} />
      {scheduling && <ScheduleDialog open onOpenChange={setScheduling} session={s} />}
      {editingPanel && <PanelDialog open onOpenChange={setEditingPanel} session={s} />}
      {moving && (
        <RescheduleDialog
          slot={moving}
          trackId={s.trackId}
          onOpenChange={(open) => !open && setMoving(null)}
        />
      )}
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="Xóa ca phỏng vấn?"
        description="Chỉ xóa được ca chưa có lịch của ứng viên nào."
        confirmLabel="Xóa ca"
        destructive
        pending={remove.isPending}
        onConfirm={onDelete}
      />
      <ConfirmDialog
        open={cancelling !== null}
        onOpenChange={(open) => !open && setCancelling(null)}
        title="Hủy lịch?"
        description={
          cancelling?.slotType === 'PV2'
            ? `Hủy lịch PV2 của ${cancelling.candidateName}: lịch PV1 trước đó sẽ có hiệu lực trở lại.`
            : `${cancelling?.candidateName} sẽ về trạng thái chưa có lịch.`
        }
        confirmLabel="Hủy lịch"
        destructive
        pending={cancel.isPending}
        onConfirm={onCancel}
      />
    </div>
  )
}

const ATTENDANCE_ORDER: Attendance[] = ['ATTENDED', 'ABSENT', 'PENDING']

/** Điểm danh bằng 3 nút; đã có điểm thì backend không cho đổi khỏi Có mặt (ATTENDANCE_LOCKED). */
function AttendanceControl({ slot, editable }: { slot: Slot; editable: boolean }) {
  const attendance = useAttendance()
  if (!editable) return <span className="text-sm">{ATTENDANCE_LABELS[slot.attendance]}</span>

  async function set(value: Attendance) {
    if (value === slot.attendance) return
    try {
      await attendance.mutateAsync({ slotId: slot.id, attendance: value, note: null })
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <div className="flex gap-1" role="radiogroup" aria-label={`Điểm danh ${slot.candidateName}`}>
      {ATTENDANCE_ORDER.map((a) => (
        <button
          key={a}
          type="button"
          role="radio"
          aria-checked={slot.attendance === a}
          disabled={attendance.isPending}
          onClick={() => set(a)}
          className={cn(
            'rounded-md border px-2 py-1 text-xs whitespace-nowrap hover:bg-muted disabled:opacity-50',
            slot.attendance === a &&
              a === 'ATTENDED' &&
              'border-emerald-600 bg-emerald-600 text-white hover:bg-emerald-600',
            slot.attendance === a &&
              a === 'ABSENT' &&
              'border-destructive bg-destructive text-white hover:bg-destructive',
            slot.attendance === a &&
              a === 'PENDING' &&
              'border-foreground bg-foreground text-background hover:bg-foreground',
          )}
        >
          {a === 'PENDING' ? 'Chưa' : ATTENDANCE_LABELS[a]}
        </button>
      ))}
    </div>
  )
}

function LogCard({ session, editable }: { session: Session; editable: boolean }) {
  const save = useUpdateLog(session.id)
  // Component được gắn key theo log đã lưu, nên state khởi tạo lại khi log trên máy chủ đổi
  const [text, setText] = useState(session.logSummary ?? '')
  const dirty = text !== (session.logSummary ?? '')

  async function submit() {
    try {
      await save.mutateAsync(text.trim() || null)
      toast.success('Đã lưu Interview Log')
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle>Interview Log</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {editable ? (
          <>
            <Textarea
              rows={4}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Nhận xét chung về ca: đúng giờ, chất lượng câu hỏi, sự cố…"
              aria-label="Interview Log"
              maxLength={10000}
            />
            <div className="flex justify-end">
              <Button size="sm" onClick={submit} disabled={!dirty || save.isPending}>
                Lưu log
              </Button>
            </div>
          </>
        ) : (
          <p className="text-sm whitespace-pre-line text-muted-foreground">
            {session.logSummary || 'Chưa có ghi chú.'}
          </p>
        )}
      </CardContent>
    </Card>
  )
}

function ProgressCard({ sessionId }: { sessionId: number }) {
  const progress = useSessionProgress(sessionId)
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle>Tiến độ chấm</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState query={progress} skeletonRows={2}>
          {(p) => (
            <ul className="space-y-2 text-sm">
              {p.candidates.map((c) => (
                <li key={c.candidateId}>
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate">{c.candidateName}</span>
                    <span
                      className={cn(
                        'tabular-nums',
                        c.submitted === p.panelSize
                          ? 'text-emerald-700 dark:text-emerald-400'
                          : 'text-muted-foreground',
                      )}
                    >
                      {c.submitted}/{p.panelSize}
                    </span>
                  </div>
                  {c.attendance === 'ATTENDED' && c.pending.length > 0 && (
                    <div className="text-xs text-muted-foreground">Chưa nộp: {c.pending.join(', ')}</div>
                  )}
                  {c.attendance !== 'ATTENDED' && (
                    <div className="text-xs text-muted-foreground">{ATTENDANCE_LABELS[c.attendance]}</div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </QueryState>
      </CardContent>
    </Card>
  )
}
