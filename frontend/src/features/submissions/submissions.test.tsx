import { screen, waitFor, within } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ME_ORGANIZER, renderWithApp } from '@/test/renderWithApp'
import type { Submission, SubmissionItem } from './api'
import { sendProblems } from './MarkSentDialog'
import { checkJdFile } from './parts'
import { SubmissionDetailPage } from './SubmissionDetailPage'
import { suggestTerm } from './SubmissionFormDialog'

const TRACKS = [
  {
    id: 3,
    code: 'SE',
    name: 'Software Engineer',
    quota: 8,
    slotMin: 8,
    slotMax: 10,
    requiresPortfolio: false,
    leadUserId: null,
    leadName: null,
    sortOrder: 3,
  },
  {
    id: 1,
    code: 'AI',
    name: 'AI',
    quota: 3,
    slotMin: 4,
    slotMax: 5,
    requiresPortfolio: false,
    leadUserId: null,
    leadName: null,
    sortOrder: 1,
  },
]

function item(overrides: Partial<SubmissionItem>): SubmissionItem {
  return {
    id: 11,
    trackId: 3,
    trackCode: 'SE',
    trackName: 'Software Engineer',
    positionTitle: 'Thực tập sinh Java',
    quota: 5,
    jdContent: 'Yêu cầu Java cơ bản',
    note: null,
    sortOrder: 1,
    hasFile: false,
    fileName: null,
    fileMimeType: null,
    fileSize: null,
    fileSha256: null,
    version: 0,
    ...overrides,
  }
}

function submission(overrides: Partial<Submission>): Submission {
  const items = overrides.items ?? [item({})]
  return {
    id: 5,
    termCode: 'SP2027',
    title: 'Nhu cầu tuyển Spring 2027',
    status: 'DRAFT',
    sentAt: null,
    recipient: 'Phòng QHDN',
    externalReference: null,
    note: null,
    submittedById: null,
    submittedByName: null,
    replacesSubmissionId: null,
    positionCount: items.length,
    totalQuota: items.reduce((s, i) => s + i.quota, 0),
    version: 2,
    createdAt: '2026-11-17T01:00:00Z',
    updatedAt: '2026-11-17T02:00:00Z',
    ...overrides,
    items,
  }
}

function renderDetail(s: Submission, opts: Parameters<typeof renderWithApp>[1] = {}) {
  return renderWithApp(
    <Routes>
      <Route path="/submissions/:id" element={<SubmissionDetailPage />} />
      <Route path="/submissions" element={<p>Danh sách</p>} />
    </Routes>,
    {
      path: `/submissions/${s.id}`,
      ...opts,
      routes: {
        [`GET /api/recruitment-submissions/${s.id}`]: () => [200, s],
        'GET /api/tracks': () => [200, TRACKS],
        ...opts.routes,
      },
    },
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('quy tắc phía client', () => {
  it('liệt kê điều kiện còn thiếu trước khi chốt gửi', () => {
    expect(sendProblems(submission({ items: [] }))).toEqual(['Chưa có vị trí tuyển dụng nào'])
    expect(
      sendProblems(
        submission({ items: [item({ jdContent: '  ', positionTitle: 'AI Intern' }), item({ id: 12 })] }),
      ),
    ).toEqual(['Vị trí "AI Intern" chưa có nội dung hoặc file JD'])
    expect(sendProblems(submission({ items: [item({ jdContent: null, hasFile: true })] }))).toEqual([])
  })

  it('chỉ nhận PDF/DOCX không rỗng, tối đa 5 MB', () => {
    expect(checkJdFile(new File(['x'], 'jd.pdf'))).toBeNull()
    expect(checkJdFile(new File(['x'], 'JD.DOCX'))).toBeNull()
    expect(checkJdFile(new File(['x'], 'jd.doc'))).toBe('Chỉ nhận file PDF hoặc DOCX')
    expect(checkJdFile(new File([], 'jd.pdf'))).toBe('File đang trống')
    expect(checkJdFile(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'jd.pdf'))).toBe('File JD tối đa 5 MB')
  })

  it('gợi ý mã kỳ theo tháng', () => {
    expect(suggestTerm(new Date(2026, 0, 10))).toBe('SP2026')
    expect(suggestTerm(new Date(2026, 6, 1))).toBe('SU2026')
    expect(suggestTerm(new Date(2026, 10, 17))).toBe('FA2026')
  })
})

describe('SubmissionDetailPage', () => {
  it('bản nháp thiếu JD: hộp chốt gửi báo thiếu và khóa nút chốt', async () => {
    const { user } = renderDetail(submission({ items: [item({ jdContent: null })] }))
    await user.click(await screen.findByRole('button', { name: /Đánh dấu đã gửi/ }))
    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('Vị trí "Thực tập sinh Java" chưa có nội dung hoặc file JD'),
    ).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: /Chốt đã gửi/ })).toBeDisabled()
  })

  it('bản đã gửi chỉ đọc: không sửa/xóa, có nút tạo bản thay thế', async () => {
    renderDetail(
      submission({ status: 'SENT', sentAt: '2026-11-10T03:00:00Z', submittedByName: 'Người tổ chức' }),
    )
    expect(await screen.findByRole('button', { name: /Tạo bản bổ sung\/thay thế/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Sửa vị trí/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Đánh dấu đã gửi/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Tải file JD/ })).not.toBeInTheDocument()
  })

  it('Lead chỉ sửa được vị trí thuộc ngạch mình', async () => {
    renderDetail(
      submission({
        items: [
          item({}),
          item({ id: 12, trackId: 1, trackCode: 'AI', trackName: 'AI', positionTitle: 'AI Intern' }),
        ],
      }),
      { me: { ...ME_ORGANIZER, role: 'LEAD', leadTrackCodes: ['AI'] } },
    )
    expect(await screen.findByRole('button', { name: 'Sửa vị trí AI Intern' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sửa vị trí Thực tập sinh Java' })).not.toBeInTheDocument()
    // Lead không chốt gửi, không sửa thông tin chung
    expect(screen.queryByRole('button', { name: /Đánh dấu đã gửi/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Thêm vị trí/ })).toBeInTheDocument()
  })

  it('tạo bản thay thế sao chép vị trí và file JD của bản cũ', async () => {
    const old = submission({
      status: 'SENT',
      sentAt: '2026-11-10T03:00:00Z',
      items: [
        item({}),
        item({
          id: 12,
          positionTitle: 'Có file',
          jdContent: null,
          hasFile: true,
          fileName: 'jd.pdf',
          fileSize: 10,
        }),
      ],
    })
    let nextItemId = 100
    const { user, calls } = renderDetail(old, {
      routes: {
        'POST /api/recruitment-submissions': () => [201, submission({ id: 9, items: [] })],
        'POST /api/recruitment-submissions/9/items': (b) => [
          201,
          { ...item({}), ...(b as object), id: nextItemId++, version: 0 },
        ],
        'GET /api/recruitment-submission-items/12/file': () =>
          new Response('pdf', { headers: { 'Content-Disposition': 'attachment; filename="jd.pdf"' } }),
        'POST /api/recruitment-submission-items/101/file': () => [200, item({ id: 101, hasFile: true })],
        'GET /api/recruitment-submissions/9': () => [200, submission({ id: 9 })],
      },
    })
    await user.click(await screen.findByRole('button', { name: /Tạo bản bổ sung\/thay thế/ }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Tạo bản nháp' }),
    )

    await waitFor(() =>
      expect(calls.some((c) => c.path.endsWith('/101/file') && c.method === 'POST')).toBe(true),
    )
    const created = calls.find((c) => c.method === 'POST' && c.path === '/api/recruitment-submissions')
    expect(created?.body).toMatchObject({ replacesSubmissionId: 5, termCode: 'SP2027' })
    expect(calls.filter((c) => c.path === '/api/recruitment-submissions/9/items')).toHaveLength(2)
    const upload = calls.find((c) => c.path.endsWith('/101/file') && c.method === 'POST')!
    expect(upload.query.get('version')).toBe('0')
    expect(upload.body).toBeInstanceOf(FormData)
  })
})
