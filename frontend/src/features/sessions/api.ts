import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Schemas } from '@/api/client'
import { useApi } from '@/api/useApi'

export type Session = Schemas['SessionDto']
export type SessionBody = Schemas['SessionRequest']
export type PanelMember = Schemas['PanelMemberDto']
export type PanelRole = PanelMember['panelRole']
export type Slot = Schemas['SlotDto']
export type Attendance = Slot['attendance']
export type AutoScheduleResult = Schemas['AutoScheduleResult']
export type Progress = Schemas['SessionProgressDto']

export interface SessionFilter {
  trackId?: number
  date?: string
}

export const PANEL_ROLE_LABELS: Record<PanelRole, string> = {
  LECTURER: 'Giảng viên',
  ORGANIZER: 'Người tổ chức',
  LEAD: 'Lead ngạch',
  ROTATING: 'Luân chuyển',
}

export const ATTENDANCE_LABELS: Record<Attendance, string> = {
  PENDING: 'Chưa điểm danh',
  ATTENDED: 'Có mặt',
  ABSENT: 'Vắng mặt',
}

export const sessionKeys = {
  all: ['sessions'] as const,
  list: (f: SessionFilter) => ['sessions', 'list', f] as const,
  detail: (id: number) => ['sessions', 'detail', id] as const,
  progress: (id: number) => ['sessions', 'progress', id] as const,
}

export function useSessions(filter: SessionFilter = {}) {
  const api = useApi()
  return useQuery({
    queryKey: sessionKeys.list(filter),
    queryFn: ({ signal }) => api.get<Session[]>('/api/sessions', { signal, query: { ...filter } }),
  })
}

export function useSession(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: sessionKeys.detail(id),
    queryFn: ({ signal }) => api.get<Session>(`/api/sessions/${id}`, { signal }),
  })
}

export function useSessionProgress(id: number, enabled = true) {
  const api = useApi()
  return useQuery({
    queryKey: sessionKeys.progress(id),
    queryFn: ({ signal }) => api.get<Progress>(`/api/sessions/${id}/progress`, { signal }),
    enabled,
  })
}

/**
 * Lịch thay đổi thì hồ sơ (đã xếp lịch), việc cần chấm và dashboard cũng đổi theo,
 * nên làm mới cả các nhóm đó.
 */
function useInvalidate() {
  const qc = useQueryClient()
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: sessionKeys.all }),
      qc.invalidateQueries({ queryKey: ['candidates'] }),
      qc.invalidateQueries({ queryKey: ['scoring'] }),
      qc.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
}

export function useSaveSession() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, body }: { id: number | null; body: SessionBody }) =>
      id === null ? api.post<Session>('/api/sessions', body) : api.put<Session>(`/api/sessions/${id}`, body),
    onSuccess: invalidate,
  })
}

export function useDeleteSession() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: number) => api.del(`/api/sessions/${id}`), onSuccess: invalidate })
}

export function useUpdatePanel(id: number) {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (rotatingMemberIds: number[]) =>
      api.put<Session>(`/api/sessions/${id}/panel`, { rotatingMemberIds }),
    onSuccess: invalidate,
  })
}

export function useUpdateLog(id: number) {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (logSummary: string | null) => api.put<Session>(`/api/sessions/${id}/log`, { logSummary }),
    onSuccess: (data) => qc.setQueryData(sessionKeys.detail(id), data),
  })
}

export function useScheduleSlot(sessionId: number) {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (body: { candidateId: number; startTime: string | null }) =>
      api.post<Slot>(`/api/sessions/${sessionId}/slots`, body),
    onSuccess: invalidate,
  })
}

export function useAutoSchedule(sessionId: number) {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: () =>
      api.post<AutoScheduleResult>(`/api/sessions/${sessionId}/auto-schedule`, { candidateIds: [] }),
    onSuccess: invalidate,
  })
}

export function useReschedule() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({
      slotId,
      body,
    }: {
      slotId: number
      body: { sessionId: number; startTime: string | null; reason: string }
    }) => api.post<Slot>(`/api/slots/${slotId}/reschedule`, body),
    onSuccess: invalidate,
  })
}

export function useCancelSlot() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (slotId: number) => api.del(`/api/slots/${slotId}`),
    onSuccess: invalidate,
  })
}

export function useAttendance() {
  const api = useApi()
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({
      slotId,
      attendance,
      note,
    }: {
      slotId: number
      attendance: Attendance
      note: string | null
    }) => api.put<Slot>(`/api/slots/${slotId}/attendance`, { attendance, note }),
    onSuccess: invalidate,
  })
}

/** "10:00:00" → "10:00" cho ô input type="time". */
export function hhmm(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : ''
}
