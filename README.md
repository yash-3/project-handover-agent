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

## Second sample project: proof of config-only reuse

`projects/python-inventory.yaml` hands over a small **Python** service (`samples/python-inventory/`) that is
shaped differently from `nexus-commerce` (Java, GitHub): the repo is a **local folder**, there is **no CI** and
**no ticket tracker**. No Java code is specific to either project; the platform skips what a project does not configure.

```bash
set -a; source .env; set +a
mvn -q -pl handover-app -am install -DskipTests
mvn -q -pl handover-app exec:java -Dexec.args="index python-inventory"   # no GitHub token needed
mvn -q -pl handover-app exec:java -Dexec.args="eval python-inventory"    # 9 questions, a few cents
mvn -q -pl handover-app exec:java -Dexec.args="draft python-inventory"   # full handover, about 0.3-0.5 USD
```

Evidence: `SecondProjectReuseTest` (offline) runs the whole pipeline for it; a live `eval` scored 9 of 9 with every
answer grounded. Adding Jira, GitLab or Jenkins would be one new adapter class, not a change to any agent.

## Benchmarks (numbers for the CV)

`benchmark <project>` measures instead of guessing: files and lines the agents are given, tickets and CI pipelines read,
index and draft time, tokens and cost, citations verified, eval score. It writes `outputs/<project>/benchmark.md`
(a quotable table) and `benchmark.json`. Anything it did not measure says "not measured".

```bash
# 1. Pick a PUBLIC repo; the script prints its size so you can check it first (nothing is committed)
scripts/fetch-benchmark-repo.sh https://github.com/<owner>/<repo> <name>
# 2. Copy projects/python-inventory.yaml to projects/<name>.yaml: repo type local, url benchmarks/repos/<name>;
#    add ci (github-actions) and tickets (github-issues) sections for the same public repo if you want those counts
# 3. Run it (time a manual handover yourself first, then pass the minutes; they are stored in baseline.properties)
set -a; source .env; set +a
mvn -q -pl handover-app -am install -DskipTests
mvn -q -pl handover-app exec:java -Dexec.args="benchmark <name> --manual-minutes 240"
```

Flags: `--review-minutes N` (your time reviewing the draft), `--skip-index` (already indexed), `--skip-eval`.
Time saved is computed only from a baseline you entered. Quote numbers from `benchmark.md` only.

