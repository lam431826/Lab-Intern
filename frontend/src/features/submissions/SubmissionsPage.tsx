import { Plus, Search } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { Link } from 'react-router'
import { useCan } from '@/auth/useCan'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryState } from '@/components/common/QueryState'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useTracks } from '@/features/admin/api'
import { formatDateTime } from '@/lib/format'
import { STATUS_LABELS, useSubmissions, type SubmissionFilter, type SubmissionStatus } from './api'
import { TrackSelect } from '@/components/common/TrackSelect'
import { StatusBadge } from './parts'
import { SubmissionFormDialog } from './SubmissionFormDialog'

const STATUS_ITEMS = [
  { value: 'ALL', label: 'Mọi trạng thái' },
  ...(Object.keys(STATUS_LABELS) as SubmissionStatus[]).map((s) => ({ value: s, label: STATUS_LABELS[s] })),
]

export function SubmissionsPage() {
  const can = useCan()
  const tracks = useTracks()
  const [term, setTerm] = useState('')
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('ALL')
  const [trackId, setTrackId] = useState('ALL')
  const [creating, setCreating] = useState(false)

  const filter: SubmissionFilter = {
    termCode: useDeferredValue(term.trim()) || undefined,
    q: useDeferredValue(q.trim()) || undefined,
    status: status === 'ALL' ? undefined : (status as SubmissionStatus),
    trackId: trackId === 'ALL' ? undefined : Number(trackId),
  }
  const submissions = useSubmissions(filter)

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        title="Gửi nhu cầu tuyển dụng"
        description="Lịch sử các lần Lab gửi chỉ tiêu và JD cho nhà trường. Bản đã gửi được lưu nguyên trạng."
        actions={
          can.isOrganizer && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden /> Tạo bản nháp
            </Button>
          )
        }
      />
      <div className="mb-3 grid gap-2 sm:grid-cols-[1fr_8rem_11rem_13rem]">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="Tìm theo tiêu đề, người nhận, vị trí"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Tìm bản gửi"
          />
        </div>
        <Input
          placeholder="Mã kỳ"
          value={term}
          onChange={(e) => setTerm(e.target.value)}
          aria-label="Lọc theo mã kỳ"
        />
        <Select items={STATUS_ITEMS} value={status} onValueChange={(v) => setStatus(v ?? 'ALL')}>
          <SelectTrigger className="w-full" aria-label="Lọc theo trạng thái">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATUS_ITEMS.map((i) => (
              <SelectItem key={i.value} value={i.value}>
                {i.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <TrackSelect value={trackId} onChange={setTrackId} options={tracks.data ?? []} allLabel="Mọi ngạch" />
      </div>
      <QueryState query={submissions}>
        {(list) => (
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Kỳ</TableHead>
                  <TableHead>Tiêu đề</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="text-right">Vị trí</TableHead>
                  <TableHead className="text-right">Chỉ tiêu</TableHead>
                  <TableHead>Gửi lúc / người nhận</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                      Chưa có bản gửi nào phù hợp.
                    </TableCell>
                  </TableRow>
                ) : (
                  list.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell className="font-mono text-xs">{s.termCode}</TableCell>
                      <TableCell>
                        <Link to={`/submissions/${s.id}`} className="font-medium hover:underline">
                          {s.title}
                        </Link>
                        {s.replacesSubmissionId && (
                          <span className="block text-xs text-muted-foreground">
                            Thay thế bản #{s.replacesSubmissionId}
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={s.status} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{s.positionCount}</TableCell>
                      <TableCell className="text-right tabular-nums">{s.totalQuota}</TableCell>
                      <TableCell className="text-sm">
                        {s.sentAt ? (
                          <>
                            {formatDateTime(s.sentAt)}
                            <span className="block text-xs text-muted-foreground">{s.recipient}</span>
                          </>
                        ) : (
                          <span className="text-muted-foreground">
                            Cập nhật {formatDateTime(s.updatedAt)}
                          </span>
                        )}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        )}
      </QueryState>
      <SubmissionFormDialog open={creating} onOpenChange={setCreating} submission={null} />
    </div>
  )
}
