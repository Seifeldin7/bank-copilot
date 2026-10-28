import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { KeyRound, LockKeyhole, ShieldCheck, UserRound } from 'lucide-react'
import { useAuth } from '@/auth/context'
import { Button } from '@/components/ui/Button'
import { FormField, Input } from '@/components/ui/Field'
import { ErrorState } from '@/components/ui/States'

const schema = z.object({
  username: z.string().trim().min(1, 'Enter your username'),
  password: z.string().min(1, 'Enter your password'),
})

type LoginValues = z.infer<typeof schema>

const demoAccounts = [
  { username: 'employee1', password: 'Employee@123', name: 'Mariam Adel', role: 'Branch employee' },
  { username: 'employee2', password: 'Employee@123', name: 'Omar Hassan', role: 'Branch employee' },
  { username: 'supervisor1', password: 'Supervisor@123', name: 'Nadia Fawzy', role: 'Supervisor' },
  { username: 'supervisor2', password: 'Supervisor@123', name: 'Karim Mostafa', role: 'Supervisor' },
]

export function LoginPage() {
  const { status, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<unknown>(null)
  const from = (location.state as { from?: string } | null)?.from ?? '/copilot'

  const form = useForm<LoginValues>({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } })

  if (status === 'authenticated') return <Navigate to={from} replace />

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      await login(values.username, values.password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err)
    }
  })

  const fill = (username: string, password: string) => {
    form.setValue('username', username, { shouldValidate: true })
    form.setValue('password', password, { shouldValidate: true })
  }

  return (
    <div className="grid min-h-full lg:grid-cols-2">
      <div className="relative hidden flex-col justify-between overflow-hidden bg-brand-900 p-12 text-white lg:flex">
        <div className="absolute -right-24 -top-24 size-96 rounded-full bg-brand-700/40" />
        <div className="absolute -bottom-32 -left-16 size-80 rounded-full bg-brand-800/60" />
        <div className="relative flex items-center gap-3">
          <div className="grid size-10 place-items-center rounded-lg bg-white/10 text-lg font-bold">B</div>
          <div>
            <p className="font-semibold">Branch Operations Copilot</p>
            <p className="text-sm text-brand-200">Internal use only</p>
          </div>
        </div>
        <div className="relative max-w-md">
          <h1 className="text-3xl font-semibold leading-tight">Answers you can cite. Requests your supervisor can trust.</h1>
          <ul className="mt-8 space-y-4 text-sm text-brand-100">
            <li className="flex gap-3">
              <ShieldCheck className="size-5 shrink-0 text-brand-300" />
              Grounded answers with document and page citations, or a clear refusal.
            </li>
            <li className="flex gap-3">
              <KeyRound className="size-5 shrink-0 text-brand-300" />
              Every consequential action requires supervisor approval.
            </li>
            <li className="flex gap-3">
              <LockKeyhole className="size-5 shrink-0 text-brand-300" />
              Full audit trail of decisions, executions and AI usage.
            </li>
          </ul>
        </div>
        <p className="relative text-xs text-brand-300">Training environment. All documents and customer data are synthetic.</p>
      </div>

      <div className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <h2 className="text-2xl font-semibold tracking-tight">Sign in</h2>
          <p className="mt-1 text-sm text-slate-500">Use the staff account provided by your branch administrator.</p>

          <form onSubmit={onSubmit} className="mt-8 space-y-4" noValidate>
            <FormField label="Username" htmlFor="username" error={form.formState.errors.username?.message}>
              <Input id="username" autoComplete="username" autoFocus invalid={!!form.formState.errors.username} {...form.register('username')} />
            </FormField>
            <FormField label="Password" htmlFor="password" error={form.formState.errors.password?.message}>
              <Input id="password" type="password" autoComplete="current-password" invalid={!!form.formState.errors.password} {...form.register('password')} />
            </FormField>
            {error !== null && <ErrorState error={error} />}
            <Button type="submit" className="w-full" loading={form.formState.isSubmitting}>
              Sign in
            </Button>
          </form>

          <div className="mt-10">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Demo accounts</p>
            <div className="mt-3 grid gap-2">
              {demoAccounts.map((account) => (
                <button
                  key={account.username}
                  type="button"
                  onClick={() => fill(account.username, account.password)}
                  className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-left text-sm hover:border-brand-300 hover:bg-brand-50/40"
                >
                  <UserRound className="size-4 text-slate-400" />
                  <span className="flex-1">
                    <span className="font-medium text-slate-900">{account.username}</span>
                    <span className="text-slate-500"> · {account.name}</span>
                  </span>
                  <span className="text-xs text-slate-500">{account.role}</span>
                </button>
              ))}
            </div>
            <p className="mt-3 text-xs text-slate-500">
              There is no self sign-up: staff accounts are provisioned by a supervisor and always start with the employee role.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
