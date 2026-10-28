import type {
  AgentName,
  AgentStep,
  AskResponse,
  Citation,
  ModelCall,
  RequestCategory,
  StepStatus,
  ValidationVerdict,
} from '@/api/types'
import { passages, stopWords, type Passage, type SeedDocument } from './knowledge'

const PROVIDER = 'openai'
const MODEL = 'gpt-4o-mini'
const INPUT_PER_MILLION = 0.15
const OUTPUT_PER_MILLION = 0.6

const injectionPattern =
  /(ignore (all )?(the )?(previous|prior|above) (instructions|rules)|system prompt|system override|you are now|pretend to be|disregard (your|the) (rules|instructions)|approve (this|my|the) request)/i
const fallbackPattern = /simulate (an )?(agent )?(failure|timeout)/i

export function tokenize(text: string) {
  return Array.from(
    new Set(
      text
        .toLowerCase()
        .replace(fallbackPattern, ' ')
        .split(/[^a-z0-9]+/)
        .map((t) => (t.length > 4 && t.endsWith('s') ? t.slice(0, -1) : t))
        .filter((t) => t.length > 1 && !stopWords.has(t)),
    ),
  )
}

function words(text: string) {
  return text.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean)
}

function matches(token: string, vocabulary: string[]) {
  return vocabulary.some((w) => w === token || (token.length >= 4 && w.startsWith(token)) || (w.length >= 4 && token.startsWith(w)))
}

interface Scored {
  passage: Passage
  document: SeedDocument
  ratio: number
  matched: string[]
}

function cost(promptTokens: number, completionTokens: number) {
  return Number(((promptTokens * INPUT_PER_MILLION + completionTokens * OUTPUT_PER_MILLION) / 1_000_000).toFixed(6))
}

function estimateTokens(text: string) {
  return Math.max(1, Math.round(text.length / 4))
}

export interface CopilotRunResult {
  response: AskResponse
  modelCalls: ModelCall[]
}

export function runCopilot(
  question: string,
  documents: SeedDocument[],
  corpus: Passage[] = passages,
  now = new Date(),
): CopilotRunResult {
  const runId = `run-${crypto.randomUUID().slice(0, 8)}`
  const tokens = tokenize(question)
  const forcedFallback = fallbackPattern.test(question)
  const injection = injectionPattern.test(question)
  const docById = new Map(documents.map((d) => [d.id, d]))

  let clock = now.getTime()
  const steps: AgentStep[] = []
  const modelCalls: ModelCall[] = []

  const addStep = (
    agent: AgentName,
    status: StepStatus,
    summary: string,
    durationMs: number,
    promptTokens = 0,
    completionTokens = 0,
    iterations = 1,
  ) => {
    const step: AgentStep = {
      step: steps.length + 1,
      agent,
      status,
      summary,
      iterations,
      durationMs,
      promptTokens,
      completionTokens,
      estimatedCostUsd: cost(promptTokens, completionTokens),
      startedAt: new Date(clock).toISOString(),
    }
    steps.push(step)
    if (promptTokens > 0) {
      modelCalls.push({
        id: `mc-${crypto.randomUUID().slice(0, 8)}`,
        step: step.step,
        agent,
        provider: PROVIDER,
        model: MODEL,
        promptTokens,
        completionTokens,
        estimatedCostUsd: step.estimatedCostUsd,
        latencyMs: Math.max(80, durationMs - 40),
        at: new Date(clock).toISOString(),
      })
    }
    clock += durationMs
  }

  let excludedSuperseded = 0
  let excludedSuspicious = 0
  const scored: Scored[] = []
  for (const passage of corpus) {
    const document = docById.get(passage.documentId)
    if (!document) continue
    const vocabulary = words(`${document.title} ${passage.section ?? ''} ${passage.text}`)
    const matched = tokens.filter((t) => matches(t, vocabulary))
    if (matched.length === 0) continue
    if (document.status !== 'ACTIVE') {
      excludedSuperseded += 1
      continue
    }
    if (passage.suspicious) {
      excludedSuspicious += 1
      continue
    }
    scored.push({ passage, document, ratio: tokens.length ? matched.length / tokens.length : 0, matched })
  }
  scored.sort((a, b) => b.ratio - a.ratio || b.matched.length - a.matched.length)

  const evidence = scored.filter((s) => s.matched.length >= 2 && s.ratio >= 0.34).slice(0, 3)
  const strong = evidence.filter((s) => s.ratio >= 0.6)
  const ambiguous = tokens.length < 2
  const top = scored[0]

  addStep('ORCHESTRATOR', 'SUCCEEDED', forcedFallback ? 'Planned retrieve → validate → answer' : 'Planned retrieve → validate → answer', 6)

  const exclusions = [
    excludedSuperseded ? `${excludedSuperseded} superseded` : null,
    excludedSuspicious ? `${excludedSuspicious} flagged as suspicious` : null,
  ].filter(Boolean)
  const retrievalSummary = `Hybrid search: ${Math.min(20, scored.length + 6)} semantic and ${scored.length} keyword hits fused by RRF; ${evidence.length} passages above threshold${exclusions.length ? `; excluded ${exclusions.join(', ')}` : ''}`

  let refused = false
  let refusalReason: string | null = null
  let verdict: ValidationVerdict

  if (forcedFallback) {
    addStep('KNOWLEDGE_RETRIEVAL', 'TIMED_OUT', 'Step exceeded the 8 s timeout; orchestration aborted', 8000, 0, 0, 1)
  } else {
    const iterations = evidence.length < 2 && !injection && !ambiguous ? 2 : 1
    addStep('KNOWLEDGE_RETRIEVAL', 'SUCCEEDED', iterations > 1 ? `${retrievalSummary}; query reformulated once` : retrievalSummary, 140 + iterations * 90, iterations > 1 ? 180 : 0, iterations > 1 ? 24 : 0, iterations)
  }

  if (injection) {
    refused = true
    refusalReason = 'The question contains instructions that try to change how the copilot behaves. I can only answer questions about approved banking procedures.'
    verdict = { sufficient: false, missingInformation: [], unsupportedAssumptions: ['Question attempts to override copilot rules'], confidence: 0.98 }
  } else if (ambiguous) {
    refused = true
    refusalReason = 'The question is too broad to answer reliably. Please say which product or procedure you mean, for example "debit card replacement fee".'
    verdict = { sufficient: false, missingInformation: ['Which product, service or procedure the question is about'], unsupportedAssumptions: [], confidence: 0.4 }
  } else if (strong.length === 0) {
    refused = true
    refusalReason = 'None of the approved documents contain enough information to answer this question. Please consult your supervisor or the relevant department.'
    verdict = {
      sufficient: false,
      missingInformation: [top ? `Closest passage ("${top.document.title}") does not address the question directly` : 'No relevant passage found in the knowledge base'],
      unsupportedAssumptions: [],
      confidence: top ? Number((0.3 + top.ratio * 0.3).toFixed(2)) : 0.15,
    }
  } else {
    verdict = {
      sufficient: true,
      missingInformation: [],
      unsupportedAssumptions: [],
      confidence: Number(Math.min(0.96, 0.55 + strong[0].ratio * 0.4).toFixed(2)),
    }
  }

  const evidenceChars = evidence.reduce((sum, e) => sum + e.passage.text.length, 0)
  const answerSource = forcedFallback ? evidence : strong
  const citations: Citation[] = refused
    ? []
    : answerSource.map((e, i) => ({
        index: i + 1,
        chunkId: e.passage.id,
        documentId: e.document.id,
        documentTitle: e.document.title,
        version: e.document.version,
        page: e.passage.page,
        section: e.passage.section,
        snippet: e.passage.text,
        score: Number(e.ratio.toFixed(2)),
      }))

  let answer: string | null = null
  if (!refused && citations.length > 0) {
    answer = answerSource
      .map((e, i) => {
        const sentences = e.passage.text.split(/(?<=\.)\s+/)
        const relevant = sentences.filter((s) => e.matched.some((t) => matches(t, words(s)))).slice(0, 2)
        return `${(relevant.length ? relevant : sentences.slice(0, 1)).join(' ')} [${i + 1}]`
      })
      .join(' ')
  } else if (!refused) {
    refused = true
    refusalReason = 'None of the approved documents contain enough information to answer this question.'
  }

  const validationPrompt = 320 + estimateTokens(question) + Math.round(evidenceChars / 4)
  if (forcedFallback) {
    addStep('POLICY_VALIDATION', 'SKIPPED', 'Skipped after orchestration failure', 0)
    addStep(
      'FALLBACK_ANSWER',
      'SUCCEEDED',
      refused ? 'Plain retrieval found no sufficient evidence; refused' : `Plain hybrid retrieval and grounded answer with ${citations.length} citations`,
      610,
      refused ? 0 : 410 + Math.round(evidenceChars / 4),
      refused ? 0 : estimateTokens(answer ?? ''),
    )
  } else {
    addStep(
      'POLICY_VALIDATION',
      'SUCCEEDED',
      verdict.sufficient ? `Evidence sufficient (confidence ${verdict.confidence})` : `Evidence insufficient: ${[...verdict.missingInformation, ...verdict.unsupportedAssumptions].join('; ') || 'below threshold'}`,
      420,
      validationPrompt,
      64,
    )
    if (refused) {
      addStep('ANSWER_GENERATION', 'SKIPPED', 'Skipped: validation did not pass, refusal returned', 0)
    } else {
      addStep('ANSWER_GENERATION', 'SUCCEEDED', `Grounded answer with ${citations.length} verified citation${citations.length === 1 ? '' : 's'}`, 760, 450 + Math.round(evidenceChars / 4), estimateTokens(answer ?? '') + 20)
    }
  }

  const promptTokens = steps.reduce((s, x) => s + x.promptTokens, 0)
  const completionTokens = steps.reduce((s, x) => s + x.completionTokens, 0)
  const suggestedCategory: RequestCategory | null = refused ? null : (answerSource[0]?.document.requestCategory ?? null)

  return {
    response: {
      runId,
      question,
      answer: refused ? null : answer,
      refused,
      refusalReason: refused ? refusalReason : null,
      mode: forcedFallback ? 'FALLBACK' : 'ORCHESTRATED',
      citations,
      verdict,
      suggestedCategory,
      steps,
      usage: {
        promptTokens,
        completionTokens,
        totalTokens: promptTokens + completionTokens,
        estimatedCostUsd: cost(promptTokens, completionTokens),
      },
      latencyMs: clock - now.getTime(),
      createdAt: now.toISOString(),
    },
    modelCalls,
  }
}

export function preparationCall(stepNumber: number, at: Date): { step: AgentStep; call: ModelCall } {
  const promptTokens = 520
  const completionTokens = 140
  const estimatedCostUsd = cost(promptTokens, completionTokens)
  return {
    step: {
      step: stepNumber,
      agent: 'REQUEST_PREPARATION',
      status: 'SUCCEEDED',
      summary: 'Structured draft produced; saved as DRAFT for the employee to complete and submit',
      iterations: 1,
      durationMs: 690,
      promptTokens,
      completionTokens,
      estimatedCostUsd,
      startedAt: at.toISOString(),
    },
    call: {
      id: `mc-${crypto.randomUUID().slice(0, 8)}`,
      step: stepNumber,
      agent: 'REQUEST_PREPARATION',
      provider: PROVIDER,
      model: MODEL,
      promptTokens,
      completionTokens,
      estimatedCostUsd,
      latencyMs: 640,
      at: at.toISOString(),
    },
  }
}
