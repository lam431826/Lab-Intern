import { Route, Routes } from 'react-router'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { SubmissionDetailPage } from './SubmissionDetailPage'
import { SubmissionsPage } from './SubmissionsPage'

/** /submissions (danh sách) và /submissions/:id (chi tiết). */
export function SubmissionsRoutes() {
  return (
    <Routes>
      <Route index element={<SubmissionsPage />} />
      <Route path=":id" element={<SubmissionDetailPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
