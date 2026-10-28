const dateTime = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
const dateOnly = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
const integer = new Intl.NumberFormat('en-US')

export function formatDateTime(value: string | null | undefined) {
  return value ? dateTime.format(new Date(value)) : '—'
}

export function formatDate(value: string | null | undefined) {
  return value ? dateOnly.format(new Date(value)) : '—'
}

export function formatRelative(value: string | null | undefined) {
  if (!value) return '—'
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 60) return relative.format(seconds, 'second')
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute')
  if (abs < 86400) return relative.format(Math.round(seconds / 3600), 'hour')
  return relative.format(Math.round(seconds / 86400), 'day')
}

export function formatNumber(value: number) {
  return integer.format(value)
}

export function formatCost(value: number) {
  return `$${value.toFixed(value < 0.01 ? 5 : 4)}`
}

export function formatDuration(ms: number) {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)} s` : `${ms} ms`
}

export function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (Array.isArray(value)) return value.length ? value.join(', ') : '—'
  return String(value)
}
