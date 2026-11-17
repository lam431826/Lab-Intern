import { RefreshCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { ScoreInput } from '@/components/common/ScoreInput'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'
import { handleFormError } from '@/lib/forms'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { CV_ITEMS, STATUS_LABELS, useScreen, type Candidate, type CvKey, type ScreeningStatus } from './api'

type Values = Record<CvKey, number | null> & { status: ScreeningStatus; reason: string }

function valuesOf(c: Candidate): Values {
  return {
    cvPersonalInfo: c.cvPersonalInfo,
    cvExperience: c.cvExperience,
    cvSkills: c.cvSkills,
    cvProjects: c.cvProjects,
    cvCertificates: c.cvCertificates,
    status: c.screeningStatus,
    reason: c.screeningReason ?? '',
  }
}

/** Trung bình các mục đã chấm, 1 chữ số thập phân (khớp ScreeningRules.cvAverage của backend). */
export function cvAverage(scores: (number | null)[]): number | null {
  const given = scores.filter((s): s is number => s !== null)
  if (given.length === 0) return null
  return Math.round((given.reduce((a, b) => a + b, 0) / given.length) * 10) / 10
}

/**
 * Phiếu sàng lọc CV. Lead của ngạch hoặc Người tổ chức sửa được; người khác chỉ xem.
 * Hai người cùng sửa: người lưu sau nhận STALE_DATA, nội dung đang nhập được giữ cho tới khi bấm tải lại.
 */
export function ScreeningCard({
  candidate: c,
  editable,
  onReload,
}: {
  candidate: Candidate
  editable: boolean
  onReload: () => Promise<unknown>
}) {
  const screen = useScreen(c.id)
  const form = useForm<Values>({ defaultValues: valuesOf(c) })
  const [stale, setStale] = useState(false)
  useEffect(() => {
    if (!stale) form.reset(valuesOf(c))
    // Chỉ đồng bộ khi dữ liệu máy chủ đổi; khi đang xung đột thì giữ nội dung người dùng đã nhập
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [c])

  const watched = useWatch({ control: form.control })
  const average = cvAverage(CV_ITEMS.map((i) => watched[i.key] ?? null))
  // Hồ sơ đã có lịch phỏng vấn thì không đổi khỏi Đạt (backend: CANDIDATE_SCHEDULED)
  const lockedPassed = c.scheduled && c.screeningStatus === 'PASSED'

  async function onSubmit(v: Values) {
    if (v.status === 'REJECTED' && !v.reason.trim()) {
      form.setError('reason', { type: 'required', message: 'Loại hồ sơ thì phải ghi lý do' })
      return
    }
    try {
      await screen.mutateAsync({
        cvPersonalInfo: v.cvPersonalInfo,
        cvExperience: v.cvExperience,
        cvSkills: v.cvSkills,
        cvProjects: v.cvProjects,
        cvCertificates: v.cvCertificates,
        status: v.status,
        reason: v.reason.trim() || null,
        version: c.version,
      })
      setStale(false)
      toast.success(`Đã lưu kết quả sàng lọc: ${STATUS_LABELS[v.status]}`)
    } catch (e) {
      if (e instanceof ApiError && e.isStale) setStale(true)
      handleFormError(e, form.setError, ['reason'])
    }
  }

  async function reload() {
    setStale(false)
    await onReload()
  }

  return (
    <Card>
      <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <CardHeader>
          <CardTitle>Sàng lọc CV</CardTitle>
          <CardDescription>
            {c.screenedAt
              ? `Duyệt bởi ${c.screenedByName ?? '—'} lúc ${formatDateTime(c.screenedAt)}`
              : 'Chấm 5 mục theo thang 1–10 (bỏ trống mục không đánh giá được), rồi chọn Đạt hoặc Loại.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {stale && (
            <Alert variant="destructive">
              <AlertTitle>Hồ sơ vừa được người khác cập nhật</AlertTitle>
              <AlertDescription className="flex flex-wrap items-center gap-3">
                Nội dung bạn nhập vẫn còn ở dưới. Tải dữ liệu mới để xem thay đổi (nội dung đang nhập sẽ bị
                thay).
                <Button type="button" size="sm" variant="outline" onClick={reload}>
                  <RefreshCw aria-hidden /> Tải dữ liệu mới
                </Button>
              </AlertDescription>
            </Alert>
          )}
          <div className="space-y-3">
            {CV_ITEMS.map((item) => (
              <div key={item.key} className="grid gap-1 sm:grid-cols-[12rem_1fr] sm:items-center">
                <span className="text-sm">{item.label}</span>
                <Controller
                  control={form.control}
                  name={item.key}
                  render={({ field }) => (
                    <ScoreInput
                      label={item.label}
                      value={field.value}
                      onChange={field.onChange}
                      disabled={!editable}
                    />
                  )}
                />
              </div>
            ))}
            <p className="text-sm">
              Điểm trung bình:{' '}
              <strong className="tabular-nums">
                {average === null ? '—' : average.toLocaleString('vi-VN', { minimumFractionDigits: 1 })}
              </strong>
            </p>
          </div>

          <Controller
            control={form.control}
            name="status"
            render={({ field }) => (
              <fieldset className="space-y-2">
                <legend className="text-sm font-medium">Kết quả</legend>
                <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Kết quả sàng lọc">
                  {(Object.keys(STATUS_LABELS) as ScreeningStatus[]).map((s) => (
                    <button
                      key={s}
                      type="button"
                      role="radio"
                      aria-checked={field.value === s}
                      disabled={!editable || (lockedPassed && s !== 'PASSED')}
                      onClick={() => field.onChange(s)}
                      className={cn(
                        'rounded-md border px-3 py-1.5 text-sm transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50',
                        field.value === s &&
                          s === 'PASSED' &&
                          'border-emerald-600 bg-emerald-600 text-white hover:bg-emerald-600',
                        field.value === s &&
                          s === 'REJECTED' &&
                          'border-destructive bg-destructive text-white hover:bg-destructive',
                        field.value === s &&
                          s === 'PENDING' &&
                          'border-foreground bg-foreground text-background hover:bg-foreground',
                      )}
                    >
                      {STATUS_LABELS[s]}
                    </button>
                  ))}
                </div>
                {lockedPassed && (
                  <p className="text-xs text-muted-foreground">
                    Hồ sơ đã có lịch phỏng vấn nên giữ trạng thái Đạt. Hủy lịch trước nếu cần đổi kết quả.
                  </p>
                )}
              </fieldset>
            )}
          />

          <Field data-invalid={!!form.formState.errors.reason}>
            <FieldLabel htmlFor="screen-reason">
              Lý do {watched.status === 'REJECTED' ? '(bắt buộc khi Loại)' : '(không bắt buộc)'}
            </FieldLabel>
            <Textarea
              id="screen-reason"
              rows={3}
              disabled={!editable}
              aria-invalid={!!form.formState.errors.reason}
              {...form.register('reason')}
            />
            <FieldError errors={[form.formState.errors.reason]} />
          </Field>
        </CardContent>
        {editable && (
          <CardFooter className="justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={!form.formState.isDirty || screen.isPending}
              onClick={() => form.reset(valuesOf(c))}
            >
              Hoàn tác
            </Button>
            <Button type="submit" disabled={screen.isPending}>
              Lưu kết quả
            </Button>
          </CardFooter>
        )}
      </form>
    </Card>
  )
}
