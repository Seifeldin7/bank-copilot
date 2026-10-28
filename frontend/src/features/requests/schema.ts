import { z } from 'zod'
import type { RequestCategory, RequestPayload, ServiceRequest } from '@/api/types'

const customerReference = z.string().trim().regex(/^CUS-\d{6}$/, 'Use the format CUS-123456')
const notes = z.string().max(500, 'Keep notes under 500 characters').optional()

const cardPayload = z.object({
  category: z.literal('CARD_SERVICE'),
  customerReference,
  cardLast4: z.string().trim().regex(/^\d{4}$/, 'Enter exactly the last 4 digits'),
  issueType: z.enum(['LOST', 'STOLEN', 'REPLACEMENT', 'LIMIT_INQUIRY']),
  deliveryBranch: z.string().trim().regex(/^[A-Z]{3}-\d{3}$/, 'Use the format CAI-012'),
  notes,
})

const digitalPayload = z
  .object({
    category: z.literal('DIGITAL_BANKING'),
    customerReference,
    channel: z.enum(['INTERNET_BANKING', 'MOBILE_APP']),
    issueType: z.enum(['ENROLLMENT', 'PASSWORD_RESET', 'DEVICE_CHANGE', 'TRANSACTION_ISSUE']),
    registeredMobile: z.string().trim().regex(/^01[0125]\d{8}$/, 'Enter an 11-digit mobile starting with 010, 011, 012 or 015'),
    notes,
  })
  .superRefine((value, ctx) => {
    if (value.channel === 'INTERNET_BANKING' && value.issueType === 'DEVICE_CHANGE')
      ctx.addIssue({ code: 'custom', path: ['issueType'], message: 'Device change applies to the mobile app only' })
  })

const accountPayload = z
  .object({
    category: z.literal('ACCOUNT_SERVICE'),
    customerReference,
    accountLast4: z.string().trim().regex(/^\d{4}$/, 'Enter exactly the last 4 digits'),
    accountType: z.enum(['CURRENT', 'SAVINGS', 'FOREIGN_CURRENCY']),
    serviceType: z.enum(['STATEMENT', 'BALANCE_CERTIFICATE', 'DORMANCY_REACTIVATION', 'CHEQUE_BOOK']),
    periodFrom: z.string().optional(),
    periodTo: z.string().optional(),
    notes,
  })
  .superRefine((value, ctx) => {
    if (value.serviceType === 'STATEMENT') {
      if (!value.periodFrom) ctx.addIssue({ code: 'custom', path: ['periodFrom'], message: 'Choose the start date' })
      if (!value.periodTo) ctx.addIssue({ code: 'custom', path: ['periodTo'], message: 'Choose the end date' })
      if (value.periodFrom && value.periodTo && value.periodTo < value.periodFrom)
        ctx.addIssue({ code: 'custom', path: ['periodTo'], message: 'End date must be after the start date' })
    }
    if (value.serviceType === 'CHEQUE_BOOK' && value.accountType !== 'CURRENT')
      ctx.addIssue({ code: 'custom', path: ['accountType'], message: 'Cheque books are only issued for current accounts' })
  })

const documentationPayload = z.object({
  category: z.literal('ADDITIONAL_DOCUMENTATION'),
  customerReference,
  relatedReference: z.string().trim().optional(),
  requiredDocuments: z.array(z.string()).refine((docs) => docs.some((d) => d.trim()), 'List at least one document'),
  dueDate: z.string().min(1, 'Choose a due date'),
  notes,
})

export const requestFormSchema = z.object({
  category: z.enum(['CARD_SERVICE', 'DIGITAL_BANKING', 'ACCOUNT_SERVICE', 'ADDITIONAL_DOCUMENTATION']),
  title: z.string().trim().min(5, 'Title must be at least 5 characters').max(120, 'Keep the title under 120 characters'),
  description: z.string().trim().min(10, 'Describe the request in at least 10 characters').max(1000, 'Keep the description under 1000 characters'),
  payload: z.discriminatedUnion('category', [cardPayload, digitalPayload, accountPayload, documentationPayload]),
})

export type RequestFormValues = z.infer<typeof requestFormSchema>

export function emptyPayload(category: RequestCategory, branchCode = ''): RequestPayload {
  switch (category) {
    case 'CARD_SERVICE':
      return { category, customerReference: '', cardLast4: '', issueType: 'REPLACEMENT', deliveryBranch: branchCode, notes: '' }
    case 'DIGITAL_BANKING':
      return { category, customerReference: '', channel: 'MOBILE_APP', issueType: 'PASSWORD_RESET', registeredMobile: '', notes: '' }
    case 'ACCOUNT_SERVICE':
      return { category, customerReference: '', accountLast4: '', accountType: 'CURRENT', serviceType: 'STATEMENT', periodFrom: '', periodTo: '', notes: '' }
    case 'ADDITIONAL_DOCUMENTATION':
      return {
        category,
        customerReference: '',
        relatedReference: '',
        requiredDocuments: [],
        dueDate: new Date(Date.now() + 14 * 86_400_000).toISOString().slice(0, 10),
        notes: '',
      }
  }
}

export function emptyForm(category: RequestCategory, branchCode: string): RequestFormValues {
  return { category, title: '', description: '', payload: emptyPayload(category, branchCode) as RequestFormValues['payload'] }
}

export function toFormValues(request: Pick<ServiceRequest, 'category' | 'title' | 'description' | 'payload'>): RequestFormValues {
  return {
    category: request.category,
    title: request.title,
    description: request.description,
    payload: { ...emptyPayload(request.category), ...request.payload } as RequestFormValues['payload'],
  }
}

export function cleanValues(values: RequestFormValues): { category: RequestCategory; title: string; description: string; payload: RequestPayload } {
  const payload = { ...values.payload } as RequestPayload
  if (payload.category === 'ADDITIONAL_DOCUMENTATION') {
    payload.requiredDocuments = payload.requiredDocuments.map((d) => d.trim()).filter(Boolean)
  }
  if (payload.category === 'ACCOUNT_SERVICE' && payload.serviceType !== 'STATEMENT') {
    payload.periodFrom = ''
    payload.periodTo = ''
  }
  for (const [key, value] of Object.entries(payload)) {
    if (typeof value === 'string') (payload as unknown as Record<string, unknown>)[key] = value.trim()
  }
  return { category: values.category, title: values.title.trim(), description: values.description.trim(), payload }
}
