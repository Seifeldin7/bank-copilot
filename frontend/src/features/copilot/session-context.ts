import { createContext, useContext } from 'react'
import type { AskResponse } from '@/api/types'

export interface Exchange {
  id: string
  question: string
  status: 'pending' | 'done' | 'error'
  response?: AskResponse
  error?: unknown
}

export interface CopilotSessionValue {
  exchanges: Exchange[]
  busy: boolean
  ask: (question: string) => Promise<void>
  clear: () => void
}

export const CopilotSessionContext = createContext<CopilotSessionValue | null>(null)

export function useCopilotSession() {
  const context = useContext(CopilotSessionContext)
  if (!context) throw new Error('useCopilotSession must be used inside CopilotSessionProvider')
  return context
}
