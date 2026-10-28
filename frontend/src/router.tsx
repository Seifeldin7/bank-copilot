import { createBrowserRouter, Navigate } from 'react-router'
import { RequireAuth, RequireRole } from '@/auth/guards'
import { ApprovalQueuePage } from '@/features/approvals/ApprovalQueuePage'
import { ReviewPage } from '@/features/approvals/ReviewPage'
import { AuditPage, RunTracePage } from '@/features/audit/AuditPages'
import { LoginPage } from '@/features/auth/LoginPage'
import { CopilotPage } from '@/features/copilot/CopilotPage'
import { DocumentsPage } from '@/features/documents/DocumentsPage'
import { NotFoundPage, RouteErrorPage } from '@/features/errors/ErrorPages'
import { RequestDetailPage } from '@/features/requests/RequestDetailPage'
import { EditRequestPage, NewRequestPage } from '@/features/requests/RequestEditorPage'
import { AllRequestsPage, MyRequestsPage } from '@/features/requests/RequestListPages'
import { UsersPage } from '@/features/users/UsersPage'
import { AppLayout } from '@/layout/AppLayout'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage />, errorElement: <RouteErrorPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <Navigate to="/copilot" replace /> },
      { path: 'copilot', element: <CopilotPage /> },
      { path: 'documents', element: <DocumentsPage /> },
      { path: 'requests', element: <MyRequestsPage /> },
      { path: 'requests/new', element: <NewRequestPage /> },
      { path: 'requests/:id', element: <RequestDetailPage /> },
      { path: 'requests/:id/edit', element: <EditRequestPage /> },
      {
        element: <RequireRole role="SUPERVISOR" />,
        children: [
          { path: 'approvals', element: <ApprovalQueuePage /> },
          { path: 'approvals/:id', element: <ReviewPage /> },
          { path: 'all-requests', element: <AllRequestsPage /> },
          { path: 'audit', element: <AuditPage /> },
          { path: 'audit/runs/:runId', element: <RunTracePage /> },
          { path: 'users', element: <UsersPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
