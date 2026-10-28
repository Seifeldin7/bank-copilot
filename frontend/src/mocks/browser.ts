import { setupWorker } from 'msw/browser'
import { auditHandlers } from './handlers/audit'
import { copilotHandlers } from './handlers/copilot'
import { documentHandlers } from './handlers/documents'
import { identityHandlers } from './handlers/identity'
import { workflowHandlers } from './handlers/workflow'

export const worker = setupWorker(
  ...identityHandlers,
  ...copilotHandlers,
  ...documentHandlers,
  ...workflowHandlers,
  ...auditHandlers,
)
