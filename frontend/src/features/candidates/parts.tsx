import { AlertTriangle, CalendarCheck } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { FLAG_LABELS, STATUS_LABELS, type Flag, type ScreeningStatus } from './api'

const STATUS_VARIANT: Record<ScreeningStatus, 'outline' | 'default' | 'destructive'> = {
  PENDING: 'outline',
  PASSED: 'default',
  REJECTED: 'destructive',
}

export function ScreeningBadge({ status }: { status: ScreeningStatus }) {
  return <Badge variant={STATUS_VARIANT[status]}>{STATUS_LABELS[status]}</Badge>
}

export function FlagBadges({ flags }: { flags: Flag[] }) {
  if (flags.length === 0) return null
  return (
    <div className="flex flex-wrap gap-1">
      {flags.map((f) => (
        <Badge key={f} variant="secondary" className="gap-1 text-amber-700 dark:text-amber-400">
          <AlertTriangle className="size-3" aria-hidden />
          {FLAG_LABELS[f]}
        </Badge>
      ))}
    </div>
  )
}

export function ScheduledMark() {
  return (
    <span
      className="inline-flex items-center gap-1 text-xs text-muted-foreground"
      title="Đã xếp lịch phỏng vấn"
    >
      <CalendarCheck className="size-3.5" aria-hidden /> Đã xếp lịch
    </span>
  )
}
