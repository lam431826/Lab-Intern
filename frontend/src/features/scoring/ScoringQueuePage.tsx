import { ChevronRight } from 'lucide-react'
import { useMemo } from 'react'
import { Link } from 'react-router'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryState } from '@/components/common/QueryState'
import { Badge } from '@/components/ui/badge'
import { formatDate } from '@/lib/format'
import { SHEET_STATUS_LABELS, useMyQueue, type QueueItem } from './api'

const ORDER: Record<string, number> = { NONE: 0, DRAFT: 1, SUBMITTED: 2 }

/** Trang chính của thành viên hội đồng: ứng viên đã có mặt trong các ca mình ngồi, phiếu chưa nộp lên trước. */
export function ScoringQueuePage() {
  const queue = useMyQueue()
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Cần chấm"
        description="Ứng viên đã điểm danh có mặt trong các ca bạn ngồi hội đồng. Chấm độc lập: nộp phiếu của mình rồi mới xem được điểm người khác."
      />
      <QueryState query={queue}>{(items) => <QueueList items={items} />}</QueryState>
    </div>
  )
}

function QueueList({ items }: { items: QueueItem[] }) {
  const sorted = useMemo(
    () =>
      items.toSorted(
        (a, b) =>
          ORDER[a.sheetStatus] - ORDER[b.sheetStatus] ||
          a.sessionDate.localeCompare(b.sessionDate) ||
          a.timeLabel.localeCompare(b.timeLabel),
      ),
    [items],
  )
  if (items.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        Chưa có ứng viên nào cần chấm. Ứng viên xuất hiện ở đây sau khi được điểm danh có mặt.
      </p>
    )
  }
  const todo = items.filter((i) => i.sheetStatus !== 'SUBMITTED').length
  return (
    <div className="space-y-2">
      <p className="text-sm text-muted-foreground">
        {todo > 0 ? `${todo} phiếu chưa nộp` : 'Đã nộp hết phiếu'} · {items.length} ứng viên
      </p>
      <ul className="divide-y rounded-lg border">
        {sorted.map((i) => (
          <li key={i.candidateId}>
            <Link
              to={`/scoring/${i.candidateId}`}
              className="flex min-h-16 items-center gap-3 px-3 py-2 hover:bg-muted/50 focus-visible:bg-muted/50"
            >
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium">{i.candidateName}</div>
                <div className="text-xs text-muted-foreground">
                  {i.trackCode} · {formatDate(i.sessionDate)} {i.timeLabel}
                </div>
              </div>
              <Badge
                variant={
                  i.sheetStatus === 'SUBMITTED'
                    ? 'default'
                    : i.sheetStatus === 'DRAFT'
                      ? 'secondary'
                      : 'outline'
                }
              >
                {SHEET_STATUS_LABELS[i.sheetStatus]}
              </Badge>
              <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
