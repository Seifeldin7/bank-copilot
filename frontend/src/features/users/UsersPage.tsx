import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Lock, UserPlus } from 'lucide-react'
import { ApiError } from '@/api/client'
import { usersApi } from '@/api/endpoints'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Dialog } from '@/components/ui/Dialog'
import { FormField, Input } from '@/components/ui/Field'
import { PageHeader, Table, TBody, Td, Th, THead } from '@/components/ui/Layout'
import { Alert, ErrorState, Spinner } from '@/components/ui/States'
import { useToast } from '@/components/ui/toast-context'
import { formatDate } from '@/lib/format'

const schema = z.object({
  username: z.string().trim().regex(/^[a-z][a-z0-9._]{2,29}$/, '3-30 lowercase letters, digits, dots or underscores'),
  fullName: z.string().trim().min(3, 'At least 3 characters'),
  branchCode: z.string().trim().regex(/^[A-Z]{3}-\d{3}$/, 'Use the format CAI-012'),
  password: z
    .string()
    .min(10, 'At least 10 characters')
    .regex(/[A-Z]/, 'Include an upper-case letter')
    .regex(/[a-z]/, 'Include a lower-case letter')
    .regex(/\d/, 'Include a digit'),
})

type NewUser = z.infer<typeof schema>

export function UsersPage() {
  const query = useQuery({ queryKey: ['users'], queryFn: usersApi.list })
  const [open, setOpen] = useState(false)

  return (
    <>
      <PageHeader
        title="Users"
        description="Staff accounts. There is no public sign-up: supervisors provision employee accounts, and roles cannot be changed through the application."
        actions={
          <Button icon={<UserPlus className="size-4" />} onClick={() => setOpen(true)}>
            Provision employee
          </Button>
        }
      />
      <Card>
        {query.isPending ? (
          <Spinner />
        ) : query.error ? (
          <div className="p-5">
            <ErrorState error={query.error} />
          </div>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Name</Th>
                <Th>Username</Th>
                <Th>Role</Th>
                <Th>Branch</Th>
                <Th>Status</Th>
                <Th>Created</Th>
              </tr>
            </THead>
            <TBody>
              {query.data.map((user) => (
                <tr key={user.id}>
                  <Td className="font-medium text-slate-900">{user.fullName}</Td>
                  <Td className="font-mono text-xs">{user.username}</Td>
                  <Td>
                    <Badge tone={user.role === 'SUPERVISOR' ? 'purple' : 'blue'}>{user.role === 'SUPERVISOR' ? 'Supervisor' : 'Employee'}</Badge>
                  </Td>
                  <Td>{user.branchCode}</Td>
                  <Td>
                    <Badge tone={user.enabled ? 'green' : 'neutral'}>{user.enabled ? 'Active' : 'Disabled'}</Badge>
                  </Td>
                  <Td className="text-xs text-slate-500">{formatDate(user.createdAt)}</Td>
                </tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>
      {open && <ProvisionDialog onClose={() => setOpen(false)} />}
    </>
  )
}

function ProvisionDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const form = useForm<NewUser>({ resolver: zodResolver(schema), defaultValues: { username: '', fullName: '', branchCode: 'CAI-012', password: '' } })
  const create = useMutation({
    mutationFn: (values: NewUser) => usersApi.create(values),
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      notify({ tone: 'success', title: 'Employee account created', description: `${user.fullName} can now sign in as ${user.username}.` })
      onClose()
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) form.setError(fieldError.field as keyof NewUser, { message: fieldError.message })
      }
    },
  })
  const errors = form.formState.errors

  return (
    <Dialog
      open
      onClose={onClose}
      title="Provision employee account"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="provision-form" loading={create.isPending}>
            Create account
          </Button>
        </>
      }
    >
      <form id="provision-form" onSubmit={form.handleSubmit((values) => create.mutate(values))} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Full name" htmlFor="fullName" required error={errors.fullName?.message}>
            <Input id="fullName" invalid={!!errors.fullName} {...form.register('fullName')} />
          </FormField>
          <FormField label="Username" htmlFor="username" required error={errors.username?.message}>
            <Input id="username" autoComplete="off" invalid={!!errors.username} {...form.register('username')} />
          </FormField>
          <FormField label="Branch code" htmlFor="branchCode" required error={errors.branchCode?.message}>
            <Input id="branchCode" invalid={!!errors.branchCode} {...form.register('branchCode')} />
          </FormField>
          <FormField label="Role" htmlFor="role">
            <div id="role" className="flex h-10 items-center gap-2 rounded-lg bg-slate-50 px-3 text-sm text-slate-600 ring-1 ring-inset ring-slate-200">
              <Lock className="size-3.5" />
              Employee
            </div>
          </FormField>
        </div>
        <FormField label="Initial password" htmlFor="password" required error={errors.password?.message} hint="Stored as a BCrypt hash. Share it with the employee through a secure channel.">
          <Input id="password" type="password" autoComplete="new-password" invalid={!!errors.password} {...form.register('password')} />
        </FormField>
        <Alert tone="info">New accounts always get the employee role. Supervisor accounts are created by IT, outside this application.</Alert>
        {create.error && !(create.error instanceof ApiError && create.error.fieldErrors.length > 0) && <ErrorState error={create.error} />}
      </form>
    </Dialog>
  )
}
