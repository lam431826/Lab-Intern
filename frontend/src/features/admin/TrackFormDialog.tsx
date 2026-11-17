import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { handleFormError } from '@/lib/forms'
import { ROLE_LABELS } from '@/lib/roles'
import { useUpdateTrack, useUsers, type Track } from './api'

const count = (label: string, max: number) =>
  z
    .number({ error: `${label} phải là số` })
    .int(`${label} phải là số nguyên`)
    .min(0, `${label} không được âm`)
    .max(max, `${label} tối đa ${max}`)

const schema = z
  .object({
    name: z.string().trim().min(1, 'Tên ngạch không được để trống').max(100),
    quota: count('Chỉ tiêu', 100),
    slotMin: count('Số slot', 200),
    slotMax: count('Số slot', 200),
    requiresPortfolio: z.boolean(),
    leadUserId: z.string(),
  })
  .refine((v) => v.slotMin <= v.slotMax, {
    path: ['slotMax'],
    message: 'Số slot tối đa phải lớn hơn hoặc bằng số tối thiểu',
  })
type Values = z.infer<typeof schema>

const NO_LEAD = 'none'

export function TrackFormDialog({
  open,
  onOpenChange,
  track,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  track: Track
}) {
  const update = useUpdateTrack()
  const users = useUsers()
  const form = useForm<Values>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (open) {
      form.reset({
        name: track.name,
        quota: track.quota,
        slotMin: track.slotMin,
        slotMax: track.slotMax,
        requiresPortfolio: track.requiresPortfolio,
        leadUserId: track.leadUserId ? String(track.leadUserId) : NO_LEAD,
      })
    }
  }, [open, track, form])

  // Backend chỉ nhận Lead có vai trò LEAD hoặc ORGANIZER và đang hoạt động
  const leadItems = useMemo(
    () => [
      { value: NO_LEAD, label: 'Chưa có Lead' },
      ...(users.data ?? [])
        .filter((u) => u.active && (u.role === 'LEAD' || u.role === 'ORGANIZER'))
        .map((u) => ({ value: String(u.id), label: `${u.fullName} (${ROLE_LABELS[u.role]})` })),
    ],
    [users.data],
  )

  async function onSubmit(v: Values) {
    try {
      await update.mutateAsync({
        id: track.id,
        body: {
          name: v.name,
          quota: v.quota,
          slotMin: v.slotMin,
          slotMax: v.slotMax,
          requiresPortfolio: v.requiresPortfolio,
          leadUserId: v.leadUserId === NO_LEAD ? null : Number(v.leadUserId),
        },
      })
      toast.success(`Đã lưu ngạch ${v.name}`)
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, form.setError, ['name', 'quota', 'slotMin', 'slotMax', 'leadUserId'])
    }
  }

  const errors = form.formState.errors
  const number = { valueAsNumber: true } as const
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>Sửa ngạch {track.code}</DialogTitle>
            <DialogDescription>
              Mã ngạch không đổi được vì file Excel nhập vào và báo cáo dựa trên mã này.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field data-invalid={!!errors.name}>
              <FieldLabel htmlFor="track-name">Tên ngạch</FieldLabel>
              <Input id="track-name" aria-invalid={!!errors.name} {...form.register('name')} />
              <FieldError errors={[errors.name]} />
            </Field>
            <div className="grid grid-cols-3 gap-3">
              <Field data-invalid={!!errors.quota}>
                <FieldLabel htmlFor="track-quota">Chỉ tiêu</FieldLabel>
                <Input
                  id="track-quota"
                  type="number"
                  min={0}
                  aria-invalid={!!errors.quota}
                  {...form.register('quota', number)}
                />
              </Field>
              <Field data-invalid={!!errors.slotMin}>
                <FieldLabel htmlFor="track-slot-min">Slot tối thiểu</FieldLabel>
                <Input
                  id="track-slot-min"
                  type="number"
                  min={0}
                  aria-invalid={!!errors.slotMin}
                  {...form.register('slotMin', number)}
                />
              </Field>
              <Field data-invalid={!!errors.slotMax}>
                <FieldLabel htmlFor="track-slot-max">Slot tối đa</FieldLabel>
                <Input
                  id="track-slot-max"
                  type="number"
                  min={0}
                  aria-invalid={!!errors.slotMax}
                  {...form.register('slotMax', number)}
                />
              </Field>
            </div>
            <FieldError errors={[errors.quota, errors.slotMin, errors.slotMax]} />
            <FieldDescription className="-mt-2">
              Chỉ tiêu là số người trúng tuyển. Số slot là số ứng viên dự kiến phỏng vấn; xếp vượt mức tối đa
              sẽ có cảnh báo.
            </FieldDescription>
            <Field data-invalid={!!errors.leadUserId}>
              <FieldLabel htmlFor="track-lead">Lead chuyên môn</FieldLabel>
              <Controller
                control={form.control}
                name="leadUserId"
                render={({ field }) => (
                  <Select
                    items={leadItems}
                    value={field.value}
                    onValueChange={(v) => field.onChange(v ?? NO_LEAD)}
                  >
                    <SelectTrigger id="track-lead" className="w-full" aria-invalid={!!errors.leadUserId}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {leadItems.map((i) => (
                        <SelectItem key={i.value} value={i.value}>
                          {i.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              <FieldDescription>
                Lead duyệt CV, sửa tiêu chí chuyên môn và tự động có tên trong hội đồng các ca của ngạch.
              </FieldDescription>
              <FieldError errors={[errors.leadUserId]} />
            </Field>
            <Field orientation="horizontal">
              <Controller
                control={form.control}
                name="requiresPortfolio"
                render={({ field }) => (
                  <Switch id="track-portfolio" checked={field.value} onCheckedChange={field.onChange} />
                )}
              />
              <FieldLabel htmlFor="track-portfolio">
                Bắt buộc có Portfolio (thiếu thì gắn cờ khi sàng lọc)
              </FieldLabel>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={update.isPending}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={update.isPending}>
              Lưu
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
