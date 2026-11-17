import { Route, Routes } from 'react-router'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { CandidateScoresPage } from './CandidateScoresPage'
import { ScoreSheetPage } from './ScoreSheetPage'
import { ScoringQueuePage } from './ScoringQueuePage'

/** /scoring (cần chấm), /scoring/:candidateId (phiếu chấm), /scoring/:candidateId/scores (điểm hội đồng). */
export function ScoringRoutes() {
  return (
    <Routes>
      <Route index element={<ScoringQueuePage />} />
      <Route path=":candidateId" element={<ScoreSheetPage />} />
      <Route path=":candidateId/scores" element={<CandidateScoresPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
