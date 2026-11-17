import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Schemas } from '@/api/client'
import { useApi } from '@/api/useApi'
import { ME_KEY } from '@/auth/useMe'

export type User = Schemas['UserDto']
export type Track = Schemas['TrackDto']
export type Criterion = Schemas['CriterionDto']
export type Settings = Schemas['SettingsDto']
export type Role = User['role']

export type CreateUserBody = Schemas['CreateUserRequest']
export type UpdateUserBody = Schemas['UpdateUserRequest']
export type UpdateTrackBody = Schemas['UpdateTrackRequest']
export type CriterionBody = Schemas['CriterionRequest']

export const adminKeys = {
  users: ['users'] as const,
  tracks: ['tracks'] as const,
  criteria: (trackId: number | null) => ['criteria', trackId ?? 'general'] as const,
  settings: ['settings'] as const,
}

// ---------- Tài khoản ----------

export function useUsers() {
  const api = useApi()
  return useQuery({
    queryKey: adminKeys.users,
    queryFn: ({ signal }) => api.get<User[]>('/api/users', { signal }),
  })
}

export function useCreateUser() {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateUserBody) => api.post<User>('/api/users', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminKeys.users }),
  })
}

export function useUpdateUser() {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateUserBody }) =>
      api.put<User>(`/api/users/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.users })
      // Tên Lead hiển thị trong danh sách ngạch; vai trò của chính mình có thể đổi menu
      qc.invalidateQueries({ queryKey: adminKeys.tracks })
      qc.invalidateQueries({ queryKey: ME_KEY })
    },
  })
}

export function useResendInvite() {
  const api = useApi()
  return useMutation({ mutationFn: (id: number) => api.post<void>(`/api/users/${id}/invite`) })
}

// ---------- Ngạch và tiêu chí ----------

export function useTracks() {
  const api = useApi()
  return useQuery({
    queryKey: adminKeys.tracks,
    queryFn: ({ signal }) => api.get<Track[]>('/api/tracks', { signal }),
    staleTime: 5 * 60_000,
  })
}

export function useUpdateTrack() {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateTrackBody }) =>
      api.put<Track>(`/api/tracks/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.tracks })
      // Đổi Lead làm thay đổi leadTrackCodes của người liên quan
      qc.invalidateQueries({ queryKey: ME_KEY })
    },
  })
}

/** {@code trackId = null}: chỉ tiêu chí chung; có trackId: tiêu chí chung và chuyên môn của ngạch. */
export function useCriteria(trackId: number | null) {
  const api = useApi()
  return useQuery({
    queryKey: adminKeys.criteria(trackId),
    queryFn: ({ signal }) => api.get<Criterion[]>('/api/criteria', { signal, query: { trackId } }),
  })
}

export function useSaveCriterion() {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number | null; body: CriterionBody }) =>
      id === null
        ? api.post<Criterion>('/api/criteria', body)
        : api.put<Criterion>(`/api/criteria/${id}`, body),
    // Tiêu chí chung xuất hiện trong danh sách của mọi ngạch
    onSuccess: () => qc.invalidateQueries({ queryKey: ['criteria'] }),
  })
}

export function useDeleteCriterion() {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.del(`/api/criteria/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['criteria'] }),
  })
}

// ---------- Tham số chấm ----------

export function useSettings() {
  const api = useApi()
  return useQuery({
    queryKey: adminKeys.settings,
    queryFn: ({ signal }) => api.get<Settings>('/api/settings', { signal }),
  })
}

export function useUpdateSettings() {
  const api = useApi()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Settings) => api.put<Settings>('/api/settings', body),
    onSuccess: (data) => qc.setQueryData(adminKeys.settings, data),
  })
}
