import { Route, Routes } from 'react-router'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { RequireRole } from '@/auth/RequireRole'
import { CandidateDetailPage } from './CandidateDetailPage'
import { CandidatesPage } from './CandidatesPage'
import { ImportPage } from './ImportPage'

/** /candidates, /candidates/import (Người tổ chức), /candidates/:id. */
export function CandidatesRoutes() {
  return (
    <Routes>
      <Route index element={<CandidatesPage />} />
      <Route
        path="import"
        element={
          <RequireRole roles={['ORGANIZER']}>
            <ImportPage />
          </RequireRole>
        }
      />
      <Route path=":id" element={<CandidateDetailPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
