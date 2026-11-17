import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { TrackSelect } from '@/components/common/TrackSelect'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { useTracks } from '@/features/admin/api'
import { blankToNull, handleFormError } from '@/lib/forms'
import { hhmm, useSaveSession, type Session } from './api'
import { MemberPicker } from './MemberPicker'

const schema = z
  .object({
    trackId: z.string().min(1, 'Chưa chọn ngạch'),
    sessionDate: z.string().min(1, 'Chọn ngày'),
    startTime: z.string().min(1, 'Chọn giờ bắt đầu'),
    endTime: z.string().min(1, 'Chọn giờ kết thúc'),
    room: z.string().max(50, 'Tối đa 50 ký tự'),
    rotatingMemberIds: z.array(z.number()),
  })
  .refine((v) => !v.startTime || !v.endTime || v.startTime < v.endTime, {
    path: ['endTime'],
    message: 'Giờ kết thúc phải sau giờ bắt đầu',
  })
type Values = z.infer<typeof schema>

/** Khung giờ phỏng vấn theo Kế hoạch: 10h–12h30 và 16h–18h30 (ngoài khung chỉ cảnh báo). */
export function outsideWindows(start: string, end: string): boolean {
  if (!start || !end) return false
  const within = (a: string, b: string) => start >= a && end <= b
  return !within('10:00', '12:30') && !within('16:00', '18:30')
}

export function SessionFormDialog({
  open,
  onOpenChange,
  session,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  session: Session | null
}) {
  const navigate = useNavigate()
  const tracks = useTracks()
  const save = useSaveSession()
  const form = useForm<Values>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (open) {
      form.reset({
        trackId: session ? String(session.trackId) : '',
        sessionDate: session?.sessionDate ?? '',
        startTime: hhmm(session?.startTime) || '10:00',
        endTime: hhmm(session?.endTime) || '12:30',
        room: session?.room ?? '',
        rotatingMemberIds: [],
      })
    }
  }, [open, session, form])

  async function onSubmit(v: Values) {
    try {
      const saved = await save.mutateAsync({
        id: session?.id ?? null,
        body: {
          trackId: Number(v.trackId),
          sessionDate: v.sessionDate,
          startTime: v.startTime,
          endTime: v.endTime,
          room: blankToNull(v.room),
          rotatingMemberIds: session ? null : v.rotatingMemberIds,
        },
      })
      toast.success(session ? 'Đã lưu ca phỏng vấn' : 'Đã tạo ca phỏng vấn')
      onOpenChange(false)
      if (!session) navigate(`/sessions/${saved.id}`)
    } catch (e) {
      handleFormError(e, form.setError, ['trackId', 'sessionDate', 'startTime', 'endTime', 'room'])
    }
  }

  const errors = form.formState.errors
  const [start, end] = useWatch({ control: form.control, name: ['startTime', 'endTime'] })
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>{session ? 'Sửa ca phỏng vấn' : 'Tạo ca phỏng vấn'}</DialogTitle>
            <DialogDescription>
              {session
                ? 'Giờ mới phải chứa hết các slot đã xếp. Ca đã có lịch thì không đổi ngạch được.'
                : 'Giảng viên, bạn (Người tổ chức) và Lead của ngạch được thêm vào hội đồng tự động.'}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="max-h-[65vh] overflow-y-auto py-4">
            <Field data-invalid={!!errors.trackId}>
              <FieldLabel htmlFor="ses-track">Ngạch</FieldLabel>
              <Controller
                control={form.control}
                name="trackId"
                render={({ field }) => (
                  <TrackSelect
                    id="ses-track"
                    value={field.value}
                    onChange={field.onChange}
                    options={tracks.data ?? []}
                    invalid={!!errors.trackId}
                  />
                )}
              />
              <FieldError errors={[errors.trackId]} />
            </Field>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field data-invalid={!!errors.sessionDate}>
                <FieldLabel htmlFor="ses-date">Ngày</FieldLabel>
                <Input
                  id="ses-date"
                  type="date"
                  aria-invalid={!!errors.sessionDate}
                  {...form.register('sessionDate')}
                />
                <FieldError errors={[errors.sessionDate]} />
              </Field>
              <Field data-invalid={!!errors.room}>
                <FieldLabel htmlFor="ses-room">Phòng</FieldLabel>
                <Input id="ses-room" aria-invalid={!!errors.room} {...form.register('room')} />
                <FieldError errors={[errors.room]} />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field data-invalid={!!errors.startTime}>
                <FieldLabel htmlFor="ses-start">Bắt đầu</FieldLabel>
                <Input
                  id="ses-start"
                  type="time"
                  step={300}
                  aria-invalid={!!errors.startTime}
                  {...form.register('startTime')}
                />
              </Field>
              <Field data-invalid={!!errors.endTime}>
                <FieldLabel htmlFor="ses-end">Kết thúc</FieldLabel>
                <Input
                  id="ses-end"
                  type="time"
                  step={300}
                  aria-invalid={!!errors.endTime}
                  {...form.register('endTime')}
                />
              </Field>
            </div>
            <FieldError errors={[errors.startTime, errors.endTime]} />
            {outsideWindows(start, end) && (
              <FieldDescription className="text-amber-700 dark:text-amber-400">
                Ca nằm ngoài khung 10h–12h30 và 16h–18h30. Vẫn tạo được, ca sẽ có cảnh báo.
              </FieldDescription>
            )}
            {!session && (
              <Field>
                <FieldLabel>Thành viên luân chuyển (nên chọn 3)</FieldLabel>
                <Controller
                  control={form.control}
                  name="rotatingMemberIds"
                  render={({ field }) => <MemberPicker value={field.value} onChange={field.onChange} />}
                />
                <FieldDescription>Có thể đổi sau ở trang chi tiết ca.</FieldDescription>
              </Field>
            )}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={save.isPending}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={save.isPending}>
              {session ? 'Lưu' : 'Tạo ca'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
