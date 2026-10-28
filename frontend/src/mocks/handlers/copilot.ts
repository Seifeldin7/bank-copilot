import { http } from 'msw'
import type {
  AskRequest,
  PreparedDraftResponse,
  PrepareRequestRequest,
  RequestCategory,
  RequestPayload,
} from '@/api/types'
import { db, newEvent, persist, ref } from '../db'
import { preparationCall, runCopilot } from '../copilotEngine'
import { passages } from '../knowledge'
import { authenticate, badRequest, conflict, handle, notFound, unprocessable } from '../http'
import { createDraft } from './workflow'

function draftPayload(category: RequestCategory, question: string, branchCode: string): { payload: RequestPayload; missing: string[] } {
  const customerReference = /CUS-\d{6}/i.exec(question)?.[0].toUpperCase() ?? ''
  const last4 = /\b\d{4}\b/.exec(question)?.[0] ?? ''
  const q = question.toLowerCase()
  switch (category) {
    case 'CARD_SERVICE': {
      const issueType = q.includes('stolen') ? 'STOLEN' : q.includes('lost') ? 'LOST' : q.includes('limit') ? 'LIMIT_INQUIRY' : 'REPLACEMENT'
      const payload: RequestPayload = { category, customerReference, cardLast4: last4, issueType, deliveryBranch: branchCode, notes: '' }
      return { payload, missing: [!customerReference && 'payload.customerReference', !last4 && 'payload.cardLast4'].filter(Boolean) as string[] }
    }
    case 'DIGITAL_BANKING': {
      const channel = q.includes('internet') ? 'INTERNET_BANKING' : 'MOBILE_APP'
      const issueType = q.includes('enrol') ? 'ENROLLMENT' : q.includes('device') || q.includes('phone') ? 'DEVICE_CHANGE' : q.includes('password') || q.includes('lock') ? 'PASSWORD_RESET' : 'TRANSACTION_ISSUE'
      const mobile = /\b01[0125]\d{8}\b/.exec(question)?.[0] ?? ''
      const payload: RequestPayload = { category, customerReference, channel, issueType: channel === 'INTERNET_BANKING' && issueType === 'DEVICE_CHANGE' ? 'ENROLLMENT' : issueType, registeredMobile: mobile, notes: '' }
      return { payload, missing: [!customerReference && 'payload.customerReference', !mobile && 'payload.registeredMobile'].filter(Boolean) as string[] }
    }
    case 'ACCOUNT_SERVICE': {
      const serviceType = q.includes('certificate') ? 'BALANCE_CERTIFICATE' : q.includes('dormant') || q.includes('reactivat') ? 'DORMANCY_REACTIVATION' : q.includes('cheque') ? 'CHEQUE_BOOK' : 'STATEMENT'
      const accountType = q.includes('saving') ? 'SAVINGS' : q.includes('dollar') || q.includes('foreign') || q.includes('usd') ? 'FOREIGN_CURRENCY' : 'CURRENT'
      const payload: RequestPayload = { category, customerReference, accountLast4: last4, accountType, serviceType, periodFrom: '', periodTo: '', notes: '' }
      const missing = [!customerReference && 'payload.customerReference', !last4 && 'payload.accountLast4', serviceType === 'STATEMENT' && 'payload.periodFrom', serviceType === 'STATEMENT' && 'payload.periodTo']
      return { payload, missing: missing.filter(Boolean) as string[] }
    }
    case 'ADDITIONAL_DOCUMENTATION': {
      const docs = [q.includes('address') && 'Proof of address issued within 3 months', q.includes('income') && 'Proof of income or occupation', (q.includes('id') || q.includes('passport')) && 'Valid national ID or passport'].filter(Boolean) as string[]
      const due = new Date(Date.now() + 14 * 86_400_000).toISOString().slice(0, 10)
      const payload: RequestPayload = { category, customerReference, relatedReference: '', requiredDocuments: docs, dueDate: due, notes: '' }
      return { payload, missing: [!customerReference && 'payload.customerReference', docs.length === 0 && 'payload.requiredDocuments'].filter(Boolean) as string[] }
    }
  }
}

export const copilotHandlers = [
  http.post(
    '/api/copilot/ask',
    handle(async ({ request }) => {
      const user = authenticate(request)
      const body = (await request.json()) as Partial<AskRequest>
      const question = body.question?.trim() ?? ''
      if (question.length < 3 || question.length > 1000)
        throw badRequest('Invalid question.', [{ field: 'question', message: 'Ask a question between 3 and 1000 characters.' }])
      const { response, modelCalls } = runCopilot(question, db.documents, [...passages, ...db.uploadedPassages])
      db.runs.push({ response, user: ref(user), modelCalls })
      db.events.unshift(newEvent(response.refused ? 'COPILOT_REFUSED' : 'COPILOT_ANSWERED', ref(user), 'CopilotRun', response.runId, question, response.createdAt, response.runId))
      persist()
      return response
    }, [700, 1400]),
  ),

  http.post(
    '/api/copilot/prepare-request',
    handle(async ({ request }) => {
      const user = authenticate(request)
      const body = (await request.json()) as Partial<PrepareRequestRequest>
      const run = db.runs.find((r) => r.response.runId === body.runId && r.user.id === user.id)
      if (!run) throw notFound('Copilot run not found.')
      if (run.response.refused) throw conflict('A service request cannot be prepared from a refused answer.')
      const category = run.response.suggestedCategory
      if (!category) throw unprocessable('No service request category applies to this answer.')
      if (db.requests.some((r) => r.sourceRunId === run.response.runId && r.requester.id === user.id))
        throw conflict('A draft was already prepared from this answer.')
      const { payload, missing } = draftPayload(category, run.response.question, user.branchCode)
      const firstSentence = run.response.answer?.split(/(?<=\.)\s/)[0]?.replace(/\s*\[\d+\]/g, '') ?? ''
      const draft = createDraft(
        user,
        {
          category,
          title: run.response.question.replace(/\?$/, '').slice(0, 110),
          description: `Prepared by the copilot from run ${run.response.runId}. Guidance: ${firstSentence}`.slice(0, 1000),
          payload,
        },
        run.response.runId,
        run.response.runId,
        'Draft prepared by the Request Preparation Agent',
      )
      const { step, call } = preparationCall(run.response.steps.length + 1, new Date())
      run.response.steps.push(step)
      run.modelCalls.push(call)
      run.response.usage.promptTokens += step.promptTokens
      run.response.usage.completionTokens += step.completionTokens
      run.response.usage.totalTokens += step.promptTokens + step.completionTokens
      run.response.usage.estimatedCostUsd = Number((run.response.usage.estimatedCostUsd + step.estimatedCostUsd).toFixed(6))
      persist()
      const response: PreparedDraftResponse = { request: draft, missingFields: missing }
      return response
    }, [600, 1100]),
  ),
]
