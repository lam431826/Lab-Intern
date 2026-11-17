import type { components } from './schema'

/** Kiểu DTO sinh từ OpenAPI của backend (npm run gen:api). */
export type Schemas = components['schemas']

export interface FieldViolation {
  field: string
  message: string
}

/**
 * Lỗi từ backend, luôn có {@code code} ổn định (STALE_DATA, QUOTA_FULL...) và {@code message} tiếng Việt.
 * {@code status = 0}: không kết nối được máy chủ.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldViolation[]

  constructor(status: number, code: string, message: string, fieldErrors: FieldViolation[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }

  get isStale(): boolean {
    return this.code === 'STALE_DATA'
  }
}

export type TokenProvider = () => Promise<string | null>

export interface ApiClientOptions {
  getToken: TokenProvider
  /** Gọi khi nhận 401: phiên Clerk hết hạn hoặc token bị từ chối. */
  onUnauthorized?: () => void
  baseUrl?: string
  fetch?: typeof fetch
}

type Query = Record<string, string | number | boolean | null | undefined>

export interface RequestOptions {
  query?: Query
  body?: unknown
  signal?: AbortSignal
}

export interface DownloadedFile {
  blob: Blob
  filename: string
}

const FALLBACK_MESSAGES: Record<number, string> = {
  0: 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.',
  400: 'Yêu cầu không đúng định dạng.',
  401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  403: 'Bạn không có quyền thực hiện thao tác này.',
  404: 'Không tìm thấy dữ liệu.',
  409: 'Dữ liệu vừa bị thay đổi. Tải lại trang rồi thử lại.',
  413: 'File quá lớn.',
  422: 'Dữ liệu chưa hợp lệ.',
  500: 'Hệ thống gặp lỗi. Vui lòng thử lại sau.',
}

function fallbackMessage(status: number): string {
  return FALLBACK_MESSAGES[status] ?? FALLBACK_MESSAGES[500]
}

export function buildUrl(baseUrl: string, path: string, query?: Query): string {
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v))
  }
  const s = qs.toString()
  return `${baseUrl}${path}${s ? `?${s}` : ''}`
}

/** Tên file từ Content-Disposition, ưu tiên filename* (UTF-8, giữ dấu tiếng Việt). */
export function filenameFrom(header: string | null, fallback: string): string {
  if (!header) return fallback
  const star = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(header)
  if (star) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''))
    } catch {
      /* rơi xuống filename thường */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain ? plain[1] : fallback
}

async function toApiError(res: Response): Promise<ApiError> {
  let code = res.status === 401 ? 'UNAUTHORIZED' : res.status === 403 ? 'FORBIDDEN' : `HTTP_${res.status}`
  let message = fallbackMessage(res.status)
  let fieldErrors: FieldViolation[] = []
  try {
    const body = (await res.json()) as Partial<{
      code: string
      message: string
      fieldErrors: FieldViolation[]
    }>
    if (body.code) code = body.code
    if (body.message) message = body.message
    if (Array.isArray(body.fieldErrors)) fieldErrors = body.fieldErrors
  } catch {
    /* thân lỗi không phải JSON, ví dụ proxy trả HTML: giữ thông báo mặc định */
  }
  return new ApiError(res.status, code, message, fieldErrors)
}

export function createApiClient({
  getToken,
  onUnauthorized,
  baseUrl = '',
  fetch: fetchImpl,
}: ApiClientOptions) {
  const doFetch = fetchImpl ?? ((...args: Parameters<typeof fetch>) => globalThis.fetch(...args))

  async function send(method: string, path: string, opts: RequestOptions = {}): Promise<Response> {
    const headers = new Headers({ Accept: 'application/json' })
    // Lấy token mới mỗi lần: Clerk tự làm mới token sống ngắn (~60 giây)
    const token = await getToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)

    let body: BodyInit | undefined
    if (opts.body instanceof FormData) {
      body = opts.body // trình duyệt tự đặt multipart boundary
    } else if (opts.body !== undefined) {
      headers.set('Content-Type', 'application/json')
      body = JSON.stringify(opts.body)
    }

    let res: Response
    try {
      res = await doFetch(buildUrl(baseUrl, path, opts.query), { method, headers, body, signal: opts.signal })
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') throw e
      throw new ApiError(0, 'NETWORK_ERROR', fallbackMessage(0))
    }
    if (!res.ok) {
      const err = await toApiError(res)
      if (res.status === 401) onUnauthorized?.()
      throw err
    }
    return res
  }

  async function json<T>(method: string, path: string, opts?: RequestOptions): Promise<T> {
    const res = await send(method, path, opts)
    if (res.status === 204 || res.headers.get('Content-Length') === '0') return undefined as T
    return (await res.json()) as T
  }

  return {
    get: <T>(path: string, opts?: Omit<RequestOptions, 'body'>) => json<T>('GET', path, opts),
    post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      json<T>('POST', path, { ...opts, body }),
    put: <T>(path: string, body?: unknown, opts?: RequestOptions) => json<T>('PUT', path, { ...opts, body }),
    del: <T = void>(path: string, opts?: RequestOptions) => json<T>('DELETE', path, opts),

    /** Gửi một file dưới trường {@code file} (nhập Excel, file JD). */
    upload: <T>(path: string, file: File, opts?: Omit<RequestOptions, 'body'>) => {
      const form = new FormData()
      form.append('file', file)
      return json<T>('POST', path, { ...opts, body: form })
    },

    /** Tải file cần đăng nhập: không dùng <a href> được vì phải gửi header Bearer. */
    download: async (path: string, fallbackName: string, opts?: Omit<RequestOptions, 'body'>) => {
      const res = await send('GET', path, opts)
      return {
        blob: await res.blob(),
        filename: filenameFrom(res.headers.get('Content-Disposition'), fallbackName),
      } satisfies DownloadedFile
    },
  }
}

export type ApiClient = ReturnType<typeof createApiClient>

/** Lưu file đã tải về máy người dùng. */
export function saveFile({ blob, filename }: DownloadedFile): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
