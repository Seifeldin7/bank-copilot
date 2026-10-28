import type { ReactNode } from 'react'
import { CircleCheck, CircleDashed, CircleX, Clock3, Repeat } from 'lucide-react'
import type { AgentStep, StepStatus, UsageTotals } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { formatCost, formatDuration, formatNumber } from '@/lib/format'
import { agentLabels, stepTones } from '@/lib/labels'

const statusIcon: Record<StepStatus, ReactNode> = {
  SUCCEEDED: <CircleCheck className="size-5 text-emerald-600" />,
  FAILED: <CircleX className="size-5 text-red-600" />,
  TIMED_OUT: <Clock3 className="size-5 text-red-600" />,
  SKIPPED: <CircleDashed className="size-5 text-slate-400" />,
}

const statusText: Record<StepStatus, string> = {
  SUCCEEDED: 'Succeeded',
  FAILED: 'Failed',
  TIMED_OUT: 'Timed out',
  SKIPPED: 'Skipped',
}

export function UsageSummaryRow({ usage, latencyMs }: { usage: UsageTotals; latencyMs?: number }) {
  const items = [
    { label: 'Prompt tokens', value: formatNumber(usage.promptTokens) },
    { label: 'Completion tokens', value: formatNumber(usage.completionTokens) },
    { label: 'Estimated cost', value: formatCost(usage.estimatedCostUsd) },
    ...(latencyMs !== undefined ? [{ label: 'Latency', value: formatDuration(latencyMs) }] : []),
  ]
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {items.map((item) => (
        <div key={item.label} className="rounded-lg bg-slate-50 px-3 py-2">
          <dt className="text-[11px] font-medium text-slate-500">{item.label}</dt>
          <dd className="text-sm font-semibold tabular-nums text-slate-900">{item.value}</dd>
        </div>
      ))}
    </dl>
  )
}

export function AgentStepList({ steps }: { steps: AgentStep[] }) {
  return (
    <ol className="space-y-3">
      {steps.map((step) => (
        <li key={step.step} className="flex gap-3 rounded-lg border border-slate-200 p-3">
          <div className="pt-0.5">{statusIcon[step.status]}</div>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-medium text-slate-900">
                <span className="mr-1.5 text-slate-400">{step.step}.</span>
                {agentLabels[step.agent]}
              </p>
              <Badge tone={stepTones[step.status]}>{statusText[step.status]}</Badge>
            </div>
            <p className="mt-1 text-sm text-slate-600">{step.summary}</p>
            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
              <span className="flex items-center gap-1">
                <Clock3 className="size-3.5" />
                {formatDuration(step.durationMs)}
              </span>
              {step.iterations > 1 && (
                <span className="flex items-center gap-1">
                  <Repeat className="size-3.5" />
                  {step.iterations} iterations
                </span>
              )}
              {step.promptTokens > 0 ? (
                <span className="tabular-nums">
                  {formatNumber(step.promptTokens)} in / {formatNumber(step.completionTokens)} out · {formatCost(step.estimatedCostUsd)}
                </span>
              ) : (
                <span>no model call</span>
              )}
            </div>
          </div>
        </li>
      ))}
    </ol>
  )
}
