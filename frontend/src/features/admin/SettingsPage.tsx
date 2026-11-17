import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { useCan } from '@/auth/useCan'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryState } from '@/components/common/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { handleFormError } from '@/lib/forms'
import { formatScore } from '@/lib/format'
import { useSettings, useUpdateSettings, type Settings } from './api'

/** Làm tròn 2 chữ số để 1 − 0,4 không thành 0,6000000000000001. */
const round2 = (n: number) => Math.round(n * 100) / 100

export const settingsSchema = z
  .object({
    weightGeneral: z.number({ error: 'Nhập số từ 0 đến 1' }).min(0, 'Từ 0 đến 1').max(1, 'Từ 0 đến 1'),
    weightProfessional: z.number({ error: 'Nhập số từ 0 đến 1' }).min(0, 'Từ 0 đến 1').max(1, 'Từ 0 đến 1'),
    passThreshold: z.number({ error: 'Nhập số từ 1 đến 10' }).min(1, 'Từ 1 đến 10').max(10, 'Từ 1 đến 10'),
    slaHours: z
      .number({ error: 'Nhập số giờ' })
      .int('Số giờ nguyên')
      .min(1, 'Ít nhất 1 giờ')
      .max(720, 'Tối đa 720 giờ'),
    slotMinutes: z
      .number({ error: 'Nhập số phút' })
      .int('Số phút nguyên')
      .min(5, 'Từ 5 phút')
      .max(60, 'Tối đa 60 phút'),
  })
  .refine((v) => round2(v.weightGeneral + v.weightProfessional) === 1, {
    path: ['weightProfessional'],
    message: 'Hai trọng số phải cộng lại bằng 1',
  })

export function SettingsPage() {
  const settings = useSettings()
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Tham số chấm"
        description="Áp dụng ngay cho mọi bảng kết quả: điểm không lưu sẵn mà luôn được tính lại từ phiếu chấm."
      />
      <QueryState query={settings}>{(data) => <SettingsForm settings={data} />}</QueryState>
    </div>
  )
}

function SettingsForm({ settings }: { settings: Settings }) {
  const can = useCan()
  const update = useUpdateSettings()
  const form = useForm<Settings>({ resolver: zodResolver(settingsSchema), defaultValues: settings })
  useEffect(() => form.reset(settings), [settings, form])

  const [wg, wp, threshold] = useWatch({
    control: form.control,
    name: ['weightGeneral', 'weightProfessional', 'passThreshold'],
  })
  const errors = form.formState.errors
  const readOnly = !can.editSettings

  // Sửa một trọng số thì trọng số kia tự đổi để tổng luôn bằng 1
  const weightField = (name: 'weightGeneral' | 'weightProfessional', other: typeof name) =>
    form.register(name, {
      valueAsNumber: true,
      onChange: (e) => {
        const v = Number(e.target.value)
        if (!Number.isNaN(v) && v >= 0 && v <= 1) {
          form.setValue(other, round2(1 - v), { shouldValidate: true })
        }
      },
    })

  async function onSubmit(v: Settings) {
    try {
      await update.mutateAsync(v)
      toast.success('Đã lưu tham số chấm')
    } catch (e) {
      handleFormError(e, form.setError, [
        'weightGeneral',
        'weightProfessional',
        'passThreshold',
        'slaHours',
        'slotMinutes',
      ])
    }
  }

  // Ví dụ minh họa công thức với điểm chung 8, chuyên môn 7
  const example = Number.isFinite(wg) && Number.isFinite(wp) ? wg * 8 + wp * 7 : null

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Tính điểm và kết luận</CardTitle>
          <CardDescription>
            Tổng = trọng số chung × điểm chung + trọng số chuyên môn × điểm chuyên môn. Đạt điểm sàn và nằm
            trong chỉ tiêu thì được gợi ý Trúng tuyển.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field data-invalid={!!errors.weightGeneral}>
                <FieldLabel htmlFor="s-wg">Trọng số điểm chung</FieldLabel>
                <Input
                  id="s-wg"
                  type="number"
                  step={0.05}
                  min={0}
                  max={1}
                  disabled={readOnly}
                  aria-invalid={!!errors.weightGeneral}
                  {...weightField('weightGeneral', 'weightProfessional')}
                />
                <FieldError errors={[errors.weightGeneral]} />
              </Field>
              <Field data-invalid={!!errors.weightProfessional}>
                <FieldLabel htmlFor="s-wp">Trọng số điểm chuyên môn</FieldLabel>
                <Input
                  id="s-wp"
                  type="number"
                  step={0.05}
                  min={0}
                  max={1}
                  disabled={readOnly}
                  aria-invalid={!!errors.weightProfessional}
                  {...weightField('weightProfessional', 'weightGeneral')}
                />
                <FieldError errors={[errors.weightProfessional]} />
              </Field>
              <Field data-invalid={!!errors.passThreshold}>
                <FieldLabel htmlFor="s-pass">Điểm sàn</FieldLabel>
                <Input
                  id="s-pass"
                  type="number"
                  step={0.1}
                  min={1}
                  max={10}
                  disabled={readOnly}
                  aria-invalid={!!errors.passThreshold}
                  {...form.register('passThreshold', { valueAsNumber: true })}
                />
                <FieldError errors={[errors.passThreshold]} />
              </Field>
            </div>
            {example !== null && (
              <p className="rounded-md bg-muted/50 p-3 text-sm" aria-live="polite">
                Ví dụ: điểm chung 8, chuyên môn 7 → tổng {formatScore(example)}
                {Number.isFinite(threshold) && (example >= threshold ? ' (đạt sàn)' : ' (dưới sàn)')}.
              </p>
            )}
          </FieldGroup>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Sàng lọc và lịch phỏng vấn</CardTitle>
        </CardHeader>
        <CardContent>
          <FieldGroup className="grid gap-4 sm:grid-cols-2">
            <Field data-invalid={!!errors.slaHours}>
              <FieldLabel htmlFor="s-sla">Hạn duyệt CV (giờ)</FieldLabel>
              <Input
                id="s-sla"
                type="number"
                min={1}
                max={720}
                disabled={readOnly}
                aria-invalid={!!errors.slaHours}
                {...form.register('slaHours', { valueAsNumber: true })}
              />
              <FieldDescription>Hồ sơ chờ duyệt quá hạn này bị đánh dấu quá SLA.</FieldDescription>
              <FieldError errors={[errors.slaHours]} />
            </Field>
            <Field data-invalid={!!errors.slotMinutes}>
              <FieldLabel htmlFor="s-slot">Độ dài một slot (phút)</FieldLabel>
              <Input
                id="s-slot"
                type="number"
                min={5}
                max={60}
                disabled={readOnly}
                aria-invalid={!!errors.slotMinutes}
                {...form.register('slotMinutes', { valueAsNumber: true })}
              />
              <FieldDescription>Dùng khi tự xếp lịch; slot đã xếp không đổi.</FieldDescription>
              <FieldError errors={[errors.slotMinutes]} />
            </Field>
          </FieldGroup>
        </CardContent>
        {!readOnly && (
          <CardFooter className="justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={!form.formState.isDirty || update.isPending}
              onClick={() => form.reset(settings)}
            >
              Hoàn tác
            </Button>
            <Button type="submit" disabled={!form.formState.isDirty || update.isPending}>
              Lưu tham số
            </Button>
          </CardFooter>
        )}
      </Card>
    </form>
  )
}
