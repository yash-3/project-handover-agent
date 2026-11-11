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
- [x] `handover-adapters` module: ports (repo, CI, tickets, docs), type-based registry, local repo adapter
- [ ] `core`, `knowledge`, `agents`, `orchestrator`, `guardrails`, `observability`, `evals`, `app`
