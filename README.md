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
- [x] `handover-app` module: CLI (`index`, `ask`, `code`, `devops`, `tickets`)
- [x] Ticket agent: "open work and known issues" section from the tracker, ticket ids checked against the tracker
- [x] Code agent: "service overview and architecture" section for any language, citations checked (search results or files read)
- [x] DevOps agent: "build, deployment and operations" section from manifests and CI pipelines; secret values masked before the model sees them
- [ ] `docs agent`, `orchestrator`, `guardrails`, `observability`, `evals`, REST API
