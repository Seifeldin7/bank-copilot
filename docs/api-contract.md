# API Contract

The React app is built against this contract, and the Spring Boot backend implements it. Request and response shapes are defined in `frontend/src/api/types.ts`. In mock mode, `frontend/src/mocks` serves the same contract and enforces the same rules.

## Conventions

- Base path `/api`, JSON bodies, `Authorization: Bearer <jwt>` on every endpoint except login.
- Every request carries an `X-Request-Id` header. The server echoes it back and uses it as the run ID in logs, audit events and AI usage records.
- Errors use RFC 9457 Problem Details: `{ type, title, status, detail, instance, runId, errors: [{ field, message }] }`.
- Optimistic locking: mutating request endpoints accept the `version` the client last saw. A stale version returns `409`.

| Status | Meaning |
|---|---|
| 400 | Malformed input or a missing required value |
| 401 | Missing, expired or invalid token |
| 403 | Authenticated but not permitted (role, or acting on your own request as a supervisor) |
| 404 | Not found, **or** another employee's request (to avoid revealing that it exists) |
| 409 | Invalid state transition, stale version, or duplicate draft preparation |
| 422 | Business validation failed (for example, submitting a request with errors) |

## Identity

| Method | Path | Role | Notes |
|---|---|---|---|
| POST | `/auth/login` | public | `LoginRequest` → `AuthResponse` (JWT, 60 min) |
| GET | `/auth/me` | any | `UserSummary` |
| GET | `/users` | SUPERVISOR | `UserSummary[]` |
| POST | `/users` | SUPERVISOR | `CreateUserRequest` → `UserSummary`. The role is always EMPLOYEE, and any `role` field sent is ignored |

There is no public sign-up and no endpoint that changes a role.

## Copilot

| Method | Path | Role | Notes |
|---|---|---|---|
| POST | `/copilot/ask` | any | `AskRequest` → `AskResponse` (answer or refusal, citations, verdict, agent steps, usage) |
| POST | `/copilot/prepare-request` | any | `{ runId }` → `PreparedDraftResponse`. Creates a **DRAFT** owned by the caller. `404` for another user's run, `409` if the run was refused or a draft already exists, `422` if no category applies, `503` if the agent fails |
| GET | `/search?q&limit` | SUPERVISOR | `Evidence[]` (at most 20): each hit's semantic rank, keyword rank, fused RRF score and cosine similarity. For inspecting retrieval |

`AskResponse.mode` is `ORCHESTRATED`, or `FALLBACK` when a step failed and the plain retrieval path answered. `steps` lists ORCHESTRATOR, KNOWLEDGE_RETRIEVAL, POLICY_VALIDATION and ANSWER_GENERATION (plus FALLBACK_ANSWER on fallback); steps that did not run are `SKIPPED`. `verdict` comes from the Policy Validation Agent, and `suggestedCategory` from the category of the first cited document.

## Documents

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/documents` | any | `DocumentSummary[]`, all versions with status |
| POST | `/documents` | SUPERVISOR | multipart `file` (`.pdf` or `.md`, at most 10 MB, content-sniffed), optional `title` and `category` → `IngestionReport` (`INGESTED`, `NEW_VERSION` or `DUPLICATE`) |
| GET | `/documents/{id}/ingestion-log` | SUPERVISOR | `StageLog[]` |
| DELETE | `/documents/{id}` | SUPERVISOR | Retires the document (soft): it is excluded from retrieval and kept for audit |

## Service requests

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/requests?scope=mine\|all&status&category&requesterId` | any (`all` = SUPERVISOR) | `ServiceRequest[]` |
| GET | `/requests/{id}` | owner or SUPERVISOR | `404` for anyone else |
| GET | `/requests/{id}/history` | owner or SUPERVISOR | `HistoryEntry[]` with field diffs |
| POST | `/requests` | any | `CreateRequestRequest` → DRAFT |
| PUT | `/requests/{id}` | owner | DRAFT only, `UpdateRequestRequest` |
| POST | `/requests/{id}/submit` | owner | `{ version }`. Runs validation: `422` with field errors, otherwise PENDING_APPROVAL |

Lifecycle: `DRAFT → PENDING_APPROVAL → APPROVED → EXECUTED`, plus `PENDING_APPROVAL → REJECTED`. Any other transition returns `409`.

## Approvals (SUPERVISOR, never on your own request)

| Method | Path | Notes |
|---|---|---|
| GET | `/approvals/queue` | PENDING_APPROVAL requests, oldest first |
| POST | `/approvals/{id}/approve` | `{ comment?, version }`. Re-validates, approves, then runs the simulated execution once |
| POST | `/approvals/{id}/reject` | `{ reason (≥10 chars), version }` |
| POST | `/approvals/{id}/edit-approve` | `{ title, description, payload, reason, version }`. Re-validates and records the field diff, then approves and executes |
| POST | `/approvals/{id}/execute` | Idempotent. Returns `{ execution, alreadyExecuted }`; a repeat returns the original record and logs `EXECUTION_RETRY` |

## Audit (SUPERVISOR)

| Method | Path | Notes |
|---|---|---|
| GET | `/audit/runs` | `RunSummary[]`, newest 200 |
| GET | `/audit/runs/{runId}` | `RunTrace`: steps, model calls, related audit events (including the draft prepared from the run) |
| GET | `/audit/events` | `AuditEvent[]`, newest 200 first |
| GET | `/audit/usage?from&to` | `UsageSummary`: totals, per day (UTC), per provider and model. Defaults to the last 30 days; at most one year |
