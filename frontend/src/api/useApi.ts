import { useContext } from 'react'
import type { ApiClient } from './client'
import { ApiContext } from './context'

export function useApi(): ApiClient {
  const client = useContext(ApiContext)
  if (!client) throw new Error('useApi phải dùng bên trong <ApiProvider>')
  return client
}
