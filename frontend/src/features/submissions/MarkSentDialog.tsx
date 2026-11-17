import { zodResolver } from '@hookform/resolvers/zod'
import { CircleAlert, CircleCheck, Lock } from 'lucide-react'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
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
import { blankToNull, handleFormError } from '@/lib/forms'
import { toDateTimeLocal } from '@/lib/format'
import { useMarkSent, type Submission } from './api'

const schema = z.object({
  sentAt: z
    .string()
    .min(1, 'Chọn thời điểm gửi')
    .refine((v) => !Number.isNaN(new Date(v).getTime()), 'Thời điểm không hợp lệ')
    // Backend cho lệch tối đa 5 phút để tránh lệch đồng hồ
    .refine(
      (v) => new Date(v).getTime() <= Date.now() + 5 * 60_000,
      'Thời điểm gửi không được nằm trong tương lai',
    ),
  recipient: z
    .string()
    .trim()
    .min(1, 'Đơn vị hoặc người nhận không được để trống')
    .max(200, 'Tối đa 200 ký tự'),
  externalReference: z.string().max(300, 'Tối đa 300 ký tự'),
})
type Values = z.infer<typeof schema>

/** Kiểm tra trước các điều kiện backend sẽ kiểm tra khi chốt, để người dùng biết còn thiếu gì. */
export function sendProblems(s: Submission): string[] {
  const items = s.items ?? []
  if (items.length === 0) return ['Chưa có vị trí tuyển dụng nào']
  return items
    .filter((i) => !i.hasFile && !i.jdContent?.trim())
    .map((i) => `Vị trí "${i.positionTitle}" chưa có nội dung hoặc file JD`)
}

export function MarkSentDialog({
  open,
  onOpenChange,
  submission,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  submission: Submission
}) {
  const markSent = useMarkSent(submission.id)
  const form = useForm<Values>({ resolver: zodResolver(schema) })
  const problems = sendProblems(submission)

  useEffect(() => {
    if (open) {
      form.reset({
        sentAt: toDateTimeLocal(new Date()),
        recipient: submission.recipient ?? '',
        externalReference: submission.externalReference ?? '',
      })
    }
  }, [open, submission, form])

  async function onSubmit(v: Values) {
    try {
      await markSent.mutateAsync({
        sentAt: new Date(v.sentAt).toISOString(),
        recipient: v.recipient.trim(),
        externalReference: blankToNull(v.externalReference),
        version: submission.version,
      })
      toast.success('Đã chốt bản gửi. Nội dung được lưu nguyên trạng để tra cứu về sau.')
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, form.setError, ['sentAt', 'recipient', 'externalReference'])
    }
  }

  const errors = form.formState.errors
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>Đánh dấu đã gửi cho trường</DialogTitle>
            <DialogDescription>
              Ghi nhận Lab đã gửi bản này ({submission.positionCount} vị trí, tổng chỉ tiêu{' '}
              {submission.totalQuota}).
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            {problems.length > 0 ? (
              <Alert variant="destructive">
                <CircleAlert aria-hidden />
                <AlertTitle>Chưa chốt được</AlertTitle>
                <AlertDescription>
                  <ul className="list-disc pl-4">
                    {problems.map((p) => (
                      <li key={p}>{p}</li>
                    ))}
                  </ul>
                </AlertDescription>
              </Alert>
            ) : (
              <Alert>
                <Lock aria-hidden />
                <AlertTitle>Sau khi chốt sẽ không sửa hay xóa được</AlertTitle>
                <AlertDescription>
                  Muốn điều chỉnh thì tạo bản bổ sung/thay thế từ bản này. Lịch sử các lần gửi luôn được giữ.
                </AlertDescription>
              </Alert>
            )}
            <FieldGroup>
              <Field data-invalid={!!errors.sentAt}>
                <FieldLabel htmlFor="sent-at">Thời điểm gửi</FieldLabel>
                <Input
                  id="sent-at"
                  type="datetime-local"
                  aria-invalid={!!errors.sentAt}
                  {...form.register('sentAt')}
                />
                <FieldDescription>Theo giờ trên máy của bạn.</FieldDescription>
                <FieldError errors={[errors.sentAt]} />
              </Field>
              <Field data-invalid={!!errors.recipient}>
                <FieldLabel htmlFor="sent-recipient">Đơn vị / người nhận</FieldLabel>
                <Input
                  id="sent-recipient"
                  aria-invalid={!!errors.recipient}
                  {...form.register('recipient')}
                />
                <FieldError errors={[errors.recipient]} />
              </Field>
              <Field data-invalid={!!errors.externalReference}>
                <FieldLabel htmlFor="sent-ref">Số công văn / link email</FieldLabel>
                <Input
                  id="sent-ref"
                  aria-invalid={!!errors.externalReference}
                  {...form.register('externalReference')}
                />
                <FieldError errors={[errors.externalReference]} />
              </Field>
            </FieldGroup>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={markSent.isPending}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={problems.length > 0 || markSent.isPending}>
              <CircleCheck aria-hidden /> Chốt đã gửi
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
