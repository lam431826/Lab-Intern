import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
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
import { useTracks } from '@/features/admin/api'
import { TrackSelect } from '@/components/common/TrackSelect'
import { blankToNull, handleFormError } from '@/lib/forms'
import { useSaveCandidate, type Candidate } from './api'

const url = z
  .string()
  .max(500, 'Tối đa 500 ký tự')
  .refine(
    (v) => !v.trim() || /^https?:\/\/\S+$/i.test(v.trim()),
    'Link phải bắt đầu bằng http:// hoặc https://',
  )

const schema = z.object({
  studentCode: z.string().trim().min(1, 'Mã SV không được để trống').max(20, 'Mã SV tối đa 20 ký tự'),
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(100, 'Tối đa 100 ký tự'),
  email: z
    .string()
    .max(254)
    .refine((v) => !v.trim() || z.email().safeParse(v.trim()).success, 'Email không đúng định dạng'),
  phone: z.string().max(20, 'Tối đa 20 ký tự'),
  major: z.string().max(100, 'Tối đa 100 ký tự'),
  trackId: z.string().min(1, 'Chưa chọn ngạch'),
  cvLink: url,
  portfolioLink: url,
})
type Values = z.infer<typeof schema>

const FIELDS = [
  'studentCode',
  'fullName',
  'email',
  'phone',
  'major',
  'trackId',
  'cvLink',
  'portfolioLink',
] as const

export function CandidateFormDialog({
  open,
  onOpenChange,
  candidate,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  candidate: Candidate | null
}) {
  const navigate = useNavigate()
  const tracks = useTracks()
  const save = useSaveCandidate()
  const form = useForm<Values>({ resolver: zodResolver(schema) })
  const trackId = useWatch({ control: form.control, name: 'trackId' })
  const trackChanged = candidate !== null && trackId !== '' && trackId !== String(candidate.trackId)

  useEffect(() => {
    if (open) {
      form.reset({
        studentCode: candidate?.studentCode ?? '',
        fullName: candidate?.fullName ?? '',
        email: candidate?.email ?? '',
        phone: candidate?.phone ?? '',
        major: candidate?.major ?? '',
        trackId: candidate ? String(candidate.trackId) : '',
        cvLink: candidate?.cvLink ?? '',
        portfolioLink: candidate?.portfolioLink ?? '',
      })
    }
  }, [open, candidate, form])

  async function onSubmit(v: Values) {
    try {
      const saved = await save.mutateAsync({
        id: candidate?.id ?? null,
        body: {
          studentCode: v.studentCode.trim().toUpperCase(),
          fullName: v.fullName.trim(),
          email: blankToNull(v.email)?.toLowerCase() ?? null,
          phone: blankToNull(v.phone),
          major: blankToNull(v.major),
          trackId: Number(v.trackId),
          cvLink: blankToNull(v.cvLink),
          portfolioLink: blankToNull(v.portfolioLink),
        },
      })
      toast.success(candidate ? 'Đã lưu hồ sơ' : `Đã thêm hồ sơ ${saved.studentCode}`)
      onOpenChange(false)
      if (!candidate) navigate(`/candidates/${saved.id}`)
    } catch (e) {
      handleFormError(e, form.setError, FIELDS)
    }
  }

  const errors = form.formState.errors
  const text = (name: (typeof FIELDS)[number], label: string, props: Record<string, unknown> = {}) => (
    <Field data-invalid={!!errors[name]}>
      <FieldLabel htmlFor={`cand-${name}`}>{label}</FieldLabel>
      <Input id={`cand-${name}`} aria-invalid={!!errors[name]} {...props} {...form.register(name)} />
      <FieldError errors={[errors[name]]} />
    </Field>
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>{candidate ? 'Sửa hồ sơ' : 'Thêm hồ sơ ứng viên'}</DialogTitle>
            <DialogDescription>
              Mã SV là khóa khi nhập Excel: nhập lại file có cùng Mã SV sẽ cập nhật hồ sơ này.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="max-h-[65vh] overflow-y-auto py-4">
            <div className="grid gap-3 sm:grid-cols-[10rem_1fr]">
              {text('studentCode', 'Mã SV', { autoCapitalize: 'characters' })}
              {text('fullName', 'Họ và tên')}
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {text('email', 'Email', { type: 'email' })}
              {text('phone', 'Số điện thoại', { inputMode: 'tel' })}
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {text('major', 'Chuyên ngành')}
              <Field data-invalid={!!errors.trackId}>
                <FieldLabel htmlFor="cand-trackId">Ngạch ứng tuyển</FieldLabel>
                <Controller
                  control={form.control}
                  name="trackId"
                  render={({ field }) => (
                    <TrackSelect
                      id="cand-trackId"
                      value={field.value}
                      onChange={field.onChange}
                      options={tracks.data ?? []}
                      invalid={!!errors.trackId}
                    />
                  )}
                />
                <FieldError errors={[errors.trackId]} />
              </Field>
            </div>
            {trackChanged && (
              <Alert>
                <AlertDescription>
                  Đổi ngạch sẽ đưa hồ sơ về <strong>Chờ duyệt</strong> để Lead ngạch mới duyệt lại. Hồ sơ đã
                  có lịch phỏng vấn thì không đổi ngạch được.
                </AlertDescription>
              </Alert>
            )}
            {text('cvLink', 'Link CV', { type: 'url', placeholder: 'https://drive.google.com/…' })}
            {text('portfolioLink', 'Link Portfolio', { type: 'url' })}
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
              {candidate ? 'Lưu' : 'Thêm hồ sơ'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
