import { Link } from 'react-router'
import { ChevronRight, CircleAlert, ClipboardCheck, TriangleAlert, UserRound } from 'lucide-react'
import { useCurrentUser } from '@/auth/context'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { PageHeader } from '@/components/ui/Layout'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/States'
import { formatDateTime, formatRelative } from '@/lib/format'
import { categoryLabels } from '@/lib/labels'
import { useApprovalQueue } from '@/features/requests/hooks'

export function ApprovalQueuePage() {
  const user = useCurrentUser()
  const query = useApprovalQueue()

  return (
    <>
      <PageHeader
        title="Approval queue"
        description="Requests waiting for a supervisor decision, oldest first. Approving runs the simulated action exactly once; rejecting requires a reason."
      />
      <Card>
        {query.isPending ? (
          <Spinner />
        ) : query.error ? (
          <div className="p-5">
            <ErrorState error={query.error} />
          </div>
        ) : query.data.length === 0 ? (
          <EmptyState icon={<ClipboardCheck className="size-6" />} title="The queue is empty" description="New submissions from employees will appear here." />
        ) : (
          <ul className="divide-y divide-slate-100">
            {query.data.map((request) => {
              const own = request.requester.id === user.id
              const warnings = request.validation?.issues.filter((i) => i.severity === 'WARNING').length ?? 0
              const errors = request.validation?.issues.filter((i) => i.severity === 'ERROR').length ?? 0
              return (
                <li key={request.id}>
                  <Link to={`/approvals/${request.id}`} className="flex items-center gap-4 px-5 py-4 hover:bg-slate-50">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-xs font-medium text-brand-700">{request.reference}</span>
                        <Badge>{categoryLabels[request.category]}</Badge>
                        {own && <Badge tone="purple">Your request</Badge>}
                        {errors > 0 && (
                          <Badge tone="red" icon={<CircleAlert className="size-3" />}>
                            {errors} validation error{errors === 1 ? '' : 's'}
                          </Badge>
                        )}
                        {warnings > 0 && (
                          <Badge tone="amber" icon={<TriangleAlert className="size-3" />}>
                            {warnings} warning{warnings === 1 ? '' : 's'}
                          </Badge>
                        )}
                      </div>
                      <p className="mt-1 truncate text-sm font-medium text-slate-900">{request.title}</p>
                      <p className="mt-0.5 flex items-center gap-1.5 text-xs text-slate-500">
                        <UserRound className="size-3.5" />
                        {request.requester.fullName}
                        <span>·</span>
                        <span title={formatDateTime(request.submittedAt)}>submitted {formatRelative(request.submittedAt)}</span>
                      </p>
                    </div>
                    <ChevronRight className="size-5 shrink-0 text-slate-300" />
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </Card>
    </>
  )
}
