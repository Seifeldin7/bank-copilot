import { http } from 'msw'
import type { RunSummary, RunTrace, UsageByDay, UsageByModel, UsageSummary } from '@/api/types'
import { db, type MockRun } from '../db'
import { authenticate, handle, notFound, param } from '../http'

const toSummary = (run: MockRun): RunSummary => ({
  runId: run.response.runId,
  user: run.user,
  question: run.response.question,
  mode: run.response.mode,
  refused: run.response.refused,
  latencyMs: run.response.latencyMs,
  totalTokens: run.response.usage.totalTokens,
  estimatedCostUsd: run.response.usage.estimatedCostUsd,
  createdAt: run.response.createdAt,
})

const round = (value: number) => Number(value.toFixed(6))

export const auditHandlers = [
  http.get(
    '/api/audit/runs',
    handle(({ request }) => {
      authenticate(request, 'SUPERVISOR')
      return db.runs.map(toSummary).sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    }),
  ),

  http.get(
    '/api/audit/runs/:runId',
    handle(({ request, params }) => {
      authenticate(request, 'SUPERVISOR')
      const runId = param(params, 'runId')
      const run = db.runs.find((r) => r.response.runId === runId)
      if (!run) throw notFound('Run not found.')
      const relatedRefs = new Set(db.requests.filter((r) => r.sourceRunId === runId).map((r) => r.reference))
      const trace: RunTrace = {
        run: toSummary(run),
        steps: run.response.steps,
        modelCalls: run.modelCalls,
        events: db.events.filter((e) => e.runId === runId || relatedRefs.has(e.resourceId)).sort((a, b) => a.at.localeCompare(b.at)),
      }
      return trace
    }),
  ),

  http.get(
    '/api/audit/events',
    handle(({ request }) => {
      authenticate(request, 'SUPERVISOR')
      return [...db.events].sort((a, b) => b.at.localeCompare(a.at)).slice(0, 200)
    }),
  ),

  http.get(
    '/api/audit/usage',
    handle(({ request }) => {
      authenticate(request, 'SUPERVISOR')
      const url = new URL(request.url)
      const to = url.searchParams.get('to') ?? new Date().toISOString().slice(0, 10)
      const from = url.searchParams.get('from') ?? new Date(Date.now() - 29 * 86_400_000).toISOString().slice(0, 10)
      const runs = db.runs.filter((r) => {
        const day = r.response.createdAt.slice(0, 10)
        return day >= from && day <= to
      })
      const byDay = new Map<string, UsageByDay>()
      const byModel = new Map<string, UsageByModel>()
      let promptTokens = 0
      let completionTokens = 0
      let estimatedCostUsd = 0
      for (const run of runs) {
        const date = run.response.createdAt.slice(0, 10)
        const day = byDay.get(date) ?? { date, runs: 0, promptTokens: 0, completionTokens: 0, estimatedCostUsd: 0 }
        day.runs += 1
        for (const call of run.modelCalls) {
          day.promptTokens += call.promptTokens
          day.completionTokens += call.completionTokens
          day.estimatedCostUsd = round(day.estimatedCostUsd + call.estimatedCostUsd)
          const key = `${call.provider}/${call.model}`
          const model = byModel.get(key) ?? { provider: call.provider, model: call.model, calls: 0, totalTokens: 0, estimatedCostUsd: 0 }
          model.calls += 1
          model.totalTokens += call.promptTokens + call.completionTokens
          model.estimatedCostUsd = round(model.estimatedCostUsd + call.estimatedCostUsd)
          byModel.set(key, model)
          promptTokens += call.promptTokens
          completionTokens += call.completionTokens
          estimatedCostUsd += call.estimatedCostUsd
        }
        byDay.set(date, day)
      }
      const summary: UsageSummary = {
        from,
        to,
        runs: runs.length,
        totals: { promptTokens, completionTokens, totalTokens: promptTokens + completionTokens, estimatedCostUsd: round(estimatedCostUsd) },
        byDay: [...byDay.values()].sort((a, b) => b.date.localeCompare(a.date)),
        byModel: [...byModel.values()],
      }
      return summary
    }),
  ),
]
