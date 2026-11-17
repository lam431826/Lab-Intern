import { useMemo, type ReactNode } from 'react'
import { createApiClient, type TokenProvider } from './client'
import { ApiContext } from './context'

interface Props {
  getToken: TokenProvider
  onUnauthorized?: () => void
  children: ReactNode
}

/** Cung cấp client API cho cây component. F1 nối {@code getToken} với Clerk. */
export function ApiProvider({ getToken, onUnauthorized, children }: Props) {
  const client = useMemo(() => createApiClient({ getToken, onUnauthorized }), [getToken, onUnauthorized])
  return <ApiContext.Provider value={client}>{children}</ApiContext.Provider>
}
