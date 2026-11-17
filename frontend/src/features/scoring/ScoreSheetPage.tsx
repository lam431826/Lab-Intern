import { ArrowLeft, CheckCircle2, Lock, RefreshCw, Send } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { QueryState } from '@/components/common/QueryState'
import { ScoreInput } from '@/components/common/ScoreInput'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { RUBRIC_LEVELS } from '@/features/admin/rubric'
import { toastError } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  missingForSubmit,
  RECOMMENDATION_LABELS,
  useSaveSheet,
  useSheet,
  type Recommendation,
  type Sheet,
  type SheetItem,
} from './api'

const AUTOSAVE_MS = 2000

export function ScoreSheetPage() {
  const { candidateId } = useParams()
  const id = Number(candidateId)
  const sheet = useSheet(id)
  if (!Number.isInteger(id) || id <= 0) return <NotFoundPage />
  return (
    <div className="mx-auto max-w-3xl pb-28">
      <Button
        variant="ghost"
        size="sm"
        className="mb-2 -ml-2"
        nativeButton={false}
        render={<Link to="/scoring" />}
      >
        <ArrowLeft aria-hidden /> Cần chấm
      </Button>
      <QueryState query={sheet}>
        {(s) => <SheetForm key={s.candidateId} sheet={s} reload={() => sheet.refetch()} />}
      </QueryState>
    </div>
  )
}

function SheetForm({ sheet, reload }: { sheet: Sheet; reload: () => Promise<unknown> }) {
  const save = useSaveSheet(sheet.candidateId)
  const [values, setValues] = useState<Record<number, number | null>>(() =>
    Object.fromEntries(sheet.items.map((i) => [i.criterionId, i.value])),
  )
  const [comment, setComment] = useState(sheet.comment ?? '')
  const [rec, setRec] = useState<Recommendation | null>(sheet.recommendation)
  const [dirty, setDirty] = useState(false)
  const [savedAt, setSavedAt] = useState<Date | null>(null)
  const [stale, setStale] = useState(false)
  // Đếm số lần sửa: sửa trong lúc đang lưu thì vẫn còn "chưa lưu" sau khi lưu xong
  const edits = useRef(0)

  const submitted = sheet.submittedAt !== null
  const editable = sheet.editable && !stale
  const filled = sheet.items.filter((i) => values[i.criterionId] != null).length

  function touch() {
    edits.current += 1
    setDirty(true)
  }

  async function persist(submit: boolean): Promise<boolean> {
    const seq = edits.current
    try {
      await save.mutateAsync({
        scores: sheet.items.map((i) => ({
          criterionId: i.criterionId,
          value: values[i.criterionId] ?? null,
        })),
        comment: comment.trim() || null,
        recommendation: rec,
        version: sheet.version,
        submit,
      })
      if (edits.current === seq) setDirty(false)
      setSavedAt(new Date())
      return true
    } catch (e) {
      if (e instanceof ApiError && e.isStale) setStale(true)
      toastError(e)
      return false
    }
  }

  // Tự lưu nháp khi phiếu chưa nộp. Phiếu đã nộp phải luôn đủ điểm nên chỉ lưu khi bấm nút.
  useEffect(() => {
    if (!dirty || submitted || !editable || save.isPending) return
    const t = setTimeout(() => void persist(false), AUTOSAVE_MS)
    return () => clearTimeout(t)
    // persist đọc giá trị mới nhất của lần render này
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [values, comment, rec, dirty, submitted, editable, save.isPending, sheet.version])

  async function onSubmit() {
    const missing = missingForSubmit(values, sheet.items, rec)
    if (missing.length > 0) {
      toast.error(`Còn thiếu: ${missing.join(', ')}`)
      return
    }
    if (await persist(true)) {
      toast.success(submitted ? 'Đã lưu thay đổi phiếu chấm' : 'Đã nộp phiếu chấm')
    }
  }

  async function onReload() {
    setStale(false)
    await reload()
    toast.info('Đã tải lại phiếu mới nhất')
  }

  const groups = [
    { label: 'Tiêu chí chung', items: sheet.items.filter((i) => i.group === 'GENERAL') },
    { label: 'Tiêu chí chuyên môn', items: sheet.items.filter((i) => i.group === 'PROFESSIONAL') },
  ]

  return (
    <div className="space-y-4">
      <div>
        <h2 className="flex flex-wrap items-center gap-2 text-lg font-semibold">
          {sheet.candidateName}
          {submitted ? <Badge>Đã nộp</Badge> : <Badge variant="outline">Phiếu nháp</Badge>}
        </h2>
        <p className="text-sm text-muted-foreground">
          {sheet.studentCode} · {sheet.trackCode}
          {submitted && ` · nộp lúc ${formatDateTime(sheet.submittedAt)}`}
        </p>
      </div>

      {!sheet.editable && (
        <Alert>
          <Lock aria-hidden />
          <AlertTitle>Phiếu đã khóa</AlertTitle>
          <AlertDescription>{sheet.lockedReason}</AlertDescription>
        </Alert>
      )}
      {stale && (
        <Alert variant="destructive">
          <AlertTitle>Phiếu vừa được lưu ở nơi khác</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-3">
            Có thể bạn đang mở phiếu này ở tab hoặc máy khác. Tải lại để lấy bản mới nhất (điểm đang nhập ở
            đây sẽ bị thay).
            <Button size="sm" variant="outline" onClick={onReload}>
              <RefreshCw aria-hidden /> Tải lại phiếu
            </Button>
          </AlertDescription>
        </Alert>
      )}
      {submitted && (
        <Button
          variant="outline"
          size="sm"
          nativeButton={false}
          render={<Link to={`/scoring/${sheet.candidateId}/scores`} />}
        >
          Xem điểm của hội đồng
        </Button>
      )}

      {groups.map(
        (g) =>
          g.items.length > 0 && (
            <section key={g.label} className="space-y-3">
              <h3 className="text-sm font-semibold text-muted-foreground">{g.label}</h3>
              {g.items.map((item) => (
                <CriterionCard
                  key={item.criterionId}
                  item={item}
                  value={values[item.criterionId] ?? null}
                  disabled={!editable}
                  onChange={(v) => {
                    setValues((prev) => ({ ...prev, [item.criterionId]: v }))
                    touch()
                  }}
                />
              ))}
            </section>
          ),
      )}

      <Card size="sm">
        <CardContent className="space-y-3">
          <div className="space-y-2">
            <span className="text-sm font-medium">Đề xuất của bạn</span>
            <div className="flex gap-2" role="radiogroup" aria-label="Đề xuất">
              {(Object.keys(RECOMMENDATION_LABELS) as Recommendation[]).map((r) => (
                <button
                  key={r}
                  type="button"
                  role="radio"
                  aria-checked={rec === r}
                  disabled={!editable}
                  onClick={() => {
                    setRec(r)
                    touch()
                  }}
                  className={cn(
                    'min-h-11 flex-1 rounded-md border text-sm font-medium hover:bg-muted disabled:opacity-50 sm:flex-none sm:px-6',
                    rec === r &&
                      r === 'PASS' &&
                      'border-emerald-600 bg-emerald-600 text-white hover:bg-emerald-600',
                    rec === r &&
                      r === 'FAIL' &&
                      'border-destructive bg-destructive text-white hover:bg-destructive',
                  )}
                >
                  {RECOMMENDATION_LABELS[r]}
                </button>
              ))}
            </div>
          </div>
          <label className="block space-y-2">
            <span className="text-sm font-medium">Nhận xét</span>
            <Textarea
              rows={4}
              value={comment}
              disabled={!editable}
              maxLength={2000}
              placeholder="Điểm mạnh, điểm cần cải thiện, ghi chú cho hội đồng…"
              onChange={(e) => {
                setComment(e.target.value)
                touch()
              }}
            />
          </label>
        </CardContent>
      </Card>

      {/* Thanh dưới cố định: tiến độ, trạng thái lưu, nút nộp — với tới bằng ngón cái trên điện thoại */}
      {sheet.editable && (
        <div className="fixed inset-x-0 bottom-0 z-20 border-t bg-background/95 p-3 backdrop-blur md:left-(--sidebar-width)">
          <div className="mx-auto flex max-w-3xl items-center gap-3">
            <div className="min-w-0 flex-1 text-xs text-muted-foreground" aria-live="polite">
              <div className="font-medium text-foreground">
                {filled}/{sheet.items.length} tiêu chí{rec ? ` · ${RECOMMENDATION_LABELS[rec]}` : ''}
              </div>
              {save.isPending
                ? 'Đang lưu…'
                : dirty
                  ? submitted
                    ? 'Có thay đổi chưa lưu'
                    : 'Sẽ tự lưu nháp…'
                  : savedAt
                    ? `Đã lưu lúc ${savedAt.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`
                    : submitted
                      ? 'Đã nộp; sửa xong bấm Lưu'
                      : 'Chưa lưu'}
            </div>
            <Button
              size="lg"
              onClick={onSubmit}
              disabled={!editable || save.isPending || (submitted && !dirty)}
            >
              {submitted ? (
                <>
                  <CheckCircle2 aria-hidden /> Lưu thay đổi
                </>
              ) : (
                <>
                  <Send aria-hidden /> Nộp phiếu
                </>
              )}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

function CriterionCard({
  item,
  value,
  disabled,
  onChange,
}: {
  item: SheetItem
  value: number | null
  disabled: boolean
  onChange: (v: number | null) => void
}) {
  const hasRubric = RUBRIC_LEVELS.some((l) => item[l.key])
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle className="flex items-start justify-between gap-2">
          <span>{item.name}</span>
          {value !== null && <span className="text-lg font-semibold tabular-nums">{value}</span>}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {(item.description || hasRubric) && (
          <details className="rounded-md bg-muted/50 p-2 text-sm">
            <summary className="cursor-pointer text-muted-foreground">Gợi ý chấm</summary>
            {item.description && <p className="mt-2 whitespace-pre-line">{item.description}</p>}
            {hasRubric && (
              <dl className="mt-2 space-y-1">
                {RUBRIC_LEVELS.map(
                  (l) =>
                    item[l.key] && (
                      <div key={l.key}>
                        <dt className="inline font-medium">
                          {l.label} ({l.range}):{' '}
                        </dt>
                        <dd className="inline">{item[l.key]}</dd>
                      </div>
                    ),
                )}
              </dl>
            )}
          </details>
        )}
        <ScoreInput label={item.name} value={value} onChange={onChange} disabled={disabled} />
      </CardContent>
    </Card>
  )
}
