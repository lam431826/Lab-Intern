import { Route, Routes } from 'react-router'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { SessionDetailPage } from './SessionDetailPage'
import { SessionsPage } from './SessionsPage'

/** /sessions (danh sách ca theo ngày) và /sessions/:id (chi tiết ca). */
export function SessionsRoutes() {
  return (
    <Routes>
      <Route index element={<SessionsPage />} />
      <Route path=":id" element={<SessionDetailPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
