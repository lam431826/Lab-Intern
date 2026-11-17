import { describe, expect, it, vi } from 'vitest'
import { ApiError, buildUrl, createApiClient, filenameFrom } from './client'

type Fetch = typeof globalThis.fetch

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

describe('createApiClient', () => {
  it('gửi Bearer token mới cho mỗi request và parse JSON', async () => {
    const fetch = vi.fn<Fetch>(async () => jsonResponse(200, { id: 1 }))
    const getToken = vi.fn<() => Promise<string | null>>().mockResolvedValueOnce('t1').mockResolvedValueOnce('t2')
    const api = createApiClient({ getToken, fetch })

    await api.get('/api/me')
    expect(await api.get('/api/me')).toEqual({ id: 1 })

    const calls = fetch.mock.calls as unknown as [string, RequestInit][]
    expect(new Headers(calls[0][1].headers).get('Authorization')).toBe('Bearer t1')
    expect(new Headers(calls[1][1].headers).get('Authorization')).toBe('Bearer t2')
  })

  it('chuyển lỗi backend thành ApiError giữ code, message và fieldErrors', async () => {
    const fetch = vi.fn<Fetch>(async () =>
      jsonResponse(422, {
        code: 'VALIDATION_FAILED',
        message: 'Dữ liệu chưa hợp lệ',
        fieldErrors: [{ field: 'email', message: 'không đúng định dạng' }],
      }),
    )
    const api = createApiClient({ getToken: async () => 't', fetch })

    const err = await api.post('/api/users', { email: 'x' }).catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ status: 422, code: 'VALIDATION_FAILED', message: 'Dữ liệu chưa hợp lệ' })
    expect((err as ApiError).fieldErrors[0].field).toBe('email')
  })

  it('nhận biết STALE_DATA và gọi onUnauthorized khi 401', async () => {
    const onUnauthorized = vi.fn<() => void>()
    const fetch = vi
      .fn<Fetch>()
      .mockResolvedValueOnce(jsonResponse(409, { code: 'STALE_DATA', message: 'Dữ liệu vừa được cập nhật' }))
      .mockResolvedValueOnce(new Response('<html>', { status: 401 }))
    const api = createApiClient({ getToken: async () => 't', fetch, onUnauthorized })

    const stale = (await api.put('/api/x', {}).catch((e: unknown) => e)) as ApiError
    expect(stale.isStale).toBe(true)

    const unauth = (await api.get('/api/me').catch((e: unknown) => e)) as ApiError
    expect(unauth).toMatchObject({ status: 401, code: 'UNAUTHORIZED' })
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('báo NETWORK_ERROR khi không kết nối được', async () => {
    const api = createApiClient({
      getToken: async () => null,
      fetch: vi.fn<Fetch>(async () => {
        throw new TypeError('Failed to fetch')
      }),
    })
    await expect(api.get('/api/me')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('trả undefined với 204 và không đặt Content-Type JSON khi gửi file', async () => {
    const fetch = vi.fn<Fetch>(async () => new Response(null, { status: 204 }))
    const api = createApiClient({ getToken: async () => null, fetch })

    expect(await api.del('/api/slots/1')).toBeUndefined()
    await api.upload('/api/candidates/import', new File(['x'], 'ds.xlsx'))
    const [, init] = (fetch.mock.calls as unknown as [string, RequestInit][])[1]
    expect(init.body).toBeInstanceOf(FormData)
    expect(new Headers(init.headers).has('Content-Type')).toBe(false)
  })

  it('tải file kèm tên tiếng Việt từ Content-Disposition', async () => {
    const fetch = vi.fn<Fetch>(
      async () =>
        new Response('xlsx', {
          headers: {
            'Content-Disposition': "attachment; filename*=UTF-8''l%E1%BB%8Bch-ph%E1%BB%8Fng-v%E1%BA%A5n.xlsx",
          },
        }),
    )
    const api = createApiClient({ getToken: async () => 't', fetch })
    const file = await api.download('/api/sessions/export', 'lich.xlsx')
    expect(file.filename).toBe('lịch-phỏng-vấn.xlsx')
    expect(await file.blob.text()).toBe('xlsx')
  })
})

describe('helpers', () => {
  it('bỏ tham số rỗng khỏi query string', () => {
    expect(buildUrl('', '/api/candidates', { trackId: 3, q: '', status: undefined, overdue: false })).toBe(
      '/api/candidates?trackId=3&overdue=false',
    )
  })

  it('đọc filename thường khi không có filename*', () => {
    expect(filenameFrom('attachment; filename="ket-qua.xlsx"', 'x')).toBe('ket-qua.xlsx')
    expect(filenameFrom(null, 'mac-dinh.xlsx')).toBe('mac-dinh.xlsx')
  })
})
