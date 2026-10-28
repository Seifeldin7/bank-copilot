export type Role = 'EMPLOYEE' | 'SUPERVISOR'

export interface UserRef {
  id: string
  username: string
  fullName: string
}

export interface UserSummary extends UserRef {
  role: Role
  branchCode: string
  enabled: boolean
  createdAt: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: UserSummary
}

export interface CreateUserRequest {
  username: string
  fullName: string
  branchCode: string
  password: string
}

export interface ProblemFieldError {
  field: string
  message: string
}

export interface ProblemDetail {
  type?: string
  title: string
  status: number
  detail?: string
  instance?: string
  runId?: string
  errors?: ProblemFieldError[]
}

export type CopilotMode = 'ORCHESTRATED' | 'FALLBACK'

export type AgentName =
  | 'ORCHESTRATOR'
  | 'KNOWLEDGE_RETRIEVAL'
  | 'POLICY_VALIDATION'
  | 'ANSWER_GENERATION'
  | 'REQUEST_PREPARATION'
  | 'FALLBACK_ANSWER'

export type StepStatus = 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT' | 'SKIPPED'

export interface Citation {
  index: number
  chunkId: string
  documentId: string
  documentTitle: string
  version: number
  page: number | null
  section: string | null
  snippet: string
  score: number
}

export interface ValidationVerdict {
  sufficient: boolean
  missingInformation: string[]
  unsupportedAssumptions: string[]
  confidence: number
}

export interface UsageTotals {
  promptTokens: number
  completionTokens: number
  totalTokens: number
  estimatedCostUsd: number
}

export interface AgentStep {
  step: number
  agent: AgentName
  status: StepStatus
  summary: string
  iterations: number
  durationMs: number
  promptTokens: number
  completionTokens: number
  estimatedCostUsd: number
  startedAt: string
}

export interface AskRequest {
  question: string
}

export interface AskResponse {
  runId: string
  question: string
  answer: string | null
  refused: boolean
  refusalReason: string | null
  mode: CopilotMode
  citations: Citation[]
  verdict: ValidationVerdict | null
  suggestedCategory: RequestCategory | null
  steps: AgentStep[]
  usage: UsageTotals
  latencyMs: number
  createdAt: string
}

export interface PrepareRequestRequest {
  runId: string
}

export interface PreparedDraftResponse {
  request: ServiceRequest
  missingFields: string[]
}

export type RequestCategory =
  | 'CARD_SERVICE'
  | 'DIGITAL_BANKING'
  | 'ACCOUNT_SERVICE'
  | 'ADDITIONAL_DOCUMENTATION'

export type RequestStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXECUTED'

export type CardIssueType = 'LOST' | 'STOLEN' | 'REPLACEMENT' | 'LIMIT_INQUIRY'
export type DigitalChannel = 'INTERNET_BANKING' | 'MOBILE_APP'
export type DigitalIssueType = 'ENROLLMENT' | 'PASSWORD_RESET' | 'DEVICE_CHANGE' | 'TRANSACTION_ISSUE'
export type AccountType = 'CURRENT' | 'SAVINGS' | 'FOREIGN_CURRENCY'
export type AccountServiceType = 'STATEMENT' | 'BALANCE_CERTIFICATE' | 'DORMANCY_REACTIVATION' | 'CHEQUE_BOOK'

export interface CardServicePayload {
  category: 'CARD_SERVICE'
  customerReference: string
  cardLast4: string
  issueType: CardIssueType
  deliveryBranch: string
  notes?: string
}

export interface DigitalBankingPayload {
  category: 'DIGITAL_BANKING'
  customerReference: string
  channel: DigitalChannel
  issueType: DigitalIssueType
  registeredMobile: string
  notes?: string
}

export interface AccountServicePayload {
  category: 'ACCOUNT_SERVICE'
  customerReference: string
  accountLast4: string
  accountType: AccountType
  serviceType: AccountServiceType
  periodFrom?: string
  periodTo?: string
  notes?: string
}

export interface AdditionalDocumentationPayload {
  category: 'ADDITIONAL_DOCUMENTATION'
  customerReference: string
  relatedReference?: string
  requiredDocuments: string[]
  dueDate: string
  notes?: string
}

export type RequestPayload =
  | CardServicePayload
  | DigitalBankingPayload
  | AccountServicePayload
  | AdditionalDocumentationPayload

export interface ValidationIssue {
  field: string
  message: string
  severity: 'ERROR' | 'WARNING'
}

export interface ValidationResult {
  valid: boolean
  issues: ValidationIssue[]
  checkedAt: string
}

export interface FieldChange {
  field: string
  from: unknown
  to: unknown
}

export type DecisionType = 'APPROVED' | 'REJECTED' | 'EDITED_APPROVED'

export interface ApprovalDecision {
  decision: DecisionType
  supervisor: UserRef
  reason: string | null
  changes: FieldChange[]
  decidedAt: string
}

export interface ExecutionRecord {
  id: string
  outcome: 'SUCCEEDED' | 'FAILED'
  simulatedAction: string
  result: Record<string, string>
  executedBy: UserRef
  executedAt: string
}

export interface ServiceRequest {
  id: string
  reference: string
  category: RequestCategory
  status: RequestStatus
  title: string
  description: string
  payload: RequestPayload
  requester: UserRef
  validation: ValidationResult | null
  decision: ApprovalDecision | null
  execution: ExecutionRecord | null
  sourceRunId: string | null
  version: number
  createdAt: string
  updatedAt: string
  submittedAt: string | null
  decidedAt: string | null
  executedAt: string | null
}

export type HistoryAction =
  | 'CREATED'
  | 'UPDATED'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'EDITED_AND_APPROVED'
  | 'EXECUTED'
  | 'EXECUTION_RETRY'

export interface HistoryEntry {
  id: string
  action: HistoryAction
  actor: UserRef
  fromStatus: RequestStatus | null
  toStatus: RequestStatus | null
  changes: FieldChange[]
  reason: string | null
  runId: string
  at: string
}

export interface CreateRequestRequest {
  category: RequestCategory
  title: string
  description: string
  payload: RequestPayload
}

export interface UpdateRequestRequest {
  title: string
  description: string
  payload: RequestPayload
  version: number
}

export interface ApproveRequest {
  comment?: string
  version: number
}

export interface RejectRequest {
  reason: string
  version: number
}

export interface EditApproveRequest {
  title: string
  description: string
  payload: RequestPayload
  reason: string
  version: number
}

export interface ExecuteResponse {
  execution: ExecutionRecord
  alreadyExecuted: boolean
}

export interface RequestListQuery {
  scope?: 'mine' | 'all'
  status?: RequestStatus
  category?: RequestCategory
  requesterId?: string
}

export type DocumentFormat = 'PDF' | 'MARKDOWN'
export type DocumentStatus = 'ACTIVE' | 'SUPERSEDED' | 'RETIRED'
export type DocumentSource = 'CORPUS' | 'UPLOAD'

export interface DocumentSummary {
  id: string
  title: string
  fileName: string
  format: DocumentFormat
  category: string
  version: number
  pageCount: number
  chunkCount: number
  suspiciousChunks: number
  status: DocumentStatus
  source: DocumentSource
  contentSha256: string
  ingestedBy: UserRef | null
  ingestedAt: string
}

export type IngestionStage = 'FINGERPRINT' | 'EXTRACT' | 'CLEAN' | 'CHUNK' | 'SCREEN' | 'EMBED' | 'INDEX'

export interface StageLog {
  stage: IngestionStage
  status: 'SUCCEEDED' | 'SKIPPED' | 'FAILED'
  durationMs: number
  detail: string
}

export type IngestionOutcome = 'INGESTED' | 'NEW_VERSION' | 'DUPLICATE'

export interface IngestionReport {
  documentId: string
  title: string
  outcome: IngestionOutcome
  version: number
  pages: number
  chunks: number
  suspiciousChunks: number
  stages: StageLog[]
}

export interface RunSummary {
  runId: string
  user: UserRef
  question: string
  mode: CopilotMode
  refused: boolean
  latencyMs: number
  totalTokens: number
  estimatedCostUsd: number
  createdAt: string
}

export interface ModelCall {
  id: string
  step: number
  agent: AgentName
  provider: string
  model: string
  promptTokens: number
  completionTokens: number
  estimatedCostUsd: number
  latencyMs: number
  at: string
}

export interface AuditEvent {
  id: string
  action: string
  actor: UserRef
  resourceType: string
  resourceId: string
  detail: string
  runId: string
  at: string
}

export interface RunTrace {
  run: RunSummary
  steps: AgentStep[]
  modelCalls: ModelCall[]
  events: AuditEvent[]
}

export interface UsageByDay {
  date: string
  runs: number
  promptTokens: number
  completionTokens: number
  estimatedCostUsd: number
}

export interface UsageByModel {
  provider: string
  model: string
  calls: number
  totalTokens: number
  estimatedCostUsd: number
}

export interface UsageSummary {
  from: string
  to: string
  runs: number
  totals: UsageTotals
  byDay: UsageByDay[]
  byModel: UsageByModel[]
}
