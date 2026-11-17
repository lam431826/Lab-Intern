import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME_ORGANIZER, renderWithApp } from '@/test/renderWithApp'
import { DashboardPage, taskCards, type Dashboard } from './DashboardPage'

const DATA: Dashboard = {
  myTasks: { sheetsToScore: 2, screeningToDo: 5, toApprove: 3 },
  tracks: [
    {
      trackId: 1,
      trackCode: 'AI',
      trackName: 'AI',
      quota: 3,
      candidates: 12,
      screeningPending: 4,
      screeningOverdue: 1,
      screeningPassed: 6,
      scheduled: 6,
      attended: 5,
      absent: 1,
      scoresComplete: 4,
      decided: 3,
      approved: 2,
      seatsTaken: 3,
      offersAccepted: 1,
    },
  ],
}

const keys = (role: Parameters<typeof taskCards>[1]) => taskCards(DATA, role).map((c) => c.key)

afterEach(() => vi.unstubAllGlobals())

describe('taskCards', () => {
  it('mỗi vai trò thấy đúng việc của mình', () => {
    expect(keys('MEMBER')).toEqual(['score'])
    expect(keys('LEAD')).toEqual(['score', 'screen'])
    expect(keys('ORGANIZER')).toEqual(['score', 'screen'])
    expect(keys('LECTURER')).toEqual(['score', 'approve'])
  })
})

describe('DashboardPage', () => {
  it('hiện việc cần làm dẫn tới đúng màn hình và tiến độ theo ngạch', async () => {
    renderWithApp(<DashboardPage />, { routes: { 'GET /api/dashboard': () => [200, DATA] } })
    const screen5 = await screen.findByRole('link', { name: /Hồ sơ chờ duyệt CV/ })
    expect(screen5).toHaveAttribute('href', '/candidates')
    expect(screen.getByRole('link', { name: /Phiếu cần chấm/ })).toHaveAttribute('href', '/scoring')
    expect(screen.getByText('(1 quá hạn)')).toBeInTheDocument()
    expect(screen.getByText('3/3')).toBeInTheDocument()
  })

  it('thành viên không thấy bảng ngạch khi backend không trả ngạch nào', async () => {
    renderWithApp(<DashboardPage />, {
      me: { ...ME_ORGANIZER, role: 'MEMBER' },
      routes: { 'GET /api/dashboard': () => [200, { ...DATA, tracks: [] }] },
    })
    expect(await screen.findByText('Phiếu cần chấm')).toBeInTheDocument()
    expect(screen.queryByText('Tiến độ theo ngạch')).not.toBeInTheDocument()
  })
})
