# Banking Operations Copilot

An internal copilot for simulated branch employees. It answers questions from approved, synthetic banking documents with citations (or refuses), and runs a service-request workflow that needs a supervisor's approval before any simulated action executes.

All documents, customers and accounts are synthetic. Nothing connects to real banking systems.

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Complete React app on a mock API | Done |
| 2 | Backend foundation, identity, corpus, ingestion | Done |
| 3 | Hybrid retrieval, grounded answers, refusals | Done |
| 4 | Service requests, approvals, execution, audit | Not started |
| 5 | Multi-agent orchestration, observability, evaluation, Docker | Not started |

## Backend

Java 21, Spring Boot 4.1, Spring Data JPA with Hibernate 7 (including `hibernate-vector`), PostgreSQL 16 with pgvector, Flyway, Spring Security (JWT), Spring AI 2.0 (local ONNX embeddings) and PDFBox.

### Run locally

```bash
docker run -d --name bm-pg -p 5433:5432 \
  -e POSTGRES_DB=copilot -e POSTGRES_USER=copilot -e POSTGRES_PASSWORD=copilot \
  pgvector/pgvector:pg16

cd backend
./mvnw spring-boot:run
```

- The API runs on http://localhost:8080, and Swagger UI is at http://localhost:8080/swagger-ui.html.
- Port 5433 avoids clashing with a PostgreSQL that may already run on 5432.
- The first start downloads the embedding model (about 90 MB) into `~/.cache/banking-copilot/models`.
- On every start the app syncs `corpus/`. Unchanged files are skipped, and `/actuator/health/readiness` reports UP once the sync finishes.
- Demo users are created on first start (see below). Set `SEED_DEMO_USERS=false` to turn this off.
- Logs are structured JSON (ECS) on stdout.

| Variable | Default |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5433/copilot` / `copilot` / `copilot` |
| `JWT_SECRET` | development value; set your own (at least 32 bytes) outside local use |
| `CORPUS_DIR` | `../corpus` |
| `UPLOADS_DIR` | `./data/uploads` |
| `MODEL_CACHE_DIR` | `~/.cache/banking-copilot/models` |
| `LLM_PROVIDER` | `stub`; or `openai` / `anthropic` |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | empty; needed only for that provider |
| `LLM_INPUT_USD_PER_MILLION` / `LLM_OUTPUT_USD_PER_MILLION` | `0`; used for the cost estimate |

### Copilot

`POST /api/copilot/ask` runs these steps:

1. A pre-check refuses questions that try to instruct the copilot, or that have fewer than two meaningful words.
2. Hybrid search runs semantic search (pgvector) and keyword search (PostgreSQL full text) and fuses them with Reciprocal Rank Fusion. Superseded, retired and flagged passages are never returned.
3. The pre-check refuses if no passage reaches cosine similarity 0.35, so the model isn't called.
4. The model answers from the top 5 passages and returns JSON. The citation guard withholds any answer that cites nothing, or cites a passage that wasn't retrieved.

The default `stub` model needs no key. It quotes the best-matching sentences and cites them, so its answers are extractive rather than written prose. Set `LLM_PROVIDER=openai` with `OPENAI_API_KEY` (or `SPRING_AI_OPENAI_BASE_URL` for an OpenAI-compatible server such as Ollama), or `LLM_PROVIDER=anthropic` with `ANTHROPIC_API_KEY`, for a real model. Supervisors can inspect retrieval ranks with `GET /api/search?q=`.

### Tests

```bash
cd backend
./mvnw test
```

Unit tests cover business logic only: text cleaning, chunking, injection screening, upload validation, the ingestion pipeline (new, duplicate, new version), user provisioning, rank fusion, the pre-check, the citation guard, grounded answering (valid, invented citation, malformed reply), the stub model and the copilot flow (refusals skip the model, a model failure becomes a refusal).

### Corpus

`corpus/` holds 18 synthetic documents (about 70 pages): 10 Markdown files in `corpus/markdown` and 8 PDFs in `corpus/pdf`.
- The PDFs are generated from `corpus/pdf-sources` with `tools/generate-corpus-pdfs.sh`.
- `corpus/pdf/versions/v1` holds the superseded version of the approval policy. It's ingested first, so the current version replaces it.
- `branch-notice-q3-digital.md` deliberately contains prompt-injection text, which ingestion flags.
- `corpus/catalog.csv` sets each file's title and category.

## Frontend

React 19 + TypeScript (Vite), React Router, TanStack Query, React Hook Form + Zod, and Tailwind CSS.

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.
- `VITE_USE_MOCKS=true` turns on the in-browser mock API (Mock Service Worker).
- `VITE_REAL_API_GROUPS` lists the parts served by the real backend instead, proxied to http://localhost:8080. In development it's `identity,documents,copilot`, so sign-in, users, documents and the copilot use Spring, while requests and audit stay mocked until their phases are built. Until Phase 5 the copilot answer has no confidence verdict and no "prepare a draft" button, and copilot runs don't appear on the mocked audit page.
- Mock data persists in `localStorage`; **Reset** in the sidebar restores the seed data.

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

There is no public sign-up. Supervisors provision employee accounts, and roles can't be changed through the application.

## Documentation

- [API contract](docs/api-contract.md)
- [Architecture](docs/architecture.html): open locally; keep `docs/assets` next to it
