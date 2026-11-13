# Project Handover Agent

AI agents that automate microservice project handover: service knowledge, deployment process, and tickets.
Personal learning project; uses only personal resources and dummy data.

- Plan and notes: [docs/plan.md](docs/plan.md)
- Layout: `agents/` agent logic, `knowledge/` ingestion + retrieval, `adapters/` TicketSource/CISource,
  `projects/` per-project config, `evals/` question sets, `outputs/<project>/` generated docs.

Status: scaffold only. Open decisions: language, vector DB, LLM key route.

## Build

Personal project: `.mvn/maven.config` points Maven at `.mvn/settings.xml` (Maven Central only), so nothing
resolves through a company repository. Run from the repo root:

```bash
mvn clean install
```

Stack: Java 21, Spring Boot 4.1.2, Spring AI 2.0.2 (see `docs/plan.md` and the architecture plan).

## Status

- [x] `handover-config` module: `projects/*.yaml` loader, `${ENV}` resolution, validation (literal secrets rejected)
- [x] `handover-adapters` module: ports (repo, CI, tickets, docs), type-based registry, adapters for local repo, GitHub repo, GitHub Actions, GitHub Issues, docs folder
- [x] `handover-core` module: AgentRuntime port, Spring AI (Claude) implementation, usage and cost tracking, token budget, read-only repo tools
- [x] `handover-knowledge` module: chunker, embedder port (local ONNX), pgvector store with hybrid search, indexer, `searchKnowledge` tool (`docker compose up -d` for Postgres)
- [x] `handover-agents` module: Q&A agent (answers only from project sources, cites `path:lines`, flags citations the search never returned)
- [x] `handover-app` module: CLI (`index`, `ask`, `code`, `devops`, `draft`, `approve`, `report`, `eval`, `tickets`)
- [x] Ticket agent: "open work and known issues" section from the tracker, ticket ids checked against the tracker
- [x] Code agent: "service overview and architecture" section for any language, citations checked (search results or files read)
- [x] DevOps agent: "build, deployment and operations" section from manifests and CI pipelines; secret values masked before the model sees them
- [x] Docs agent: fills `templates/default.md` (summary by the model, sections inserted verbatim), lists unverified references under Review notes, saves `handover-DRAFT.md`; `draft <project>` runs everything
- [x] `handover-orchestrator` module: agents run in parallel, a failed agent becomes an explicit gap, tasks planned from config, `approve` step stamps `handover.md`, audit log (`audit.log`)
- [x] Citation checks: cited files and line ranges are validated against the real repo; optional `handover.repairCitations: true` re-runs an agent once to fix failed citations (extra tokens, off by default)
- [x] `handover-guardrails` module: secret and PII masking applied at index time, in every tool output and on agent answers (`piiMasking`, default on); `allowedTools: readOnly` enforced by a tool allowlist; prompt-injection warnings at index time
- [x] Observability: every agent run and tool call is traced to `outputs/<project>/trace.jsonl` (sizes only, never content); `report <project>` summarises runs, tokens, cost and tool calls
- [x] `handover-evals` module: question set with answer key (`evals/<project>.yaml`), deterministic grader (terms, verified citations, correct refusals), optional LLM judge (`--judge`), score history in `eval-history.jsonl`
- [x] `handover-api` module: REST API (Spring Boot) over the same pipeline: ask, index, draft (background job), draft text, approve, report. Requires `HANDOVER_API_KEY` (header `X-API-Key`), listens on 127.0.0.1 only

  Run from the repo root with `.env` loaded: `mvn -q -pl handover-api -am install -DskipTests` then `mvn -q -pl handover-api spring-boot:run`

  | Method and path | What it does |
  |---|---|
  | `GET /api/projects` | list projects |
  | `POST /api/projects/{p}/ask` `{"question": "..."}` | answer with citations, tokens, cost |
  | `POST /api/projects/{p}/index` | index repos (202, returns `jobId`) |
  | `POST /api/projects/{p}/drafts` | run the handover (202, returns `jobId`; 409 if one is running) |
  | `GET /api/jobs/{id}` | job status, progress lines, result |
  | `GET /api/projects/{p}/draft` | latest draft as Markdown |
  | `POST /api/projects/{p}/approve` `{"approver": "..."}` | approve the draft |
  | `GET /api/projects/{p}/report` | trace summary |
- [x] Ready-to-import Postman files: `postman/import/Handover-API.postman_collection.json` and `postman/import/Handover-API-Local.postman_environment.json` (Postman: Import, then pick the environment and paste your `HANDOVER_API_KEY` into `api_key`).
- [x] Git-friendly v3 Postman collection for the API in `postman/` (collection `Handover API` + `Local` environment). Open the `postman/` folder in Postman, pick the `Local` environment and paste your `HANDOVER_API_KEY` into `api_key` (a local secret, never committed). From the command line, free folders only:
  `set -a; source .env; set +a; postman collection run "postman/collections/Handover API" -e postman/environments/Local.environment.yaml --env-var "api_key=$HANDOVER_API_KEY" -i "1 Read-only" -i "5 Negative checks"`
