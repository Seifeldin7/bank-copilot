import type { ReactNode } from 'react'
import { Controller, useWatch, type FieldErrors, type UseFormReturn } from 'react-hook-form'
import type { RequestCategory } from '@/api/types'
import { FormField, Input, Select, Textarea } from '@/components/ui/Field'
import { cn } from '@/lib/cn'
import {
  accountServiceLabels,
  accountTypeLabels,
  cardIssueLabels,
  categoryDescriptions,
  categoryLabels,
  channelLabels,
  digitalIssueLabels,
} from '@/lib/labels'
import { emptyPayload, type RequestFormValues } from './schema'

function errorAt(errors: FieldErrors<RequestFormValues>, path: string): string | undefined {
  let node: unknown = errors
  for (const key of path.split('.')) {
    if (node === null || typeof node !== 'object') return undefined
    node = (node as Record<string, unknown>)[key]
  }
  if (node && typeof node === 'object' && 'message' in node) return String((node as { message?: unknown }).message ?? '')
  return undefined
}

interface RequestFieldsProps {
  form: UseFormReturn<RequestFormValues>
  categoryLocked?: boolean
  branchCode?: string
  highlight?: string[]
}

function Options<T extends string>({ labels }: { labels: Record<T, string> }) {
  return (
    <>
      {(Object.entries(labels) as [T, string][]).map(([value, label]) => (
        <option key={value} value={value}>
          {label}
        </option>
      ))}
    </>
  )
}

export function RequestFields({ form, categoryLocked, branchCode = '', highlight = [] }: RequestFieldsProps) {
  const { register, control, formState, setValue } = form
  const category = useWatch({ control, name: 'category' })
  const payload = useWatch({ control, name: 'payload' })
  const err = (path: string) => errorAt(formState.errors, path)
  const hl = (path: string) => highlight.includes(path) && 'ring-2 ring-amber-400'

  const changeCategory = (next: RequestCategory) => {
    setValue('category', next)
    setValue('payload', emptyPayload(next, branchCode) as RequestFormValues['payload'], { shouldDirty: true })
  }

  return (
    <div className="space-y-6">
      <div>
        <p className="mb-2 text-sm font-medium text-slate-700">Category</p>
        <div className="grid gap-2 sm:grid-cols-2">
          {(Object.keys(categoryLabels) as RequestCategory[]).map((value) => (
            <button
              key={value}
              type="button"
              disabled={categoryLocked && value !== category}
              onClick={() => !categoryLocked && changeCategory(value)}
              className={cn(
                'rounded-lg border px-3 py-2.5 text-left transition-colors',
                value === category ? 'border-brand-600 bg-brand-50 ring-1 ring-brand-600' : 'border-slate-200 bg-white hover:border-slate-300',
                categoryLocked && value !== category && 'cursor-not-allowed opacity-40',
              )}
            >
              <p className="text-sm font-medium text-slate-900">{categoryLabels[value]}</p>
              <p className="text-xs text-slate-500">{categoryDescriptions[value]}</p>
            </button>
          ))}
        </div>
      </div>

      <div className="grid gap-4">
        <FormField label="Title" htmlFor="title" required error={err('title')}>
          <Input id="title" placeholder="Short summary of the request" invalid={!!err('title')} className={cn(hl('title'))} {...register('title')} />
        </FormField>
        <FormField label="Description" htmlFor="description" required error={err('description')}>
          <Textarea id="description" rows={3} placeholder="What does the customer need, and what has already been checked?" invalid={!!err('description')} {...register('description')} />
        </FormField>
      </div>

      <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4">
        <p className="mb-4 text-sm font-semibold text-slate-800">{categoryLabels[category]} details</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Customer reference" htmlFor="customerReference" required error={err('payload.customerReference')} hint="Synthetic reference, e.g. CUS-104233">
            <Input id="customerReference" placeholder="CUS-000000" invalid={!!err('payload.customerReference')} className={cn(hl('payload.customerReference'))} {...register('payload.customerReference')} />
          </FormField>

          {payload.category === 'CARD_SERVICE' && (
            <>
              <FormField label="Issue type" htmlFor="issueType" required error={err('payload.issueType')}>
                <Select id="issueType" {...register('payload.issueType')}>
                  <Options labels={cardIssueLabels} />
                </Select>
              </FormField>
              <FormField label="Card last 4 digits" htmlFor="cardLast4" required error={err('payload.cardLast4')} hint="Never record the full card number">
                <Input id="cardLast4" inputMode="numeric" maxLength={4} placeholder="1234" invalid={!!err('payload.cardLast4')} className={cn(hl('payload.cardLast4'))} {...register('payload.cardLast4')} />
              </FormField>
              <FormField label="Delivery branch" htmlFor="deliveryBranch" required error={err('payload.deliveryBranch')}>
                <Input id="deliveryBranch" placeholder="CAI-012" invalid={!!err('payload.deliveryBranch')} {...register('payload.deliveryBranch')} />
              </FormField>
            </>
          )}

          {payload.category === 'DIGITAL_BANKING' && (
            <>
              <FormField label="Channel" htmlFor="channel" required error={err('payload.channel')}>
                <Select id="channel" {...register('payload.channel')}>
                  <Options labels={channelLabels} />
                </Select>
              </FormField>
              <FormField label="Issue type" htmlFor="issueType" required error={err('payload.issueType')}>
                <Select id="issueType" invalid={!!err('payload.issueType')} {...register('payload.issueType')}>
                  <Options labels={digitalIssueLabels} />
                </Select>
              </FormField>
              <FormField label="Registered mobile" htmlFor="registeredMobile" required error={err('payload.registeredMobile')}>
                <Input id="registeredMobile" inputMode="tel" placeholder="01012345678" invalid={!!err('payload.registeredMobile')} className={cn(hl('payload.registeredMobile'))} {...register('payload.registeredMobile')} />
              </FormField>
            </>
          )}

          {payload.category === 'ACCOUNT_SERVICE' && (
            <>
              <FormField label="Service type" htmlFor="serviceType" required error={err('payload.serviceType')}>
                <Select id="serviceType" {...register('payload.serviceType')}>
                  <Options labels={accountServiceLabels} />
                </Select>
              </FormField>
              <FormField label="Account last 4 digits" htmlFor="accountLast4" required error={err('payload.accountLast4')}>
                <Input id="accountLast4" inputMode="numeric" maxLength={4} placeholder="1234" invalid={!!err('payload.accountLast4')} className={cn(hl('payload.accountLast4'))} {...register('payload.accountLast4')} />
              </FormField>
              <FormField label="Account type" htmlFor="accountType" required error={err('payload.accountType')}>
                <Select id="accountType" invalid={!!err('payload.accountType')} {...register('payload.accountType')}>
                  <Options labels={accountTypeLabels} />
                </Select>
              </FormField>
              {payload.serviceType === 'STATEMENT' && (
                <>
                  <FormField label="Period from" htmlFor="periodFrom" required error={err('payload.periodFrom')}>
                    <Input id="periodFrom" type="date" invalid={!!err('payload.periodFrom')} className={cn(hl('payload.periodFrom'))} {...register('payload.periodFrom')} />
                  </FormField>
                  <FormField label="Period to" htmlFor="periodTo" required error={err('payload.periodTo')} hint="Up to 12 months per request">
                    <Input id="periodTo" type="date" invalid={!!err('payload.periodTo')} className={cn(hl('payload.periodTo'))} {...register('payload.periodTo')} />
                  </FormField>
                </>
              )}
            </>
          )}

          {payload.category === 'ADDITIONAL_DOCUMENTATION' && (
            <>
              <FormField label="Related reference" htmlFor="relatedReference" hint="Optional account or request reference">
                <Input id="relatedReference" placeholder="SR-2026-000000" {...register('payload.relatedReference')} />
              </FormField>
              <FormField label="Due date" htmlFor="dueDate" required error={err('payload.dueDate')} hint="Policy: within 14 days of the visit">
                <Input id="dueDate" type="date" invalid={!!err('payload.dueDate')} {...register('payload.dueDate')} />
              </FormField>
              <FormField label="Required documents" htmlFor="requiredDocuments" required error={err('payload.requiredDocuments')} hint="One document per line" className="sm:col-span-2">
                <Controller
                  control={control}
                  name="payload.requiredDocuments"
                  render={({ field }) => (
                    <Textarea
                      id="requiredDocuments"
                      rows={3}
                      placeholder={'Proof of address issued within 3 months\nProof of income'}
                      invalid={!!err('payload.requiredDocuments')}
                      className={cn(hl('payload.requiredDocuments'))}
                      value={(field.value ?? []).join('\n')}
                      onChange={(event) => field.onChange(event.target.value.split('\n'))}
                      onBlur={field.onBlur}
                    />
                  )}
                />
              </FormField>
            </>
          )}

          <FormField label="Notes" htmlFor="notes" error={err('payload.notes')} className="sm:col-span-2" hint="Optional. Do not include full card or account numbers.">
            <Textarea id="notes" rows={2} {...register('payload.notes')} />
          </FormField>
        </div>
      </div>
    </div>
  )
}

export function FormActions({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-100 pt-4">{children}</div>
}
