import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { usersApi } from '@/api/endpoints'
import type { RequestCategory, RequestStatus } from '@/api/types'
import { LinkButton } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Field'
import { PageHeader, Tabs } from '@/components/ui/Layout'
import { ErrorState, Spinner } from '@/components/ui/States'
import { categoryLabels, statusLabels } from '@/lib/labels'
import { RequestsTable } from './components'
import { useRequests } from './hooks'

type StatusTab = 'ALL' | RequestStatus

const tabs: StatusTab[] = ['ALL', 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'EXECUTED', 'REJECTED']

export function MyRequestsPage() {
  const [tab, setTab] = useState<StatusTab>('ALL')
  const query = useRequests({ scope: 'mine' })
  const requests = query.data ?? []
  const filtered = tab === 'ALL' ? requests : requests.filter((r) => r.status === tab)

  return (
    <>
      <PageHeader
        title="My requests"
        description="Service requests you created. Drafts can be edited until you submit them; after that only a supervisor can decide."
        actions={
          <LinkButton to="/requests/new" icon={<Plus className="size-4" />}>
            New request
          </LinkButton>
        }
      />
      <Card>
        <div className="px-5 pt-3">
          <Tabs
            value={tab}
            onChange={setTab}
            items={tabs.map((value) => ({
              value,
              label: value === 'ALL' ? 'All' : statusLabels[value],
              count: value === 'ALL' ? requests.length : requests.filter((r) => r.status === value).length,
            }))}
          />
        </div>
        {query.isPending ? (
          <Spinner />
        ) : query.error ? (
          <div className="p-5">
            <ErrorState error={query.error} />
          </div>
        ) : (
          <RequestsTable
            requests={filtered}
            emptyTitle={tab === 'ALL' ? 'You have not created any requests yet' : `No ${statusLabels[tab as RequestStatus].toLowerCase()} requests`}
            emptyAction={
              tab === 'ALL' ? (
                <LinkButton to="/requests/new" size="sm" icon={<Plus className="size-4" />}>
                  Create your first request
                </LinkButton>
              ) : undefined
            }
          />
        )}
      </Card>
    </>
  )
}

export function AllRequestsPage() {
  const [status, setStatus] = useState<RequestStatus | ''>('')
  const [category, setCategory] = useState<RequestCategory | ''>('')
  const [requesterId, setRequesterId] = useState('')
  const users = useQuery({ queryKey: ['users'], queryFn: usersApi.list })
  const query = useRequests({ scope: 'all', status: status || undefined, category: category || undefined, requesterId: requesterId || undefined })

  return (
    <>
      <PageHeader title="All requests" description="Every service request in the branch, across all employees." />
      <Card>
        <div className="flex flex-wrap gap-3 border-b border-slate-100 px-5 py-4">
          <Select className="w-48" value={status} onChange={(e) => setStatus(e.target.value as RequestStatus | '')} aria-label="Filter by status">
            <option value="">All statuses</option>
            {(Object.keys(statusLabels) as RequestStatus[]).map((s) => (
              <option key={s} value={s}>
                {statusLabels[s]}
              </option>
            ))}
          </Select>
          <Select className="w-56" value={category} onChange={(e) => setCategory(e.target.value as RequestCategory | '')} aria-label="Filter by category">
            <option value="">All categories</option>
            {(Object.keys(categoryLabels) as RequestCategory[]).map((c) => (
              <option key={c} value={c}>
                {categoryLabels[c]}
              </option>
            ))}
          </Select>
          <Select className="w-56" value={requesterId} onChange={(e) => setRequesterId(e.target.value)} aria-label="Filter by employee">
            <option value="">All employees</option>
            {(users.data ?? []).map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
              </option>
            ))}
          </Select>
          {query.data && <p className="ml-auto self-center text-sm text-slate-500">{query.data.length} requests</p>}
        </div>
        {query.isPending ? (
          <Spinner />
        ) : query.error ? (
          <div className="p-5">
            <ErrorState error={query.error} />
          </div>
        ) : (
          <RequestsTable requests={query.data} showRequester emptyTitle="No requests match these filters" />
        )}
      </Card>
    </>
  )
}
