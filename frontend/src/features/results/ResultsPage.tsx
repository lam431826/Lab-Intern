import { CheckCheck, Download, Lock } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { saveFile } from '@/api/client'
import { useApi } from '@/api/useApi'
import { useCan } from '@/auth/useCan'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryState } from '@/components/common/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { toastError } from '@/lib/errors'
import { formatScore } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  FINAL_LABELS,
  INTERVIEW_LABELS,
  OFFER_LABELS,
  useApproveTrack,
  useDecisionAction,
  useResults,
  type DecisionAction,
  type ResultRow,
  type TrackResult,
} from './api'
import { DecisionDialog, OfferDialog } from './dialogs'

export function ResultsPage() {
  const api = useApi()
  const can = useCan()
  const results = useResults()
  const [params, setParams] = useSearchParams()

  async function exportExcel() {
    try {
      saveFile(await api.download('/api/results/export', 'ket-qua-tuyen-dung.xlsx'))
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <div className="mx-auto max-w-7xl">
      <PageHeader
        title="Kết quả"
        description="Điểm tổng, xếp hạng và gợi ý luôn tính lại từ phiếu đã nộp. Giảng viên phê duyệt thì kết luận và phiếu chấm bị khóa."
        actions={
          (can.isOrganizer || can.isLecturer) && (
            <Button variant="outline" onClick={exportExcel}>
              <Download aria-hidden /> Xuất Excel
            </Button>
          )
        }
      />
      <QueryState query={results}>
        {(tables) => {
          if (tables.length === 0) {
            return <p className="text-sm text-muted-foreground">Bạn chưa được giao ngạch nào.</p>
          }
          const tab = params.get('track') ?? tables[0].trackCode
          return (
            <Tabs value={tab} onValueChange={(v) => setParams({ track: String(v) }, { replace: true })}>
              <div className="overflow-x-auto pb-1">
                <TabsList>
                  {tables.map((t) => (
                    <TabsTrigger key={t.trackCode} value={t.trackCode}>
                      {t.trackName}
                    </TabsTrigger>
                  ))}
                </TabsList>
              </div>
              {tables.map((t) => (
                <TabsContent key={t.trackCode} value={t.trackCode} className="mt-4">
                  <TrackTable table={t} />
                </TabsContent>
              ))}
            </Tabs>
          )
        }}
      </QueryState>
    </div>
  )
}

function TrackTable({ table: t }: { table: TrackResult }) {
  const can = useCan()
  const canDecide = can.isOrganizer || can.isLecturer
  const action = useDecisionAction()
  const approveTrack = useApproveTrack()
  const [deciding, setDeciding] = useState<ResultRow | null>(null)
  const [offering, setOffering] = useState<ResultRow | null>(null)
  const [approvingAll, setApprovingAll] = useState(false)

  const toApprove = t.rows.filter(
    (r) => r.decision && !r.decision.approved && r.decision.finalStatus !== 'PENDING',
  )
  const used = Math.min(100, t.quota === 0 ? 0 : (t.seatsTaken / t.quota) * 100)

  async function run(row: ResultRow, a: DecisionAction, done: string) {
    try {
      await action.mutateAsync({ candidateId: row.candidateId, action: a, version: row.decision!.version })
      toast.success(done)
    } catch (e) {
      toastError(e)
    }
  }

  async function onApproveAll() {
    try {
      await approveTrack.mutateAsync(t.trackId)
      toast.success(`Đã phê duyệt ${toApprove.length} kết luận của ngạch ${t.trackName}`)
      setApprovingAll(false)
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-64 flex-1 space-y-1">
          <div className="flex justify-between text-sm">
            <span>
              Chỉ tiêu: <strong>{t.seatsTaken}</strong>/{t.quota} đã dùng
            </span>
            <span className={t.seatsLeft > 0 ? 'text-muted-foreground' : 'font-medium'}>
              {t.seatsLeft > 0 ? `còn ${t.seatsLeft} chỗ` : 'đủ chỉ tiêu'}
            </span>
          </div>
          <div
            className="h-2 overflow-hidden rounded-full bg-muted"
            role="progressbar"
            aria-valuenow={t.seatsTaken}
            aria-valuemax={t.quota}
            aria-label="Chỉ tiêu đã dùng"
          >
            <div className="h-full bg-primary transition-all" style={{ width: `${used}%` }} />
          </div>
          <p className="text-xs text-muted-foreground">
            Trọng số chung {formatScore(t.weightGeneral)} · chuyên môn {formatScore(t.weightProfessional)} ·
            điểm sàn {formatScore(t.passThreshold)}
          </p>
        </div>
        {can.isLecturer && toApprove.length > 0 && (
          <Button onClick={() => setApprovingAll(true)}>
            <CheckCheck aria-hidden /> Phê duyệt {toApprove.length} kết luận
          </Button>
        )}
      </div>

      <div className="overflow-x-auto rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="text-right">Hạng</TableHead>
              <TableHead>Ứng viên</TableHead>
              <TableHead>Phỏng vấn</TableHead>
              <TableHead className="text-right">Chung</TableHead>
              <TableHead className="text-right">Chuyên môn</TableHead>
              <TableHead className="text-right">Tổng</TableHead>
              <TableHead>Gợi ý</TableHead>
              <TableHead>Kết luận</TableHead>
              {(canDecide || can.isOrganizer) && <TableHead className="text-right">Thao tác</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {t.rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} className="py-8 text-center text-muted-foreground">
                  Chưa có hồ sơ Đạt sàng lọc.
                </TableCell>
              </TableRow>
            ) : (
              t.rows.map((r) => {
                const d = r.decision
                const differs = d && d.finalStatus !== 'PENDING' && d.finalStatus !== r.suggestion
                const isNextWaitlist = r.candidateId === t.nextWaitlistCandidateId
                return (
                  <TableRow key={r.candidateId} className={cn(differs && 'bg-amber-50 dark:bg-amber-950/30')}>
                    <TableCell className="text-right tabular-nums">{r.rank ?? '—'}</TableCell>
                    <TableCell>
                      <Link to={`/scoring/${r.candidateId}/scores`} className="font-medium hover:underline">
                        {r.fullName}
                      </Link>
                      <div className="font-mono text-xs text-muted-foreground">{r.studentCode}</div>
                    </TableCell>
                    <TableCell className="text-sm">
                      {INTERVIEW_LABELS[r.interview]}
                      {r.interview === 'ATTENDED' && (
                        <div className="text-xs text-muted-foreground">
                          {r.submittedSheets}/{r.panelSize} phiếu · {r.passVotes} Đạt / {r.failVotes} Không
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{formatScore(r.generalAverage)}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatScore(r.professionalAverage)}
                    </TableCell>
                    <TableCell
                      className={cn(
                        'text-right font-semibold tabular-nums',
                        r.total !== null &&
                          (r.meetsThreshold ? 'text-emerald-700 dark:text-emerald-400' : 'text-destructive'),
                      )}
                    >
                      {formatScore(r.total)}
                    </TableCell>
                    <TableCell className="text-sm">
                      {r.suggestion ? FINAL_LABELS[r.suggestion] : '—'}
                    </TableCell>
                    <TableCell>
                      {d ? (
                        <div className="space-y-1">
                          <div className="flex flex-wrap items-center gap-1">
                            <Badge
                              variant={
                                d.finalStatus === 'HIRED'
                                  ? 'default'
                                  : d.finalStatus === 'REJECTED'
                                    ? 'destructive'
                                    : 'secondary'
                              }
                            >
                              {FINAL_LABELS[d.finalStatus]}
                              {d.transferTrackCode && ` → ${d.transferTrackCode}`}
                            </Badge>
                            {d.approved && (
                              <Lock className="size-3.5 text-muted-foreground" aria-label="Đã phê duyệt" />
                            )}
                          </div>
                          {(d.emailStatus === 'SENT' || d.offerStatus !== 'NONE') && (
                            <div className="text-xs text-muted-foreground">
                              {d.emailStatus === 'SENT' ? 'Đã gửi email' : 'Chưa gửi email'} ·{' '}
                              {OFFER_LABELS[d.offerStatus]}
                            </div>
                          )}
                          {d.note && (
                            <div className="max-w-56 truncate text-xs text-muted-foreground" title={d.note}>
                              “{d.note}”
                            </div>
                          )}
                        </div>
                      ) : (
                        <span className="text-sm text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    {(canDecide || can.isOrganizer) && (
                      <TableCell className="text-right whitespace-nowrap">
                        {canDecide && !d?.approved && (
                          <Button size="sm" variant="ghost" onClick={() => setDeciding(r)}>
                            Kết luận
                          </Button>
                        )}
                        {can.isLecturer && d && !d.approved && d.finalStatus !== 'PENDING' && (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={action.isPending}
                            onClick={() => run(r, 'approve', `Đã phê duyệt ${r.fullName}`)}
                          >
                            Duyệt
                          </Button>
                        )}
                        {can.isLecturer && d?.approved && d.emailStatus !== 'SENT' && (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={action.isPending}
                            onClick={() => run(r, 'reopen', `Đã mở lại kết luận của ${r.fullName}`)}
                          >
                            Mở lại
                          </Button>
                        )}
                        {can.isOrganizer &&
                          d?.approved &&
                          (d.finalStatus === 'HIRED' || d.finalStatus === 'TRANSFER') && (
                            <Button size="sm" variant="ghost" onClick={() => setOffering(r)}>
                              Offer
                            </Button>
                          )}
                        {canDecide && isNextWaitlist && (
                          <Button
                            size="sm"
                            disabled={action.isPending}
                            onClick={() => run(r, 'promote', `Đã gọi dự bị ${r.fullName} lên Trúng tuyển`)}
                          >
                            Gọi dự bị
                          </Button>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      {deciding && (
        <DecisionDialog row={deciding} trackId={t.trackId} onOpenChange={(o) => !o && setDeciding(null)} />
      )}
      {offering && <OfferDialog row={offering} onOpenChange={(o) => !o && setOffering(null)} />}
      <ConfirmDialog
        open={approvingAll}
        onOpenChange={setApprovingAll}
        title={`Phê duyệt ${toApprove.length} kết luận?`}
        description="Sau khi phê duyệt, kết luận và phiếu chấm của các ứng viên này bị khóa. Có thể mở lại từng người nếu chưa gửi email."
        confirmLabel="Phê duyệt"
        pending={approveTrack.isPending}
        onConfirm={onApproveAll}
      />
    </div>
  )
}
