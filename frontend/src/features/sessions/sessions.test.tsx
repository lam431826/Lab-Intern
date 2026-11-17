import { screen, waitFor, within } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME_ORGANIZER, renderWithApp, type Handler } from '@/test/renderWithApp'
import type { Session, Slot } from './api'
import { SessionDetailPage } from './SessionDetailPage'
import { outsideWindows } from './SessionFormDialog'

const MEMBER = { ...ME_ORGANIZER, id: 9, role: 'MEMBER' as const, fullName: 'Thành viên A' }

function slot(o: Partial<Slot> = {}): Slot {
  return {
    id: 31,
    sessionId: 5,
    sessionDate: '2026-11-20',
    room: 'P.101',
    candidateId: 7,
    studentCode: 'HE180001',
    candidateName: 'Nguyễn Văn An',
    startTime: '10:00:00',
    endTime: '10:15:00',
    timeLabel: '10h - 10h15',
    slotType: 'PV1',
    active: true,
    attendance: 'PENDING',
    note: null,
    replacedById: null,
    ...o,
  }
}

function session(o: Partial<Session> = {}): Session {
  return {
    id: 5,
    trackId: 3,
    trackCode: 'SE',
    trackName: 'Software Engineer',
    sessionDate: '2026-11-20',
    startTime: '10:00:00',
    endTime: '11:00:00',
    room: 'P.101',
    logSummary: null,
    panel: [
      { userId: 1, fullName: 'Người tổ chức', email: 'org@dxlab.vn', panelRole: 'ORGANIZER' },
      { userId: 9, fullName: 'Thành viên A', email: 'a@dxlab.vn', panelRole: 'ROTATING' },
    ],
    slots: [slot()],
    warnings: [{ code: 'PANEL_SIZE', message: 'Hội đồng có 2 người, quy định 6' }],
    ...o,
  }
}

function renderSession(
  s: Session,
  opts: { me?: typeof ME_ORGANIZER; routes?: Record<string, Handler> } = {},
) {
  return renderWithApp(
    <Routes>
      <Route path="/sessions/:id" element={<SessionDetailPage />} />
    </Routes>,
    {
      path: `/sessions/${s.id}`,
      me: opts.me,
      routes: {
        [`GET /api/sessions/${s.id}`]: () => [200, s],
        [`GET /api/sessions/${s.id}/progress`]: () => [
          200,
          { sessionId: s.id, panelSize: 2, candidates: [] },
        ],
        ...opts.routes,
      },
    },
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('khung giờ phỏng vấn', () => {
  it('trong 10h–12h30 hoặc 16h–18h30 thì không cảnh báo', () => {
    expect(outsideWindows('10:00', '12:30')).toBe(false)
    expect(outsideWindows('16:15', '17:00')).toBe(false)
    expect(outsideWindows('09:45', '11:00')).toBe(true)
    expect(outsideWindows('12:00', '16:30')).toBe(true)
  })
})

describe('SessionDetailPage', () => {
  it('thành viên hội đồng điểm danh được nhưng không xếp, dời hay hủy lịch', async () => {
    const { user, calls } = renderSession(session(), {
      me: MEMBER,
      routes: { 'PUT /api/slots/31/attendance': (b) => [200, slot(b as Partial<Slot>)] },
    })
    expect(await screen.findByText('Hội đồng có 2 người, quy định 6')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Tự xếp/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Dời lịch' })).not.toBeInTheDocument()

    await user.click(
      within(screen.getByRole('radiogroup', { name: /Điểm danh Nguyễn Văn An/ })).getByRole('radio', {
        name: 'Có mặt',
      }),
    )
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ attendance: 'ATTENDED', note: null })
  })

  it('người ngoài hội đồng chỉ xem điểm danh', async () => {
    renderSession(session(), { me: { ...MEMBER, id: 99 } })
    expect(await screen.findByText('Chưa điểm danh')).toBeInTheDocument()
    expect(screen.queryByRole('radiogroup', { name: /Điểm danh/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Interview Log' })).not.toBeInTheDocument()
  })

  it('tự xếp báo ứng viên chưa xếp được vì ca hết giờ', async () => {
    const { user } = renderSession(session({ slots: [] }), {
      routes: {
        'POST /api/sessions/5/auto-schedule': () => [
          200,
          { scheduled: [slot()], notScheduled: ['HE180009'], session: session() },
        ],
      },
    })
    await user.click(await screen.findByRole('button', { name: /Tự xếp/ }))
    expect(await screen.findByText(/còn 1 chưa xếp: HE180009/)).toBeInTheDocument()
  })

  it('slot PV2 không dời được nữa; slot đã dời hiển thị lý do', async () => {
    renderSession(
      session({
        slots: [
          slot({ id: 30, active: false, attendance: 'ABSENT', note: 'Ốm', replacedById: 31 }),
          slot({ id: 31, slotType: 'PV2', startTime: '10:30:00', timeLabel: '10h30 - 10h45' }),
        ],
      }),
    )
    expect(await screen.findByText('Đã dời lịch: Ốm')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Dời lịch' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeInTheDocument()
  })
})
