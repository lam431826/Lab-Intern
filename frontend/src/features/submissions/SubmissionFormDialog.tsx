import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
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
import { useCreateSubmission, useUpdateSubmission, type Submission } from './api'

const schema = z.object({
  termCode: z
    .string()
    .trim()
    .min(1, 'Mã kỳ không được để trống')
    .max(20, 'Tối đa 20 ký tự')
    .regex(/^[A-Za-z0-9_-]+$/, 'Mã kỳ chỉ gồm chữ, số, gạch ngang hoặc gạch dưới'),
  title: z.string().trim().min(1, 'Tiêu đề không được để trống').max(200, 'Tối đa 200 ký tự'),
  recipient: z.string().max(200, 'Tối đa 200 ký tự'),
  externalReference: z.string().max(300, 'Tối đa 300 ký tự'),
  note: z.string().max(2000, 'Tối đa 2000 ký tự'),
})
type Values = z.infer<typeof schema>

/** Kỳ gợi ý theo mùa tuyển: tháng 1–4 SP, 5–8 SU, 9–12 FA (theo cách gọi học kỳ của trường). */
export function suggestTerm(now = new Date()): string {
  const m = now.getMonth() + 1
  const season = m <= 4 ? 'SP' : m <= 8 ? 'SU' : 'FA'
  return `${season}${now.getFullYear()}`
}

/** {@code submission = null}: tạo bản nháp mới rồi mở trang chi tiết; có submission: sửa thông tin chung. */
export function SubmissionFormDialog({
  open,
  onOpenChange,
  submission,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  submission: Submission | null
}) {
  const navigate = useNavigate()
  const create = useCreateSubmission()
  const update = useUpdateSubmission(submission?.id ?? 0)
  const form = useForm<Values>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (open) {
      form.reset({
        termCode: submission?.termCode ?? suggestTerm(),
        title: submission?.title ?? '',
        recipient: submission?.recipient ?? '',
        externalReference: submission?.externalReference ?? '',
        note: submission?.note ?? '',
      })
    }
  }, [open, submission, form])

  const pending = create.isPending || update.isPending

  async function onSubmit(v: Values) {
    const common = {
      termCode: v.termCode.trim().toUpperCase(),
      title: v.title.trim(),
      recipient: blankToNull(v.recipient),
      externalReference: blankToNull(v.externalReference),
      note: blankToNull(v.note),
    }
    try {
      if (submission) {
        await update.mutateAsync({ ...common, version: submission.version })
        toast.success('Đã lưu thông tin bản gửi')
        onOpenChange(false)
      } else {
        const created = await create.mutateAsync({ ...common, replacesSubmissionId: null })
        toast.success('Đã tạo bản nháp. Thêm các vị trí tuyển dụng và JD.')
        onOpenChange(false)
        navigate(`/submissions/${created.id}`)
      }
    } catch (e) {
      handleFormError(e, form.setError, ['termCode', 'title', 'recipient', 'externalReference', 'note'])
    }
  }

  const errors = form.formState.errors
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>
              {submission ? 'Sửa thông tin bản gửi' : 'Tạo bản nháp gửi nhu cầu tuyển dụng'}
            </DialogTitle>
            <DialogDescription>
              Lưu lại những gì Lab gửi cho nhà trường (chỉ tiêu, JD). Hệ thống không đăng bài lên trang của
              trường.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <div className="grid gap-3 sm:grid-cols-[8rem_1fr]">
              <Field data-invalid={!!errors.termCode}>
                <FieldLabel htmlFor="sub-term">Mã kỳ</FieldLabel>
                <Input id="sub-term" aria-invalid={!!errors.termCode} {...form.register('termCode')} />
                <FieldError errors={[errors.termCode]} />
              </Field>
              <Field data-invalid={!!errors.title}>
                <FieldLabel htmlFor="sub-title">Tiêu đề</FieldLabel>
                <Input
                  id="sub-title"
                  placeholder="Nhu cầu tuyển thực tập sinh kỳ Spring 2027"
                  aria-invalid={!!errors.title}
                  {...form.register('title')}
                />
                <FieldError errors={[errors.title]} />
              </Field>
            </div>
            <Field data-invalid={!!errors.recipient}>
              <FieldLabel htmlFor="sub-recipient">Đơn vị / người nhận</FieldLabel>
              <Input
                id="sub-recipient"
                placeholder="Phòng Quan hệ doanh nghiệp"
                aria-invalid={!!errors.recipient}
                {...form.register('recipient')}
              />
              <FieldDescription>Có thể điền sau; bắt buộc khi đánh dấu đã gửi.</FieldDescription>
              <FieldError errors={[errors.recipient]} />
            </Field>
            <Field data-invalid={!!errors.externalReference}>
              <FieldLabel htmlFor="sub-ref">Số công văn / link email</FieldLabel>
              <Input
                id="sub-ref"
                aria-invalid={!!errors.externalReference}
                {...form.register('externalReference')}
              />
              <FieldError errors={[errors.externalReference]} />
            </Field>
            <Field data-invalid={!!errors.note}>
              <FieldLabel htmlFor="sub-note">Ghi chú</FieldLabel>
              <Textarea id="sub-note" rows={3} aria-invalid={!!errors.note} {...form.register('note')} />
              <FieldError errors={[errors.note]} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
              Hủy
            </Button>
            <Button type="submit" disabled={pending}>
              {submission ? 'Lưu' : 'Tạo bản nháp'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
