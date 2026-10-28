import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ArrowLeft, Save, Send, Sparkles } from 'lucide-react'
import { ApiError } from '@/api/client'
import type { ProblemDetail, ServiceRequest } from '@/api/types'
import { useCurrentUser } from '@/auth/context'
import { Button } from '@/components/ui/Button'
import { Card, CardBody } from '@/components/ui/Card'
import { PageHeader } from '@/components/ui/Layout'
import { Alert, ErrorState, Spinner } from '@/components/ui/States'
import { useToast } from '@/components/ui/toast-context'
import { NotFoundPage } from '@/features/errors/ErrorPages'
import { fieldLabel } from '@/lib/labels'
import { useCreateRequest, useRequest, useSubmitRequest, useUpdateRequest } from './hooks'
import { FormActions, RequestFields } from './RequestForm'
import { useRequestForm } from './useRequestForm'
import { cleanValues, emptyForm, toFormValues, type RequestFormValues } from './schema'
import { StatusBadge } from './components'

interface EditorState {
  missingFields?: string[]
  fromCopilot?: boolean
  submitError?: ProblemDetail
}

export function NewRequestPage() {
  const user = useCurrentUser()
  return <RequestEditor initial={emptyForm('CARD_SERVICE', user.branchCode)} />
}

export function EditRequestPage() {
  const { id = '' } = useParams()
  const user = useCurrentUser()
  const query = useRequest(id)
  if (query.isPending) return <Spinner />
  if (query.error) {
    if (query.error instanceof ApiError && query.error.status === 404) return <NotFoundPage title="Request not found" description="It does not exist or you do not have access to it." />
    return <ErrorState error={query.error} />
  }
  const request = query.data
  if (request.requester.id !== user.id || request.status !== 'DRAFT') {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <Alert tone="warning" title="This request can no longer be edited">
          Only the employee who created a request can edit it, and only while it is a draft. It is currently <StatusBadge status={request.status} />.
        </Alert>
        <Link to={`/requests/${request.id}`} className="text-sm font-medium text-brand-700 hover:underline">
          View the request
        </Link>
      </div>
    )
  }
  return <RequestEditor key={request.id + request.version} existing={request} initial={toFormValues(request)} />
}

function RequestEditor({ initial, existing }: { initial: RequestFormValues; existing?: ServiceRequest }) {
  const user = useCurrentUser()
  const navigate = useNavigate()
  const location = useLocation()
  const { notify } = useToast()
  const state = (location.state ?? {}) as EditorState
  const [serverError, setServerError] = useState<unknown>(state.submitError ? new ApiError(state.submitError) : null)
  const form = useRequestForm(initial, serverError)

  const create = useCreateRequest()
  const update = useUpdateRequest(existing?.id ?? '')
  const submit = useSubmitRequest()
  const busy = create.isPending || update.isPending || submit.isPending

  const save = async (values: RequestFormValues) => {
    const body = cleanValues(values)
    if (existing) return update.mutateAsync({ title: body.title, description: body.description, payload: body.payload, version: existing.version })
    return create.mutateAsync(body)
  }

  const onSaveDraft = async () => {
    const ok = await form.trigger('title')
    if (!ok) return
    setServerError(null)
    try {
      const saved = await save(form.getValues())
      notify({ tone: 'success', title: 'Draft saved', description: `${saved.reference} is saved as a draft.` })
      navigate(`/requests/${saved.id}`)
    } catch (error) {
      setServerError(error)
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setServerError(null)
    let saved: ServiceRequest
    try {
      saved = await save(values)
    } catch (error) {
      setServerError(error)
      return
    }
    try {
      const submitted = await submit.mutateAsync({ id: saved.id, version: saved.version })
      notify({ tone: 'success', title: 'Submitted for approval', description: `${submitted.reference} is now in the supervisor queue.` })
      navigate(`/requests/${submitted.id}`)
    } catch (error) {
      if (!existing && error instanceof ApiError) {
        navigate(`/requests/${saved.id}/edit`, {
          replace: true,
          state: { submitError: { title: error.title, status: error.status, detail: error.detail, runId: error.runId, errors: error.fieldErrors } },
        })
        return
      }
      setServerError(error)
    }
  })

  const missing = state.missingFields ?? []

  return (
    <div className="mx-auto max-w-3xl">
      <Link to={existing ? `/requests/${existing.id}` : '/requests'} className="mb-4 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="size-4" />
        Back
      </Link>
      <PageHeader
        eyebrow={existing ? existing.reference : 'New service request'}
        title={existing ? 'Edit draft' : 'Create a service request'}
        description="Requests are validated when you submit them and then wait for a supervisor's decision. Nothing is executed until a supervisor approves."
      />

      {state.fromCopilot && (
        <Alert tone="info" icon={<Sparkles className="size-4" />} title="Draft prepared by the copilot" className="mb-4">
          The Request Preparation Agent filled in what it could from your question.
          {missing.length > 0 ? ` Complete the highlighted fields: ${missing.map(fieldLabel).join(', ')}.` : ' Review every field before submitting.'}
        </Alert>
      )}

      <Card>
        <CardBody>
          <form onSubmit={onSubmit} noValidate className="space-y-6">
            <RequestFields form={form} categoryLocked={!!existing} branchCode={user.branchCode} highlight={missing} />
            {serverError !== null && <ErrorState error={serverError} />}
            <FormActions>
              <Button variant="secondary" icon={<Save className="size-4" />} onClick={onSaveDraft} disabled={busy} loading={create.isPending || (update.isPending && !submit.isPending)}>
                Save draft
              </Button>
              <Button type="submit" icon={<Send className="size-4" />} loading={submit.isPending} disabled={busy}>
                Submit for approval
              </Button>
            </FormActions>
          </form>
        </CardBody>
      </Card>
    </div>
  )
}
