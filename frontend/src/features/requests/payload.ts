import type { RequestPayload } from '@/api/types'
import { formatDate, formatValue } from '@/lib/format'
import { accountServiceLabels, accountTypeLabels, cardIssueLabels, channelLabels, digitalIssueLabels } from '@/lib/labels'

export function payloadItems(payload: RequestPayload): { label: string; value: string; full?: boolean }[] {
  const common = { label: 'Customer reference', value: formatValue(payload.customerReference) }
  switch (payload.category) {
    case 'CARD_SERVICE':
      return [
        common,
        { label: 'Issue type', value: cardIssueLabels[payload.issueType] ?? '—' },
        { label: 'Card', value: payload.cardLast4 ? `•••• ${payload.cardLast4}` : '—' },
        { label: 'Delivery branch', value: formatValue(payload.deliveryBranch) },
        { label: 'Notes', value: formatValue(payload.notes), full: true },
      ]
    case 'DIGITAL_BANKING':
      return [
        common,
        { label: 'Channel', value: channelLabels[payload.channel] ?? '—' },
        { label: 'Issue type', value: digitalIssueLabels[payload.issueType] ?? '—' },
        { label: 'Registered mobile', value: formatValue(payload.registeredMobile) },
        { label: 'Notes', value: formatValue(payload.notes), full: true },
      ]
    case 'ACCOUNT_SERVICE':
      return [
        common,
        { label: 'Service', value: accountServiceLabels[payload.serviceType] ?? '—' },
        { label: 'Account', value: payload.accountLast4 ? `•••• ${payload.accountLast4}` : '—' },
        { label: 'Account type', value: accountTypeLabels[payload.accountType] ?? '—' },
        ...(payload.serviceType === 'STATEMENT'
          ? [{ label: 'Statement period', value: `${formatDate(payload.periodFrom || null)} → ${formatDate(payload.periodTo || null)}` }]
          : []),
        { label: 'Notes', value: formatValue(payload.notes), full: true },
      ]
    case 'ADDITIONAL_DOCUMENTATION':
      return [
        common,
        { label: 'Related reference', value: formatValue(payload.relatedReference) },
        { label: 'Due date', value: formatDate(payload.dueDate || null) },
        { label: 'Required documents', value: formatValue(payload.requiredDocuments), full: true },
        { label: 'Notes', value: formatValue(payload.notes), full: true },
      ]
  }
}
