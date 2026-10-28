# Banking Operations Copilot

An internal copilot for simulated branch employees. It answers questions from approved, synthetic banking documents with citations (or refuses), and runs a service-request workflow that needs a supervisor's approval before any simulated action executes.

All documents, customers and accounts are synthetic. Nothing connects to real banking systems.

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Complete React app on a mock API | Done, awaiting review |
| 2 | Backend foundation, identity, corpus, ingestion | Not started |
| 3 | Hybrid retrieval, grounded answers, refusals | Not started |
| 4 | Service requests, approvals, execution, audit | Not started |
| 5 | Multi-agent orchestration, observability, evaluation, Docker | Not started |

## Frontend

React 19 + TypeScript (Vite), React Router, TanStack Query, React Hook Form + Zod, and Tailwind CSS. In mock mode, Mock Service Worker serves the full API contract in the browser, with the same business rules the backend will enforce. Data persists in `localStorage`, and **Reset** in the sidebar restores the seed data.

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. `VITE_USE_MOCKS=true` (set in `.env.development`) turns the mock API on. Set it to `false` to proxy `/api` to `http://localhost:8080`.

```bash
npm run build
npm run lint
docker build -t bm-copilot-frontend frontend && docker run -p 3000:80 bm-copilot-frontend
```

### Demo accounts

| Username | Password | Role |
|---|---|---|
| employee1 | Employee@123 | Branch employee |
| employee2 | Employee@123 | Branch employee |
| supervisor1 | Supervisor@123 | Supervisor |
| supervisor2 | Supervisor@123 | Supervisor |

### Walkthrough

1. Sign in as **employee1** and ask an example question. You get a cited answer; open **Agent trace** to see the agent steps, tokens and cost.
2. Try the out-of-scope, ambiguous, prompt-injection and agent-failure examples. Each one is refused or falls back.
3. On a grounded answer, choose **Create draft request**, complete the highlighted fields, and submit.
4. Open `/approvals`. The 403 page appears, and the API rejects the call too.
5. Sign in as **supervisor1** and open the approval queue. Reject one request (a reason is required) and **Edit & approve** another (a before/after diff is shown). The request executes once; **Retry execution** returns the original record.
6. Under **Documents**, upload `docs/demo-files/branch-atm-guidelines.md`, then upload it again to see duplicate detection. Ask about ATM withdrawal limits.
7. Open **Audit & usage** for run traces, AI usage and the activity log.

The API contract lives in [docs/api-contract.md](docs/api-contract.md).
