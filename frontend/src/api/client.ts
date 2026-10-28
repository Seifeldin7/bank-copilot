import type { ProblemDetail, ProblemFieldError } from './types'

const TOKEN_KEY = 'bm-copilot.token'

let accessToken: string | null = readStoredToken()
const unauthorizedListeners = new Set<() => void>()

function readStoredToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export const tokenStore = {
  get: () => accessToken,
  set(token: string | null) {
    accessToken = token
    try {
      if (token) sessionStorage.setItem(TOKEN_KEY, token)
      else sessionStorage.removeItem(TOKEN_KEY)
    } catch {
      return
    }
  },
  onUnauthorized(listener: () => void) {
    unauthorizedListeners.add(listener)
    return () => {
      unauthorizedListeners.delete(listener)
    }
  },
}

export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail?: string
  readonly runId?: string
  readonly fieldErrors: ProblemFieldError[]

  constructor(problem: ProblemDetail, runId?: string) {
    super(problem.detail ?? problem.title)
    this.name = 'ApiError'
    this.status = problem.status
    this.title = problem.title
    this.detail = problem.detail
    this.runId = problem.runId ?? runId
    this.fieldErrors = problem.errors ?? []
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

interface RequestOptions {
  method?: Method
  body?: unknown
  formData?: FormData
  query?: Record<string, string | undefined>
}

function buildUrl(path: string, query?: Record<string, string | undefined>) {
  const url = new URL(`/api${path}`, window.location.origin)
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== '') url.searchParams.set(key, value)
    }
  }
  return url.pathname + url.search
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const requestId = crypto.randomUUID()
  const headers: Record<string, string> = { 'X-Request-Id': requestId, Accept: 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  let body: BodyInit | undefined
  if (options.formData) {
    body = options.formData
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body,
    })
  } catch {
    throw new ApiError({ title: 'Network error', status: 0, detail: 'The server could not be reached.' }, requestId)
  }

  const runId = response.headers.get('X-Request-Id') ?? requestId

  if (!response.ok) {
    let problem: ProblemDetail = { title: response.statusText || 'Request failed', status: response.status }
    try {
      problem = { ...problem, ...((await response.json()) as ProblemDetail) }
    } catch {
      problem.detail = undefined
    }
    if (response.status === 401 && token) {
      unauthorizedListeners.forEach((listener) => listener())
    }
    throw new ApiError(problem, runId)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
