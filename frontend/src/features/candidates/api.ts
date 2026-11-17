import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Schemas } from '@/api/client'
import { useApi } from '@/api/useApi'

export type Candidate = Schemas['CandidateDto']
export type CandidateBody = Schemas['CandidateRequest']
export type ScreeningBody = Schemas['ScreeningRequest']
export type ScreeningStatus = Candidate['screeningStatus']
export type Flag = Candidate['flags'][number]
export type TrackSummary = Schemas['TrackScreeningSummary']
export type ImportReport = Schemas['ImportReport']
export type HistoryEntry = Schemas['HistoryEntryDto']
export type Slot = Schemas['SlotDto']

export interface CandidateFilter {
  trackId?: number
  status?: ScreeningStatus
  q?: string
  overdue?: boolean
}

export const STATUS_LABELS: Record<ScreeningStatus, string> = {
  PENDING: 'Chờ duyệt',
  PASSED: 'Đạt',
  REJECTED: 'Loại',
}

export const FLAG_LABELS: Record<Flag, string> = {
  MISSING_CV: 'Thiếu CV',
  INVALID_CV_LINK: 'Link CV không mở được',
  MISSING_PORTFOLIO: 'Thiếu Portfolio',
  INVALID_PORTFOLIO_LINK: 'Link Portfolio không mở được',
}

/** 5 mục chấm CV, theo tab "Đánh giá CV" của file Excel cũ. */
export const CV_ITEMS = [
  { key: 'cvPersonalInfo', label: 'Thông tin cá nhân' },
  { key: 'cvExperience', label: 'Kinh nghiệm' },
  { key: 'cvSkills', label: 'Kỹ năng' },
  { key: 'cvProjects', label: 'Dự án' },
  { key: 'cvCertificates', label: 'Chứng chỉ, giải thưởng' },
] as const
export type CvKey = (typeof CV_ITEMS)[number]['key']

export const candidateKeys = {
  all: ['candidates'] as const,
  list: (f: CandidateFilter) => ['candidates', 'list', f] as const,
  summary: ['candidates', 'summary'] as const,
  detail: (id: number) => ['candidates', 'detail', id] as const,
  history: (id: number) => ['candidates', 'history', id] as const,
  slots: (id: number) => ['candidates', 'slots', id] as const,
}

export function useCandidates(filter: CandidateFilter) {
  const api = useApi()
  return useQuery({
    queryKey: candidateKeys.list(filter),
    queryFn: ({ signal }) =>
      api.get<Candidate[]>('/api/candidates', {
        signal,
        query: { ...filter, overdue: filter.overdue || undefined },
      }),
  })
}

export function useCandidateSummary() {
  const api = useApi()
  return useQuery({
    queryKey: candidateKeys.summary,
    queryFn: ({ signal }) => api.get<TrackSummary[]>('/api/candidates/summary', { signal }),
  })
}

export function useCandidate(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: candidateKeys.detail(id),
    queryFn: ({ signal }) => api.get<Candidate>(`/api/candidates/${id}`, { signal }),
  })
}

export function useCandidateHistory(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: candidateKeys.history(id),
    queryFn: ({ signal }) => api.get<HistoryEntry[]>(`/api/candidates/${id}/history`, { signal }),
  })
}

export function useCandidateSlots(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: candidateKeys.slots(id),
    queryFn: ({ signal }) => api.get<Slot[]>(`/api/candidates/${id}/slots`, { signal }),
  })
}

/** Mọi thao tác ghi làm mới danh sách, bộ đếm, chi tiết và lịch sử. */
function useInvalidate() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: candidateKeys.all })
}

export function useSaveCandidate() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, body }: { id: number | null; body: CandidateBody }) =>
      id === null
        ? api.post<Candidate>('/api/candidates', body)
        : api.put<Candidate>(`/api/candidates/${id}`, body),
    onSuccess: invalidate,
  })
}

export function useDeleteCandidate() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: number) => api.del(`/api/candidates/${id}`), onSuccess: invalidate })
}

export function useScreen(id: number) {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ScreeningBody) => api.put<Candidate>(`/api/candidates/${id}/screening`, body),
    onSuccess: (data) => {
      // Cập nhật ngay version mới cho lần lưu kế tiếp, rồi làm mới phần còn lại
      qc.setQueryData(candidateKeys.detail(id), data)
      qc.invalidateQueries({ queryKey: candidateKeys.all })
    },
  })
}

export function useImportCandidates() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ file, dryRun }: { file: File; dryRun: boolean }) =>
      api.upload<ImportReport>('/api/candidates/import', file, { query: { dryRun } }),
    onSuccess: (report) => {
      if (report.applied) invalidate()
    },
  })
}
