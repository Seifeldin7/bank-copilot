import { createContext, useContext } from 'react'
import type { Role, UserSummary } from '@/api/types'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  user: UserSummary | null
  login: (username: string, password: string) => Promise<UserSummary>
  logout: () => void
  hasRole: (role: Role) => boolean
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

export function useCurrentUser() {
  const { user } = useAuth()
  if (!user) throw new Error('No authenticated user')
  return user
}
