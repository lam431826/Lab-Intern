import { screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME_ORGANIZER, renderWithApp } from '@/test/renderWithApp'
import { needsNote, type Decision, type ResultRow, type TrackResult } from './api'
import { ResultsPage } from './ResultsPage'

function decision(o: Partial<Decision> = {}): Decision {
  return {
    id: 1,
    finalStatus: 'HIRED',
    transferTrackId: null,
    transferTrackCode: null,
    note: null,
    approved: false,
    approvedByName: null,
    approvedAt: null,
    emailStatus: 'NOT_SENT',
    offerStatus: 'NONE',
    version: 2,
    ...o,
  }
}

function row(o: Partial<ResultRow>): ResultRow {
  return {
    candidateId: 1,
    studentCode: 'AI01',
    fullName: 'Ứng viên AI01',
    interview: 'ATTENDED',
    submittedSheets: 2,
    panelSize: 2,
    passVotes: 2,
    failVotes: 0,
    generalAverage: 9,
    professionalAverage: 9,
    total: 9,
    meetsThreshold: true,
    rank: 1,
    suggestion: 'HIRED',
    decision: null,
    ...o,
  }
}

function table(rows: ResultRow[], o: Partial<TrackResult> = {}): TrackResult {
  return {
    trackId: 1,
    trackCode: 'AI',
    trackName: 'AI',
    quota: 2,
    seatsTaken: 1,
    seatsLeft: 1,
    weightGeneral: 0.4,
    weightProfessional: 0.6,
    passThreshold: 7,
    rows,
    nextWaitlistCandidateId: null,
    ...o,
  }
}

afterEach(() => vi.unstubAllGlobals())

const LECTURER = { ...ME_ORGANIZER, role: 'LECTURER' as const }

describe('needsNote', () => {
  it('kết luận khác gợi ý thì cần lý do', () => {
    expect(needsNote('HIRED', 'HIRED')).toBe(false)
    expect(needsNote('PENDING', 'HIRED')).toBe(false)
    expect(needsNote('WAITLIST', 'HIRED')).toBe(true)
    expect(needsNote('REJECTED', null)).toBe(true)
  })
})

describe('ResultsPage', () => {
  it('kết luận khác gợi ý mà chưa ghi lý do thì không lưu được', async () => {
    const { user, calls } = renderWithApp(<ResultsPage />, {
      routes: {
        'GET /api/results': () => [200, [table([row({})])]],
        'PUT /api/candidates/1/decision': (b) => [200, decision(b as Partial<Decision>)],
      },
    })
    await user.click(await screen.findByRole('button', { name: 'Kết luận' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('radio', { name: 'Dự bị' }))
    const save = within(dialog).getByRole('button', { name: 'Lưu kết luận' })
    expect(save).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/Lý do/), 'Ưu tiên ứng viên khác')
    await user.click(save)
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      finalStatus: 'WAITLIST',
      transferTrackId: null,
      note: 'Ưu tiên ứng viên khác',
      version: null,
    })
  })

  it('Giảng viên phê duyệt cả ngạch; Người tổ chức không thấy nút duyệt', async () => {
    const data = [
      table([
        row({ decision: decision() }),
        row({ candidateId: 2, fullName: 'B', decision: decision({ finalStatus: 'REJECTED' }) }),
      ]),
    ]
    const { unmount } = renderWithApp(<ResultsPage />, { routes: { 'GET /api/results': () => [200, data] } })
    await screen.findAllByText('Ứng viên AI01')
    expect(screen.queryByRole('button', { name: /Phê duyệt 2 kết luận/ })).not.toBeInTheDocument()
    unmount()

    const { user, calls } = renderWithApp(<ResultsPage />, {
      me: LECTURER,
      routes: { 'GET /api/results': () => [200, data], 'POST /api/results/1/approve': () => [200, data[0]] },
    })
    await user.click(await screen.findByRole('button', { name: /Phê duyệt 2 kết luận/ }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Phê duyệt' }),
    )
    await waitFor(() => expect(calls.some((c) => c.path === '/api/results/1/approve')).toBe(true))
  })

  it('đã phê duyệt: Người tổ chức ghi nhận offer, chỉ người dự bị kế tiếp có nút gọi', async () => {
    const data = [
      table(
        [
          row({ decision: decision({ approved: true }) }),
          row({
            candidateId: 2,
            fullName: 'Dự bị 1',
            rank: 3,
            suggestion: 'WAITLIST',
            decision: decision({ finalStatus: 'WAITLIST', approved: true }),
          }),
          row({
            candidateId: 3,
            fullName: 'Dự bị 2',
            rank: 4,
            suggestion: 'WAITLIST',
            decision: decision({ finalStatus: 'WAITLIST', approved: true }),
          }),
        ],
        { nextWaitlistCandidateId: 2 },
      ),
    ]
    const { user } = renderWithApp(<ResultsPage />, { routes: { 'GET /api/results': () => [200, data] } })
    const rows = await screen.findAllByRole('row')
    const promote = screen.getAllByRole('button', { name: 'Gọi dự bị' })
    expect(promote).toHaveLength(1)
    expect(
      within(rows.find((r) => r.textContent?.includes('Dự bị 1'))!).getByRole('button', {
        name: 'Gọi dự bị',
      }),
    ).toBeInTheDocument()

    // Offer: chưa gửi email thì không chọn được phản hồi
    await user.click(screen.getByRole('button', { name: 'Offer' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('radio', { name: 'Từ chối offer' })).toBeDisabled()
    await user.click(within(dialog).getByRole('switch'))
    expect(within(dialog).getByRole('radio', { name: 'Từ chối offer' })).toBeEnabled()
  })
})
