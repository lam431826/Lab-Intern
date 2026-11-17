import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
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
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { blankToNull, handleFormError } from '@/lib/forms'
import { useSaveCriterion, type Criterion } from './api'
import { RUBRIC_LEVELS } from './rubric'

const schema = z.object({
  name: z.string().trim().min(1, 'Tên tiêu chí không được để trống').max(200, 'Tối đa 200 ký tự'),
  description: z.string().max(2000, 'Tối đa 2000 ký tự'),
  rubricLow: z.string().max(1000, 'Tối đa 1000 ký tự'),
  rubricMid: z.string().max(1000, 'Tối đa 1000 ký tự'),
  rubricHigh: z.string().max(1000, 'Tối đa 1000 ký tự'),
  sortOrder: z.number({ error: 'Thứ tự phải là số' }).int().min(0, 'Thứ tự không được âm'),
})
type Values = z.infer<typeof schema>

/**
 * Tạo/sửa tiêu chí. {@code trackId = null}: tiêu chí chung (chỉ Người tổ chức).
 * {@code criterion = null}: tạo mới với thứ tự {@code nextOrder}.
 */
export function CriterionFormDialog({
  open,
  onOpenChange,
  trackId,
  trackName,
  criterion,
  nextOrder,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  trackId: number | null
  trackName: string
  criterion: Criterion | null
  nextOrder: number
}) {
  const save = useSaveCriterion()
  const form = useForm<Values>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (open) {
      form.reset({
        name: criterion?.name ?? '',
        description: criterion?.description ?? '',
        rubricLow: criterion?.rubricLow ?? '',
        rubricMid: criterion?.rubricMid ?? '',
        rubricHigh: criterion?.rubricHigh ?? '',
        sortOrder: criterion?.sortOrder ?? nextOrder,
      })
    }
  }, [open, criterion, nextOrder, form])

  async function onSubmit(v: Values) {
    try {
      await save.mutateAsync({
        id: criterion?.id ?? null,
        body: {
          trackId,
          name: v.name.trim(),
          description: blankToNull(v.description),
          rubricLow: blankToNull(v.rubricLow),
          rubricMid: blankToNull(v.rubricMid),
          rubricHigh: blankToNull(v.rubricHigh),
          sortOrder: v.sortOrder,
        },
      })
      toast.success(criterion ? 'Đã lưu tiêu chí' : 'Đã thêm tiêu chí')
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, form.setError, [
        'name',
        'description',
        'rubricLow',
        'rubricMid',
        'rubricHigh',
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
            <DialogTitle>{criterion ? 'Sửa tiêu chí' : 'Thêm tiêu chí'}</DialogTitle>
            <DialogDescription>
              {trackId === null
                ? 'Tiêu chí chung, áp dụng cho mọi ngạch.'
                : `Tiêu chí chuyên môn của ngạch ${trackName}.`}{' '}
              Giám khảo chấm thang 1–10 và xem thang hướng dẫn ngay trên phiếu chấm.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="max-h-[60vh] overflow-y-auto py-4">
            <div className="grid gap-3 sm:grid-cols-[1fr_7rem]">
              <Field data-invalid={!!errors.name}>
                <FieldLabel htmlFor="crit-name">Tên tiêu chí</FieldLabel>
                <Input id="crit-name" aria-invalid={!!errors.name} {...form.register('name')} />
                <FieldError errors={[errors.name]} />
              </Field>
              <Field data-invalid={!!errors.sortOrder}>
                <FieldLabel htmlFor="crit-order">Thứ tự</FieldLabel>
                <Input
                  id="crit-order"
                  type="number"
                  min={0}
                  aria-invalid={!!errors.sortOrder}
                  {...form.register('sortOrder', { valueAsNumber: true })}
                />
                <FieldError errors={[errors.sortOrder]} />
              </Field>
            </div>
            <Field data-invalid={!!errors.description}>
              <FieldLabel htmlFor="crit-desc">Mô tả, câu hỏi gợi ý</FieldLabel>
              <Textarea
                id="crit-desc"
                rows={3}
                aria-invalid={!!errors.description}
                {...form.register('description')}
              />
              <FieldError errors={[errors.description]} />
            </Field>
            {RUBRIC_LEVELS.map((level) => (
              <Field key={level.key} data-invalid={!!errors[level.key]}>
                <FieldLabel htmlFor={`crit-${level.key}`}>
                  {level.label} ({level.range})
                </FieldLabel>
                <Textarea
                  id={`crit-${level.key}`}
                  rows={2}
                  aria-invalid={!!errors[level.key]}
                  {...form.register(level.key)}
                />
                <FieldError errors={[errors[level.key]]} />
              </Field>
            ))}
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
              {criterion ? 'Lưu' : 'Thêm tiêu chí'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
