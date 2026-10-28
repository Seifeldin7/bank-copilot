import type { RequestPayload, RequestStatus, ValidationIssue, ValidationResult } from '@/api/types'
export { diffRequest } from '@/lib/diff'

const transitions: Record<RequestStatus, RequestStatus[]> = {
  DRAFT: ['PENDING_APPROVAL'],
  PENDING_APPROVAL: ['APPROVED', 'REJECTED'],
  APPROVED: ['EXECUTED'],
  REJECTED: [],
  EXECUTED: [],
}

export function canTransition(from: RequestStatus, to: RequestStatus) {
  return transitions[from].includes(to)
}

const customerRef = /^CUS-\d{6}$/
const last4 = /^\d{4}$/
const branchCode = /^[A-Z]{3}-\d{3}$/
const egyptianMobile = /^01[0125]\d{8}$/

function blank(value: string | undefined) {
  return value === undefined || value.trim() === ''
}

function monthsBetween(from: string, to: string) {
  const a = new Date(from)
  const b = new Date(to)
  return (b.getFullYear() - a.getFullYear()) * 12 + (b.getMonth() - a.getMonth())
}

function daysFromToday(date: string) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((new Date(date).getTime() - today.getTime()) / 86_400_000)
}

export function validateRequest(title: string, description: string, payload: RequestPayload): ValidationResult {
  const issues: ValidationIssue[] = []
  const error = (field: string, message: string) => issues.push({ field, message, severity: 'ERROR' })
  const warning = (field: string, message: string) => issues.push({ field, message, severity: 'WARNING' })

  if (blank(title) || title.trim().length < 5) error('title', 'Title must be at least 5 characters.')
  if (blank(description) || description.trim().length < 10) error('description', 'Description must be at least 10 characters.')
  if (!customerRef.test(payload.customerReference ?? '')) error('payload.customerReference', 'Customer reference must look like CUS-123456.')

  switch (payload.category) {
    case 'CARD_SERVICE':
      if (!last4.test(payload.cardLast4 ?? '')) error('payload.cardLast4', 'Enter exactly the last 4 digits of the card.')
      if (!payload.issueType) error('payload.issueType', 'Select the card issue type.')
      if (!branchCode.test(payload.deliveryBranch ?? '')) error('payload.deliveryBranch', 'Delivery branch must look like CAI-012.')
      if (payload.issueType === 'STOLEN' && !/police/i.test(payload.notes ?? ''))
        warning('payload.notes', 'Stolen cards with disputed transactions need a police report reference within 48 hours.')
      if (payload.issueType === 'LOST' || payload.issueType === 'STOLEN')
        warning('payload.issueType', 'Confirm the card was blocked before requesting a replacement.')
      break
    case 'DIGITAL_BANKING':
      if (!payload.channel) error('payload.channel', 'Select the digital channel.')
      if (!payload.issueType) error('payload.issueType', 'Select the issue type.')
      if (!egyptianMobile.test(payload.registeredMobile ?? '')) error('payload.registeredMobile', 'Registered mobile must be an 11-digit number starting with 010, 011, 012 or 015.')
      if (payload.channel === 'INTERNET_BANKING' && payload.issueType === 'DEVICE_CHANGE')
        error('payload.issueType', 'Device change applies to the mobile app channel only.')
      break
    case 'ACCOUNT_SERVICE':
      if (!last4.test(payload.accountLast4 ?? '')) error('payload.accountLast4', 'Enter exactly the last 4 digits of the account.')
      if (!payload.accountType) error('payload.accountType', 'Select the account type.')
      if (!payload.serviceType) error('payload.serviceType', 'Select the service type.')
      if (payload.serviceType === 'CHEQUE_BOOK' && payload.accountType !== 'CURRENT')
        error('payload.accountType', 'Cheque books can only be issued for current accounts.')
      if (payload.serviceType === 'STATEMENT') {
        if (blank(payload.periodFrom) || blank(payload.periodTo)) {
          error('payload.periodFrom', 'Statement requests need a start and end date.')
        } else if (new Date(payload.periodTo!) < new Date(payload.periodFrom!)) {
          error('payload.periodTo', 'The end date must be after the start date.')
        } else if (monthsBetween(payload.periodFrom!, payload.periodTo!) > 12) {
          warning('payload.periodTo', 'Statements are limited to 12 months per request; the supervisor may need to split or shorten the period.')
        }
      }
      break
    case 'ADDITIONAL_DOCUMENTATION':
      if (!payload.requiredDocuments || payload.requiredDocuments.filter((d) => d.trim()).length === 0)
        error('payload.requiredDocuments', 'List at least one missing document.')
      if (blank(payload.dueDate)) {
        error('payload.dueDate', 'Set a due date for the documents.')
      } else if (daysFromToday(payload.dueDate) < 0) {
        error('payload.dueDate', 'The due date cannot be in the past.')
      } else if (daysFromToday(payload.dueDate) > 14) {
        warning('payload.dueDate', 'Policy requires missing documents within 14 days of the visit.')
      }
      break
  }

  return {
    valid: !issues.some((issue) => issue.severity === 'ERROR'),
    issues,
    checkedAt: new Date().toISOString(),
  }
}

export const simulatedActions: Record<RequestPayload['category'], (payload: RequestPayload) => { action: string; result: Record<string, string> }> = {
  CARD_SERVICE: (payload) => {
    const p = payload as Extract<RequestPayload, { category: 'CARD_SERVICE' }>
    return {
      action: p.issueType === 'LIMIT_INQUIRY' ? 'Simulated card limit inquiry logged' : 'Simulated replacement card order placed',
      result: {
        cardOrderRef: `CO-${Math.floor(100000 + Math.random() * 899999)}`,
        collectionBranch: p.deliveryBranch,
        expectedReadyInWorkingDays: p.issueType === 'LOST' || p.issueType === 'STOLEN' ? '7' : '5',
      },
    }
  },
  DIGITAL_BANKING: (payload) => {
    const p = payload as Extract<RequestPayload, { category: 'DIGITAL_BANKING' }>
    return {
      action: `Simulated ${p.issueType.toLowerCase().replace('_', ' ')} on ${p.channel.toLowerCase().replace('_', ' ')}`,
      result: { ticketRef: `DB-${Math.floor(100000 + Math.random() * 899999)}`, smsSentTo: `${p.registeredMobile.slice(0, 3)}*****${p.registeredMobile.slice(-3)}` },
    }
  },
  ACCOUNT_SERVICE: (payload) => {
    const p = payload as Extract<RequestPayload, { category: 'ACCOUNT_SERVICE' }>
    return {
      action: `Simulated ${p.serviceType.toLowerCase().replace('_', ' ')} for account ending ${p.accountLast4}`,
      result: { serviceRef: `AS-${Math.floor(100000 + Math.random() * 899999)}`, readyBy: new Date(Date.now() + 86_400_000).toISOString().slice(0, 10) },
    }
  },
  ADDITIONAL_DOCUMENTATION: (payload) => {
    const p = payload as Extract<RequestPayload, { category: 'ADDITIONAL_DOCUMENTATION' }>
    return {
      action: 'Simulated documentation reminder scheduled',
      result: { reminderRef: `RM-${Math.floor(100000 + Math.random() * 899999)}`, documents: String(p.requiredDocuments.length), dueDate: p.dueDate },
    }
  },
}
