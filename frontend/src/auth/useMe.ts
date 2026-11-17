import { useQuery } from '@tanstack/react-query'
import type { Schemas } from '@/api/client'
import { useApi } from '@/api/useApi'

export type Me = Schemas['MeDto']

export const ME_KEY = ['me'] as const

export function useMeQuery() {
  const api = useApi()
  return useQuery({
    queryKey: ME_KEY,
    queryFn: ({ signal }) => api.get<Me>('/api/me', { signal }),
    staleTime: 5 * 60_000,
    retry: false,
  })
}

/** Dùng bên trong AccessGate: thông tin người dùng chắc chắn đã có. */
export function useMe(): Me {
  const { data } = useMeQuery()
  if (!data) throw new Error('useMe() chỉ dùng bên trong <AccessGate>')
  return data
}
