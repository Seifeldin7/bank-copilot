import { Fragment, useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, FilePlus2, FileText, GitBranch, Route, ShieldAlert, TriangleAlert } from 'lucide-react'
import { copilotApi } from '@/api/endpoints'
import type { AskResponse, Citation } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Drawer } from '@/components/ui/Dialog'
import { ErrorState } from '@/components/ui/States'
import { cn } from '@/lib/cn'
import { formatDuration } from '@/lib/format'
import { categoryLabels } from '@/lib/labels'
import { AgentStepList, UsageSummaryRow } from './AgentTrace'

function citationLocation(citation: Citation) {
  return citation.page !== null ? `p. ${citation.page}` : citation.section ? `§ ${citation.section}` : ''
}

function AnswerText({ text, onCite, active }: { text: string; onCite: (index: number) => void; active: number | null }) {
  const parts = text.split(/(\[\d+\])/g)
  return (
    <p className="text-[15px] leading-7 text-slate-800">
      {parts.map((part, i) => {
        const match = /^\[(\d+)\]$/.exec(part)
        if (!match) return <Fragment key={i}>{part}</Fragment>
        const index = Number(match[1])
        return (
          <button
            key={i}
            type="button"
            onClick={() => onCite(index)}
            className={cn(
              'mx-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded px-1 align-text-top text-[11px] font-semibold',
              active === index ? 'bg-brand-700 text-white' : 'bg-brand-100 text-brand-800 hover:bg-brand-200',
            )}
          >
            {index}
          </button>
        )
      })}
    </p>
  )
}

function CitationItem({ citation, active, onToggle }: { citation: Citation; active: boolean; onToggle: () => void }) {
  return (
    <li className={cn('rounded-lg border transition-colors', active ? 'border-brand-300 bg-brand-50/50' : 'border-slate-200')}>
      <button type="button" onClick={onToggle} className="flex w-full items-start gap-3 px-3 py-2.5 text-left">
        <span className="mt-0.5 grid size-5 shrink-0 place-items-center rounded bg-brand-100 text-[11px] font-semibold text-brand-800">{citation.index}</span>
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-x-2 text-sm font-medium text-slate-900">
            <FileText className="size-3.5 text-slate-400" />
            {citation.documentTitle}
            <span className="font-normal text-slate-500">
              v{citation.version} · {citationLocation(citation)}
            </span>
          </span>
          {citation.section && citation.page !== null && <span className="block text-xs text-slate-500">{citation.section}</span>}
        </span>
        <span className="text-xs tabular-nums text-slate-400">score {citation.score.toFixed(2)}</span>
        <ChevronDown className={cn('size-4 text-slate-400 transition-transform', active && 'rotate-180')} />
      </button>
      {active && <blockquote className="mx-3 mb-3 border-l-2 border-brand-300 pl-3 text-sm italic text-slate-600">{citation.snippet}</blockquote>}
    </li>
  )
}

export function AnswerCard({ response }: { response: AskResponse }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [active, setActive] = useState<number | null>(null)
  const [traceOpen, setTraceOpen] = useState(false)
  const failedStep = response.steps.find((s) => s.status === 'TIMED_OUT' || s.status === 'FAILED')

  const prepare = useMutation({
    mutationFn: () => copilotApi.prepareRequest({ runId: response.runId }),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['requests'] })
      navigate(`/requests/${result.request.id}/edit`, { state: { fromCopilot: true, missingFields: result.missingFields } })
    },
  })

  return (
    <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-5 py-3">
        <div className="flex flex-wrap items-center gap-2">
          {response.refused ? (
            <Badge tone="amber" icon={<ShieldAlert className="size-3.5" />}>
              Declined to answer
            </Badge>
          ) : (
            <Badge tone="green">Grounded answer</Badge>
          )}
          <Badge tone={response.mode === 'FALLBACK' ? 'red' : 'purple'} icon={<GitBranch className="size-3" />}>
            {response.mode === 'FALLBACK' ? 'Fallback mode' : 'Multi-agent'}
          </Badge>
          {response.verdict && <span className="text-xs text-slate-500">confidence {Math.round(response.verdict.confidence * 100)}%</span>}
          <span className="text-xs text-slate-400">· {formatDuration(response.latencyMs)}</span>
        </div>
        <button type="button" onClick={() => setTraceOpen(true)} className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-brand-700">
          <Route className="size-3.5" />
          Agent trace
        </button>
      </div>

      <div className="space-y-4 px-5 py-4">
        {response.mode === 'FALLBACK' && (
          <div className="flex items-start gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">
            <TriangleAlert className="mt-0.5 size-4 shrink-0" />
            <span>
              Orchestration failed{failedStep ? ` (${failedStep.summary.toLowerCase()})` : ''}. This answer comes from the fallback path: plain hybrid retrieval and grounded answering.
            </span>
          </div>
        )}

        {response.refused ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
            <p className="text-sm font-medium text-amber-900">{response.refusalReason}</p>
            {response.verdict && (response.verdict.missingInformation.length > 0 || response.verdict.unsupportedAssumptions.length > 0) && (
              <ul className="mt-2 list-disc space-y-0.5 pl-5 text-sm text-amber-800">
                {[...response.verdict.missingInformation, ...response.verdict.unsupportedAssumptions].map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            )}
          </div>
        ) : (
          response.answer && <AnswerText text={response.answer} active={active} onCite={(i) => setActive(active === i ? null : i)} />
        )}

        {response.citations.length > 0 && (
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Sources</p>
            <ul className="space-y-2">
              {response.citations.map((citation) => (
                <CitationItem key={citation.chunkId} citation={citation} active={active === citation.index} onToggle={() => setActive(active === citation.index ? null : citation.index)} />
              ))}
            </ul>
          </div>
        )}

        {!response.refused && response.suggestedCategory && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-dashed border-slate-300 px-4 py-3">
            <p className="text-sm text-slate-600">
              Need to act on this? Prepare a <span className="font-medium text-slate-900">{categoryLabels[response.suggestedCategory]}</span> draft. It will not be submitted until you review it.
            </p>
            <Button size="sm" variant="secondary" icon={<FilePlus2 className="size-4" />} onClick={() => prepare.mutate()} loading={prepare.isPending}>
              Create draft request
            </Button>
          </div>
        )}
        {prepare.error && <ErrorState error={prepare.error} />}
      </div>

      <div className="border-t border-slate-100 px-5 py-2 font-mono text-[11px] text-slate-400">run {response.runId}</div>

      <Drawer open={traceOpen} onClose={() => setTraceOpen(false)} title="Agent trace" description={`Run ${response.runId} · ${response.mode === 'FALLBACK' ? 'fallback path' : 'orchestrated'}`}>
        <div className="space-y-5">
          <UsageSummaryRow usage={response.usage} latencyMs={response.latencyMs} />
          <AgentStepList steps={response.steps} />
          <p className="text-xs text-slate-500">
            Agents exchange typed messages, each has a step timeout and an iteration limit, and none of them can submit, approve or execute requests.
          </p>
        </div>
      </Drawer>
    </div>
  )
}
