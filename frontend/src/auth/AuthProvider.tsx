import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { tokenStore } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { Role, UserSummary } from '@/api/types'
import { AuthContext, type AuthStatus } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<UserSummary | null>(null)
  const [status, setStatus] = useState<AuthStatus>(tokenStore.get() ? 'loading' : 'anonymous')

  const logout = useCallback(() => {
    tokenStore.set(null)
    setUser(null)
    setStatus('anonymous')
    queryClient.clear()
  }, [queryClient])

  useEffect(() => tokenStore.onUnauthorized(logout), [logout])

  useEffect(() => {
    if (!tokenStore.get()) return
    let cancelled = false
    authApi
      .me()
      .then((me) => {
        if (cancelled) return
        setUser(me)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        tokenStore.set(null)
        setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(
    async (username: string, password: string) => {
      const response = await authApi.login({ username, password })
      tokenStore.set(response.accessToken)
      queryClient.clear()
      setUser(response.user)
      setStatus('authenticated')
      return response.user
    },
    [queryClient],
  )

  const hasRole = useCallback(
    (role: Role) => {
      if (!user) return false
      if (role === 'EMPLOYEE') return true
      return user.role === 'SUPERVISOR'
    },
    [user],
  )

  const value = useMemo(() => ({ status, user, login, logout, hasRole }), [status, user, login, logout, hasRole])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
