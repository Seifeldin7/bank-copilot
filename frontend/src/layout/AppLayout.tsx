import { useState, type ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import {
  Activity,
  BookOpenText,
  ClipboardCheck,
  FileStack,
  FolderKanban,
  LogOut,
  Menu,
  MessageSquareText,
  RotateCcw,
  Users,
  X,
} from 'lucide-react'
import { approvalsApi } from '@/api/endpoints'
import { useAuth, useCurrentUser } from '@/auth/context'
import { Badge } from '@/components/ui/Badge'
import { CopilotSessionProvider } from '@/features/copilot/session'
import { cn } from '@/lib/cn'

const mockMode = import.meta.env.VITE_USE_MOCKS === 'true'

function NavItem({ to, icon, children, count, onNavigate }: { to: string; icon: ReactNode; children: ReactNode; count?: number; onNavigate: () => void }) {
  return (
    <NavLink
      to={to}
      onClick={onNavigate}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
          isActive ? 'bg-white/10 text-white' : 'text-brand-100/80 hover:bg-white/5 hover:text-white',
        )
      }
    >
      <span className="[&>svg]:size-4.5">{icon}</span>
      <span className="flex-1">{children}</span>
      {count !== undefined && count > 0 && <span className="rounded-full bg-amber-400 px-1.5 text-xs font-semibold text-amber-950">{count}</span>}
    </NavLink>
  )
}

function Sidebar({ onNavigate }: { onNavigate: () => void }) {
  const user = useCurrentUser()
  const { logout, hasRole } = useAuth()
  const supervisor = hasRole('SUPERVISOR')
  const queue = useQuery({ queryKey: ['approvals', 'queue'], queryFn: approvalsApi.queue, enabled: supervisor, refetchInterval: 30_000 })

  const resetDemo = async () => {
    const { resetDb } = await import('@/mocks/db')
    resetDb()
    logout()
    window.location.assign('/login')
  }

  return (
    <div className="flex h-full flex-col bg-brand-900 text-white">
      <div className="flex items-center gap-3 px-5 py-5">
        <div className="grid size-9 place-items-center rounded-lg bg-white/10 font-bold">B</div>
        <div className="leading-tight">
          <p className="text-sm font-semibold">Branch Operations</p>
          <p className="text-xs text-brand-200">Internal Copilot</p>
        </div>
      </div>

      <nav className="flex-1 space-y-6 overflow-y-auto px-3 py-2">
        <div className="space-y-1">
          <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-brand-300">Workspace</p>
          <NavItem to="/copilot" icon={<MessageSquareText />} onNavigate={onNavigate}>
            Copilot
          </NavItem>
          <NavItem to="/documents" icon={<BookOpenText />} onNavigate={onNavigate}>
            Documents
          </NavItem>
          <NavItem to="/requests" icon={<FolderKanban />} onNavigate={onNavigate}>
            My requests
          </NavItem>
        </div>
        {supervisor && (
          <div className="space-y-1">
            <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-brand-300">Supervisor</p>
            <NavItem to="/approvals" icon={<ClipboardCheck />} count={queue.data?.length} onNavigate={onNavigate}>
              Approval queue
            </NavItem>
            <NavItem to="/all-requests" icon={<FileStack />} onNavigate={onNavigate}>
              All requests
            </NavItem>
            <NavItem to="/audit" icon={<Activity />} onNavigate={onNavigate}>
              Audit & usage
            </NavItem>
            <NavItem to="/users" icon={<Users />} onNavigate={onNavigate}>
              Users
            </NavItem>
          </div>
        )}
      </nav>

      <div className="space-y-3 border-t border-white/10 p-4">
        {mockMode && (
          <div className="flex items-center justify-between rounded-lg bg-white/5 px-3 py-2 text-xs text-brand-100">
            <span>Mock API · synthetic data</span>
            <button type="button" onClick={resetDemo} className="flex items-center gap-1 text-brand-200 hover:text-white" title="Reset demo data">
              <RotateCcw className="size-3.5" />
              Reset
            </button>
          </div>
        )}
        <div className="flex items-center gap-3">
          <div className="grid size-9 shrink-0 place-items-center rounded-full bg-brand-600 text-sm font-semibold">
            {user.fullName
              .split(' ')
              .map((part) => part[0])
              .join('')
              .slice(0, 2)}
          </div>
          <div className="min-w-0 flex-1 leading-tight">
            <p className="truncate text-sm font-medium">{user.fullName}</p>
            <p className="truncate text-xs text-brand-200">
              {user.role === 'SUPERVISOR' ? 'Supervisor' : 'Branch employee'} · {user.branchCode}
            </p>
          </div>
          <button type="button" onClick={logout} className="rounded-md p-1.5 text-brand-200 hover:bg-white/10 hover:text-white" title="Sign out" aria-label="Sign out">
            <LogOut className="size-4" />
          </button>
        </div>
      </div>
    </div>
  )
}

export function AppLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const user = useCurrentUser()
  return (
    <div className="flex h-full">
      <aside className="hidden w-64 shrink-0 lg:block">
        <div className="fixed inset-y-0 w-64">
          <Sidebar onNavigate={() => undefined} />
        </div>
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setMobileOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-64">
            <Sidebar onNavigate={() => setMobileOpen(false)} />
          </div>
          <button type="button" className="absolute left-66 top-4 rounded-md bg-white/90 p-1.5" onClick={() => setMobileOpen(false)} aria-label="Close menu">
            <X className="size-5" />
          </button>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex items-center justify-between border-b border-slate-200 bg-white/90 px-4 py-3 backdrop-blur lg:hidden">
          <button type="button" onClick={() => setMobileOpen(true)} className="rounded-md p-1.5 text-slate-600 hover:bg-slate-100" aria-label="Open menu">
            <Menu className="size-5" />
          </button>
          <span className="text-sm font-semibold">Branch Operations Copilot</span>
          <Badge tone={user.role === 'SUPERVISOR' ? 'purple' : 'blue'}>{user.role === 'SUPERVISOR' ? 'Supervisor' : 'Employee'}</Badge>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <CopilotSessionProvider>
            <Outlet />
          </CopilotSessionProvider>
        </main>
      </div>
    </div>
  )
}
