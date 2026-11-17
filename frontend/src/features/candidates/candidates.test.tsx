import { screen, waitFor, within } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME_ORGANIZER, renderWithApp, type Handler } from '@/test/renderWithApp'
import type { Candidate, ImportReport, TrackSummary } from './api'
import { CandidateDetailPage } from './CandidateDetailPage'
import { CandidatesPage } from './CandidatesPage'
import { describe as describeHistory } from './history'
import { checkImportFile, ImportPage } from './ImportPage'
import { cvAverage } from './ScreeningCard'

function candidate(o: Partial<Candidate> = {}): Candidate {
  return {
    id: 7,
    studentCode: 'HE180001',
    fullName: 'Nguyễn Văn An',
    email: 'an@fpt.edu.vn',
    phone: '0901234567',
    major: 'Kỹ thuật phần mềm',
    trackId: 3,
    trackCode: 'SE',
    trackName: 'Software Engineer',
    cvLink: 'https://drive.google.com/an',
    portfolioLink: null,
    screeningStatus: 'PENDING',
    screeningReason: null,
    cvPersonalInfo: null,
    cvExperience: null,
    cvSkills: null,
    cvProjects: null,
    cvCertificates: null,
    cvAverage: null,
    flags: [],
    assignedAt: '2026-11-16T01:00:00Z',
    slaDeadline: '2026-11-17T13:00:00Z',
    overdue: false,
    screenedByName: null,
    screenedAt: null,
    scheduled: false,
    version: 4,
    ...o,
  }
}

const LEAD_SE = { ...ME_ORGANIZER, role: 'LEAD' as const, leadTrackCodes: ['SE'] }

function renderDetail(
  c: Candidate,
  opts: { me?: typeof ME_ORGANIZER; routes?: Record<string, Handler> } = {},
) {
  return renderWithApp(
    <Routes>
      <Route path="/candidates/:id" element={<CandidateDetailPage />} />
    </Routes>,
    {
      path: `/candidates/${c.id}`,
      me: opts.me,
      routes: {
        [`GET /api/candidates/${c.id}`]: () => [200, c],
        [`GET /api/candidates/${c.id}/history`]: () => [200, []],
        [`GET /api/candidates/${c.id}/slots`]: () => [200, []],
        ...opts.routes,
      },
    },
  )
}

const xlsx = () => new File(['fake'], 'ung-vien.xlsx')

afterEach(() => vi.unstubAllGlobals())

describe('quy tắc phía client', () => {
  it('điểm CV trung bình chỉ tính mục đã chấm, 1 chữ số (khớp backend)', () => {
    expect(cvAverage([null, null, null, null, null])).toBeNull()
    expect(cvAverage([7, 8, null, 9, null])).toBe(8)
    expect(cvAverage([7, 8, 8, 8, 8])).toBe(7.8)
    expect(cvAverage([7, 8])).toBe(7.5)
  })

  it('chỉ nhận file .xlsx tối đa 5 MB', () => {
    expect(checkImportFile(new File(['x'], 'ds.xlsx'))).toBeNull()
    expect(checkImportFile(new File(['x'], 'ds.xls'))).toMatch(/\.xlsx/)
    expect(checkImportFile(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'ds.xlsx'))).toBe(
      'File tối đa 5 MB',
    )
  })

  it('dịch lịch sử sang tiếng Việt', () => {
    const h = {
      field: 'screeningStatus',
      oldValue: 'PENDING',
      newValue: 'PASSED',
      note: null,
      changedByName: 'A',
      changedAt: '',
    }
    expect(describeHistory(h)).toBe('Sàng lọc CV: Chờ → Đạt')
    expect(
      describeHistory({ ...h, field: 'slot', oldValue: null, newValue: 'PV1 2026-11-20 10h - 10h15' }),
    ).toBe('Lịch phỏng vấn: PV1 2026-11-20 10h - 10h15')
  })
})

describe('Sàng lọc CV', () => {
  it('Lead của ngạch chấm điểm, chọn Đạt và lưu kèm version', async () => {
    const { user, calls } = renderDetail(candidate(), {
      me: LEAD_SE,
      routes: {
        'PUT /api/candidates/7/screening': (b) => [200, candidate({ ...(b as object), version: 5 })],
      },
    })
    await user.click(await screen.findByRole('radio', { name: 'Kỹ năng: 8 điểm' }))
    await user.click(screen.getByRole('radio', { name: 'Kinh nghiệm: 7 điểm' }))
    expect(screen.getByText('7,5')).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: 'Đạt' }))
    await user.click(screen.getByRole('button', { name: 'Lưu kết quả' }))

    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({
      cvSkills: 8,
      cvExperience: 7,
      cvPersonalInfo: null,
      status: 'PASSED',
      reason: null,
      version: 4,
    })
  })

  it('Loại mà chưa ghi lý do thì chặn ngay, không gửi', async () => {
    const { user, calls } = renderDetail(candidate(), { me: LEAD_SE })
    await user.click(await screen.findByRole('radio', { name: 'Loại' }))
    await user.click(screen.getByRole('button', { name: 'Lưu kết quả' }))
    expect(await screen.findByText('Loại hồ sơ thì phải ghi lý do')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('xung đột STALE_DATA: báo rõ, giữ nguyên điểm đang nhập', async () => {
    const { user } = renderDetail(candidate(), {
      me: LEAD_SE,
      routes: {
        'PUT /api/candidates/7/screening': () => [
          409,
          { code: 'STALE_DATA', message: 'Hồ sơ vừa được người khác cập nhật. Tải lại trang rồi thử lại.' },
        ],
      },
    })
    await user.click(await screen.findByRole('radio', { name: 'Dự án: 9 điểm' }))
    await user.click(screen.getByRole('radio', { name: 'Đạt' }))
    await user.click(screen.getByRole('button', { name: 'Lưu kết quả' }))

    expect(await screen.findByText('Hồ sơ vừa được người khác cập nhật')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Dự án: 9 điểm' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('button', { name: /Tải dữ liệu mới/ })).toBeInTheDocument()
  })

  it('Lead ngạch khác và Giảng viên chỉ xem', async () => {
    renderDetail(candidate(), { me: { ...LEAD_SE, leadTrackCodes: ['AI'] } })
    expect(await screen.findByRole('radio', { name: 'Kỹ năng: 8 điểm' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Lưu kết quả' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Sửa hồ sơ/ })).not.toBeInTheDocument()
  })

  it('hồ sơ đã có lịch thì giữ Đạt, không chọn được Loại', async () => {
    renderDetail(candidate({ screeningStatus: 'PASSED', scheduled: true }), { me: LEAD_SE })
    expect(await screen.findByRole('radio', { name: 'Loại' })).toBeDisabled()
    expect(screen.getByText(/Hồ sơ đã có lịch phỏng vấn nên giữ trạng thái Đạt/)).toBeInTheDocument()
  })
})

describe('Nhập Excel', () => {
  it('kiểm tra thử báo lỗi theo dòng và khóa nút nhập', async () => {
    const report: ImportReport = {
      applied: false,
      dryRun: true,
      totalRows: 3,
      created: 2,
      updated: 0,
      unchanged: 0,
      errors: [{ row: 4, studentCode: 'HE200003', message: 'Không nhận ra ngạch "Marketing"' }],
    }
    const { user, calls } = renderWithApp(<ImportPage />, {
      routes: { 'POST /api/candidates/import': () => [200, report] },
    })
    await user.upload(screen.getByLabelText('Chọn file Excel danh sách ứng viên'), xlsx())

    expect(await screen.findByText('File có 1 lỗi, chưa nhập được')).toBeInTheDocument()
    expect(screen.getByText('Không nhận ra ngạch "Marketing"')).toBeInTheDocument()
    expect(calls[0].query.get('dryRun')).toBe('true')
    expect(screen.getByRole('button', { name: /Nhập 3 hồ sơ/ })).toBeDisabled()
  })

  it('file hợp lệ thì nhập thật', async () => {
    let applied = false
    const { user, calls } = renderWithApp(<ImportPage />, {
      routes: {
        'POST /api/candidates/import': () => {
          const r: ImportReport = {
            applied,
            dryRun: !applied,
            totalRows: 2,
            created: 1,
            updated: 1,
            unchanged: 0,
            errors: [],
          }
          applied = true
          return [200, r]
        },
      },
    })
    await user.upload(screen.getByLabelText('Chọn file Excel danh sách ứng viên'), xlsx())
    await user.click(await screen.findByRole('button', { name: /Nhập 2 hồ sơ/ }))

    expect(await screen.findByText('Đã nhập xong')).toBeInTheDocument()
    expect(calls.map((c) => c.query.get('dryRun'))).toEqual(['true', 'false'])
  })
})

describe('Danh sách hồ sơ', () => {
  it('bấm vào ô Quá hạn của bộ đếm thì lọc đúng ngạch', async () => {
    const summary: TrackSummary[] = [
      {
        trackId: 3,
        trackCode: 'SE',
        trackName: 'Software Engineer',
        total: 5,
        pending: 2,
        passed: 2,
        rejected: 1,
        overdue: 1,
      },
    ]
    const { user, calls } = renderWithApp(<CandidatesPage />, {
      routes: {
        'GET /api/candidates/summary': () => [200, summary],
        'GET /api/candidates': () => [200, [candidate({ overdue: true })]],
      },
    })
    await user.click(await screen.findByRole('button', { name: 'Software Engineer: Quá hạn 1' }))
    await waitFor(() =>
      expect(calls.some((c) => c.path === '/api/candidates' && c.query.get('overdue') === 'true')).toBe(true),
    )
    const last = calls.filter((c) => c.path === '/api/candidates').at(-1)!
    expect(last.query.get('trackId')).toBe('3')
    expect(within(screen.getByRole('table')).getByText('Quá hạn')).toBeInTheDocument()
  })
})
