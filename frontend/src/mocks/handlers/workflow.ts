import { http } from 'msw'
import type {
  ApproveRequest,
  CreateRequestRequest,
  EditApproveRequest,
  ExecuteResponse,
  HistoryEntry,
  RejectRequest,
  RequestCategory,
  RequestStatus,
  ServiceRequest,
  UpdateRequestRequest,
} from '@/api/types'
import { buildExecution, db, newEvent, newHistory, persist, ref, type MockUser } from '../db'
import { canTransition, diffRequest, validateRequest } from '../rules'
import { authenticate, badRequest, conflict, forbidden, handle, notFound, param, unprocessable } from '../http'

const categories: RequestCategory[] = ['CARD_SERVICE', 'DIGITAL_BANKING', 'ACCOUNT_SERVICE', 'ADDITIONAL_DOCUMENTATION']

function findVisible(user: MockUser, id: string) {
  const request = db.requests.find((r) => r.id === id)
  if (!request) throw notFound('Service request not found.')
  if (user.role !== 'SUPERVISOR' && request.requester.id !== user.id) throw notFound('Service request not found.')
  return request
}

function findForDecision(user: MockUser, id: string) {
  const request = db.requests.find((r) => r.id === id)
  if (!request) throw notFound('Service request not found.')
  if (request.requester.id === user.id) throw forbidden('Supervisors cannot approve, reject or edit their own requests.')
  return request
}

function requireOwner(user: MockUser, request: ServiceRequest) {
  if (request.requester.id !== user.id) throw forbidden('Only the employee who created this request can change it.')
}

function requireVersion(request: ServiceRequest, version: number | undefined) {
  if (version !== undefined && version !== request.version)
    throw conflict('This request was changed by someone else. Reload it and try again.')
}

function move(request: ServiceRequest, to: RequestStatus) {
  if (!canTransition(request.status, to))
    throw conflict(`A request in status ${request.status} cannot move to ${to}.`)
  request.status = to
  request.version += 1
  request.updatedAt = new Date().toISOString()
}

function record(request: ServiceRequest, entry: HistoryEntry) {
  db.history[request.id] = [...(db.history[request.id] ?? []), entry]
  db.events.unshift(
    newEvent(`REQUEST_${entry.action}`, entry.actor, 'ServiceRequest', request.reference, entry.reason ?? `${entry.fromStatus ?? '∅'} → ${entry.toStatus ?? '∅'}`, entry.at, entry.runId),
  )
}

function checkBody(body: Partial<CreateRequestRequest>) {
  if (!body.category || !categories.includes(body.category)) throw badRequest('Unknown request category.', [{ field: 'category', message: 'Select a valid category.' }])
  if (!body.payload || body.payload.category !== body.category)
    throw badRequest('Payload does not match the category.', [{ field: 'payload', message: 'Payload category must match the request category.' }])
  if (typeof body.title !== 'string' || body.title.length > 120) throw badRequest('Invalid title.', [{ field: 'title', message: 'Title is required and at most 120 characters.' }])
  if (typeof body.description !== 'string' || body.description.length > 1000)
    throw badRequest('Invalid description.', [{ field: 'description', message: 'Description is required and at most 1000 characters.' }])
}

function issuesAsErrors(result: ReturnType<typeof validateRequest>) {
  return result.issues.filter((i) => i.severity === 'ERROR').map((i) => ({ field: i.field, message: i.message }))
}

function execute(request: ServiceRequest, actor: MockUser, runId: string) {
  move(request, 'EXECUTED')
  const at = new Date().toISOString()
  request.execution = buildExecution(request, ref(actor), at)
  request.executedAt = at
  record(request, newHistory('EXECUTED', ref(actor), 'APPROVED', 'EXECUTED', at, { runId }))
}

function nextReference() {
  db.counters.request += 1
  return {
    id: `r-${String(db.counters.request).padStart(3, '0')}`,
    reference: `SR-2026-${String(104 + db.counters.request).padStart(6, '0')}`,
  }
}

export function createDraft(owner: MockUser, body: CreateRequestRequest, runId: string, sourceRunId: string | null, reason?: string) {
  const now = new Date().toISOString()
  const { id, reference } = nextReference()
  const request: ServiceRequest = {
    id,
    reference,
    category: body.category,
    status: 'DRAFT',
    title: body.title.trim(),
    description: body.description.trim(),
    payload: body.payload,
    requester: ref(owner),
    validation: validateRequest(body.title, body.description, body.payload),
    decision: null,
    execution: null,
    sourceRunId,
    version: 1,
    createdAt: now,
    updatedAt: now,
    submittedAt: null,
    decidedAt: null,
    executedAt: null,
  }
  db.requests.push(request)
  record(request, newHistory('CREATED', ref(owner), null, 'DRAFT', now, { runId, reason }))
  return request
}

export const workflowHandlers = [
  http.get(
    '/api/requests',
    handle(({ request }) => {
      const user = authenticate(request)
      const url = new URL(request.url)
      const scope = url.searchParams.get('scope') ?? 'mine'
      if (scope === 'all' && user.role !== 'SUPERVISOR') throw forbidden('Only supervisors can list all requests.')
      const status = url.searchParams.get('status')
      const category = url.searchParams.get('category')
      const requesterId = url.searchParams.get('requesterId')
      return db.requests
        .filter((r) => (scope === 'all' ? true : r.requester.id === user.id))
        .filter((r) => !status || r.status === status)
        .filter((r) => !category || r.category === category)
        .filter((r) => !requesterId || r.requester.id === requesterId)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    }),
  ),

  http.get(
    '/api/requests/:id',
    handle(({ request, params }) => findVisible(authenticate(request), param(params, 'id'))),
  ),

  http.get(
    '/api/requests/:id/history',
    handle(({ request, params }) => {
      const found = findVisible(authenticate(request), param(params, 'id'))
      return db.history[found.id] ?? []
    }),
  ),

  http.post(
    '/api/requests',
    handle(async ({ request }) => {
      const user = authenticate(request)
      const body = (await request.json()) as CreateRequestRequest
      checkBody(body)
      const created = createDraft(user, body, request.headers.get('X-Request-Id') ?? '', null)
      persist()
      return created
    }),
  ),

  http.put(
    '/api/requests/:id',
    handle(async ({ request, params }) => {
      const user = authenticate(request)
      const found = findVisible(user, param(params, 'id'))
      requireOwner(user, found)
      if (found.status !== 'DRAFT') throw conflict('Only draft requests can be edited.')
      const body = (await request.json()) as UpdateRequestRequest
      checkBody({ ...body, category: found.category })
      requireVersion(found, body.version)
      const changes = diffRequest(found, body)
      found.title = body.title.trim()
      found.description = body.description.trim()
      found.payload = body.payload
      found.validation = validateRequest(found.title, found.description, found.payload)
      found.version += 1
      found.updatedAt = new Date().toISOString()
      if (changes.length) record(found, newHistory('UPDATED', ref(user), 'DRAFT', 'DRAFT', found.updatedAt, { changes, runId: request.headers.get('X-Request-Id') ?? undefined }))
      persist()
      return found
    }),
  ),

  http.post(
    '/api/requests/:id/submit',
    handle(async ({ request, params }) => {
      const user = authenticate(request)
      const found = findVisible(user, param(params, 'id'))
      requireOwner(user, found)
      const body = (await request.json().catch(() => ({}))) as { version?: number }
      requireVersion(found, body.version)
      if (found.status !== 'DRAFT') throw conflict(`A request in status ${found.status} cannot be submitted.`)
      const validation = validateRequest(found.title, found.description, found.payload)
      found.validation = validation
      if (!validation.valid) {
        persist()
        throw unprocessable('The request has validation errors. Fix them before submitting.', issuesAsErrors(validation))
      }
      move(found, 'PENDING_APPROVAL')
      found.submittedAt = found.updatedAt
      record(found, newHistory('SUBMITTED', ref(user), 'DRAFT', 'PENDING_APPROVAL', found.updatedAt, { runId: request.headers.get('X-Request-Id') ?? undefined }))
      persist()
      return found
    }),
  ),

  http.get(
    '/api/approvals/queue',
    handle(({ request }) => {
      authenticate(request, 'SUPERVISOR')
      return db.requests
        .filter((r) => r.status === 'PENDING_APPROVAL')
        .sort((a, b) => (a.submittedAt ?? '').localeCompare(b.submittedAt ?? ''))
    }),
  ),

  http.post(
    '/api/approvals/:id/approve',
    handle(async ({ request, params }) => {
      const user = authenticate(request, 'SUPERVISOR')
      const found = findForDecision(user, param(params, 'id'))
      const body = (await request.json().catch(() => ({}))) as Partial<ApproveRequest>
      if (found.status !== 'PENDING_APPROVAL') throw conflict(`A request in status ${found.status} cannot be approved.`)
      requireVersion(found, body.version)
      const validation = validateRequest(found.title, found.description, found.payload)
      found.validation = validation
      if (!validation.valid) throw unprocessable('The request no longer passes validation. Use Edit & approve or reject it.', issuesAsErrors(validation))
      const runId = request.headers.get('X-Request-Id') ?? ''
      move(found, 'APPROVED')
      found.decidedAt = found.updatedAt
      found.decision = { decision: 'APPROVED', supervisor: ref(user), reason: body.comment?.trim() || null, changes: [], decidedAt: found.updatedAt }
      record(found, newHistory('APPROVED', ref(user), 'PENDING_APPROVAL', 'APPROVED', found.updatedAt, { runId, reason: body.comment?.trim() || undefined }))
      execute(found, user, runId)
      persist()
      return found
    }),
  ),

  http.post(
    '/api/approvals/:id/reject',
    handle(async ({ request, params }) => {
      const user = authenticate(request, 'SUPERVISOR')
      const found = findForDecision(user, param(params, 'id'))
      const body = (await request.json()) as Partial<RejectRequest>
      if (!body.reason || body.reason.trim().length < 10)
        throw badRequest('A rejection reason is required.', [{ field: 'reason', message: 'Give a reason of at least 10 characters.' }])
      if (found.status !== 'PENDING_APPROVAL') throw conflict(`A request in status ${found.status} cannot be rejected.`)
      requireVersion(found, body.version)
      move(found, 'REJECTED')
      found.decidedAt = found.updatedAt
      found.decision = { decision: 'REJECTED', supervisor: ref(user), reason: body.reason.trim(), changes: [], decidedAt: found.updatedAt }
      record(found, newHistory('REJECTED', ref(user), 'PENDING_APPROVAL', 'REJECTED', found.updatedAt, { reason: body.reason.trim(), runId: request.headers.get('X-Request-Id') ?? undefined }))
      persist()
      return found
    }),
  ),

  http.post(
    '/api/approvals/:id/edit-approve',
    handle(async ({ request, params }) => {
      const user = authenticate(request, 'SUPERVISOR')
      const found = findForDecision(user, param(params, 'id'))
      const body = (await request.json()) as EditApproveRequest
      if (!body.reason || body.reason.trim().length < 10)
        throw badRequest('A reason for the edit is required.', [{ field: 'reason', message: 'Explain the change in at least 10 characters.' }])
      checkBody({ ...body, category: found.category })
      if (found.status !== 'PENDING_APPROVAL') throw conflict(`A request in status ${found.status} cannot be approved.`)
      requireVersion(found, body.version)
      const changes = diffRequest(found, body)
      if (changes.length === 0) throw badRequest('No changes were made. Use Approve instead.')
      const validation = validateRequest(body.title, body.description, body.payload)
      if (!validation.valid) throw unprocessable('The edited request has validation errors.', issuesAsErrors(validation))
      const runId = request.headers.get('X-Request-Id') ?? ''
      found.title = body.title.trim()
      found.description = body.description.trim()
      found.payload = body.payload
      found.validation = validation
      move(found, 'APPROVED')
      found.decidedAt = found.updatedAt
      found.decision = { decision: 'EDITED_APPROVED', supervisor: ref(user), reason: body.reason.trim(), changes, decidedAt: found.updatedAt }
      record(found, newHistory('EDITED_AND_APPROVED', ref(user), 'PENDING_APPROVAL', 'APPROVED', found.updatedAt, { changes, reason: body.reason.trim(), runId }))
      execute(found, user, runId)
      persist()
      return found
    }),
  ),

  http.post(
    '/api/approvals/:id/execute',
    handle(({ request, params }) => {
      const user = authenticate(request, 'SUPERVISOR')
      const found = db.requests.find((r) => r.id === param(params, 'id'))
      if (!found) throw notFound('Service request not found.')
      const runId = request.headers.get('X-Request-Id') ?? ''
      if (found.status === 'EXECUTED' && found.execution) {
        record(found, newHistory('EXECUTION_RETRY', ref(user), 'EXECUTED', 'EXECUTED', new Date().toISOString(), { runId, reason: `Repeated execution blocked; returned existing record ${found.execution.id}` }))
        persist()
        const response: ExecuteResponse = { execution: found.execution, alreadyExecuted: true }
        return response
      }
      if (found.status !== 'APPROVED') throw conflict('Only approved requests can be executed.')
      execute(found, user, runId)
      persist()
      const response: ExecuteResponse = { execution: found.execution!, alreadyExecuted: false }
      return response
    }),
  ),
]
