import { apiRequest } from './client'
import type {
  ApproveRequest,
  AskRequest,
  AskResponse,
  AuditEvent,
  AuthResponse,
  CreateRequestRequest,
  CreateUserRequest,
  DocumentSummary,
  EditApproveRequest,
  ExecuteResponse,
  HistoryEntry,
  IngestionReport,
  LoginRequest,
  PreparedDraftResponse,
  PrepareRequestRequest,
  RejectRequest,
  RequestListQuery,
  RunSummary,
  RunTrace,
  ServiceRequest,
  StageLog,
  UpdateRequestRequest,
  UsageSummary,
  UserSummary,
} from './types'

export const authApi = {
  login: (body: LoginRequest) => apiRequest<AuthResponse>('/auth/login', { method: 'POST', body }),
  me: () => apiRequest<UserSummary>('/auth/me'),
}

export const copilotApi = {
  ask: (body: AskRequest) => apiRequest<AskResponse>('/copilot/ask', { method: 'POST', body }),
  prepareRequest: (body: PrepareRequestRequest) =>
    apiRequest<PreparedDraftResponse>('/copilot/prepare-request', { method: 'POST', body }),
}

export const documentsApi = {
  list: () => apiRequest<DocumentSummary[]>('/documents'),
  upload: (file: File, title?: string, category?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    if (title) formData.append('title', title)
    if (category) formData.append('category', category)
    return apiRequest<IngestionReport>('/documents', { method: 'POST', formData })
  },
  ingestionLog: (id: string) => apiRequest<StageLog[]>(`/documents/${id}/ingestion-log`),
  retire: (id: string) => apiRequest<DocumentSummary>(`/documents/${id}`, { method: 'DELETE' }),
}

export const requestsApi = {
  list: (query: RequestListQuery = {}) =>
    apiRequest<ServiceRequest[]>('/requests', {
      query: {
        scope: query.scope,
        status: query.status,
        category: query.category,
        requesterId: query.requesterId,
      },
    }),
  get: (id: string) => apiRequest<ServiceRequest>(`/requests/${id}`),
  history: (id: string) => apiRequest<HistoryEntry[]>(`/requests/${id}/history`),
  create: (body: CreateRequestRequest) => apiRequest<ServiceRequest>('/requests', { method: 'POST', body }),
  update: (id: string, body: UpdateRequestRequest) =>
    apiRequest<ServiceRequest>(`/requests/${id}`, { method: 'PUT', body }),
  submit: (id: string, version: number) =>
    apiRequest<ServiceRequest>(`/requests/${id}/submit`, { method: 'POST', body: { version } }),
}

export const approvalsApi = {
  queue: () => apiRequest<ServiceRequest[]>('/approvals/queue'),
  approve: (id: string, body: ApproveRequest) =>
    apiRequest<ServiceRequest>(`/approvals/${id}/approve`, { method: 'POST', body }),
  reject: (id: string, body: RejectRequest) =>
    apiRequest<ServiceRequest>(`/approvals/${id}/reject`, { method: 'POST', body }),
  editApprove: (id: string, body: EditApproveRequest) =>
    apiRequest<ServiceRequest>(`/approvals/${id}/edit-approve`, { method: 'POST', body }),
  execute: (id: string) => apiRequest<ExecuteResponse>(`/approvals/${id}/execute`, { method: 'POST' }),
}

export const auditApi = {
  runs: () => apiRequest<RunSummary[]>('/audit/runs'),
  run: (runId: string) => apiRequest<RunTrace>(`/audit/runs/${runId}`),
  events: () => apiRequest<AuditEvent[]>('/audit/events'),
  usage: (from?: string, to?: string) => apiRequest<UsageSummary>('/audit/usage', { query: { from, to } }),
}

export const usersApi = {
  list: () => apiRequest<UserSummary[]>('/users'),
  create: (body: CreateUserRequest) => apiRequest<UserSummary>('/users', { method: 'POST', body }),
}
