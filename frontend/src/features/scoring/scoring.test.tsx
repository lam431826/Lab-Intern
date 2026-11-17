import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME_ORGANIZER, renderWithApp, type Handler } from '@/test/renderWithApp'
import { missingForSubmit, type Sheet, type SheetBody } from './api'
import { CandidateScoresPage } from './CandidateScoresPage'
import { ScoreSheetPage } from './ScoreSheetPage'

function sheet(o: Partial<Sheet> = {}): Sheet {
  return {
    candidateId: 7,
    studentCode: 'HE180001',
    candidateName: 'Nguyễn Văn An',
    trackCode: 'SE',
    items: [
      {
        criterionId: 1,
        group: 'GENERAL',
        name: 'Giao tiếp',
        description: null,
        rubricLow: 'Rụt rè',
        rubricMid: null,
        rubricHigh: null,
        value: null,
      },
      {
        criterionId: 2,
        group: 'PROFESSIONAL',
        name: 'Java cơ bản',
        description: 'OOP',
        rubricLow: null,
        rubricMid: null,
        rubricHigh: null,
        value: null,
      },
    ],
    comment: null,
    recommendation: null,
    submittedAt: null,
    version: null,
    editable: true,
    lockedReason: null,
    ...o,
  }
}

function renderSheet(s: Sheet, routes: Record<string, Handler> = {}) {
  return renderWithApp(
    <Routes>
      <Route path="/scoring/:candidateId" element={<ScoreSheetPage />} />
    </Routes>,
    {
      path: `/scoring/${s.candidateId}`,
      me: { ...ME_ORGANIZER, role: 'MEMBER' },
      routes: { [`GET /api/candidates/${s.candidateId}/scoresheet`]: () => [200, s], ...routes },
    },
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('missingForSubmit', () => {
  it('liệt kê tiêu chí chưa chấm và thiếu đề xuất', () => {
    const items = sheet().items
    expect(missingForSubmit({ 1: 8, 2: null }, items, null)).toEqual(['Java cơ bản', 'Đề xuất Đạt/Không đạt'])
    expect(missingForSubmit({ 1: 8, 2: 7 }, items, 'PASS')).toEqual([])
  })
})

describe('ScoreSheetPage', () => {
  it('tự lưu nháp sau khi chấm, lần lưu sau dùng version mới', async () => {
    let version = 0
    const bodies: SheetBody[] = []
    const { user } = renderSheet(sheet(), {
      'PUT /api/candidates/7/scoresheet': (b) => {
        const body = b as SheetBody
        bodies.push(body)
        version += 1
        return [
          200,
          sheet({
            version,
            items: sheet().items.map((i) => ({
              ...i,
              value: body.scores.find((x) => x.criterionId === i.criterionId)?.value ?? null,
            })),
          }),
        ]
      },
    })
    await user.click(await screen.findByRole('radio', { name: 'Giao tiếp: 8 điểm' }))
    await waitFor(() => expect(bodies).toHaveLength(1), { timeout: 4000 })
    expect(bodies[0]).toMatchObject({
      submit: false,
      version: null,
      scores: [
        { criterionId: 1, value: 8 },
        { criterionId: 2, value: null },
      ],
    })
    expect(await screen.findByText(/Đã lưu lúc/)).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: 'Java cơ bản: 7 điểm' }))
    await waitFor(() => expect(bodies).toHaveLength(2), { timeout: 4000 })
    expect(bodies[1].version).toBe(1)
  })

  it('nộp phiếu thiếu thì chặn ngay; đủ thì gửi submit = true', async () => {
    const bodies: SheetBody[] = []
    const { user } = renderSheet(
      sheet({ items: sheet().items.map((i) => ({ ...i, value: 8 })), version: 3 }),
      {
        'PUT /api/candidates/7/scoresheet': (b) => {
          bodies.push(b as SheetBody)
          return [200, sheet({ version: 4, submittedAt: '2026-11-20T03:20:00Z', recommendation: 'PASS' })]
        },
      },
    )
    await user.click(await screen.findByRole('button', { name: /Nộp phiếu/ }))
    expect(await screen.findByText('Còn thiếu: Đề xuất Đạt/Không đạt')).toBeInTheDocument()
    expect(bodies).toHaveLength(0)

    await user.click(screen.getByRole('radio', { name: 'Đạt' }))
    await user.click(screen.getByRole('button', { name: /Nộp phiếu/ }))
    await waitFor(() => expect(bodies.some((x) => x.submit)).toBe(true))
    expect(bodies.find((x) => x.submit)).toMatchObject({ recommendation: 'PASS', version: 3 })
    expect(await screen.findByText('Đã nộp phiếu chấm')).toBeInTheDocument()
  })

  it('phiếu bị khóa sau phê duyệt thì không sửa được', async () => {
    renderSheet(
      sheet({
        editable: false,
        lockedReason: 'Kết luận đã được phê duyệt',
        submittedAt: '2026-11-20T03:20:00Z',
      }),
    )
    expect(await screen.findByText('Kết luận đã được phê duyệt')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Giao tiếp: 8 điểm' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /Nộp phiếu|Lưu thay đổi/ })).not.toBeInTheDocument()
  })
})

describe('CandidateScoresPage', () => {
  it('chưa nộp phiếu thì nhắc nộp trước thay vì hiện điểm người khác', async () => {
    renderWithApp(
      <Routes>
        <Route path="/scoring/:candidateId/scores" element={<CandidateScoresPage />} />
      </Routes>,
      {
        path: '/scoring/7/scores',
        routes: {
          'GET /api/candidates/7/scores': () => [
            403,
            {
              code: 'SUBMIT_FIRST',
              message: 'Hãy nộp phiếu chấm của mình trước khi xem điểm của người khác.',
            },
          ],
        },
      },
    )
    expect(await screen.findByText('Nộp phiếu của bạn trước')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mở phiếu chấm' })).toBeInTheDocument()
  })
})
