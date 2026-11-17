import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useCandidates } from '@/features/candidates/api'
import { toastError } from '@/lib/errors'
import { formatDate, formatTime } from '@/lib/format'
import { useReschedule, useScheduleSlot, useSessions, useUpdatePanel, type Session, type Slot } from './api'
import { MemberPicker } from './MemberPicker'

function SimpleSelect({
  id,
  value,
  onChange,
  items,
  placeholder,
}: {
  id: string
  value: string
  onChange: (v: string) => void
  items: { value: string; label: string }[]
  placeholder: string
}) {
  return (
    <Select items={items} value={value} onValueChange={(v) => v && onChange(v)}>
      <SelectTrigger id={id} className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {items.map((i) => (
          <SelectItem key={i.value} value={i.value}>
            {i.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

/**
 * Các hộp thoại dưới đây chỉ được render khi mở (state khởi tạo từ props mỗi lần mở).
 */

/** Xếp tay một ứng viên Đạt (chưa có lịch) của ngạch vào ca; bỏ trống giờ thì lấy chỗ trống sớm nhất. */
export function ScheduleDialog({
  open,
  onOpenChange,
  session,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  session: Session
}) {
  const candidates = useCandidates({ trackId: session.trackId, status: 'PASSED' })
  const schedule = useScheduleSlot(session.id)
  const [candidateId, setCandidateId] = useState('')
  const [start, setStart] = useState('')

  const items = useMemo(
    () =>
      (candidates.data ?? [])
        .filter((c) => !c.scheduled)
        .map((c) => ({ value: String(c.id), label: `${c.fullName} (${c.studentCode})` })),
    [candidates.data],
  )

  async function submit() {
    try {
      const slot = await schedule.mutateAsync({ candidateId: Number(candidateId), startTime: start || null })
      toast.success(`Đã xếp ${slot.candidateName} lúc ${slot.timeLabel}`)
      onOpenChange(false)
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Xếp lịch</DialogTitle>
          <DialogDescription>
            Chỉ hồ sơ Đạt sàng lọc của ngạch {session.trackName} và chưa có lịch.
          </DialogDescription>
        </DialogHeader>
        <FieldGroup className="py-4">
          <Field>
            <FieldLabel htmlFor="sch-candidate">Ứng viên</FieldLabel>
            {items.length === 0 && !candidates.isPending ? (
              <p className="text-sm text-muted-foreground">Không còn hồ sơ Đạt nào chưa có lịch.</p>
            ) : (
              <SimpleSelect
                id="sch-candidate"
                value={candidateId}
                onChange={setCandidateId}
                items={items}
                placeholder="Chọn ứng viên"
              />
            )}
          </Field>
          <Field>
            <FieldLabel htmlFor="sch-start">Giờ bắt đầu</FieldLabel>
            <Input
              id="sch-start"
              type="time"
              step={300}
              min={session.startTime.slice(0, 5)}
              max={session.endTime.slice(0, 5)}
              value={start}
              onChange={(e) => setStart(e.target.value)}
            />
            <FieldDescription>Bỏ trống để lấy chỗ trống sớm nhất trong ca.</FieldDescription>
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={schedule.isPending}>
            Hủy
          </Button>
          <Button onClick={submit} disabled={!candidateId || schedule.isPending}>
            Xếp lịch
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Dời lịch PV1 sang PV2 (mỗi ứng viên một lần); slot cũ được giữ lại kèm lý do. */
export function RescheduleDialog({
  slot,
  trackId,
  onOpenChange,
}: {
  slot: Slot | null
  trackId: number
  onOpenChange: (open: boolean) => void
}) {
  const sessions = useSessions({ trackId })
  const reschedule = useReschedule()
  const [target, setTarget] = useState(slot ? String(slot.sessionId) : '')
  const [start, setStart] = useState('')
  const [reason, setReason] = useState('')

  const items = (sessions.data ?? []).map((s) => ({
    value: String(s.id),
    label: `${formatDate(s.sessionDate)} · ${formatTime(s.startTime)}–${formatTime(s.endTime)}${s.room ? ` · ${s.room}` : ''}`,
  }))

  async function submit() {
    if (!slot) return
    try {
      const pv2 = await reschedule.mutateAsync({
        slotId: slot.id,
        body: { sessionId: Number(target), startTime: start || null, reason: reason.trim() },
      })
      toast.success(`Đã dời ${pv2.candidateName} sang ${formatDate(pv2.sessionDate)} ${pv2.timeLabel} (PV2)`)
      onOpenChange(false)
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <Dialog open={slot !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Dời lịch {slot?.candidateName}</DialogTitle>
          <DialogDescription>
            Lịch cũ ({slot?.timeLabel}) được giữ trong lịch sử. Mỗi ứng viên chỉ được dời một lần (PV2).
          </DialogDescription>
        </DialogHeader>
        <FieldGroup className="py-4">
          <Field>
            <FieldLabel htmlFor="rs-session">Ca mới</FieldLabel>
            <SimpleSelect
              id="rs-session"
              value={target}
              onChange={setTarget}
              items={items}
              placeholder="Chọn ca"
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="rs-start">Giờ bắt đầu</FieldLabel>
            <Input
              id="rs-start"
              type="time"
              step={300}
              value={start}
              onChange={(e) => setStart(e.target.value)}
            />
            <FieldDescription>Bỏ trống để lấy chỗ trống sớm nhất trong ca mới.</FieldDescription>
          </Field>
          <Field>
            <FieldLabel htmlFor="rs-reason">Lý do dời</FieldLabel>
            <Textarea
              id="rs-reason"
              rows={2}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
            />
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={reschedule.isPending}>
            Hủy
          </Button>
          <Button onClick={submit} disabled={!target || !reason.trim() || reschedule.isPending}>
            Dời sang PV2
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Thay thành viên luân chuyển; thành viên cố định giữ nguyên. Người đã chấm điểm thì backend không cho bỏ. */
export function PanelDialog({
  open,
  onOpenChange,
  session,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  session: Session
}) {
  const update = useUpdatePanel(session.id)
  const fixedIds = session.panel.filter((p) => p.panelRole !== 'ROTATING').map((p) => p.userId)
  const [selected, setSelected] = useState<number[]>(() =>
    session.panel.filter((p) => p.panelRole === 'ROTATING').map((p) => p.userId),
  )

  async function submit() {
    try {
      await update.mutateAsync(selected)
      toast.success('Đã cập nhật hội đồng')
      onOpenChange(false)
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Thành viên luân chuyển</DialogTitle>
          <DialogDescription>
            Hội đồng chuẩn 6 người. Hiện có {fixedIds.length} thành viên cố định, đã chọn {selected.length}{' '}
            luân chuyển.
          </DialogDescription>
        </DialogHeader>
        <div className="py-4">
          <MemberPicker value={selected} onChange={setSelected} excludeIds={fixedIds} />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={update.isPending}>
            Hủy
          </Button>
          <Button onClick={submit} disabled={update.isPending}>
            Lưu hội đồng
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
