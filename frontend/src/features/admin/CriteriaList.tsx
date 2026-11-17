import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { QueryState } from '@/components/common/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { toastError } from '@/lib/errors'
import { useCriteria, useDeleteCriterion, type Criterion } from './api'
import { CriterionFormDialog } from './CriterionFormDialog'
import { RUBRIC_LEVELS } from './rubric'

/**
 * Danh sách tiêu chí của một nhóm: {@code trackId = null} là tiêu chí chung; có trackId thì chỉ hiện
 * tiêu chí chuyên môn của ngạch (API trả kèm tiêu chí chung, lọc bỏ ở đây).
 */
export function CriteriaList({
  trackId,
  trackName,
  editable,
}: {
  trackId: number | null
  trackName: string
  editable: boolean
}) {
  const criteria = useCriteria(trackId)
  const remove = useDeleteCriterion()
  const [dialog, setDialog] = useState<{ open: boolean; criterion: Criterion | null }>({
    open: false,
    criterion: null,
  })
  const [toDelete, setToDelete] = useState<Criterion | null>(null)

  const group = trackId === null ? 'GENERAL' : 'PROFESSIONAL'
  const list = (criteria.data ?? []).filter((c) => c.group === group)
  const nextOrder = list.reduce((max, c) => Math.max(max, c.sortOrder), 0) + 1

  async function confirmDelete() {
    if (!toDelete) return
    try {
      await remove.mutateAsync(toDelete.id)
      toast.success(`Đã xóa tiêu chí "${toDelete.name}"`)
      setToDelete(null)
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-semibold">
          {trackId === null ? 'Tiêu chí chung (mọi ngạch)' : 'Tiêu chí chuyên môn'}
          {criteria.data && (
            <span className="ml-2 text-sm font-normal text-muted-foreground">{list.length} tiêu chí</span>
          )}
        </h3>
        {editable && (
          <Button size="sm" onClick={() => setDialog({ open: true, criterion: null })}>
            <Plus aria-hidden /> Thêm tiêu chí
          </Button>
        )}
      </div>
      <QueryState query={criteria} skeletonRows={3}>
        {() =>
          list.length === 0 ? (
            <p className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
              Chưa có tiêu chí nào.
            </p>
          ) : (
            <div className="grid gap-3">
              {list.map((c) => (
                <Card key={c.id} size="sm">
                  <CardHeader>
                    <CardTitle>
                      <span className="mr-2 text-muted-foreground">{c.sortOrder}.</span>
                      {c.name}
                    </CardTitle>
                    {c.description && (
                      <CardDescription className="whitespace-pre-line">{c.description}</CardDescription>
                    )}
                    {editable && (
                      <CardAction className="flex gap-1">
                        <Button
                          size="icon-sm"
                          variant="ghost"
                          aria-label={`Sửa tiêu chí ${c.name}`}
                          onClick={() => setDialog({ open: true, criterion: c })}
                        >
                          <Pencil aria-hidden />
                        </Button>
                        <Button
                          size="icon-sm"
                          variant="ghost"
                          aria-label={`Xóa tiêu chí ${c.name}`}
                          onClick={() => setToDelete(c)}
                        >
                          <Trash2 aria-hidden />
                        </Button>
                      </CardAction>
                    )}
                  </CardHeader>
                  {RUBRIC_LEVELS.some((l) => c[l.key]) && (
                    <CardContent>
                      <dl className="grid gap-2 text-sm sm:grid-cols-3">
                        {RUBRIC_LEVELS.map((l) => (
                          <div key={l.key} className="rounded-md bg-muted/50 p-2">
                            <dt className="text-xs font-medium text-muted-foreground">
                              {l.label} ({l.range})
                            </dt>
                            <dd className="mt-1 whitespace-pre-line">{c[l.key] ?? '—'}</dd>
                          </div>
                        ))}
                      </dl>
                    </CardContent>
                  )}
                </Card>
              ))}
            </div>
          )
        }
      </QueryState>
      <CriterionFormDialog
        open={dialog.open}
        onOpenChange={(open) => setDialog((d) => ({ ...d, open }))}
        trackId={trackId}
        trackName={trackName}
        criterion={dialog.criterion}
        nextOrder={nextOrder}
      />
      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => !open && setToDelete(null)}
        title="Xóa tiêu chí?"
        description={
          <>
            Tiêu chí <strong>{toDelete?.name}</strong> sẽ không còn trên phiếu chấm. Nếu đã có giám khảo chấm
            tiêu chí này, hệ thống chỉ ẩn đi để giữ nguyên điểm đã chấm.
          </>
        }
        confirmLabel="Xóa"
        destructive
        pending={remove.isPending}
        onConfirm={confirmDelete}
      />
    </section>
  )
}
