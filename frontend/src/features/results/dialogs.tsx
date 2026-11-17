import { useState } from 'react'
import { toast } from 'sonner'
import { TrackSelect } from '@/components/common/TrackSelect'
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
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { useTracks } from '@/features/admin/api'
import { toastError } from '@/lib/errors'
import { formatScore } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  FINAL_LABELS,
  needsNote,
  OFFER_LABELS,
  useDecide,
  useOffer,
  type FinalStatus,
  type OfferStatus,
  type ResultRow,
} from './api'

const STATUSES: FinalStatus[] = ['HIRED', 'WAITLIST', 'TRANSFER', 'REJECTED', 'PENDING']

/** Chốt kết luận cho một ứng viên. Chỉ render khi mở, nên state khởi tạo từ kết luận hiện tại. */
export function DecisionDialog({
  row,
  trackId,
  onOpenChange,
}: {
  row: ResultRow
  trackId: number
  onOpenChange: (open: boolean) => void
}) {
  const tracks = useTracks()
  const decide = useDecide()
  const [status, setStatus] = useState<FinalStatus>(row.decision?.finalStatus ?? row.suggestion ?? 'PENDING')
  const [transfer, setTransfer] = useState(
    row.decision?.transferTrackId ? String(row.decision.transferTrackId) : '',
  )
  const [note, setNote] = useState(row.decision?.note ?? '')
  const noteRequired = needsNote(status, row.suggestion)
  const invalid = (noteRequired && !note.trim()) || (status === 'TRANSFER' && !transfer)

  async function submit() {
    try {
      await decide.mutateAsync({
        candidateId: row.candidateId,
        body: {
          finalStatus: status,
          transferTrackId: status === 'TRANSFER' ? Number(transfer) : null,
          note: note.trim() || null,
          version: row.decision?.version ?? null,
        },
      })
      toast.success(`${row.fullName}: ${FINAL_LABELS[status]}`)
      onOpenChange(false)
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Kết luận cho {row.fullName}</DialogTitle>
          <DialogDescription>
            Tổng {formatScore(row.total)}
            {row.rank !== null && ` · hạng ${row.rank}`} · hệ thống gợi ý{' '}
            <strong>{row.suggestion ? FINAL_LABELS[row.suggestion] : 'chưa có'}</strong>
          </DialogDescription>
        </DialogHeader>
        <FieldGroup className="py-4">
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Kết luận">
            {STATUSES.map((s) => (
              <button
                key={s}
                type="button"
                role="radio"
                aria-checked={status === s}
                onClick={() => setStatus(s)}
                className={cn(
                  'rounded-md border px-3 py-1.5 text-sm hover:bg-muted',
                  status === s && 'border-foreground bg-foreground text-background hover:bg-foreground',
                )}
              >
                {FINAL_LABELS[s]}
                {s === row.suggestion && ' (gợi ý)'}
              </button>
            ))}
          </div>
          {status === 'TRANSFER' && (
            <Field>
              <FieldLabel htmlFor="dec-transfer">Chuyển sang ngạch</FieldLabel>
              <TrackSelect
                id="dec-transfer"
                value={transfer}
                onChange={setTransfer}
                options={(tracks.data ?? []).filter((t) => t.id !== trackId)}
              />
              <FieldDescription>Người chuyển vào được tính vào chỉ tiêu của ngạch đích.</FieldDescription>
            </Field>
          )}
          <Field>
            <FieldLabel htmlFor="dec-note">
              Lý do {noteRequired ? '(bắt buộc vì khác gợi ý)' : '(không bắt buộc)'}
            </FieldLabel>
            <Textarea
              id="dec-note"
              rows={3}
              maxLength={1000}
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </Field>
          {status !== 'PENDING' && status !== 'REJECTED' && (
            <Alert>
              <AlertDescription>
                Cần hội đồng nộp đủ phiếu ({row.submittedSheets}/{row.panelSize}). Ngạch đã đủ chỉ tiêu thì
                không nhận thêm Trúng tuyển.
              </AlertDescription>
            </Alert>
          )}
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={decide.isPending}>
            Hủy
          </Button>
          <Button onClick={submit} disabled={invalid || decide.isPending}>
            Lưu kết luận
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

const OFFERS: OfferStatus[] = ['NONE', 'ACCEPTED', 'DECLINED']

/** Ghi nhận đã gửi email kết quả và phản hồi offer. Phải gửi email trước khi ghi nhận phản hồi. */
export function OfferDialog({
  row,
  onOpenChange,
}: {
  row: ResultRow
  onOpenChange: (open: boolean) => void
}) {
  const offer = useOffer()
  const d = row.decision!
  const [sent, setSent] = useState(d.emailStatus === 'SENT')
  const [status, setStatus] = useState<OfferStatus>(d.offerStatus)

  async function submit() {
    try {
      await offer.mutateAsync({
        candidateId: row.candidateId,
        body: { emailStatus: sent ? 'SENT' : 'NOT_SENT', offerStatus: status, version: d.version },
      })
      toast.success(
        status === 'DECLINED'
          ? `${row.fullName} từ chối offer: ngạch có thêm chỗ, có thể gọi dự bị kế tiếp`
          : 'Đã lưu email và offer',
      )
      onOpenChange(false)
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Email và offer: {row.fullName}</DialogTitle>
          <DialogDescription>
            {FINAL_LABELS[d.finalStatus]}
            {d.transferTrackCode && ` sang ${d.transferTrackCode}`}. Đã gửi email thì không mở lại kết luận
            được.
          </DialogDescription>
        </DialogHeader>
        <FieldGroup className="py-4">
          <Field orientation="horizontal">
            <Switch id="offer-email" checked={sent} onCheckedChange={setSent} />
            <FieldLabel htmlFor="offer-email">Đã gửi email kết quả cho ứng viên</FieldLabel>
          </Field>
          <div className="space-y-2">
            <span className="text-sm font-medium">Phản hồi của ứng viên</span>
            <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Phản hồi offer">
              {OFFERS.map((o) => (
                <button
                  key={o}
                  type="button"
                  role="radio"
                  aria-checked={status === o}
                  disabled={!sent && o !== 'NONE'}
                  onClick={() => setStatus(o)}
                  className={cn(
                    'rounded-md border px-3 py-1.5 text-sm hover:bg-muted disabled:opacity-50',
                    status === o && 'border-foreground bg-foreground text-background hover:bg-foreground',
                  )}
                >
                  {OFFER_LABELS[o]}
                </button>
              ))}
            </div>
          </div>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={offer.isPending}>
            Hủy
          </Button>
          <Button onClick={submit} disabled={offer.isPending || (!sent && status !== 'NONE')}>
            Lưu
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
