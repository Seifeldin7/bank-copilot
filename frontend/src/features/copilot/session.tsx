import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { copilotApi } from '@/api/endpoints'
import { CopilotSessionContext, type Exchange } from './session-context'

export function CopilotSessionProvider({ children }: { children: ReactNode }) {
  const [exchanges, setExchanges] = useState<Exchange[]>([])

  const ask = useCallback(async (question: string) => {
    const id = crypto.randomUUID()
    setExchanges((current) => [...current, { id, question, status: 'pending' }])
    try {
      const response = await copilotApi.ask({ question })
      setExchanges((current) => current.map((e) => (e.id === id ? { ...e, status: 'done', response } : e)))
    } catch (error) {
      setExchanges((current) => current.map((e) => (e.id === id ? { ...e, status: 'error', error } : e)))
    }
  }, [])

  const clear = useCallback(() => setExchanges([]), [])
  const busy = exchanges.some((e) => e.status === 'pending')
  const value = useMemo(() => ({ exchanges, busy, ask, clear }), [exchanges, busy, ask, clear])

  return <CopilotSessionContext.Provider value={value}>{children}</CopilotSessionContext.Provider>
}
