import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useWatch } from 'react-hook-form'
import { ArrowLeft, Check, PencilLine, ShieldAlert, X } from 'lucide-react'
import { ApiError } from '@/api/client'
import type { ServiceRequest } from '@/api/types'
import { useCurrentUser } from '@/auth/context'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Dialog } from '@/components/ui/Dialog'
import { FormField, Textarea } from '@/components/ui/Field'
import { Alert, ErrorState, Spinner } from '@/components/ui/States'
import { useToast } from '@/components/ui/toast-context'
import { NotFoundPage } from '@/features/errors/ErrorPages'
import { DiffTable, HistoryTimeline, RequestDetailsCard, StatusBadge, ValidationPanel } from '@/features/requests/components'
import { useApprove, useEditApprove, useReject, useRequest, useRequestHistory } from '@/features/requests/hooks'
import { RequestFields } from '@/features/requests/RequestForm'
import { useRequestForm } from '@/features/requests/useRequestForm'
import { cleanValues, toFormValues, type RequestFormValues } from '@/features/requests/schema'
import { diffRequest } from '@/lib/diff'
import { formatRelative } from '@/lib/format'
import { categoryLabels } from '@/lib/labels'

export function ReviewPage() {
  const { id = '' } = useParams()
  const query = useRequest(id)
  if (query.isPending) return <Spinner />
  if (query.error) {
    if (query.error instanceof ApiError && query.error.status === 404) return <NotFoundPage title="Request not found" />
    return <ErrorState error={query.error} />
  }
  return <Review key={query.data.id} request={query.data} />
}

function Review({ request }: { request: ServiceRequest }) {
  const user = useCurrentUser()
  const navigate = useNavigate()
  const { notify } = useToast()
  const history = useRequestHistory(request.id)
  const [mode, setMode] = useState<'review' | 'edit'>('review')
  const [comment, setComment] = useState('')
  const [rejectOpen, setRejectOpen] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const approve = useApprove(request.id)
  const reject = useReject(request.id)
  const editApprove = useEditApprove(request.id)

  const own = request.requester.id === user.id
  const decidable = request.status === 'PENDING_APPROVAL' && !own

  const done = (updated: ServiceRequest, title: string) => {
    notify({ tone: 'success', title, description: `${updated.reference} is now ${updated.status === 'EXECUTED' ? 'executed' : updated.status.toLowerCase()}.` })
    navigate(`/requests/${updated.id}`)
  }

  const onApprove = async () => {
    setError(null)
    try {
      done(await approve.mutateAsync({ comment: comment.trim() || undefined, version: request.version }), 'Request approved')
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/approvals" className="mb-4 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-800">
          <ArrowLeft className="size-4" />
          Approval queue
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-sm font-medium text-slate-500">{request.reference}</span>
          <StatusBadge status={request.status} />
          <Badge>{categoryLabels[request.category]}</Badge>
        </div>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">Review: {request.title}</h1>
        <p className="mt-1 text-sm text-slate-500">
          Submitted by {request.requester.fullName} {formatRelative(request.submittedAt)}
        </p>
      </div>

      {request.status !== 'PENDING_APPROVAL' && (
        <Alert tone="info" title="This request has already been decided">
          It is currently <StatusBadge status={request.status} />.{' '}
          <Link to={`/requests/${request.id}`} className="font-medium underline">
            Open the request
          </Link>
        </Alert>
      )}
      {own && request.status === 'PENDING_APPROVAL' && (
        <Alert tone="warning" icon={<ShieldAlert className="size-4" />} title="You submitted this request">
          Segregation of duties: a supervisor cannot approve, reject or edit their own request. Another supervisor must review it.
        </Alert>
      )}

      {mode === 'edit' && decidable ? (
        <EditAndApprove request={request} onCancel={() => setMode('review')} onDone={(updated) => done(updated, 'Edited and approved')} mutation={editApprove} />
      ) : (
        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <RequestDetailsCard request={request} />
            <ValidationPanel validation={request.validation} />
            <Card>
              <CardHeader title="History" />
              <CardBody>{history.isPending ? <Spinner /> : history.error ? <ErrorState error={history.error} /> : <HistoryTimeline entries={history.data} />}</CardBody>
            </Card>
          </div>
          <div className="space-y-4">
            <Card className="lg:sticky lg:top-6">
              <CardHeader title="Decision" description="Your identity, decision and timestamp are recorded in the audit trail." />
              <CardBody className="space-y-4">
                <FormField label="Comment (optional)" htmlFor="comment">
                  <Textarea id="comment" rows={2} value={comment} onChange={(e) => setComment(e.target.value)} disabled={!decidable} placeholder="Visible in the request history" />
                </FormField>
                {error !== null && <ErrorState error={error} />}
                <div className="grid gap-2">
                  <Button variant="success" icon={<Check className="size-4" />} onClick={onApprove} loading={approve.isPending} disabled={!decidable}>
                    Approve and execute
                  </Button>
                  <Button variant="secondary" icon={<PencilLine className="size-4" />} onClick={() => setMode('edit')} disabled={!decidable}>
                    Edit & approve
                  </Button>
                  <Button variant="danger" icon={<X className="size-4" />} onClick={() => setRejectOpen(true)} disabled={!decidable}>
                    Reject
                  </Button>
                </div>
                <p className="text-xs text-slate-500">Approval triggers the simulated action once. Repeated execution attempts return the original record.</p>
              </CardBody>
            </Card>
          </div>
        </div>
      )}

      <RejectDialog
        open={rejectOpen}
        onClose={() => setRejectOpen(false)}
        loading={reject.isPending}
        onConfirm={async (reason) => {
          const updated = await reject.mutateAsync({ reason, version: request.version })
          setRejectOpen(false)
          done(updated, 'Request rejected')
        }}
      />
    </div>
  )
}

function RejectDialog({ open, onClose, onConfirm, loading }: { open: boolean; onClose: () => void; onConfirm: (reason: string) => Promise<void>; loading: boolean }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<unknown>(null)
  const tooShort = reason.trim().length < 10

  const confirm = async () => {
    setError(null)
    try {
      await onConfirm(reason.trim())
      setReason('')
    } catch (err) {
      setError(err)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Reject request"
      description="The employee will see this reason. It is stored permanently in the audit trail."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="danger" onClick={confirm} disabled={tooShort} loading={loading}>
            Reject request
          </Button>
        </>
      }
    >
      <FormField label="Reason" htmlFor="reason" required hint="At least 10 characters" error={reason && tooShort ? 'Give a reason of at least 10 characters.' : undefined}>
        <Textarea id="reason" rows={4} autoFocus value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Explain which rule or check the request fails" />
      </FormField>
      {error !== null && <ErrorState error={error} className="mt-3" />}
    </Dialog>
  )
}

function EditAndApprove({ request, onCancel, onDone, mutation }: { request: ServiceRequest; onCancel: () => void; onDone: (updated: ServiceRequest) => void; mutation: ReturnType<typeof useEditApprove> }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<unknown>(null)
  const form = useRequestForm(toFormValues(request), error)
  const watched = useWatch({ control: form.control }) as RequestFormValues
  const changes = diffRequest(request, cleanValues(watched))
  const reasonInvalid = reason.trim().length < 10

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    const body = cleanValues(values)
    try {
      onDone(await mutation.mutateAsync({ title: body.title, description: body.description, payload: body.payload, reason: reason.trim(), version: request.version }))
    } catch (err) {
      setError(err)
    }
  })

  return (
    <form onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-3">
      <Card className="lg:col-span-2">
        <CardHeader title="Edit request" description="Correct the request before approving it. The employee's original values are kept in the history." />
        <CardBody>
          <RequestFields form={form} categoryLocked />
        </CardBody>
      </Card>
      <div className="space-y-4">
        <Card className="lg:sticky lg:top-6">
          <CardHeader title="Changes preview" description={`${changes.length} field${changes.length === 1 ? '' : 's'} changed`} />
          <CardBody className="space-y-4">
            <DiffTable changes={changes} emptyText="Edit a field to see the before/after comparison." />
            <FormField label="Reason for the edit" htmlFor="editReason" required error={reason && reasonInvalid ? 'Explain the change in at least 10 characters.' : undefined}>
              <Textarea id="editReason" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Why was the request changed?" />
            </FormField>
            {error !== null && <ErrorState error={error} />}
            <div className="grid gap-2">
              <Button type="submit" variant="success" icon={<Check className="size-4" />} loading={mutation.isPending} disabled={changes.length === 0 || reasonInvalid}>
                Approve edited version
              </Button>
              <Button variant="secondary" onClick={onCancel}>
                Cancel editing
              </Button>
            </div>
          </CardBody>
        </Card>
      </div>
    </form>
  )
}
