import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import type { Role } from '@/api/types'
import { Spinner } from '@/components/ui/States'
import { ForbiddenPage } from '@/features/errors/ErrorPages'
import { useAuth } from './context'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <Spinner label="Restoring your session…" className="h-screen" />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <>{children}</>
}

export function RequireRole({ role }: { role: Role }) {
  const { hasRole } = useAuth()
  if (!hasRole(role)) return <ForbiddenPage />
  return <Outlet />
}
