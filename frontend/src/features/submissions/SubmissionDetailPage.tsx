import { ArrowLeft, CopyPlus, Pencil, Plus, Send, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { useCan } from '@/auth/useCan'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { QueryState } from '@/components/common/QueryState'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { toastError } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import {
  useCreateReplacement,
  useDeleteItem,
  useDeleteSubmission,
  useSubmission,
  type Submission,
  type SubmissionItem,
} from './api'
import { ItemFormDialog } from './ItemFormDialog'
import { MarkSentDialog } from './MarkSentDialog'
import { JdFile, StatusBadge } from './parts'
import { SubmissionFormDialog } from './SubmissionFormDialog'

export function SubmissionDetailPage() {
  const { id } = useParams()
  const submissionId = Number(id)
  const submission = useSubmission(submissionId)
  if (!Number.isInteger(submissionId) || submissionId <= 0) return <NotFoundPage />
  return (
    <div className="mx-auto max-w-5xl">
      <Button
        variant="ghost"
        size="sm"
        className="mb-2 -ml-2"
        nativeButton={false}
        render={<Link to="/submissions" />}
      >
        <ArrowLeft aria-hidden /> Danh sách bản gửi
      </Button>
      <QueryState query={submission}>{(s) => <SubmissionDetail submission={s} />}</QueryState>
    </div>
  )
}

function SubmissionDetail({ submission: s }: { submission: Submission }) {
  const can = useCan()
  const navigate = useNavigate()
  const draft = s.status === 'DRAFT'
  const items = (s.items ?? []).toSorted((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  const canEditItem = (it: SubmissionItem) => draft && (can.isOrganizer || can.leads(it.trackCode))
  const canAddItem =
    draft && (can.isOrganizer || (can.me.role === 'LEAD' && can.me.leadTrackCodes.length > 0))

  const [editingInfo, setEditingInfo] = useState(false)
  const [sending, setSending] = useState(false)
  const [itemDialog, setItemDialog] = useState<{ open: boolean; item: SubmissionItem | null }>({
    open: false,
    item: null,
  })
  const [deletingItem, setDeletingItem] = useState<SubmissionItem | null>(null)
  const [deletingDraft, setDeletingDraft] = useState(false)
  const [replacing, setReplacing] = useState(false)

  const deleteItem = useDeleteItem()
  const deleteDraft = useDeleteSubmission()
  const replace = useCreateReplacement()

  async function onDeleteItem() {
    if (!deletingItem) return
    try {
      await deleteItem.mutateAsync(deletingItem.id)
      toast.success(`Đã xóa vị trí "${deletingItem.positionTitle}"`)
      setDeletingItem(null)
    } catch (e) {
      toastError(e)
    }
  }

  async function onDeleteDraft() {
    try {
      await deleteDraft.mutateAsync(s.id)
      toast.success('Đã xóa bản nháp')
      navigate('/submissions')
    } catch (e) {
      toastError(e)
    }
  }

  async function onReplace() {
    try {
      const { submission: created, failed } = await replace.mutateAsync(s)
      setReplacing(false)
      if (failed.length > 0) {
        toast.warning(`Đã tạo bản thay thế nhưng chưa sao chép được: ${failed.join(', ')}. Hãy bổ sung tay.`)
      } else {
        toast.success('Đã tạo bản thay thế kèm các vị trí của bản cũ. Sửa phần thay đổi rồi đánh dấu đã gửi.')
      }
      navigate(`/submissions/${created.id}`)
    } catch (e) {
      toastError(e)
    }
  }

  const info = [
    { label: 'Mã kỳ', value: s.termCode },
    { label: 'Đơn vị / người nhận', value: s.recipient },
    { label: 'Số công văn / link', value: s.externalReference },
    { label: 'Gửi lúc', value: s.sentAt ? formatDateTime(s.sentAt) : null },
    { label: 'Người chốt', value: s.submittedByName },
    { label: 'Cập nhật', value: formatDateTime(s.updatedAt) },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="flex flex-wrap items-center gap-2 text-lg font-semibold sm:text-xl">
            {s.title} <StatusBadge status={s.status} />
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {s.positionCount} vị trí · tổng chỉ tiêu {s.totalQuota}
          </p>
        </div>
        {can.isOrganizer && (
          <div className="flex flex-wrap gap-2">
            {draft ? (
              <>
                <Button variant="outline" onClick={() => setEditingInfo(true)}>
                  <Pencil aria-hidden /> Sửa thông tin
                </Button>
                <Button variant="outline" onClick={() => setDeletingDraft(true)}>
                  <Trash2 aria-hidden /> Xóa nháp
                </Button>
                <Button onClick={() => setSending(true)}>
                  <Send aria-hidden /> Đánh dấu đã gửi
                </Button>
              </>
            ) : (
              <Button variant="outline" onClick={() => setReplacing(true)}>
                <CopyPlus aria-hidden /> Tạo bản bổ sung/thay thế
              </Button>
            )}
          </div>
        )}
      </div>

      {s.replacesSubmissionId && (
        <Alert>
          <AlertDescription>
            Bản này bổ sung/thay thế cho{' '}
            <Link className="font-medium underline" to={`/submissions/${s.replacesSubmissionId}`}>
              bản #{s.replacesSubmissionId}
            </Link>
            .
          </AlertDescription>
        </Alert>
      )}
      {!draft && (
        <Alert>
          <AlertDescription>
            Bản đã gửi được lưu nguyên trạng, không sửa hay xóa được. Muốn điều chỉnh, tạo bản bổ sung/thay
            thế.
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardContent>
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
            {info.map((i) => (
              <div key={i.label} className="min-w-0">
                <dt className="text-muted-foreground">{i.label}</dt>
                <dd className="truncate font-medium">{i.value || '—'}</dd>
              </div>
            ))}
          </dl>
          {s.note && <p className="mt-4 text-sm whitespace-pre-line">{s.note}</p>}
        </CardContent>
      </Card>

      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-semibold">Vị trí tuyển dụng</h3>
          {canAddItem && (
            <Button size="sm" onClick={() => setItemDialog({ open: true, item: null })}>
              <Plus aria-hidden /> Thêm vị trí
            </Button>
          )}
        </div>
        {items.length === 0 ? (
          <p className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
            Chưa có vị trí nào.{draft && ' Cần ít nhất một vị trí có JD trước khi đánh dấu đã gửi.'}
          </p>
        ) : (
          items.map((it) => (
            <Card key={it.id} size="sm">
              <CardHeader>
                <CardTitle className="flex flex-wrap items-center gap-2">
                  {it.positionTitle}
                  <Badge variant="outline">{it.trackName}</Badge>
                  <Badge variant="secondary">Chỉ tiêu {it.quota}</Badge>
                </CardTitle>
                {it.note && <CardDescription className="whitespace-pre-line">{it.note}</CardDescription>}
                {canEditItem(it) && (
                  <CardAction className="flex gap-1">
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      aria-label={`Sửa vị trí ${it.positionTitle}`}
                      onClick={() => setItemDialog({ open: true, item: it })}
                    >
                      <Pencil aria-hidden />
                    </Button>
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      aria-label={`Xóa vị trí ${it.positionTitle}`}
                      onClick={() => setDeletingItem(it)}
                    >
                      <Trash2 aria-hidden />
                    </Button>
                  </CardAction>
                )}
              </CardHeader>
              <CardContent className="space-y-3">
                {it.jdContent ? (
                  <details className="rounded-md bg-muted/50 p-3 text-sm">
                    <summary className="cursor-pointer font-medium">Nội dung JD</summary>
                    <p className="mt-2 whitespace-pre-line">{it.jdContent}</p>
                  </details>
                ) : null}
                <JdFile item={it} editable={canEditItem(it)} />
              </CardContent>
            </Card>
          ))
        )}
      </section>

      <SubmissionFormDialog open={editingInfo} onOpenChange={setEditingInfo} submission={s} />
      <MarkSentDialog open={sending} onOpenChange={setSending} submission={s} />
      <ItemFormDialog
        open={itemDialog.open}
        onOpenChange={(open) => setItemDialog((d) => ({ ...d, open }))}
        submissionId={s.id}
        item={itemDialog.item}
        nextOrder={items.reduce((m, i) => Math.max(m, i.sortOrder), 0) + 1}
      />
      <ConfirmDialog
        open={deletingItem !== null}
        onOpenChange={(open) => !open && setDeletingItem(null)}
        title="Xóa vị trí?"
        description={
          <>
            Vị trí <strong>{deletingItem?.positionTitle}</strong> và file JD kèm theo sẽ bị xóa khỏi bản nháp.
          </>
        }
        confirmLabel="Xóa"
        destructive
        pending={deleteItem.isPending}
        onConfirm={onDeleteItem}
      />
      <ConfirmDialog
        open={deletingDraft}
        onOpenChange={setDeletingDraft}
        title="Xóa bản nháp?"
        description="Bản nháp cùng mọi vị trí và file JD sẽ bị xóa. Không hoàn tác được."
        confirmLabel="Xóa bản nháp"
        destructive
        pending={deleteDraft.isPending}
        onConfirm={onDeleteDraft}
      />
      <ConfirmDialog
        open={replacing}
        onOpenChange={setReplacing}
        title="Tạo bản bổ sung/thay thế?"
        description={`Tạo bản nháp mới sao chép ${s.positionCount} vị trí (kể cả file JD) của bản này. Bản cũ giữ nguyên trong lịch sử.`}
        confirmLabel="Tạo bản nháp"
        pending={replace.isPending}
        onConfirm={onReplace}
      />
    </div>
  )
}
