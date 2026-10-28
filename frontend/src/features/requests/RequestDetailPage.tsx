import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ArrowLeft, ClipboardCheck, Pencil, PlayCircle, RefreshCw, Send, Sparkles } from 'lucide-react'
import { ApiError } from '@/api/client'
import type { ServiceRequest } from '@/api/types'
import { useAuth, useCurrentUser } from '@/auth/context'
import { Badge } from '@/components/ui/Badge'
import { Button, LinkButton } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Alert, ErrorState, Spinner } from '@/components/ui/States'
import { useToast } from '@/components/ui/toast-context'
import { NotFoundPage } from '@/features/errors/ErrorPages'
import { categoryLabels } from '@/lib/labels'
import {
  DecisionCard,
  ExecutionCard,
  HistoryTimeline,
  LifecycleStepper,
  RequestDetailsCard,
  StatusBadge,
  ValidationPanel,
} from './components'
import { useExecute, useRequest, useRequestHistory, useSubmitRequest } from './hooks'

export function RequestDetailPage() {
  const { id = '' } = useParams()
  const query = useRequest(id)
  const history = useRequestHistory(id, query.isSuccess)

  if (query.isPending) return <Spinner />
  if (query.error) {
    if (query.error instanceof ApiError && query.error.status === 404)
      return <NotFoundPage title="Request not found" description="This request does not exist, or it belongs to another employee. Employees can only open their own requests." />
    return <ErrorState error={query.error} />
  }

  const request = query.data
  return (
    <div className="space-y-6">
      <div>
        <Link to="/requests" className="mb-4 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-800">
          <ArrowLeft className="size-4" />
          Requests
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-sm font-medium text-slate-500">{request.reference}</span>
              <StatusBadge status={request.status} />
              <Badge>{categoryLabels[request.category]}</Badge>
            </div>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{request.title}</h1>
          </div>
          <RequestActions request={request} />
        </div>
        <div className="mt-4">
          <LifecycleStepper status={request.status} />
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <SourceRunNotice request={request} />
          <RequestDetailsCard request={request} />
          <ValidationPanel validation={request.validation} />
          {request.decision && <DecisionCard decision={request.decision} />}
          {request.execution && <ExecutionCard execution={request.execution} />}
        </div>
        <Card className="h-fit">
          <CardHeader title="History" description="Every change, decision and execution attempt" />
          <CardBody>{history.isPending ? <Spinner /> : history.error ? <ErrorState error={history.error} /> : <HistoryTimeline entries={history.data} />}</CardBody>
        </Card>
      </div>
    </div>
  )
}

function SourceRunNotice({ request }: { request: ServiceRequest }) {
  const { hasRole } = useAuth()
  if (!request.sourceRunId) return null
  return (
    <Alert tone="info" icon={<Sparkles className="size-4" />} title="Prepared by the copilot">
      Drafted by the Request Preparation Agent from copilot run{' '}
      {hasRole('SUPERVISOR') ? (
        <Link to={`/audit/runs/${request.sourceRunId}`} className="font-mono font-medium underline">
          {request.sourceRunId}
        </Link>
      ) : (
        <span className="font-mono font-medium">{request.sourceRunId}</span>
      )}
      . Agents can only create drafts; submission and approval stay with people.
    </Alert>
  )
}

function RequestActions({ request }: { request: ServiceRequest }) {
  const user = useCurrentUser()
  const { hasRole } = useAuth()
  const { notify } = useToast()
  const submit = useSubmitRequest()
  const execute = useExecute(request.id)
  const [error, setError] = useState<unknown>(null)
  const owner = request.requester.id === user.id
  const supervisor = hasRole('SUPERVISOR')

  const onSubmit = async () => {
    setError(null)
    try {
      await submit.mutateAsync({ id: request.id, version: request.version })
      notify({ tone: 'success', title: 'Submitted for approval', description: `${request.reference} is now in the supervisor queue.` })
    } catch (err) {
      setError(err)
    }
  }

  const onExecute = async () => {
    setError(null)
    try {
      const result = await execute.mutateAsync()
      notify(
        result.alreadyExecuted
          ? { tone: 'warning', title: 'Already executed', description: `Duplicate execution blocked. Returned the original record ${result.execution.id}.` }
          : { tone: 'success', title: 'Simulated action executed', description: `Execution record ${result.execution.id} created.` },
      )
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="flex max-w-md flex-col items-end gap-2">
      <div className="flex flex-wrap justify-end gap-2">
        {owner && request.status === 'DRAFT' && (
          <>
            <LinkButton to={`/requests/${request.id}/edit`} variant="secondary" icon={<Pencil className="size-4" />}>
              Edit draft
            </LinkButton>
            <Button icon={<Send className="size-4" />} onClick={onSubmit} loading={submit.isPending}>
              Submit for approval
            </Button>
          </>
        )}
        {supervisor && request.status === 'PENDING_APPROVAL' && !owner && (
          <LinkButton to={`/approvals/${request.id}`} icon={<ClipboardCheck className="size-4" />}>
            Review request
          </LinkButton>
        )}
        {supervisor && request.status === 'PENDING_APPROVAL' && owner && <Badge tone="amber">Awaiting another supervisor</Badge>}
        {supervisor && request.status === 'APPROVED' && (
          <Button variant="success" icon={<PlayCircle className="size-4" />} onClick={onExecute} loading={execute.isPending}>
            Execute simulated action
          </Button>
        )}
        {supervisor && request.status === 'EXECUTED' && (
          <Button variant="secondary" icon={<RefreshCw className="size-4" />} onClick={onExecute} loading={execute.isPending} title="Demonstrates idempotent execution">
            Retry execution
          </Button>
        )}
      </div>
      {error !== null && <ErrorState error={error} className="w-full" />}
    </div>
  )
}
