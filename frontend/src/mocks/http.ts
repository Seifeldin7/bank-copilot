import { delay, HttpResponse } from 'msw'
import type { ProblemFieldError, Role } from '@/api/types'
import { db, type MockUser } from './db'

export class MockHttpError extends Error {
  readonly status: number
  readonly title: string
  readonly errors?: ProblemFieldError[]

  constructor(status: number, title: string, detail: string, errors?: ProblemFieldError[]) {
    super(detail)
    this.status = status
    this.title = title
    this.errors = errors
  }
}

export const badRequest = (detail: string, errors?: ProblemFieldError[]) => new MockHttpError(400, 'Bad Request', detail, errors)
export const forbidden = (detail = 'You do not have permission to perform this action.') => new MockHttpError(403, 'Forbidden', detail)
export const notFound = (detail = 'The requested resource was not found.') => new MockHttpError(404, 'Not Found', detail)
export const conflict = (detail: string) => new MockHttpError(409, 'Conflict', detail)
export const unprocessable = (detail: string, errors?: ProblemFieldError[]) => new MockHttpError(422, 'Unprocessable Entity', detail, errors)

const TOKEN_TTL_MS = 60 * 60 * 1000

export function issueToken(user: MockUser) {
  const expiresAt = Date.now() + TOKEN_TTL_MS
  return { token: `mock.${btoa(user.id)}.${expiresAt}`, expiresAt: new Date(expiresAt).toISOString() }
}

function userFromBackendJwt(token: string): MockUser {
  let claims: { sub?: string; exp?: number; roles?: string[] }
  try {
    claims = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    throw new MockHttpError(401, 'Unauthorized', 'The access token is invalid.')
  }
  if (!claims.sub || !claims.exp || claims.exp * 1000 < Date.now()) {
    throw new MockHttpError(401, 'Unauthorized', 'The access token has expired.')
  }
  const existing = db.users.find((u) => u.username === claims.sub)
  if (existing) return existing
  const user: MockUser = {
    id: `u-${claims.sub}`,
    username: claims.sub,
    fullName: claims.sub,
    role: claims.roles?.includes('SUPERVISOR') ? 'SUPERVISOR' : 'EMPLOYEE',
    branchCode: 'CAI-012',
    enabled: true,
    createdAt: new Date().toISOString(),
    password: '',
  }
  db.users.push(user)
  return user
}

export function authenticate(request: Request, role?: Role): MockUser {
  const header = request.headers.get('Authorization') ?? ''
  const jwt = /^Bearer ([\w-]+\.[\w-]+\.[\w-]+)$/.exec(header)
  if (jwt) {
    const user = userFromBackendJwt(jwt[1])
    if (role === 'SUPERVISOR' && user.role !== 'SUPERVISOR') throw forbidden('This action requires the SUPERVISOR role.')
    return user
  }
  const match = /^Bearer mock\.([A-Za-z0-9+/=]+)\.(\d+)$/.exec(header)
  if (!match) throw new MockHttpError(401, 'Unauthorized', 'Authentication is required.')
  if (Number(match[2]) < Date.now()) throw new MockHttpError(401, 'Unauthorized', 'The access token has expired.')
  let userId = ''
  try {
    userId = atob(match[1])
  } catch {
    throw new MockHttpError(401, 'Unauthorized', 'The access token is invalid.')
  }
  const user = db.users.find((u) => u.id === userId && u.enabled)
  if (!user) throw new MockHttpError(401, 'Unauthorized', 'The access token is invalid.')
  if (role === 'SUPERVISOR' && user.role !== 'SUPERVISOR') throw forbidden('This action requires the SUPERVISOR role.')
  return user
}

type Resolver = (info: { request: Request; params: Record<string, string | readonly string[] | undefined> }) => Promise<unknown> | unknown

export function handle(resolver: Resolver, latency: [number, number] = [150, 450]) {
  return async (info: { request: Request; params: Record<string, string | readonly string[] | undefined> }) => {
    const runId = info.request.headers.get('X-Request-Id') ?? crypto.randomUUID()
    await delay(latency[0] + Math.random() * (latency[1] - latency[0]))
    try {
      const result = await resolver(info)
      if (result instanceof Response) return result
      if (result === undefined) return new HttpResponse(null, { status: 204, headers: { 'X-Request-Id': runId } })
      return HttpResponse.json(result as Record<string, unknown>, { headers: { 'X-Request-Id': runId } })
    } catch (error) {
      if (error instanceof MockHttpError) {
        return HttpResponse.json(
          {
            type: 'about:blank',
            title: error.title,
            status: error.status,
            detail: error.message,
            instance: new URL(info.request.url).pathname,
            runId,
            errors: error.errors,
          },
          { status: error.status, headers: { 'X-Request-Id': runId, 'Content-Type': 'application/problem+json' } },
        )
      }
      throw error
    }
  }
}

export function param(params: Record<string, string | readonly string[] | undefined>, key: string) {
  const value = params[key]
  return Array.isArray(value) ? value[0] : (value as string)
}
