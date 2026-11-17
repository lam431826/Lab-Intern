import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Schemas } from '@/api/client'
import { useApi } from '@/api/useApi'

export type QueueItem = Schemas['ScoringQueueItem']
export type Sheet = Schemas['ScoreSheetDto']
export type SheetItem = Sheet['items'][number]
export type SheetBody = Schemas['ScoreSheetRequest']
export type Recommendation = NonNullable<Sheet['recommendation']>
export type CandidateScores = Schemas['CandidateScoresDto']

export const SHEET_STATUS_LABELS: Record<string, string> = {
  NONE: 'Chưa chấm',
  DRAFT: 'Đang nháp',
  SUBMITTED: 'Đã nộp',
}

export const RECOMMENDATION_LABELS: Record<Recommendation, string> = { PASS: 'Đạt', FAIL: 'Không đạt' }

export const scoringKeys = {
  all: ['scoring'] as const,
  queue: ['scoring', 'queue'] as const,
  sheet: (candidateId: number) => ['scoring', 'sheet', candidateId] as const,
  scores: (candidateId: number) => ['scoring', 'scores', candidateId] as const,
}

/** Ứng viên đã có mặt trong các ca mình ngồi hội đồng. */
export function useMyQueue() {
  const api = useApi()
  return useQuery({
    queryKey: scoringKeys.queue,
    queryFn: ({ signal }) => api.get<QueueItem[]>('/api/me/scoring', { signal }),
  })
}

export function useSheet(candidateId: number) {
  const api = useApi()
  return useQuery({
    queryKey: scoringKeys.sheet(candidateId),
    queryFn: ({ signal }) => api.get<Sheet>(`/api/candidates/${candidateId}/scoresheet`, { signal }),
    // Đang chấm thì không tự tải lại khi quay lại tab, tránh làm mất điểm đang nhập
    refetchOnWindowFocus: false,
  })
}

export function useSaveSheet(candidateId: number) {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: SheetBody) => api.put<Sheet>(`/api/candidates/${candidateId}/scoresheet`, body),
    onSuccess: (sheet) => {
      // Version mới phải có ngay cho lần lưu kế tiếp (tự lưu nháp liên tục)
      qc.setQueryData(scoringKeys.sheet(candidateId), sheet)
      qc.invalidateQueries({ queryKey: scoringKeys.queue })
      qc.invalidateQueries({ queryKey: scoringKeys.scores(candidateId) })
      qc.invalidateQueries({ queryKey: ['sessions', 'progress'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

/** Điểm của cả hội đồng; thành viên hội đồng chưa nộp phiếu nhận 403 SUBMIT_FIRST. */
export function useCandidateScores(candidateId: number) {
  const api = useApi()
  return useQuery({
    queryKey: scoringKeys.scores(candidateId),
    queryFn: ({ signal }) => api.get<CandidateScores>(`/api/candidates/${candidateId}/scores`, { signal }),
  })
}

/** Tiêu chí còn thiếu điểm (để nộp phiếu phải đủ mọi tiêu chí và có đề xuất). */
export function missingForSubmit(
  values: Record<number, number | null>,
  items: SheetItem[],
  rec: Recommendation | null,
) {
  const missing = items.filter((i) => values[i.criterionId] == null).map((i) => i.name)
  if (!rec) missing.push('Đề xuất Đạt/Không đạt')
  return missing
}
