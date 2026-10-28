import { Link, isRouteErrorResponse, useRouteError } from 'react-router'
import { FileQuestion, ShieldX, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'

function ErrorLayout({ icon, code, title, description, children }: { icon: ReactNode; code: string; title: string; description: string; children?: ReactNode }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center px-6 text-center">
      <div className="mb-4 rounded-full bg-brand-50 p-4 text-brand-700">{icon}</div>
      <p className="text-sm font-semibold text-brand-700">{code}</p>
      <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
      <p className="mt-2 max-w-md text-sm text-slate-500">{description}</p>
      <div className="mt-6 flex gap-3">
        <Link to="/copilot" className="rounded-lg bg-brand-700 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800">
          Back to copilot
        </Link>
        {children}
      </div>
    </div>
  )
}

export function ForbiddenPage() {
  return (
    <ErrorLayout
      icon={<ShieldX className="size-8" />}
      code="403 · Forbidden"
      title="You don't have access to this page"
      description="This area is restricted to supervisors. The server enforces the same rule, so calling the API directly is rejected too."
    />
  )
}

export function NotFoundPage({ title = 'Page not found', description = 'The page you are looking for does not exist or was moved.' }: { title?: string; description?: string }) {
  return <ErrorLayout icon={<FileQuestion className="size-8" />} code="404 · Not found" title={title} description={description} />
}

export function RouteErrorPage() {
  const error = useRouteError()
  const message = isRouteErrorResponse(error) ? `${error.status} ${error.statusText}` : error instanceof Error ? error.message : 'Unexpected error'
  return (
    <ErrorLayout icon={<TriangleAlert className="size-8" />} code="Error" title="Something went wrong" description={message}>
      <button type="button" onClick={() => window.location.reload()} className="rounded-lg px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50">
        Reload
      </button>
    </ErrorLayout>
  )
}
