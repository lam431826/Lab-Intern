import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { ApiError } from '@/api/client'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { QueryState } from '@/components/common/QueryState'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { formatScore } from '@/lib/format'
import { RECOMMENDATION_LABELS, useCandidateScores, type CandidateScores } from './api'

/** Điểm của cả hội đồng cho một ứng viên: chỉ tính phiếu đã nộp. */
export function CandidateScoresPage() {
  const { candidateId } = useParams()
  const id = Number(candidateId)
  const scores = useCandidateScores(id)
  if (!Number.isInteger(id) || id <= 0) return <NotFoundPage />

  const submitFirst =
    scores.error instanceof ApiError && scores.error.code === 'SUBMIT_FIRST' ? scores.error : null
  return (
    <div className="mx-auto max-w-6xl">
      <Button
        variant="ghost"
        size="sm"
        className="mb-2 -ml-2"
        nativeButton={false}
        render={<Link to={`/scoring/${id}`} />}
      >
        <ArrowLeft aria-hidden /> Phiếu chấm của tôi
      </Button>
      {submitFirst ? (
        <Alert>
          <AlertTitle>Nộp phiếu của bạn trước</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>{submitFirst.message}</p>
            <Button size="sm" nativeButton={false} render={<Link to={`/scoring/${id}`} />}>
              Mở phiếu chấm
            </Button>
          </AlertDescription>
        </Alert>
      ) : (
        <QueryState query={scores}>{(s) => <ScoreMatrix scores={s} />}</QueryState>
      )}
    </div>
  )
}

function ScoreMatrix({ scores: s }: { scores: CandidateScores }) {
  const summary = [
    { label: 'Điểm chung', value: s.generalAverage },
    { label: 'Điểm chuyên môn', value: s.professionalAverage },
    { label: 'Tổng có trọng số', value: s.total },
  ]
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold">Điểm của hội đồng</h2>
        <span className="text-sm text-muted-foreground">
          {s.submittedCount}/{s.panelSize} phiếu đã nộp
        </span>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {summary.map((x) => (
          <Card key={x.label} size="sm">
            <CardContent className="text-center">
              <div className="text-2xl font-semibold tabular-nums">{formatScore(x.value)}</div>
              <div className="text-xs text-muted-foreground">{x.label}</div>
            </CardContent>
          </Card>
        ))}
      </div>
      {s.evaluators.length === 0 ? (
        <p className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
          Chưa có phiếu nào được nộp.
        </p>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="min-w-48">Tiêu chí</TableHead>
                {s.evaluators.map((e) => (
                  <TableHead key={e.evaluatorId} className="text-center">
                    {e.name}
                  </TableHead>
                ))}
                <TableHead className="text-center">Trung bình</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {s.criteria.map((c) => (
                <TableRow key={c.criterionId}>
                  <TableCell>
                    {c.name}
                    {c.group === 'PROFESSIONAL' && (
                      <Badge variant="outline" className="ml-2">
                        Chuyên môn
                      </Badge>
                    )}
                  </TableCell>
                  {s.evaluators.map((e) => (
                    <TableCell key={e.evaluatorId} className="text-center tabular-nums">
                      {e.values[c.criterionId] ?? '—'}
                    </TableCell>
                  ))}
                  <TableCell className="text-center font-medium tabular-nums">
                    {formatScore(c.average)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <TableCell>Đề xuất</TableCell>
                {s.evaluators.map((e) => (
                  <TableCell key={e.evaluatorId} className="text-center">
                    <Badge variant={e.recommendation === 'PASS' ? 'default' : 'destructive'}>
                      {RECOMMENDATION_LABELS[e.recommendation]}
                    </Badge>
                  </TableCell>
                ))}
                <TableCell />
              </TableRow>
            </TableFooter>
          </Table>
        </div>
      )}
      {s.evaluators.some((e) => e.comment) && (
        <section className="space-y-2">
          <h3 className="font-semibold">Nhận xét</h3>
          {s.evaluators
            .filter((e) => e.comment)
            .map((e) => (
              <Card key={e.evaluatorId} size="sm">
                <CardContent>
                  <div className="text-xs text-muted-foreground">{e.name}</div>
                  <p className="text-sm whitespace-pre-line">{e.comment}</p>
                </CardContent>
              </Card>
            ))}
        </section>
      )}
    </div>
  )
}
