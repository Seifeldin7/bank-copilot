import type { ReactNode } from 'react'
import { AlertTriangle, Inbox, Loader2 } from 'lucide-react'
import { ApiError } from '@/api/client'
import { cn } from '@/lib/cn'

export function Spinner({ label = 'Loading…', className }: { label?: string; className?: string }) {
  return (
    <div className={cn('flex items-center justify-center gap-2 py-10 text-sm text-slate-500', className)}>
      <Loader2 className="size-4 animate-spin" />
      {label}
    </div>
  )
}

export function EmptyState({ title, description, icon, action }: { title: string; description?: ReactNode; icon?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <div className="mb-3 rounded-full bg-slate-100 p-3 text-slate-400">{icon ?? <Inbox className="size-6" />}</div>
      <p className="text-sm font-medium text-slate-900">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function ErrorState({ error, className }: { error: unknown; className?: string }) {
  const apiError = error instanceof ApiError ? error : null
  return (
    <div className={cn('rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800', className)} role="alert">
      <div className="flex items-start gap-2">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" />
        <div className="min-w-0">
          <p className="font-medium">{apiError ? `${apiError.status ? `${apiError.status} · ` : ''}${apiError.title}` : 'Something went wrong'}</p>
          <p className="mt-0.5 text-red-700">{apiError?.detail ?? (error instanceof Error ? error.message : 'Unexpected error')}</p>
          {apiError && apiError.fieldErrors.length > 0 && (
            <ul className="mt-1 list-disc pl-4 text-red-700">
              {apiError.fieldErrors.map((fieldError) => (
                <li key={fieldError.field + fieldError.message}>{fieldError.message}</li>
              ))}
            </ul>
          )}
          {apiError?.runId && <p className="mt-1 font-mono text-[11px] text-red-500">Run ID: {apiError.runId}</p>}
        </div>
      </div>
    </div>
  )
}

const alertStyles = {
  info: 'border-sky-200 bg-sky-50 text-sky-900',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
  danger: 'border-red-200 bg-red-50 text-red-900',
}

export function Alert({ tone = 'info', title, children, icon, className }: { tone?: keyof typeof alertStyles; title?: ReactNode; children?: ReactNode; icon?: ReactNode; className?: string }) {
  return (
    <div className={cn('flex items-start gap-2.5 rounded-lg border px-4 py-3 text-sm', alertStyles[tone], className)}>
      {icon && <div className="mt-0.5 shrink-0">{icon}</div>}
      <div className="min-w-0 flex-1">
        {title && <p className="font-medium">{title}</p>}
        {children && <div className={cn(title ? 'mt-0.5' : undefined, 'opacity-90')}>{children}</div>}
      </div>
    </div>
  )
}
