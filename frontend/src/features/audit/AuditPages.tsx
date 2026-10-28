import { useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Search } from 'lucide-react'
import { ApiError } from '@/api/client'
import { auditApi } from '@/api/endpoints'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Input } from '@/components/ui/Field'
import { PageHeader, Stat, Table, TBody, Tabs, Td, Th, THead, Tr } from '@/components/ui/Layout'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/States'
import { NotFoundPage } from '@/features/errors/ErrorPages'
import { AgentStepList } from '@/features/copilot/AgentTrace'
import { cn } from '@/lib/cn'
import { formatCost, formatDate, formatDateTime, formatDuration, formatNumber, formatRelative } from '@/lib/format'
import { agentLabels } from '@/lib/labels'

type AuditTab = 'runs' | 'usage' | 'activity'

export function AuditPage() {
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as AuditTab | null) ?? 'runs'
  return (
    <>
      <PageHeader title="Audit & usage" description="Trace every copilot run through the orchestrator, agents and model calls, review AI cost, and inspect the activity log." />
      <div className="mb-6">
        <Tabs
          value={tab}
          onChange={(value) => setParams({ tab: value })}
          items={[
            { value: 'runs', label: 'Copilot runs' },
            { value: 'usage', label: 'AI usage & cost' },
            { value: 'activity', label: 'Activity log' },
          ]}
        />
      </div>
      {tab === 'runs' && <RunsTab />}
      {tab === 'usage' && <UsageTab />}
      {tab === 'activity' && <ActivityTab />}
    </>
  )
}

function RunsTab() {
  const query = useQuery({ queryKey: ['audit', 'runs'], queryFn: auditApi.runs })
  if (query.isPending) return <Spinner />
  if (query.error) return <ErrorState error={query.error} />
  return (
    <Card>
      {query.data.length === 0 ? (
        <EmptyState title="No copilot runs yet" />
      ) : (
        <Table>
          <THead>
            <tr>
              <Th>When</Th>
              <Th>User</Th>
              <Th>Question</Th>
              <Th>Outcome</Th>
              <Th className="text-right">Latency</Th>
              <Th className="text-right">Tokens</Th>
              <Th className="text-right">Cost</Th>
            </tr>
          </THead>
          <TBody>
            {query.data.map((run) => (
              <Tr key={run.runId}>
                <Td className="whitespace-nowrap text-xs text-slate-500" title={formatDateTime(run.createdAt)}>
                  {formatRelative(run.createdAt)}
                </Td>
                <Td className="whitespace-nowrap">{run.user.fullName}</Td>
                <Td className="max-w-md">
                  <Link to={`/audit/runs/${run.runId}`} className="font-medium text-slate-900 hover:text-brand-700">
                    {run.question}
                  </Link>
                  <p className="font-mono text-[11px] text-slate-400">{run.runId}</p>
                </Td>
                <Td>
                  <div className="flex flex-wrap gap-1">
                    <Badge tone={run.refused ? 'amber' : 'green'}>{run.refused ? 'Refused' : 'Answered'}</Badge>
                    {run.mode === 'FALLBACK' && <Badge tone="red">Fallback</Badge>}
                  </div>
                </Td>
                <Td className="text-right tabular-nums">{formatDuration(run.latencyMs)}</Td>
                <Td className="text-right tabular-nums">{formatNumber(run.totalTokens)}</Td>
                <Td className="text-right tabular-nums">{formatCost(run.estimatedCostUsd)}</Td>
              </Tr>
            ))}
          </TBody>
        </Table>
      )}
    </Card>
  )
}

function UsageTab() {
  const [today] = useState(() => new Date().toISOString().slice(0, 10))
  const [from, setFrom] = useState(() => new Date(Date.now() - 29 * 86_400_000).toISOString().slice(0, 10))
  const [to, setTo] = useState(today)
  const query = useQuery({ queryKey: ['audit', 'usage', from, to], queryFn: () => auditApi.usage(from, to) })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs font-medium text-slate-500">From</span>
          <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-xs font-medium text-slate-500">To</span>
          <Input type="date" value={to} min={from} max={today} onChange={(e) => setTo(e.target.value)} />
        </label>
      </div>
      {query.isPending ? (
        <Spinner />
      ) : query.error ? (
        <ErrorState error={query.error} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat label="Copilot runs" value={formatNumber(query.data.runs)} />
            <Stat label="Prompt tokens" value={formatNumber(query.data.totals.promptTokens)} />
            <Stat label="Completion tokens" value={formatNumber(query.data.totals.completionTokens)} />
            <Stat label="Estimated cost" value={formatCost(query.data.totals.estimatedCostUsd)} hint="From the configured model price table" />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader title="By day" />
              {query.data.byDay.length === 0 ? (
                <EmptyState title="No usage in this period" />
              ) : (
                <Table>
                  <THead>
                    <tr>
                      <Th>Date</Th>
                      <Th className="text-right">Runs</Th>
                      <Th className="text-right">Tokens</Th>
                      <Th className="text-right">Cost</Th>
                    </tr>
                  </THead>
                  <TBody>
                    {query.data.byDay.map((day) => (
                      <tr key={day.date}>
                        <Td>{formatDate(day.date)}</Td>
                        <Td className="text-right tabular-nums">{day.runs}</Td>
                        <Td className="text-right tabular-nums">{formatNumber(day.promptTokens + day.completionTokens)}</Td>
                        <Td className="text-right tabular-nums">{formatCost(day.estimatedCostUsd)}</Td>
                      </tr>
                    ))}
                  </TBody>
                </Table>
              )}
            </Card>
            <Card>
              <CardHeader title="By model" />
              {query.data.byModel.length === 0 ? (
                <EmptyState title="No model calls in this period" />
              ) : (
                <Table>
                  <THead>
                    <tr>
                      <Th>Provider / model</Th>
                      <Th className="text-right">Calls</Th>
                      <Th className="text-right">Tokens</Th>
                      <Th className="text-right">Cost</Th>
                    </tr>
                  </THead>
                  <TBody>
                    {query.data.byModel.map((model) => (
                      <tr key={model.provider + model.model}>
                        <Td className="font-mono text-xs">
                          {model.provider}/{model.model}
                        </Td>
                        <Td className="text-right tabular-nums">{model.calls}</Td>
                        <Td className="text-right tabular-nums">{formatNumber(model.totalTokens)}</Td>
                        <Td className="text-right tabular-nums">{formatCost(model.estimatedCostUsd)}</Td>
                      </tr>
                    ))}
                  </TBody>
                </Table>
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  )
}

function ActivityTab() {
  const query = useQuery({ queryKey: ['audit', 'events'], queryFn: auditApi.events })
  const [filter, setFilter] = useState('')
  const events = useMemo(() => {
    const term = filter.trim().toLowerCase()
    return (query.data ?? []).filter((e) => !term || `${e.action} ${e.actor.fullName} ${e.resourceId} ${e.detail} ${e.runId}`.toLowerCase().includes(term))
  }, [query.data, filter])

  return (
    <Card>
      <div className="border-b border-slate-100 px-5 py-4">
        <div className="relative max-w-md">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input className="pl-9" placeholder="Filter by action, user, reference or run ID" value={filter} onChange={(e) => setFilter(e.target.value)} />
        </div>
      </div>
      {query.isPending ? (
        <Spinner />
      ) : query.error ? (
        <div className="p-5">
          <ErrorState error={query.error} />
        </div>
      ) : events.length === 0 ? (
        <EmptyState title="No matching events" />
      ) : (
        <Table>
          <THead>
            <tr>
              <Th>When</Th>
              <Th>Action</Th>
              <Th>Actor</Th>
              <Th>Resource</Th>
              <Th>Detail</Th>
              <Th>Run ID</Th>
            </tr>
          </THead>
          <TBody>
            {events.map((event) => (
              <tr key={event.id}>
                <Td className="whitespace-nowrap text-xs text-slate-500">{formatDateTime(event.at)}</Td>
                <Td className="whitespace-nowrap font-mono text-xs">{event.action}</Td>
                <Td className="whitespace-nowrap">{event.actor.fullName}</Td>
                <Td className="whitespace-nowrap text-xs">
                  <span className="text-slate-500">{event.resourceType}</span> <span className="font-mono">{event.resourceId}</span>
                </Td>
                <Td className="max-w-sm text-xs text-slate-600">{event.detail}</Td>
                <Td className="whitespace-nowrap font-mono text-[11px] text-slate-400">{event.runId}</Td>
              </tr>
            ))}
          </TBody>
        </Table>
      )}
    </Card>
  )
}

export function RunTracePage() {
  const { runId = '' } = useParams()
  const query = useQuery({ queryKey: ['audit', 'run', runId], queryFn: () => auditApi.run(runId), retry: false })
  if (query.isPending) return <Spinner />
  if (query.error) {
    if (query.error instanceof ApiError && query.error.status === 404) return <NotFoundPage title="Run not found" />
    return <ErrorState error={query.error} />
  }
  const { run, steps, modelCalls, events } = query.data
  const total = Math.max(1, steps.reduce((sum, s) => sum + s.durationMs, 0))
  const offsets = steps.map((_, index) => steps.slice(0, index).reduce((sum, s) => sum + s.durationMs, 0))

  return (
    <div className="space-y-6">
      <div>
        <Link to="/audit" className="mb-4 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-800">
          <ArrowLeft className="size-4" />
          Audit
        </Link>
        <p className="font-mono text-sm text-slate-500">{run.runId}</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{run.question}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-slate-500">
          <Badge tone={run.refused ? 'amber' : 'green'}>{run.refused ? 'Refused' : 'Answered'}</Badge>
          <Badge tone={run.mode === 'FALLBACK' ? 'red' : 'purple'}>{run.mode === 'FALLBACK' ? 'Fallback' : 'Orchestrated'}</Badge>
          <span>
            {run.user.fullName} · {formatDateTime(run.createdAt)}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Latency" value={formatDuration(run.latencyMs)} />
        <Stat label="Agent steps" value={steps.length} />
        <Stat label="Total tokens" value={formatNumber(run.totalTokens)} />
        <Stat label="Estimated cost" value={formatCost(run.estimatedCostUsd)} />
      </div>

      <Card>
        <CardHeader title="Timeline" description="API → orchestrator → agents → model calls" />
        <CardBody className="space-y-2">
          {steps.map((step, index) => {
            const left = (offsets[index] / total) * 100
            const width = Math.max(1.5, (step.durationMs / total) * 100)
            return (
              <div key={step.step} className="grid grid-cols-[12rem_1fr_4rem] items-center gap-3 text-xs">
                <span className="truncate text-slate-700">{agentLabels[step.agent]}</span>
                <div className="relative h-5 rounded bg-slate-100">
                  <div
                    className={cn(
                      'absolute inset-y-0 rounded',
                      step.status === 'SUCCEEDED' && 'bg-brand-500',
                      (step.status === 'TIMED_OUT' || step.status === 'FAILED') && 'bg-red-500',
                      step.status === 'SKIPPED' && 'bg-slate-300',
                    )}
                    style={{ left: `${left}%`, width: `${Math.min(width, 100 - left)}%` }}
                  />
                </div>
                <span className="text-right tabular-nums text-slate-500">{formatDuration(step.durationMs)}</span>
              </div>
            )
          })}
        </CardBody>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Agent steps" />
          <CardBody>
            <AgentStepList steps={steps} />
          </CardBody>
        </Card>
        <div className="space-y-6">
          <Card>
            <CardHeader title="Model calls" description="Token usage and estimated cost per call" />
            {modelCalls.length === 0 ? (
              <EmptyState title="No model calls" />
            ) : (
              <Table>
                <THead>
                  <tr>
                    <Th>Step</Th>
                    <Th>Model</Th>
                    <Th className="text-right">In / out</Th>
                    <Th className="text-right">Cost</Th>
                  </tr>
                </THead>
                <TBody>
                  {modelCalls.map((call) => (
                    <tr key={call.id}>
                      <Td className="text-xs">
                        {call.step}. {agentLabels[call.agent]}
                      </Td>
                      <Td className="font-mono text-xs">{call.model}</Td>
                      <Td className="text-right text-xs tabular-nums">
                        {formatNumber(call.promptTokens)} / {formatNumber(call.completionTokens)}
                      </Td>
                      <Td className="text-right text-xs tabular-nums">{formatCost(call.estimatedCostUsd)}</Td>
                    </tr>
                  ))}
                </TBody>
              </Table>
            )}
          </Card>
          <Card>
            <CardHeader title="Related audit events" />
            <CardBody>
              {events.length === 0 ? (
                <p className="text-sm text-slate-500">No related events.</p>
              ) : (
                <ul className="space-y-2 text-sm">
                  {events.map((event) => (
                    <li key={event.id} className="flex items-start justify-between gap-3">
                      <span>
                        <span className="font-mono text-xs text-slate-700">{event.action}</span>
                        <span className="block text-xs text-slate-500">
                          {event.actor.fullName} · {event.resourceId}
                        </span>
                      </span>
                      <span className="whitespace-nowrap text-xs text-slate-400">{formatDateTime(event.at)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  )
}
