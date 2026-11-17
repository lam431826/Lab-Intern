import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
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
import { Textarea } from '@/components/ui/textarea'
import { blankToNull, handleFormError } from '@/lib/forms'
import { useSaveItem, type SubmissionItem } from './api'
import { TrackSelect, useEditableTracks } from './parts'

const schema = z.object({
  trackId: z.string().min(1, 'Chưa chọn ngạch'),
  positionTitle: z.string().trim().min(1, 'Tên vị trí không được để trống').max(200, 'Tối đa 200 ký tự'),
  quota: z
    .number({ error: 'Chỉ tiêu phải là số' })
    .int('Chỉ tiêu phải là số nguyên')
    .min(1, 'Chỉ tiêu phải lớn hơn 0')
    .max(1000, 'Tối đa 1000'),
  jdContent: z.string().max(100_000, 'Nội dung JD tối đa 100.000 ký tự'),
  note: z.string().max(2000, 'Tối đa 2000 ký tự'),
  sortOrder: z.number({ error: 'Thứ tự phải là số' }).int().min(0),
})
type Values = z.infer<typeof schema>

export function ItemFormDialog({
  open,
  onOpenChange,
  submissionId,
  item,
  nextOrder,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  submissionId: number
  item: SubmissionItem | null
  nextOrder: number
}) {
  const save = useSaveItem(submissionId)
  const tracks = useEditableTracks()
  const form = useForm<Values>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (open) {
      const onlyTrack = tracks.length === 1 ? String(tracks[0].id) : ''
      form.reset({
        trackId: item ? String(item.trackId) : onlyTrack,
        positionTitle: item?.positionTitle ?? '',
        quota: item?.quota ?? 1,
        jdContent: item?.jdContent ?? '',
        note: item?.note ?? '',
        sortOrder: item?.sortOrder ?? nextOrder,
      })
    }
    // tracks chỉ dùng để chọn sẵn khi mở hộp thoại
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, item, nextOrder, form])

  async function onSubmit(v: Values) {
    try {
      await save.mutateAsync({
        itemId: item?.id ?? null,
        body: {
          trackId: Number(v.trackId),
          positionTitle: v.positionTitle.trim(),
          quota: v.quota,
          jdContent: blankToNull(v.jdContent),
          note: blankToNull(v.note),
          sortOrder: v.sortOrder,
          version: item?.version ?? null,
        },
      })
      toast.success(item ? 'Đã lưu vị trí' : 'Đã thêm vị trí')
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, form.setError, [
        'trackId',
        'positionTitle',
        'quota',
        'jdContent',
        'note',
        'sortOrder',
      ])
    }
  }

  const errors = form.formState.errors
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>{item ? 'Sửa vị trí tuyển dụng' : 'Thêm vị trí tuyển dụng'}</DialogTitle>
            <DialogDescription>
              Mỗi vị trí cần có nội dung JD hoặc file JD (PDF/DOCX) trước khi đánh dấu đã gửi. File tải lên
              sau khi lưu vị trí.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="max-h-[65vh] overflow-y-auto py-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field data-invalid={!!errors.trackId}>
                <FieldLabel htmlFor="item-track">Ngạch</FieldLabel>
                <Controller
                  control={form.control}
                  name="trackId"
                  render={({ field }) => (
                    <TrackSelect
                      id="item-track"
                      value={field.value}
                      onChange={field.onChange}
                      options={tracks}
                      invalid={!!errors.trackId}
                    />
                  )}
                />
                <FieldError errors={[errors.trackId]} />
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field data-invalid={!!errors.quota}>
                  <FieldLabel htmlFor="item-quota">Chỉ tiêu</FieldLabel>
                  <Input
                    id="item-quota"
                    type="number"
                    min={1}
                    aria-invalid={!!errors.quota}
                    {...form.register('quota', { valueAsNumber: true })}
                  />
                  <FieldError errors={[errors.quota]} />
                </Field>
                <Field data-invalid={!!errors.sortOrder}>
                  <FieldLabel htmlFor="item-order">Thứ tự</FieldLabel>
                  <Input
                    id="item-order"
                    type="number"
                    min={0}
                    aria-invalid={!!errors.sortOrder}
                    {...form.register('sortOrder', { valueAsNumber: true })}
                  />
                </Field>
              </div>
            </div>
            <Field data-invalid={!!errors.positionTitle}>
              <FieldLabel htmlFor="item-title">Tên vị trí</FieldLabel>
              <Input
                id="item-title"
                placeholder="Thực tập sinh Software Engineer (Java)"
                aria-invalid={!!errors.positionTitle}
                {...form.register('positionTitle')}
              />
              <FieldError errors={[errors.positionTitle]} />
            </Field>
            <Field data-invalid={!!errors.jdContent}>
              <FieldLabel htmlFor="item-jd">Nội dung JD</FieldLabel>
              <Textarea
                id="item-jd"
                rows={8}
                placeholder="Mô tả công việc, yêu cầu, quyền lợi…"
                aria-invalid={!!errors.jdContent}
                {...form.register('jdContent')}
              />
              <FieldDescription>Có thể để trống nếu sẽ tải lên file JD.</FieldDescription>
              <FieldError errors={[errors.jdContent]} />
            </Field>
            <Field data-invalid={!!errors.note}>
              <FieldLabel htmlFor="item-note">Ghi chú</FieldLabel>
              <Textarea id="item-note" rows={2} aria-invalid={!!errors.note} {...form.register('note')} />
              <FieldError errors={[errors.note]} />
            </Field>
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
              {item ? 'Lưu' : 'Thêm vị trí'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
