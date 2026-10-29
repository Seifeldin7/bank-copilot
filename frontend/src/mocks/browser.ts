import { setupWorker } from 'msw/browser'
import { auditHandlers } from './handlers/audit'
import { copilotHandlers } from './handlers/copilot'
import { documentHandlers } from './handlers/documents'
import { identityHandlers } from './handlers/identity'
import { workflowHandlers } from './handlers/workflow'

const groups = {
  identity: identityHandlers,
  documents: documentHandlers,
  copilot: copilotHandlers,
  workflow: workflowHandlers,
  audit: auditHandlers,
}

const servedByBackend = new Set(
  (import.meta.env.VITE_REAL_API_GROUPS ?? '')
    .split(',')
    .map((group: string) => group.trim())
    .filter(Boolean),
)

export const worker = setupWorker(
  ...Object.entries(groups)
    .filter(([group]) => !servedByBackend.has(group))
    .flatMap(([, handlers]) => handlers),
)
