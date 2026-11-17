import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Schemas } from '@/api/client'
import { useApi } from '@/api/useApi'

export type TrackResult = Schemas['TrackResultDto']
export type ResultRow = Schemas['ResultRow']
export type Decision = Schemas['DecisionDto']
export type FinalStatus = Decision['finalStatus']
export type EmailStatus = Decision['emailStatus']
export type OfferStatus = Decision['offerStatus']
export type DecisionBody = Schemas['DecisionRequest']

export const FINAL_LABELS: Record<FinalStatus, string> = {
  PENDING: 'Chưa kết luận',
  HIRED: 'Trúng tuyển',
  WAITLIST: 'Dự bị',
  TRANSFER: 'Chuyển ngạch',
  REJECTED: 'Loại',
}

export const INTERVIEW_LABELS: Record<string, string> = {
  NOT_SCHEDULED: 'Chưa xếp lịch',
  SCHEDULED: 'Đã xếp lịch',
  ATTENDED: 'Có mặt',
  ABSENT: 'Vắng mặt',
}

export const OFFER_LABELS: Record<OfferStatus, string> = {
  NONE: 'Chưa phản hồi',
  ACCEPTED: 'Nhận offer',
  DECLINED: 'Từ chối offer',
}

export const resultKeys = {
  all: ['results'] as const,
  list: ['results', 'list'] as const,
}

/** Bảng kết quả các ngạch được xem (Lead: ngạch mình). Điểm luôn tính lại từ phiếu đã nộp. */
export function useResults() {
  const api = useApi()
  return useQuery({
    queryKey: resultKeys.list,
    queryFn: ({ signal }) => api.get<TrackResult[]>('/api/results', { signal }),
  })
}

/** Kết luận đổi thì chỉ tiêu, dashboard và khóa phiếu chấm cũng đổi theo. */
function useInvalidate() {
  const qc = useQueryClient()
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: resultKeys.all }),
      qc.invalidateQueries({ queryKey: ['dashboard'] }),
      qc.invalidateQueries({ queryKey: ['scoring'] }),
      qc.invalidateQueries({ queryKey: ['candidates', 'history'] }),
    ])
}

const decisionPath = (candidateId: number) => `/api/candidates/${candidateId}/decision`

export function useDecide() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ candidateId, body }: { candidateId: number; body: DecisionBody }) =>
      api.put<Decision>(decisionPath(candidateId), body),
    onSuccess: invalidate,
  })
}

export type DecisionAction = 'approve' | 'reopen' | 'promote'

/** Phê duyệt, mở lại (Giảng viên) và gọi dự bị: cùng dạng {version}. */
export function useDecisionAction() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({
      candidateId,
      action,
      version,
    }: {
      candidateId: number
      action: DecisionAction
      version: number
    }) => api.post<Decision>(`${decisionPath(candidateId)}/${action}`, { version }),
    onSuccess: invalidate,
  })
}

export function useApproveTrack() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (trackId: number) => api.post<TrackResult>(`/api/results/${trackId}/approve`),
    onSuccess: invalidate,
  })
}

export function useOffer() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({
      candidateId,
      body,
    }: {
      candidateId: number
      body: { emailStatus: EmailStatus; offerStatus: OfferStatus; version: number }
    }) => api.put<Decision>(`${decisionPath(candidateId)}/offer`, body),
    onSuccess: invalidate,
  })
}

/** Kết luận được chốt khi hội đồng đã nộp đủ phiếu (khớp ResultRow.scoresComplete của backend). */
export function scoresComplete(r: ResultRow): boolean {
  return r.interview === 'ATTENDED' && r.panelSize > 0 && r.submittedSheets >= r.panelSize
}

/** Kết luận khác gợi ý của hệ thống thì backend bắt buộc ghi lý do. */
export function needsNote(status: FinalStatus, suggestion: FinalStatus | null): boolean {
  return status !== 'PENDING' && status !== suggestion
}
