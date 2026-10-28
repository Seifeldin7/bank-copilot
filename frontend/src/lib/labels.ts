import type {
  AccountServiceType,
  AccountType,
  AgentName,
  CardIssueType,
  DecisionType,
  DigitalChannel,
  DigitalIssueType,
  DocumentStatus,
  HistoryAction,
  IngestionOutcome,
  RequestCategory,
  RequestStatus,
  StepStatus,
} from '@/api/types'

export type Tone = 'neutral' | 'blue' | 'amber' | 'green' | 'red' | 'purple' | 'brand'

export const statusLabels: Record<RequestStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  EXECUTED: 'Executed',
}

export const statusTones: Record<RequestStatus, Tone> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'amber',
  APPROVED: 'blue',
  REJECTED: 'red',
  EXECUTED: 'green',
}

export const categoryLabels: Record<RequestCategory, string> = {
  CARD_SERVICE: 'Card service',
  DIGITAL_BANKING: 'Digital banking',
  ACCOUNT_SERVICE: 'Account service',
  ADDITIONAL_DOCUMENTATION: 'Additional documentation',
}

export const categoryDescriptions: Record<RequestCategory, string> = {
  CARD_SERVICE: 'Replacements, lost or stolen cards, limit inquiries',
  DIGITAL_BANKING: 'Internet banking and mobile app support',
  ACCOUNT_SERVICE: 'Statements, certificates, cheque books, reactivation',
  ADDITIONAL_DOCUMENTATION: 'Missing documents the customer must provide',
}

export const cardIssueLabels: Record<CardIssueType, string> = {
  LOST: 'Lost card',
  STOLEN: 'Stolen card',
  REPLACEMENT: 'Replacement (damaged / expiring)',
  LIMIT_INQUIRY: 'Limit inquiry',
}

export const channelLabels: Record<DigitalChannel, string> = {
  INTERNET_BANKING: 'Internet banking',
  MOBILE_APP: 'Mobile app',
}

export const digitalIssueLabels: Record<DigitalIssueType, string> = {
  ENROLLMENT: 'Enrollment',
  PASSWORD_RESET: 'Password reset / unlock',
  DEVICE_CHANGE: 'Device change',
  TRANSACTION_ISSUE: 'Transaction issue',
}

export const accountTypeLabels: Record<AccountType, string> = {
  CURRENT: 'Current',
  SAVINGS: 'Savings',
  FOREIGN_CURRENCY: 'Foreign currency',
}

export const accountServiceLabels: Record<AccountServiceType, string> = {
  STATEMENT: 'Account statement',
  BALANCE_CERTIFICATE: 'Balance certificate',
  DORMANCY_REACTIVATION: 'Dormant account reactivation',
  CHEQUE_BOOK: 'Cheque book',
}

export const decisionLabels: Record<DecisionType, string> = {
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  EDITED_APPROVED: 'Edited & approved',
}

export const historyLabels: Record<HistoryAction, string> = {
  CREATED: 'Draft created',
  UPDATED: 'Draft updated',
  SUBMITTED: 'Submitted for approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  EDITED_AND_APPROVED: 'Edited & approved',
  EXECUTED: 'Simulated action executed',
  EXECUTION_RETRY: 'Repeated execution blocked',
}

export const historyTones: Record<HistoryAction, Tone> = {
  CREATED: 'neutral',
  UPDATED: 'neutral',
  SUBMITTED: 'amber',
  APPROVED: 'blue',
  REJECTED: 'red',
  EDITED_AND_APPROVED: 'purple',
  EXECUTED: 'green',
  EXECUTION_RETRY: 'amber',
}

export const agentLabels: Record<AgentName, string> = {
  ORCHESTRATOR: 'Orchestrator',
  KNOWLEDGE_RETRIEVAL: 'Knowledge Retrieval Agent',
  POLICY_VALIDATION: 'Policy Validation Agent',
  ANSWER_GENERATION: 'Grounded Answer Generation',
  REQUEST_PREPARATION: 'Request Preparation Agent',
  FALLBACK_ANSWER: 'Fallback Retrieval & Answer',
}

export const stepTones: Record<StepStatus, Tone> = {
  SUCCEEDED: 'green',
  FAILED: 'red',
  TIMED_OUT: 'red',
  SKIPPED: 'neutral',
}

export const documentStatusTones: Record<DocumentStatus, Tone> = {
  ACTIVE: 'green',
  SUPERSEDED: 'amber',
  RETIRED: 'neutral',
}

export const outcomeLabels: Record<IngestionOutcome, string> = {
  INGESTED: 'Ingested',
  NEW_VERSION: 'New version ingested',
  DUPLICATE: 'Duplicate skipped',
}

export const outcomeTones: Record<IngestionOutcome, Tone> = {
  INGESTED: 'green',
  NEW_VERSION: 'blue',
  DUPLICATE: 'amber',
}

const fieldLabels: Record<string, string> = {
  title: 'Title',
  description: 'Description',
  category: 'Category',
  customerReference: 'Customer reference',
  cardLast4: 'Card last 4 digits',
  issueType: 'Issue type',
  deliveryBranch: 'Delivery branch',
  channel: 'Channel',
  registeredMobile: 'Registered mobile',
  accountLast4: 'Account last 4 digits',
  accountType: 'Account type',
  serviceType: 'Service type',
  periodFrom: 'Period from',
  periodTo: 'Period to',
  relatedReference: 'Related reference',
  requiredDocuments: 'Required documents',
  dueDate: 'Due date',
  notes: 'Notes',
}

export function fieldLabel(path: string) {
  const key = path.replace(/^payload\./, '')
  return fieldLabels[key] ?? key
}
