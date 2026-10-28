import { http } from 'msw'
import type { AuthResponse, CreateUserRequest, LoginRequest, ProblemFieldError, UserSummary } from '@/api/types'
import { db, newEvent, persist, ref, type MockUser } from '../db'
import { authenticate, badRequest, conflict, handle, issueToken, MockHttpError } from '../http'

const toSummary = ({ password: _password, ...user }: MockUser): UserSummary => user

export const identityHandlers = [
  http.post(
    '/api/auth/login',
    handle(async ({ request }) => {
      const body = (await request.json()) as Partial<LoginRequest>
      const errors: ProblemFieldError[] = []
      if (!body.username?.trim()) errors.push({ field: 'username', message: 'Username is required.' })
      if (!body.password) errors.push({ field: 'password', message: 'Password is required.' })
      if (errors.length) throw badRequest('Validation failed.', errors)
      const user = db.users.find((u) => u.username === body.username!.trim().toLowerCase())
      if (!user || user.password !== body.password || !user.enabled) {
        throw new MockHttpError(401, 'Unauthorized', 'Invalid username or password.')
      }
      const { token, expiresAt } = issueToken(user)
      db.events.unshift(newEvent('LOGIN', ref(user), 'User', user.username, 'Successful login', new Date().toISOString()))
      persist()
      const response: AuthResponse = { accessToken: token, tokenType: 'Bearer', expiresAt, user: toSummary(user) }
      return response
    }),
  ),

  http.get(
    '/api/auth/me',
    handle(({ request }) => toSummary(authenticate(request)), [50, 120]),
  ),

  http.get(
    '/api/users',
    handle(({ request }) => {
      authenticate(request, 'SUPERVISOR')
      return db.users.map(toSummary)
    }),
  ),

  http.post(
    '/api/users',
    handle(async ({ request }) => {
      const actor = authenticate(request, 'SUPERVISOR')
      const body = (await request.json()) as Partial<CreateUserRequest>
      const errors: ProblemFieldError[] = []
      const username = body.username?.trim().toLowerCase() ?? ''
      if (!/^[a-z][a-z0-9._]{2,29}$/.test(username))
        errors.push({ field: 'username', message: 'Use 3-30 lowercase letters, digits, dots or underscores, starting with a letter.' })
      if (!body.fullName || body.fullName.trim().length < 3) errors.push({ field: 'fullName', message: 'Full name must be at least 3 characters.' })
      if (!/^[A-Z]{3}-\d{3}$/.test(body.branchCode ?? '')) errors.push({ field: 'branchCode', message: 'Branch code must look like CAI-012.' })
      if (!body.password || body.password.length < 10 || !/[A-Z]/.test(body.password) || !/[a-z]/.test(body.password) || !/\d/.test(body.password))
        errors.push({ field: 'password', message: 'Password needs at least 10 characters with upper case, lower case and a digit.' })
      if (errors.length) throw badRequest('Validation failed.', errors)
      if (db.users.some((u) => u.username === username)) throw conflict(`The username "${username}" is already taken.`)
      const user: MockUser = {
        id: `u-${crypto.randomUUID().slice(0, 8)}`,
        username,
        fullName: body.fullName!.trim(),
        role: 'EMPLOYEE',
        branchCode: body.branchCode!,
        enabled: true,
        createdAt: new Date().toISOString(),
        password: body.password!,
      }
      db.users.push(user)
      db.events.unshift(newEvent('USER_PROVISIONED', ref(actor), 'User', username, 'Employee account created with role EMPLOYEE', user.createdAt))
      persist()
      return toSummary(user)
    }),
  ),
]
