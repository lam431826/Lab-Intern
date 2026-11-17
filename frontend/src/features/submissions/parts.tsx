import { Download, FileText, Upload } from 'lucide-react'
import { useMemo, useRef } from 'react'
import { toast } from 'sonner'
import { saveFile } from '@/api/client'
import { useApi } from '@/api/useApi'
import { useMe } from '@/auth/useMe'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useTracks } from '@/features/admin/api'
import { toastError } from '@/lib/errors'
import { formatBytes } from '@/lib/format'
import {
  downloadJd,
  JD_MAX_BYTES,
  STATUS_LABELS,
  useUploadJd,
  type SubmissionItem,
  type SubmissionStatus,
} from './api'

export function StatusBadge({ status }: { status: SubmissionStatus }) {
  return <Badge variant={status === 'SENT' ? 'default' : 'outline'}>{STATUS_LABELS[status]}</Badge>
}

/** Ngạch được chọn khi thêm vị trí: Người tổ chức mọi ngạch, Lead chỉ ngạch mình phụ trách. */
export function useEditableTracks() {
  const me = useMe()
  const tracks = useTracks()
  return useMemo(
    () => (tracks.data ?? []).filter((t) => me.role === 'ORGANIZER' || me.leadTrackCodes.includes(t.code)),
    [tracks.data, me],
  )
}

/** Kiểm tra trước ở client cho nhanh; backend vẫn kiểm tra nội dung file thật là PDF/DOCX. */
export function checkJdFile(file: File): string | null {
  const name = file.name.toLowerCase()
  if (!name.endsWith('.pdf') && !name.endsWith('.docx')) return 'Chỉ nhận file PDF hoặc DOCX'
  if (file.size === 0) return 'File đang trống'
  if (file.size > JD_MAX_BYTES) return 'File JD tối đa 5 MB'
  return null
}

/** File JD của một vị trí: tải về (mọi người xem được), tải lên/thay thế (khi được sửa). */
export function JdFile({ item, editable }: { item: SubmissionItem; editable: boolean }) {
  const api = useApi()
  const upload = useUploadJd()
  const input = useRef<HTMLInputElement>(null)

  async function onPick(file: File | undefined) {
    if (!file) return
    const problem = checkJdFile(file)
    if (problem) {
      toast.error(problem)
      return
    }
    try {
      await upload.mutateAsync({ item, file })
      toast.success(`Đã tải lên ${file.name}`)
    } catch (e) {
      toastError(e)
    } finally {
      if (input.current) input.current.value = ''
    }
  }

  async function onDownload() {
    try {
      saveFile(await downloadJd(api, item))
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2 text-sm">
      {item.hasFile ? (
        <Button size="sm" variant="outline" onClick={onDownload}>
          <Download aria-hidden />
          <span className="max-w-56 truncate">{item.fileName}</span>
          <span className="text-muted-foreground">{formatBytes(item.fileSize)}</span>
        </Button>
      ) : (
        <span className="flex items-center gap-1 text-muted-foreground">
          <FileText className="size-4" aria-hidden /> Chưa có file JD
        </span>
      )}
      {editable && (
        <>
          <input
            ref={input}
            type="file"
            accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            className="sr-only"
            aria-label={`Chọn file JD cho ${item.positionTitle}`}
            onChange={(e) => onPick(e.target.files?.[0])}
          />
          <Button
            size="sm"
            variant="ghost"
            disabled={upload.isPending}
            onClick={() => input.current?.click()}
          >
            <Upload aria-hidden /> {item.hasFile ? 'Thay file' : 'Tải file JD'}
          </Button>
        </>
      )}
    </div>
  )
}
