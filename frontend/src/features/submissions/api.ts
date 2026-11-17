import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ApiClient, Schemas } from '@/api/client'
import { useApi } from '@/api/useApi'

export type Submission = Schemas['SubmissionDto']
export type SubmissionItem = Schemas['SubmissionItemDto']
export type SubmissionStatus = Submission['status']
export type CreateSubmissionBody = Schemas['CreateSubmissionRequest']
export type UpdateSubmissionBody = Schemas['UpdateSubmissionRequest']
export type MarkSentBody = Schemas['MarkSentRequest']
export type ItemBody = Schemas['SubmissionItemRequest']

export interface SubmissionFilter {
  termCode?: string
  trackId?: number
  status?: SubmissionStatus
  q?: string
}

export const STATUS_LABELS: Record<SubmissionStatus, string> = { DRAFT: 'Bản nháp', SENT: 'Đã gửi' }

/** Giới hạn của backend (RecruitmentSubmissionService): PDF hoặc DOCX hợp lệ, tối đa 5 MB. */
export const JD_MAX_BYTES = 5 * 1024 * 1024

export const submissionKeys = {
  all: ['submissions'] as const,
  list: (f: SubmissionFilter) => ['submissions', 'list', f] as const,
  detail: (id: number) => ['submissions', 'detail', id] as const,
}

const base = '/api/recruitment-submissions'

export function useSubmissions(filter: SubmissionFilter) {
  const api = useApi()
  return useQuery({
    queryKey: submissionKeys.list(filter),
    queryFn: ({ signal }) => api.get<Submission[]>(base, { signal, query: { ...filter } }),
  })
}

export function useSubmission(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: submissionKeys.detail(id),
    queryFn: ({ signal }) => api.get<Submission>(`${base}/${id}`, { signal }),
  })
}

/** Mọi thao tác ghi đều làm mới cả danh sách lẫn chi tiết (số vị trí, tổng chỉ tiêu thay đổi). */
function useInvalidate() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: submissionKeys.all })
}

export function useCreateSubmission() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (body: CreateSubmissionBody) => api.post<Submission>(base, body),
    onSuccess: invalidate,
  })
}

export function useUpdateSubmission(id: number) {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (body: UpdateSubmissionBody) => api.put<Submission>(`${base}/${id}`, body),
    onSuccess: invalidate,
  })
}

export function useDeleteSubmission() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: number) => api.del(`${base}/${id}`), onSuccess: invalidate })
}

export function useMarkSent(id: number) {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (body: MarkSentBody) => api.post<Submission>(`${base}/${id}/mark-sent`, body),
    onSuccess: invalidate,
  })
}

export function useSaveItem(submissionId: number) {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ itemId, body }: { itemId: number | null; body: ItemBody }) =>
      itemId === null
        ? api.post<SubmissionItem>(`${base}/${submissionId}/items`, body)
        : api.put<SubmissionItem>(`/api/recruitment-submission-items/${itemId}`, body),
    onSuccess: invalidate,
  })
}

export function useDeleteItem() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (itemId: number) => api.del(`/api/recruitment-submission-items/${itemId}`),
    onSuccess: invalidate,
  })
}

export function useUploadJd() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ item, file }: { item: SubmissionItem; file: File }) =>
      api.upload<SubmissionItem>(`/api/recruitment-submission-items/${item.id}/file`, file, {
        query: { version: item.version },
      }),
    onSuccess: invalidate,
  })
}

export function downloadJd(api: ApiClient, item: SubmissionItem) {
  return api.download(`/api/recruitment-submission-items/${item.id}/file`, item.fileName ?? `jd-${item.id}`)
}

export interface ReplaceResult {
  submission: Submission
  /** Vị trí không sao chép được (lỗi giữa chừng); bản nháp vẫn được tạo, người dùng bổ sung tay. */
  failed: string[]
}

/**
 * Tạo bản bổ sung/thay thế cho một bản đã gửi. Backend chỉ tạo bản nháp rỗng, nên sao chép tiếp
 * các vị trí (kể cả file JD) của bản cũ để người dùng chỉ sửa phần thay đổi.
 */
export function useCreateReplacement() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: async (old: Submission): Promise<ReplaceResult> => {
      const created = await api.post<Submission>(base, {
        termCode: old.termCode,
        title: `${old.title} (bản thay thế)`,
        recipient: old.recipient,
        externalReference: null,
        note: null,
        replacesSubmissionId: old.id,
      } satisfies CreateSubmissionBody)
      const failed: string[] = []
      for (const it of old.items ?? []) {
        try {
          const copy = await api.post<SubmissionItem>(`${base}/${created.id}/items`, {
            trackId: it.trackId,
            positionTitle: it.positionTitle,
            quota: it.quota,
            jdContent: it.jdContent,
            note: it.note,
            sortOrder: it.sortOrder,
            version: null,
          } satisfies ItemBody)
          if (it.hasFile) {
            const { blob, filename } = await downloadJd(api, it)
            await api.upload(
              `/api/recruitment-submission-items/${copy.id}/file`,
              new File([blob], filename),
              {
                query: { version: copy.version },
              },
            )
          }
        } catch {
          failed.push(it.positionTitle)
        }
      }
      return { submission: created, failed }
    },
    onSettled: invalidate,
  })
}
