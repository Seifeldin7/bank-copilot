import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { approvalsApi, requestsApi } from '@/api/endpoints'
import type {
  ApproveRequest,
  CreateRequestRequest,
  EditApproveRequest,
  RejectRequest,
  RequestListQuery,
  ServiceRequest,
  UpdateRequestRequest,
} from '@/api/types'

export const requestKeys = {
  all: ['requests'] as const,
  list: (query: RequestListQuery) => ['requests', 'list', query] as const,
  detail: (id: string) => ['requests', 'detail', id] as const,
  history: (id: string) => ['requests', 'history', id] as const,
  queue: ['approvals', 'queue'] as const,
}

export function useRequests(query: RequestListQuery) {
  return useQuery({ queryKey: requestKeys.list(query), queryFn: () => requestsApi.list(query) })
}

export function useRequest(id: string) {
  return useQuery({ queryKey: requestKeys.detail(id), queryFn: () => requestsApi.get(id), retry: false })
}

export function useRequestHistory(id: string, enabled = true) {
  return useQuery({ queryKey: requestKeys.history(id), queryFn: () => requestsApi.history(id), enabled, retry: false })
}

export function useApprovalQueue() {
  return useQuery({ queryKey: requestKeys.queue, queryFn: approvalsApi.queue })
}

function useInvalidate() {
  const queryClient = useQueryClient()
  return (request?: ServiceRequest) => {
    if (request) queryClient.setQueryData(requestKeys.detail(request.id), request)
    queryClient.invalidateQueries({ queryKey: requestKeys.all })
    queryClient.invalidateQueries({ queryKey: requestKeys.queue })
    queryClient.invalidateQueries({ queryKey: ['audit'] })
  }
}

export function useCreateRequest() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (body: CreateRequestRequest) => requestsApi.create(body), onSuccess: invalidate })
}

export function useUpdateRequest(id: string) {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (body: UpdateRequestRequest) => requestsApi.update(id, body), onSuccess: invalidate })
}

export function useSubmitRequest() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => requestsApi.submit(id, version),
    onSuccess: invalidate,
    onError: () => invalidate(),
  })
}

export function useApprove(id: string) {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (body: ApproveRequest) => approvalsApi.approve(id, body), onSuccess: invalidate })
}

export function useReject(id: string) {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (body: RejectRequest) => approvalsApi.reject(id, body), onSuccess: invalidate })
}

export function useEditApprove(id: string) {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (body: EditApproveRequest) => approvalsApi.editApprove(id, body), onSuccess: invalidate })
}

export function useExecute(id: string) {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: () => approvalsApi.execute(id), onSuccess: () => invalidate() })
}
