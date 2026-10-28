import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowRight, Check, CircleAlert, CircleCheck, CircleX, Cpu, TriangleAlert } from 'lucide-react'
import type {
  ApprovalDecision,
  ExecutionRecord,
  FieldChange,
  HistoryEntry,
  RequestStatus,
  ServiceRequest,
  ValidationResult,
} from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { DescriptionList, Table, TBody, Td, Th, THead, Tr } from '@/components/ui/Layout'
import { EmptyState } from '@/components/ui/States'
import { cn } from '@/lib/cn'
import { formatDateTime, formatRelative, formatValue } from '@/lib/format'
import { categoryLabels, decisionLabels, fieldLabel, historyLabels, historyTones, statusLabels, statusTones } from '@/lib/labels'
import { payloadItems } from './payload'

export function StatusBadge({ status }: { status: RequestStatus }) {
  return <Badge tone={statusTones[status]}>{statusLabels[status]}</Badge>
}

export function RequestDetailsCard({ request }: { request: ServiceRequest }) {
  return (
    <Card>
      <CardHeader title="Request details" description={categoryLabels[request.category]} />
      <CardBody className="space-y-5">
        <div>
          <p className="text-xs font-medium text-slate-500">Description</p>
          <p className="mt-0.5 whitespace-pre-line text-sm text-slate-900">{request.description}</p>
        </div>
        <DescriptionList items={payloadItems(request.payload)} />
        <div className="grid grid-cols-2 gap-4 border-t border-slate-100 pt-4 text-xs text-slate-500 sm:grid-cols-4">
          <div>
            <p>Requested by</p>
            <p className="mt-0.5 text-sm text-slate-900">{request.requester.fullName}</p>
          </div>
          <div>
            <p>Created</p>
            <p className="mt-0.5 text-sm text-slate-900">{formatDateTime(request.createdAt)}</p>
          </div>
          <div>
            <p>Submitted</p>
            <p className="mt-0.5 text-sm text-slate-900">{formatDateTime(request.submittedAt)}</p>
          </div>
          <div>
            <p>Version</p>
            <p className="mt-0.5 text-sm text-slate-900">v{request.version}</p>
          </div>
        </div>
      </CardBody>
    </Card>
  )
}

export function ValidationPanel({ validation, title = 'Validation results' }: { validation: ValidationResult | null; title?: string }) {
  if (!validation) {
    return (
      <Card>
        <CardHeader title={title} />
        <CardBody>
          <p className="text-sm text-slate-500">Validation runs when the request is saved or submitted.</p>
        </CardBody>
      </Card>
    )
  }
  const errors = validation.issues.filter((i) => i.severity === 'ERROR')
  const warnings = validation.issues.filter((i) => i.severity === 'WARNING')
  return (
    <Card>
      <CardHeader
        title={title}
        description={`Checked ${formatRelative(validation.checkedAt)}`}
        actions={
          validation.valid ? (
            <Badge tone="green" icon={<CircleCheck className="size-3.5" />}>
              Passes validation
            </Badge>
          ) : (
            <Badge tone="red" icon={<CircleX className="size-3.5" />}>
              {errors.length} error{errors.length === 1 ? '' : 's'}
            </Badge>
          )
        }
      />
      <CardBody>
        {validation.issues.length === 0 ? (
          <p className="flex items-center gap-2 text-sm text-emerald-700">
            <Check className="size-4" />
            All business rules satisfied.
          </p>
        ) : (
          <ul className="space-y-2">
            {[...errors, ...warnings].map((issue) => (
              <li key={issue.field + issue.message} className="flex items-start gap-2 text-sm">
                {issue.severity === 'ERROR' ? <CircleAlert className="mt-0.5 size-4 shrink-0 text-red-600" /> : <TriangleAlert className="mt-0.5 size-4 shrink-0 text-amber-500" />}
                <span>
                  <span className="font-medium text-slate-900">{fieldLabel(issue.field)}:</span> <span className="text-slate-600">{issue.message}</span>
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  )
}

export function DiffTable({ changes, emptyText = 'No changes.' }: { changes: FieldChange[]; emptyText?: string }) {
  if (changes.length === 0) return <p className="text-sm text-slate-500">{emptyText}</p>
  return (
    <div className="overflow-hidden rounded-lg border border-slate-200">
      <Table>
        <THead>
          <tr>
            <Th>Field</Th>
            <Th>Before</Th>
            <Th>After</Th>
          </tr>
        </THead>
        <TBody>
          {changes.map((change) => (
            <tr key={change.field}>
              <Td className="font-medium text-slate-900">{fieldLabel(change.field)}</Td>
              <Td>
                <span className="rounded bg-red-50 px-1.5 py-0.5 text-red-700 line-through decoration-red-300">{formatValue(change.from)}</span>
              </Td>
              <Td>
                <span className="rounded bg-emerald-50 px-1.5 py-0.5 text-emerald-800">{formatValue(change.to)}</span>
              </Td>
            </tr>
          ))}
        </TBody>
      </Table>
    </div>
  )
}

export function DecisionCard({ decision }: { decision: ApprovalDecision }) {
  const tone = decision.decision === 'REJECTED' ? 'red' : decision.decision === 'EDITED_APPROVED' ? 'purple' : 'blue'
  return (
    <Card>
      <CardHeader title="Supervisor decision" actions={<Badge tone={tone}>{decisionLabels[decision.decision]}</Badge>} />
      <CardBody className="space-y-4">
        <DescriptionList
          items={[
            { label: 'Supervisor', value: decision.supervisor.fullName },
            { label: 'Decided at', value: formatDateTime(decision.decidedAt) },
            ...(decision.reason ? [{ label: decision.decision === 'REJECTED' ? 'Rejection reason' : 'Reason / comment', value: decision.reason, full: true }] : []),
          ]}
        />
        {decision.changes.length > 0 && (
          <div>
            <p className="mb-2 text-xs font-medium text-slate-500">Changes made before approval</p>
            <DiffTable changes={decision.changes} />
          </div>
        )}
      </CardBody>
    </Card>
  )
}

export function ExecutionCard({ execution }: { execution: ExecutionRecord }) {
  return (
    <Card>
      <CardHeader
        icon={<Cpu className="size-4" />}
        title="Simulated execution"
        description="No real banking system was contacted."
        actions={<Badge tone={execution.outcome === 'SUCCEEDED' ? 'green' : 'red'}>{execution.outcome === 'SUCCEEDED' ? 'Succeeded' : 'Failed'}</Badge>}
      />
      <CardBody>
        <DescriptionList
          items={[
            { label: 'Execution record', value: <span className="font-mono text-xs">{execution.id}</span> },
            { label: 'Executed at', value: formatDateTime(execution.executedAt) },
            { label: 'Action', value: execution.simulatedAction, full: true },
            ...Object.entries(execution.result).map(([key, value]) => ({
              label: key.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()),
              value: <span className="font-mono text-xs">{value}</span>,
            })),
          ]}
        />
      </CardBody>
    </Card>
  )
}

const lifecycle: RequestStatus[] = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'EXECUTED']

export function LifecycleStepper({ status }: { status: RequestStatus }) {
  const rejected = status === 'REJECTED'
  const currentIndex = rejected ? 1 : lifecycle.indexOf(status)
  const steps = rejected ? (['DRAFT', 'PENDING_APPROVAL', 'REJECTED'] as RequestStatus[]) : lifecycle
  return (
    <ol className="flex flex-wrap items-center gap-2 text-xs font-medium">
      {steps.map((step, index) => {
        const done = rejected ? index < 2 : index < currentIndex
        const current = rejected ? step === 'REJECTED' : index === currentIndex
        return (
          <li key={step} className="flex items-center gap-2">
            <span
              className={cn(
                'flex items-center gap-1.5 rounded-full px-2.5 py-1 ring-1 ring-inset',
                done && 'bg-slate-50 text-slate-600 ring-slate-200',
                current && !rejected && 'bg-brand-700 text-white ring-brand-700',
                current && rejected && 'bg-red-600 text-white ring-red-600',
                !done && !current && 'text-slate-400 ring-slate-200',
              )}
            >
              {done && <Check className="size-3" />}
              {statusLabels[step]}
            </span>
            {index < steps.length - 1 && <ArrowRight className="size-3.5 text-slate-300" />}
          </li>
        )
      })}
    </ol>
  )
}

const dotTones: Record<string, string> = {
  neutral: 'bg-slate-400',
  amber: 'bg-amber-500',
  blue: 'bg-sky-500',
  red: 'bg-red-500',
  purple: 'bg-violet-500',
  green: 'bg-emerald-500',
  brand: 'bg-brand-600',
}

export function HistoryTimeline({ entries }: { entries: HistoryEntry[] }) {
  if (entries.length === 0) return <EmptyState title="No history yet" />
  return (
    <ol className="relative space-y-5 border-l border-slate-200 pl-5">
      {[...entries].reverse().map((entry) => (
        <li key={entry.id} className="relative">
          <span className={cn('absolute -left-[26px] top-1 size-3 rounded-full ring-4 ring-white', dotTones[historyTones[entry.action]])} />
          <div className="flex flex-wrap items-baseline justify-between gap-x-3">
            <p className="text-sm font-medium text-slate-900">{historyLabels[entry.action]}</p>
            <time className="text-xs text-slate-500" title={formatDateTime(entry.at)}>
              {formatRelative(entry.at)}
            </time>
          </div>
          <p className="text-xs text-slate-500">
            {entry.actor.fullName}
            {entry.fromStatus && entry.toStatus && entry.fromStatus !== entry.toStatus && (
              <>
                {' · '}
                {statusLabels[entry.fromStatus]} → {statusLabels[entry.toStatus]}
              </>
            )}
          </p>
          {entry.reason && <p className="mt-1 rounded-md bg-slate-50 px-2.5 py-1.5 text-xs text-slate-700">{entry.reason}</p>}
          {entry.changes.length > 0 && (
            <ul className="mt-1.5 space-y-0.5 text-xs">
              {entry.changes.map((change) => (
                <li key={change.field} className="text-slate-600">
                  <span className="font-medium text-slate-700">{fieldLabel(change.field)}:</span>{' '}
                  <span className="text-red-600 line-through">{formatValue(change.from)}</span> → <span className="text-emerald-700">{formatValue(change.to)}</span>
                </li>
              ))}
            </ul>
          )}
          <p className="mt-1 font-mono text-[10px] text-slate-400">run {entry.runId}</p>
        </li>
      ))}
    </ol>
  )
}

export function RequestsTable({ requests, showRequester, emptyTitle, emptyAction, linkTo }: { requests: ServiceRequest[]; showRequester?: boolean; emptyTitle: string; emptyAction?: ReactNode; linkTo?: (request: ServiceRequest) => string }) {
  if (requests.length === 0) return <EmptyState title={emptyTitle} action={emptyAction} />
  return (
    <Table>
      <THead>
        <tr>
          <Th>Reference</Th>
          <Th>Title</Th>
          <Th>Category</Th>
          {showRequester && <Th>Requested by</Th>}
          <Th>Status</Th>
          <Th className="text-right">Updated</Th>
        </tr>
      </THead>
      <TBody>
        {requests.map((request) => (
          <Tr key={request.id}>
            <Td className="whitespace-nowrap">
              <Link to={linkTo ? linkTo(request) : `/requests/${request.id}`} className="font-mono text-xs font-medium text-brand-700 hover:underline">
                {request.reference}
              </Link>
            </Td>
            <Td className="max-w-sm">
              <Link to={linkTo ? linkTo(request) : `/requests/${request.id}`} className="font-medium text-slate-900 hover:text-brand-700">
                {request.title}
              </Link>
              {request.validation && !request.validation.valid && request.status === 'DRAFT' && <p className="mt-0.5 text-xs text-red-600">Has validation errors</p>}
            </Td>
            <Td className="whitespace-nowrap">{categoryLabels[request.category]}</Td>
            {showRequester && <Td className="whitespace-nowrap">{request.requester.fullName}</Td>}
            <Td>
              <StatusBadge status={request.status} />
            </Td>
            <Td className="whitespace-nowrap text-right text-xs text-slate-500" title={formatDateTime(request.updatedAt)}>
              {formatRelative(request.updatedAt)}
            </Td>
          </Tr>
        ))}
      </TBody>
    </Table>
  )
}
