# 📋 ResearchAgent — Development Sprints Breakdown

This document tracks the phased execution of **ResearchAgent: Your AI Research Team, On Demand**.
Each sprint delivers a testable, verified milestone of the PRD requirements.

---

## 🏃 Sprint Overview & Status Matrix

| Sprint | Focus Area | PRD Scope | Status | Deliverables |
|---|---|---|---|---|
| **Sprint 1** | Multi-Agent Core Engine & CLI | Weeks 13–14 | ✅ **COMPLETED** | 7-agent loop, SSRF & redirect defense, CitationValidator, Picocli CLI, 16 unit tests |
| **Sprint 2** | Distributed Architecture & Persistence | Week 15 | ✅ **COMPLETED** | Docker Compose (PG, RabbitMQ, Redis), JPA entities, RabbitMQ worker + DLQ, Redis Pub/Sub event bridge, 39 unit tests |
| **Sprint 3** | REST API, SSE Streaming & Guardrails | Week 16 | ✅ **COMPLETED** | REST controllers, Redis-to-SSE bridge, cancellation, rate limiting, crash recovery, 39 unit tests |
| **Sprint 4** | PDF Generation & Report Export | Week 17 | ✅ **COMPLETED** | OpenHTMLtoPDF engine, styled PDF report with cover page, citations table, confidence appendix |
| **Sprint 5** | React Web UI & Trace Viewer | Week 18 | ✅ **COMPLETED** | Vite + React + Tailwind frontend, query form, live SSE progress visualizer, trace inspector |
| **Sprint 6** | Observability, Eval Table & Launch Polish | Week 18+ | ✅ **COMPLETED** | LangFuse tracing, 10-question evaluation benchmark, production Docker Compose, 40 unit tests |

---

## 📦 Sprint Details

### ✅ Sprint 1: Multi-Agent Core Engine, Security Guardrails & CLI Runner
> **Goal**: Build and verify the hand-rolled 7-agent orchestration loop runnable directly from terminal with strict security and budget controls.

- [x] **Project Scaffolding**:
  - [x] Java 21 LTS + Spring Boot 3.3.4 + LangChain4j 0.36.2 + Picocli + Jsoup + OpenHTMLtoPDF in `pom.xml`.
  - [x] Git repository initialization and linking to `https://github.com/bharathreddy55/Research-Agent.git`.
  - [x] Clean `.gitignore` and `.env.example` template.
  - [x] `application.yml` with Gemini 1.5 Flash (default) and OpenAI configuration.
- [x] **7-Agent Orchestration State Machine**:
  - [x] `PlannerAgent`: Query decomposition into 3–5 sub-questions with search queries, JSON schema validation, and 1-retry fallback.
  - [x] `SearcherAgent`: Web search invocation (Tavily + fallback), URL deduplication, and depth page limits (6/10/15).
  - [x] `ReaderAgent`: Web page extraction, prompt-injection defense (`<untrusted_web_content>` wrapping), factual claim extraction with quotes.
  - [x] `VerifierAgent`: Cross-domain corroboration rule ($\ge 2$ independent domains = `VERIFIED`, single-source = `UNVERIFIED`, contradictions flagged).
  - [x] `WriterAgent`: Structured Markdown authoring with executive summary and inline citations (`[n]`).
  - [x] `ReflectionCriticAgent`: Completeness critique with **hard cap of exactly 1 revision pass**.
  - [x] `CitationValidator` (Audit Fix A3): Post-write regex validator stripping hallucinated citations not in sources and flagging dead URLs (`[n†dead-source]`).
  - [x] `ResearchOrchestrator`: Master state machine executing sequential steps and persisting reports to `./reports/report-{id}.md`.
- [x] **Security & Cost Guardrails**:
  - [x] `SafeWebReader`: SSRF DNS pre-resolution blocking loopback (`127.0.0.0/8`, `::1`), private RFC 1918 ranges, cloud metadata (`169.254.169.254`), and IPv6 ULA (`fc00::/7`).
  - [x] Manual per-hop redirect validation (max 3 hops) blocking 302 redirect bypass into cloud metadata.
  - [x] `CostTracker`: Model pricing table in paise (1 INR = 100 paise) with mid-run budget cap enforcement (₹15 default).
- [x] **Picocli CLI Runner**:
  - [x] `ResearchAgentCliRunner`: Terminal execution (`java -jar research-agent.jar --query "..." --depth QUICK`).
  - [x] Real-time console event streaming and ANSI-formatted progress.
  - [x] `WebApplicationType.NONE` in CLI mode to avoid port binding conflicts.
- [x] **Automated Testing & Verification**:
  - [x] 16 unit tests passing across `SafeWebReaderTest`, `CitationValidatorTest`, `VerifierAgentTest`, `CostTrackerTest`, and `ResearchOrchestratorTest`.
  - [x] Live end-to-end CLI execution producing cited Markdown report.
- **Git Commit**: `30ccb85`

---

### ✅ Sprint 2: Distributed Architecture, Docker, PostgreSQL Persistence & RabbitMQ Worker
> **Goal**: Decouple the research pipeline into an asynchronous RabbitMQ worker with Dead Letter Queue (DLQ), persist all runs/steps/sources/claims in PostgreSQL, and set up Redis Pub/Sub for cross-process event broadcasting.

- [x] **Infrastructure Setup**:
  - [x] `docker-compose.yml` provisioning PostgreSQL 16, RabbitMQ with Management UI & DLQ, and Redis 7.
  - [x] Add PostgreSQL driver, Spring Data JPA, Spring AMQP (RabbitMQ), and Spring Data Redis dependencies to `pom.xml`.
  - [x] Update `application.yml` with datasource, RabbitMQ, and Redis connection profiles.
- [x] **Data Model & JPA Entities**:
  - [x] `RunEntity`: `id`, `user_id`, `query`, `depth`, `status`, `budget_cap_paise`, `cost_paise`, `tokens_in`, `tokens_out`, `started_at`, `finished_at`, `failure_reason`.
  - [x] `StepEntity`: `id`, `run_id`, `seq`, `agent`, `action`, `prompt`, `response`, `tokens_in`, `tokens_out`, `cost_paise`, `duration_ms`, `created_at`.
  - [x] `SourceEntity`: `id`, `run_id`, `source_number`, `url`, `title`, `snippet`, `domain`, `content`, `content_hash`, `fetched_ok`, `fetch_error`.
  - [x] `ClaimEntity`: `id`, `run_id`, `statement`, `exact_quote`, `source_id`, `source_domain`, `status`, `supporting_domains`, `contradiction_notes`.
  - [x] `ReportEntity`: `id`, `run_id`, `query`, `title`, `markdown_content`, `pdf_path`, `version` (supports v1 draft vs v2 reflection revision), `confidence_score`, `total_sources`, `verified_claims_count`, `unverified_claims_count`.
  - [x] Spring Data Repositories for all entities.
- [x] **RabbitMQ Queue Architecture with DLQ (PRD §5 & Audit B6)**:
  - [x] Exchange: `research.exchange` (Direct).
  - [x] Primary Queue: `research.jobs.queue` with dead-letter routing to `research.dlx`.
  - [x] Dead Letter Exchange & Queue: `research.dlx` -> `research.dlq` for poison messages.
  - [x] Retry Policy: Up to 3 attempts with exponential backoff before routing to DLQ.
  - [x] `ResearchJobProducer`: Enqueues research job payload `{runId, query, depth, budgetCapPaise}`.
- [x] **Asynchronous Agent Worker Engine**:
  - [x] `ResearchWorkerConsumer`: Listens to `research.jobs.queue`, triggers `ResearchOrchestrator`, and updates database records after each state transition.
  - [x] Global daily spend cap check before starting run (Audit B4).
- [x] **Distributed Event Decoupling via Redis Pub/Sub (Audit A1)**:
  - [x] `RedisEventPublisher`: Worker publishes real-time step events to Redis channel `run:{runId}:events`.
  - [x] Prepared for API SSE bridge consumption in Sprint 3.
- [x] **Verification**:
  - [x] 39 unit tests passing across all components including JPA persistence, worker message consumption, daily spend cap guardrail, and Redis event publishing.

---

### ⚪ Sprint 3: REST API, Redis-to-SSE Streaming Bridge, Cancellation & Guardrails
> **Goal**: Expose the Spring Boot REST API, connect the Redis-to-SSE event streaming bridge for live browser updates, implement job cancellation, rate limiting, and worker crash recovery.

- [ ] **REST API Endpoints (PRD §9)**:
  - [ ] `POST /api/runs`: Create and enqueue research run `{query, depth, budgetCapPaise}` with `@Valid` Bean Validation (Audit B3: query max 500 chars, depth whitelist).
  - [ ] `GET /api/runs`: List user runs with pagination, date, cost, status.
  - [ ] `GET /api/runs/{id}`: Detailed run status, timing, tokens, cost.
  - [ ] `GET /api/runs/{id}/stream`: SSE endpoint streaming live progress events.
  - [ ] `POST /api/runs/{id}/cancel`: Request cancellation; halts LLM spend immediately.
  - [ ] `GET /api/runs/{id}/report`: Returns report Markdown, metadata, and draft version comparison (v1 vs v2) (Audit B8).
  - [ ] `GET /api/runs/{id}/trace`: Returns full step-by-step trace history.
- [ ] **Redis Pub/Sub to SSE Bridge (Audit A1)**:
  - [ ] `RedisEventSubscriber`: Listens to Redis channel `run:{runId}:events` and relays events directly to active client `SseEmitter` instances.
  - [ ] Heartbeat and disconnect handling for SSE connections.
- [ ] **Guardrails & Resilience**:
  - [ ] Rate Limiting (PRD F10 & Audit B7): Redis semaphore limiting to 3 concurrent active runs per user.
  - [ ] Worker Crash Recovery (Audit B5): On worker startup, query database for non-terminal runs (`IN_PROGRESS`), inspect last completed step, and resume execution.
  - [ ] Security: Basic JWT authentication filter on `/api/**` endpoints (Audit B2).

---

### ⚪ Sprint 4: Styled PDF Report Generation & Export
> **Goal**: Convert verified Markdown reports into professional, publication-quality PDF documents with citations and confidence appendices using OpenHTMLtoPDF.

- [ ] **Markdown-to-HTML Pipeline**:
  - [ ] Flexmark parser converting GitHub-flavored Markdown to clean XHTML.
  - [ ] Custom CSS styling: professional typography, margins, callout boxes for Executive Summary, and code block formatting.
- [ ] **PDF Document Layout**:
  - [ ] Title / Cover Page: Research query, date, duration, total cost, confidence rating, author watermark.
  - [ ] Executive Summary Callout Box: 3 key bullet points.
  - [ ] Core Verified Findings sections.
  - [ ] Low Confidence & Single-Source Findings appendix.
  - [ ] Citations Bibliography Table: `[n]`, Title, Domain, URL, Verbatim Supporting Quote.
- [ ] **Export Service & Endpoint**:
  - [ ] `PdfReportService`: Thread-safe PDF rendering to disk and byte stream.
  - [ ] `GET /api/runs/{id}/pdf`: Downloadable PDF report endpoint.
- [ ] **Failure Handling & Degradation**:
  - [ ] Partial/degraded reports generated gracefully even if certain sources fail or budget cap halts search early.

---

### ⚪ Sprint 5: React Web UI & Interactive Trace Viewer
> **Goal**: Build an intuitive, modern React web application (Vite + Tailwind CSS + Lucide icons) for submitting research queries, viewing real-time agent execution, inspecting traces, and reading reports.

- [ ] **Frontend Scaffolding**:
  - [ ] React 18+ with Vite, Tailwind CSS, and Lucide React icons in `frontend/`.
  - [ ] API client service with Axios/fetch and SSE hook (`useEventSource`).
- [ ] **Query Submission View**:
  - [ ] Research query input form with depth toggle (`Quick` 2min, `Standard` 5min, `Deep` 10min).
  - [ ] Budget cap slider (in ₹ / paise).
  - [ ] Validation feedback.
- [ ] **Live Agent Execution Dashboard**:
  - [ ] Real-time active agent indicator (Planner → Searcher → Reader → Verifier → Writer → Reflection Critic → Citation Validator).
  - [ ] Live streaming log of agent thoughts, discovered sources, and token cost accumulation.
  - [ ] Cancel Run button.
- [ ] **Report & Trace Viewer**:
  - [ ] Rendered Markdown report with styled headers, blockquotes, and interactive `[n]` citation tooltips.
  - [ ] Confidence Badge (Verified % vs Single-Source %).
  - [ ] One-click PDF download.
  - [ ] **Step Trace Inspector**: Expandable step-by-step audit table showing prompt, LLM response, duration, tokens, and cost in paise for every single step.
- [ ] **Run History**:
  - [ ] Table of past research runs with status badges, dates, costs, and quick link to view/re-run.

---

### ⚪ Sprint 6: Observability (LangFuse), Benchmark Eval Table & Launch Polish
> **Goal**: Integrate LangFuse tracing, run 10-question evaluation benchmark, package Docker Compose for one-click launch, and prepare production launch artifacts.

- [ ] **LangFuse Integration**:
  - [ ] LangChain4j LangFuse client integration tracing prompts, generations, and token usage to self-hosted/cloud LangFuse instance.
- [ ] **Evaluation Benchmark & Accuracy Flex (PRD §11 & §15)**:
  - [ ] Execute 10 fixed diverse research questions across science, tech, economics, and policy.
  - [ ] Measure end-to-end latency, cost per run (< ₹15 target), citation coverage (> 90%), and verification rate.
  - [ ] Publish the complete 10-Question Eval Table in `README.md`.
- [ ] **Containerization & Deployment**:
  - [ ] Multi-stage Dockerfile for Spring Boot backend and React frontend.
  - [ ] Full `docker-compose.yml` orchestrating app, PostgreSQL, RabbitMQ, Redis, and LangFuse.
- [ ] **Launch Checklist**:
  - [ ] Complete README with architecture diagram, agent loop explanation, cost table, and eval results.
  - [ ] Demo run recording / video script.
