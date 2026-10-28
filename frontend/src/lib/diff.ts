import type { FieldChange, RequestPayload } from '@/api/types'

interface Diffable {
  title: string
  description: string
  payload: RequestPayload
}

function flatten(value: unknown, prefix: string, into: Record<string, unknown>) {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    for (const [key, inner] of Object.entries(value as Record<string, unknown>)) {
      flatten(inner, prefix ? `${prefix}.${key}` : key, into)
    }
  } else {
    into[prefix] = value
  }
}

function isEmpty(value: unknown) {
  return value === null || value === undefined || value === '' || (Array.isArray(value) && value.length === 0)
}

export function diffRequest(before: Diffable, after: Diffable): FieldChange[] {
  const a: Record<string, unknown> = {}
  const b: Record<string, unknown> = {}
  flatten({ title: before.title, description: before.description, payload: before.payload }, '', a)
  flatten({ title: after.title, description: after.description, payload: after.payload }, '', b)
  const changes: FieldChange[] = []
  for (const key of new Set([...Object.keys(a), ...Object.keys(b)])) {
    const from = a[key] ?? null
    const to = b[key] ?? null
    if (JSON.stringify(from) !== JSON.stringify(to) && !(isEmpty(from) && isEmpty(to))) changes.push({ field: key, from, to })
  }
  return changes
}
