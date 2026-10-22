# ResearchAgent engineering audit

Reviewed 10 October 2026. Scope: the local repository at commit c3c6ea5, including backend, frontend, configuration, tests, deployment files, and project claims. This is an implementation audit, not a guarantee that every defect has been found. It does not establish live provider accuracy, production performance, or current dependency vulnerability status.

The project has a coherent skeleton, but I would not release it as a trustworthy research service yet. The most urgent issues affect fabricated output, access control, unsafe PDF fetching, cancellation, spending, and job consistency. The frontend and container build path also have release blockers.

P1 means fix before a shared or paid pilot. P2 means a substantive correctness, operational, or product gap to address before claiming the associated feature complete. Findings below are established by source inspection unless a reproduction is explicitly stated. Proposed acceptance criteria are future checks, not tests already passed.

## Progress checked on 22 October 2026

| Finding | Current status | Evidence and limits |
|---|---|---|
| 01 | Fixed; offline regression suite passes | Real-provider failure and explicit demo checks run without paid model calls. |
| 02 | Fixed; security acceptance checks pass | Actual session/login/CSRF/CORS filters and controllers, two-user ownership checks, stream revocation, and H2 repository filtering are covered. |
| 03 | Resource access fixed; regression checks pass | A newly exposed image-loader bypass in OpenHTMLtoPDF 1.0.10 was closed by rejecting resource URI resolution. Normal and unsanitized-renderer probes record zero loopback requests; local images are absent and citation annotations remain clickable. Rendering size/time limits and caching remain follow-up work. |
| 04–05 | Fixed; current frontend production build passes | TypeScript and Vite build succeed. Browser appearance was not checked. |
| 06 | Dockerfile corrected | Maven builder image is present; Docker Compose was not executed in this check. |
| 07 | Fixed; cancellation regressions pass | Queued jobs are skipped, active workers consult persisted cancellation at operation boundaries, known in-flight usage is retained, and row locks/conditional updates prevent cancellation from being overwritten. Both terminal-state race orders and the real worker/API/database flow are covered with simulated provider calls. |
| 10 | Quote provenance fixed; regression checks pass | Reader accepts exact/whitespace-only matches and retains saved snapshot/hash/span metadata. Database tests cover full quotations and later page changes; Claims UI exposes the saved passage. Claim-count limits and semantic support remain separate work. |
| 13 | Fixed for the worker/API path | Reports and terminal state commit together; the worker suppresses early pipeline terminal events. The completion-event regression immediately reads the committed report successfully. |
| 12, 18, 20 | Later implementation changes retained | Confidence formula, streaming state, and partial-result loading changes are present. Offline backend regressions and frontend build pass; no new browser acceptance checks were performed. |
| 19 | Partially fixed | Callback refs prevent connection churn; disconnect still closes the stream, with no replay or missed-event recovery. |

Validation: **121 offline backend tests passed, zero failures/errors**, excluding the live-web `ResearchBenchmarkEvalTest`. This retains the previous 95 checks and adds 26 quotation matching/reader/persistence cases. Latest logs are in `target/quote-validation-regression.log` and `target/quote-validation-frontend-build.log`. These checks use isolated test accounts/data and do not use the user's credentials, paid models, or deployed infrastructure. Database checks use H2; PostgreSQL/Docker and browser acceptance have not been performed.

The next requested priority is **build-review comment 2: independent source corroboration**. Budgeting and model pricing are outside the user's current scope. The original observations below are historical; resolution notes and the current status above take precedence.

## Original validation performed on 10 October 2026

- Existing backend suite excluding ResearchBenchmarkEvalTest: **39 tests passed, zero failures/errors**. The sandboxed run stalled; the rerun outside it passed. Log: [backend test log](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/target/audit-backend-tests.log).
- Frontend production build: **failed** at App.tsx:119 with TS2339 for the unsupported inline property.
- Installed Tailwind/PostCSS integration: **failed** with the package's explicit error that tailwindcss is no longer the direct PostCSS plugin.
- Focused local probes: **two fabricated quotes accepted**, **writer spent after an exhausted budget**, **zero-source run completed with 0.5 confidence**, and **PDF renderer made one loopback HTTP request**. Probe source and output are temporary audit artifacts under target/audit: [probe source](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/target/audit/AuditProbe.java) and [probe output](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/target/audit/probe.log).
- The benchmark test was inspected, not executed: it mixes a mock LLM with live web access and is not a valid accuracy benchmark. No paid provider calls or live DNS-rebinding attack were performed. Docker deployment and browser interaction were not executed.
- Application source was not changed. Existing tests may produce ignored reports/build outputs.

## Findings

### 01. [P1] Silent fallback fabricates research when the model is unavailable

**Resolved on 10 October 2026:** Real-provider configuration errors now stop startup; generation failures propagate typed errors and fail the run without a report. Synthetic output requires explicit `provider=mock`, contains no extracted claims or quotes, and is labeled in the report. Regression checks cover credential errors, provider failures, and pipeline failure at every model stage. Other findings in this audit remain separate work.

Evidence: [LlmService.java:145](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/llm/LlmService.java:145), [LlmService.java:165](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/llm/LlmService.java:165).

Missing credentials, initialization errors, and generation exceptions all lead to hardcoded mock output. The mock reader invents statements and exact quotations; the mock writer returns the same generic report for unrelated queries. There is no demo marker in the resulting report or run status. A provider outage can therefore produce a successful-looking fabricated report.

**Fix:** Make demo mode explicit and isolated. In real mode, fail with a typed provider error or return an honestly labeled partial result. Preserve provider error details internally. Never substitute synthetic facts or quotes for failed calls.

**Acceptance:** With a missing key, rejected key, timeout, or rate limit, the production path produces no invented findings and no successful real-research status.

### 02. [P1] No authentication or ownership enforcement protects the API

**Implemented on 10 October; verified on 22 October 2026:** Configured local BCrypt accounts, browser sessions, CSRF protection, explicit credentialed CORS origins, and sign-in/sign-out UI were added. API creation derives identity from the authenticated principal, lists filter by owner, and all run/artifact operations check ownership, including SSE/PDF. Two-user API checks pass for history, metadata, reports, traces, sources, claims, PDF, streaming, and cancellation. Missing/stale CSRF, anonymous paid work, spoofed user headers, invalid passwords, unsafe account configuration, and untrusted origins are rejected. Logout, identity changes, and session invalidation revoke existing streams. H2 checks confirm ownership filtering occurs before pagination. Local accounts still require administrator setup, and no browser or deployment acceptance is claimed.

Evidence: [RunController.java:21](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/controller/RunController.java:21), [RunService.java:107](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RunService.java:107).

The create endpoint trusts X-User-Id, the list endpoint returns all runs, and reads, traces, sources, PDF export, cancellation, and event subscriptions have no ownership check. The frontend sends no identity header, so normal users also share default-user. Wildcard CORS expands browser access. The claimed JWT filter and Spring Security configuration are absent.

**Fix:** Derive identity from a verified session or token; scope every operation and subscription to that principal. Limit allowed browser origins. Do not use a request header as proof of identity.

**Acceptance:** Two-user API tests show that user A cannot list, read, stream, export, or cancel user B's run; unauthenticated requests cannot start paid work.

### 03. [P1] PDF rendering can fetch internal URLs outside SafeWebReader

**Implemented on 10 October; renderer bypass fixed and verified on 22 October 2026:** Report HTML is sanitized to a text-formatting allowlist. Images, embedded content, raw CSS, and arbitrary attributes are removed, and the external XHTML DTD is absent. A new adversarial check initially recorded ten HTTP requests because the installed OpenHTMLtoPDF 1.0.10 image loader bypasses the access predicates. Resource URI resolution now rejects every URI independently of those predicates. Normal and deliberately unsanitized renderer probes record zero requests, including redirect paths; local stylesheet markers are absent, local images are not embedded, and PDF citation links remain clickable. Repeated-rendering caching and CPU/time limits remain follow-up work.

Evidence: [PdfReportService.java:50](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/PdfReportService.java:50), [PdfReportService.java:87](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/PdfReportService.java:87).

Generated Markdown is converted to HTML and rendered without an application resource-access policy. Jsoup here converts markup to XHTML; it does not sanitize allowed elements or URLs. A controlled local probe embedded a loopback image URL and recorded one HTTP request from PDF generation. Model-produced or injected images can therefore reach internal services even though normal page fetching blocks them. Repeated PDF downloads also repeat rendering work.

**Fix:** Disable external resource loading by default, sanitize the permitted markup, and configure the renderer's resource handling with an explicit allowlist if images are needed. Add size/time limits and cache rendered results by report version.

**Acceptance:** A report containing local, private-network, redirected, and file resource references cannot cause forbidden resource access. The existing loopback probe must record zero requests.

### 04. [P1] The frontend does not build

**Resolved on 10 October 2026 while integrating sign-in:** The obsolete `react-markdown` inline property was removed, and code/block formatting now uses separate `code` and `pre` components. TypeScript compilation and the frontend production build pass.

Evidence: [App.tsx:119](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:119).

The production build fails with TS2339: Property 'inline' does not exist on the react-markdown code renderer props. The shipped renderer assumes an older API shape than the installed types. This also blocks the frontend Docker build.

**Fix:** Update the Markdown renderer to the installed API and render code/pre blocks with the correct component contracts.

**Acceptance:** npm run build exits successfully from a clean dependency installation.

### 05. [P1] Tailwind configuration contains a second frontend build blocker

**Resolved on 10 October 2026 while integrating sign-in:** Tailwind v4 now uses `@tailwindcss/postcss`, the Tailwind CSS import, explicit legacy config loading, and mappings for the existing CSS color variables. The frontend production build passes. The package installer reported one high-severity dependency advisory; its relevance has not been investigated in this repair.

Evidence: [postcss.config.js:1](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/postcss.config.js:1), [index.css:1](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/index.css:1), [package.json:1](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/package.json:1).

The project installs Tailwind 4 but uses tailwindcss directly as its PostCSS plugin and the older directive/configuration pattern. A direct local invocation of the installed plugin rejected this configuration and asked for @tailwindcss/postcss. Fixing the TypeScript error alone will not finish the build.

**Fix:** Complete the Tailwind migration, including its PostCSS integration and theme loading, or consistently use a compatible earlier setup. Check actual generated styles as well as compilation.

**Acceptance:** The stylesheet pipeline builds and the custom theme utilities render correctly in the dashboard.

### 06. [P1] The backend Docker build has no Maven toolchain

**Resolved on 20 October 2026:** Updated the builder stage to `maven:3.9-eclipse-temurin-21-alpine` so the container build compiles the backend directly via Maven without requiring a host Maven wrapper or pre-installed host toolchain.

Evidence: [Dockerfile:1](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/Dockerfile:1).

The build stage uses a JDK image, copies only pom.xml and src, and calls ./mvnw or mvn. The repository has no Maven wrapper, no wrapper is copied, and the Dockerfile does not install Maven. The documented full-stack launch is therefore not supported by a self-contained backend build. This finding is from the Dockerfile; I did not run Docker.

**Fix:** Use a Maven build image or check in and copy a complete Maven wrapper. Pin the build tool and run the normal verification path in CI.

**Acceptance:** docker compose build works on a machine with Docker alone, without relying on host Maven.

### 07. [P1] Cancel does not stop execution or spending

**Resolved and verified on 22 October 2026:** Worker claim and cancellation/finalization use the same row lock; cancelled queued messages are skipped and duplicate active claims cannot restart work. A scoped execution guard checks persisted cancellation before model/search/page operations and periodically during active page reads. Both model providers are configured for one SDK attempt so hidden retry loops cannot bypass cancellation. Cancellation during planner, reader, and reflection calls stops subsequent calls, page reads, and revision. Returned token/cost usage is retained. Final artifacts and status commit together, cancellation events wait for commit, and stale startup recovery uses a conditional update instead of overwriting terminal rows. Concurrency checks cover either completion or cancellation winning the lock; an actual worker/API/database regression cancels during a simulated reader call, records usage, and performs no subsequent calls. Cancellation is cooperative: already-started network/provider calls may finish or time out and may be billed; usage omitted by a failed provider response cannot be recovered from that response. Worker leases, crash resumption, outbox publication, and PostgreSQL deployment checks remain separate work.

Evidence: [RunService.java:115](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RunService.java:115), [ResearchOrchestrator.java:67](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ResearchOrchestrator.java:67), [ResearchWorkerConsumer.java:79](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/worker/ResearchWorkerConsumer.java:79).

The API changes the database row and emits CANCELLED, but never calls the orchestrator cancellation method or sends a cancellation signal the worker reads. The worker also starts already-cancelled queued messages and can overwrite cancellation with RUNNING or COMPLETED. Even the orchestrator's local cancellation check only runs between whole stages, not within the reader's page loop or an active model call.

**Fix:** Persist a cancellation request the owning worker checks before claiming a job and before each expensive operation. Make terminal transitions conditional and irreversible. Cancel in-flight requests where supported and report any unavoidable in-flight cost honestly.

**Acceptance:** Cancel a queued run and an active multi-page run. Neither starts further provider calls, and neither later changes to COMPLETED.

### 08. [P1] Per-run budget limits are not enforced at the model boundary

Evidence: [WriterAgent.java:105](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/WriterAgent.java:105), [VerifierAgent.java:106](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/VerifierAgent.java:106), [ReflectionCriticAgent.java:153](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ReflectionCriticAgent.java:153).

Reader iterations and entry to reflection check the budget, but verification and writing do not. A requested reflection revision also proceeds without checking after the critique call. Cost is measured after a call and generation has no configured output-token reservation. A probe started the writer with an already-exhausted budget and observed an additional charge.

**Fix:** Centralize budget admission, usage recording, and output limits around every provider call. Reserve bounded call cost and explicitly decide whether remaining budget permits another operation. Include search charges where paid search is used.

**Acceptance:** An exhausted budget prevents every new paid operation, including planner repair and reflection revision; concurrent calls cannot spend the same reservation.

### 09. [P1] Global daily spend checks race and never roll their date forward

Evidence: [DailySpendCapService.java:44](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/DailySpendCapService.java:44), [DailySpendCapService.java:54](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/DailySpendCapService.java:54), [ResearchWorkerConsumer.java:82](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/worker/ResearchWorkerConsumer.java:82).

todayStartOfDay is set at startup and resetForNewDay has no caller or scheduled trigger. A long-running service keeps accumulating spend from its startup date. Separately, workers check totals without reserving budget; active work is charged only after completion, so multiple workers can all pass the same check. Crashed or CLI work is absent from this ledger.

**Fix:** Use a durable daily spend ledger with an explicit timezone, atomic reservations, incremental usage settlement, and recovery of reservations. Compute the date window from the clock for each admission.

**Acceptance:** Tests cover day rollover, multiple concurrent workers, crashes after provider usage, and all supported entry points.

### 10. [P1] Quotes are accepted without checking that the source contains them

**Quote provenance resolved on 22 October 2026:** Empty, invented, and mismatched quotes are excluded before Claim construction. Exact and whitespace-only matching preserve original source spans, submitted quotations, snapshot UUID/hash/URL, normalization version, and UTF-16 offsets. Persistence retains full quotations, and the Claims UI highlights saved evidence. Historical claims remain explicitly without validation metadata. Twenty-six added regressions pass alongside the prior suite. Semantic support, independence, report citations, and enforcement of the requested five-claim maximum remain separate work.

Evidence: [ReaderAgent.java:149](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ReaderAgent.java:149).

Any nonempty statement is accepted. The exactQuote may be empty or invented; the code never matches it against fetched text. The local probe supplied a one-sentence source and the reader accepted two quotes that did not occur in it. Later verification treats these claims as evidence.

**Fix:** Require a valid supporting span in normalized source text, preserve its location and content version, and reject unsupported extraction output. Enforce the maximum claim count in code.

**Acceptance:** Invented, empty, and mismatched quotes fail validation; every accepted quote maps back to stored source text.

### 11. [P1] Citation auditing does not establish factual support

Evidence: [CitationValidator.java:71](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/CitationValidator.java:71), [CitationValidator.java:94](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/CitationValidator.java:94).

The validator only checks whether [n] names a fetched source. It cannot detect a factual claim cited to an unrelated page. Unknown numbers are deleted while the associated assertion remains, so sanitization can turn a falsely cited claim into an uncited published claim. Citation anomalies do not prevent successful completion. The final replacement of repeated spaces can also alter Markdown indentation and code blocks.

**Fix:** Represent final factual claims and their supporting source spans explicitly. Validate support and coverage, then remove, qualify, or reject unsupported assertions. Generate bibliography entries from the validated source map and preserve Markdown structure.

**Acceptance:** A real but irrelevant source cannot validate a claim, and stripping an invalid citation cannot leave an unqualified unsupported assertion in a successful report.

### 12. [P1] Confidence can increase when evidence is missing or contradictory

**Resolved on 20 October 2026:** Updated `calculateConfidenceScore` to require non-zero sources and non-zero claims (`0.0` when evidence is missing) and included `contradictionCount` in the denominator (`verified / (verified + unverified + contradictions)`), ensuring zero evidence yields 0.0% confidence and contradictions penalize the corroboration rating.

Evidence: [ResearchOrchestrator.java:221](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ResearchOrchestrator.java:221), [ResearchOrchestrator.java:290](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ResearchOrchestrator.java:290).

Zero verified and zero unverified claims produces 0.5 confidence. Contradictions are excluded from the denominator, so two verified claims plus many contradictory claims can still yield 100%. Duplicate extracted statements are counted separately. The local probe completed a run with zero sources and 50% confidence.

**Fix:** Return insufficient-evidence status when evidence is missing. Report counts and coverage separately; include contradictions, unsupported final claims, and unique assertion coverage. Describe this as a corroboration metric unless actual probability calibration exists.

**Acceptance:** Zero evidence never produces a verified percentage; contradictions and duplicated evidence cannot inflate the result.

### 13. [P1] Completion is announced before the report is available

**Resolved for the async worker/API path on 22 October 2026 as part of cancellation finalization:** The worker filters terminal events from the orchestrator, persists report/artifacts and terminal state in one transaction, and publishes the actual committed terminal state after that transaction returns. A regression processes the actual worker and immediately reads the report from the completion-event callback. A cancellation that wins finalization publishes no false completion and stores no final database report.

Evidence: [ResearchOrchestrator.java:251](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ResearchOrchestrator.java:251), [ResearchWorkerConsumer.java:105](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/worker/ResearchWorkerConsumer.java:105).

The orchestrator publishes COMPLETED before returning to the worker. Only afterward does the worker persist cost, steps, sources, claims, and the report; it also marks the row completed before those writes. A browser responding immediately to completion can receive a report 404. Subsequent persistence failure can leave a previously announced success incomplete.

**Fix:** Persist the final result and terminal state consistently before emitting the terminal event. Publish committed events through an outbox or an equivalent durable mechanism.

**Acceptance:** When a client receives COMPLETED, all promised report endpoints immediately return the committed result.

### 14. [P1] Job publication is not atomic with run creation

Evidence: [RunService.java:64](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RunService.java:64), [RunService.java:79](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RunService.java:79), [ResearchJobProducer.java:23](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/producer/ResearchJobProducer.java:23).

A RabbitMQ message is sent inside a database transaction before its commit. A fast worker can receive the job before its row exists; updateRunStatus silently returns on missing rows. A database rollback can also leave a published job. Conversely, there is no confirmed publication/outbox flow to reliably reconcile a committed row with broker delivery.

**Fix:** Write the run and an outbox record in one transaction, then publish with confirmation and retry. The consumer should require a valid committed run before starting.

**Acceptance:** Failures around database commit, broker acknowledgement, and worker receipt cannot produce orphan jobs or permanently unprocessed runs.

### 15. [P1] Redelivery and persistence failures can repeat expensive research

Evidence: [ResearchWorkerConsumer.java:74](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/worker/ResearchWorkerConsumer.java:74), [ResearchWorkerConsumer.java:143](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/worker/ResearchWorkerConsumer.java:143), [RunPersistenceService.java:118](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RunPersistenceService.java:118).

The consumer has no atomic job claim, processed-message check, or completed-run guard. It always runs the whole pipeline. A failure while saving the result can cause AMQP retry to repeat all paid work. Steps and sources receive new IDs on subsequent saves, and run cost is overwritten with the last attempt rather than recording all attempts. In contrast, exceptions caught inside the orchestrator become FAILED return values and do not trigger broker retry, regardless of whether they are transient.

**Fix:** Add an atomic claim/lease and attempt identity, idempotent writes, a durable cost ledger, and explicit error classification. Retry persistence separately from provider execution when the result already exists.

**Acceptance:** Duplicate delivery causes one logical execution; a database failure after generation cannot repeat provider calls or undercount cost.

### 16. [P1] Progress is not checkpointed, and startup recovery fails other workers' jobs

Evidence: [ResearchWorkerConsumer.java:96](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/worker/ResearchWorkerConsumer.java:96), [WorkerCrashRecoveryService.java:57](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/WorkerCrashRecoveryService.java:57).

Steps, sources, claims, and usage are saved only after the whole orchestrator returns. A crash loses intermediate progress and spend. On any application startup, recovery scans every active or queued run and marks it FAILED, with no owner, heartbeat, lease expiry, or age check. Starting another instance can fail healthy jobs running elsewhere and valid jobs still waiting in RabbitMQ. It does not resume from a recorded step as the sprint document claims.

**Fix:** Commit checkpoints after stages or calls and attach a worker lease. Recover only expired claims, preserving queued work. Define whether recovery resumes or deliberately restarts, including how cost is counted.

**Acceptance:** Restarting or scaling the service cannot fail another healthy worker's run; interrupted work has durable progress and usage.

### 17. [P1] Concurrency limiting is a non-atomic count, not a semaphore

Evidence: [RateLimiterService.java:47](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RateLimiterService.java:47), [RateLimiterService.java:61](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RateLimiterService.java:61).

Each request loads every run, counts active rows, and then separately creates a new one. Concurrent requests can all pass. Redis is injected but unused, and database errors are treated as zero active runs. Caller-controlled user IDs make the policy trivial to bypass until authentication is fixed.

**Fix:** Reserve a slot atomically under the authenticated principal using database locking or an actual Redis operation. Count with a filtered database query and recover/release reservations consistently.

**Acceptance:** Many simultaneous create requests cannot exceed the per-user limit; storage failures do not silently allow unlimited paid work.

### 18. [P1] The live dashboard and cancel button never become active

**Resolved on 20 October 2026:** Derived active run streaming state from lifecycle status (`isTerminal`). Form submit now transitions `isStreaming` to `true`, and history selection restores `isStreaming=true` for active in-flight runs, making live console logs, current-step highlighting, and the cancel button responsive as designed.

Evidence: [App.tsx:256](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:256), [App.tsx:485](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:485).

isStreaming starts false and every setter sets it false. Nothing sets it true. The console, current-stage highlighting, and cancel button all depend on it, so the advertised live controls stay hidden even while events arrive.

**Fix:** Derive active state from the run lifecycle and connection state, with distinct queued, running, reconnecting, and terminal behavior.

**Acceptance:** A newly started or reopened active run displays progress and offers cancellation until it becomes terminal.

### 19. [P1] The event connection is recreated on every render and cannot recover missed events

**Partially implemented on 20 October; reviewed on 22 October 2026:** `useEventSource` uses callback refs and subscriptions depend on `runId`/`active`, addressing render-driven connection churn. However, `onerror` still invokes the terminal callback and closes the connection; reconnect, replay, and missed-event recovery remain unimplemented. This finding stays open for that remaining behavior.

Evidence: [App.tsx:200](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:200), [App.tsx:241](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:241), [App.tsx:280](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:280).

The effect depends on two callbacks passed inline by App. Every state update creates new callback identities and tears down/reopens EventSource. Each event changes state, so the connection churns during normal operation. Errors explicitly close it; the server has neither event IDs nor replay nor an initial run snapshot. Reconnecting after completion can miss the only terminal event.

**Fix:** Stabilize the subscription callbacks, subscribe only while needed, and combine reconnect support with durable event IDs or a current-state fetch/poll fallback.

**Acceptance:** Many incoming events keep one connection alive; a disconnect across completion still delivers the final state.

### 20. [P1] New reports do not load automatically; partial run reads discard useful results

**Resolved on 20 October 2026:** Switched `fetchRunDetails` to `Promise.allSettled` so that 404 on ungenerated reports does not discard valid run metadata, traces, sources, or claims. Triggered automatic `fetchRunDetails` upon terminal event receipt so finished reports render immediately without manual reload.

Evidence: [App.tsx:285](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:285), [App.tsx:308](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:308).

The completion handler fetches only run metadata. Reports, traces, sources, and claims are fetched only when selecting history or cancelling. fetchRunDetails groups every endpoint in Promise.all, so the expected report 404 for a running, failed, or cancelled run prevents even successful metadata and trace responses from being displayed. Changing history selection also lacks stale-response protection and can leave the previous report visible while the new request is pending.

**Fix:** Fetch committed results on terminal completion. Load metadata and optional artifacts independently; clear or key state by run ID and abort/ignore stale responses.

**Acceptance:** Completion automatically displays the report; runs without a report still show their status, errors, sources, and traces; rapid selection never mixes runs.

### 21. [P2] The browser API URL is fixed to the visitor's localhost

Evidence: [App.tsx:99](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:99), [Dockerfile:9](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/Dockerfile:9).

All requests and PDF links target http://localhost:8085. After deployment, a remote visitor's browser contacts their own machine. The provided Nginx image has no API proxy configuration. HTTPS deployment would also encounter an HTTP API URL.

**Fix:** Use a same-origin API route with an explicit reverse proxy, or validated environment configuration for the API origin.

**Acceptance:** The built dashboard works from a second device using the deployment URL without source edits.

### 22. [P2] Different hosts are incorrectly treated as independent evidence

Evidence: [SearchResult.java:44](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/tools/SearchResult.java:44), [VerifierAgent.java:118](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/VerifierAgent.java:118), [ReaderAgent.java:75](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ReaderAgent.java:75).

The domain helper removes only www.; a.example.org and b.example.org count separately. Reader keeps the original URL/domain after redirects even though SafeWebReader returns the final URL. Two original URLs can therefore resolve to the same publisher or page and still count as independent. Syndicated copies and shared upstream sources are not detected. Two domains alone also do not establish that a claim is true.

**Fix:** Track final canonical URL, registrable domain, publisher identity where available, content duplicates, and evidence origin. Label the result corroborated rather than proven true, and favor primary sources for high-impact assertions.

**Acceptance:** Subdomains, redirects to one page, and copied articles cannot independently raise verification.

### 23. [P2] Reflection can add new facts without seeing their evidence

Evidence: [ReflectionCriticAgent.java:64](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ReflectionCriticAgent.java:64), [ReflectionCriticAgent.java:149](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ReflectionCriticAgent.java:149).

The sources parameter is unused. Revision receives the query, critique, and draft, without verified claims, source text, or the writer's verified-only constraints. A request to fill a thin section can introduce unsupported content. The subsequent validator checks citation numbers, so reuse of a valid number can pass. Invalid critique JSON defaults to approving the draft.

**Fix:** Give revision the same evidence constraints as writing and revalidate all revised assertions. Treat malformed critique output as a failed review, not approval.

**Acceptance:** A revision cannot introduce a new factual assertion without a valid supporting evidence span.

### 24. [P2] Report version history is overwritten

Evidence: [RunPersistenceService.java:199](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RunPersistenceService.java:199), [RunService.java:145](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RunService.java:145).

saveReport loads the latest entity and changes the same row. On its first save it always stores version 1, ignoring the domain report's revision version. The initial draft is never separately persisted. The read API's previous-draft logic requires multiple rows that this write path never creates. The draft-diff test seeds those rows manually, hiding the mismatch.

**Fix:** Persist immutable draft and final versions with unique run/version keys. Save the original draft before revision and honor explicit versions.

**Acceptance:** An actual revised run stores both drafts and returns their real content through the comparison endpoint.

### 25. [P2] Cost totals omit calls and may use the wrong model price

Evidence: [PlannerAgent.java:78](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/PlannerAgent.java:78), [ResearchOrchestrator.java:126](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ResearchOrchestrator.java:126), [CostTracker.java:68](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/tools/CostTracker.java:68).

The planner repair call is not included in its returned usage. The orchestrator records planner usage with model name 'planner', which falls through to Gemini pricing even for OpenAI. All model names containing gpt/openai get one mini-model rate; all others get one Gemini rate. Rounding occurs separately for each call, paid search is absent, and provider fallback can use Gemini while getActiveModelName still returns the configured OpenAI name. Failed/cancelled orchestrator paths also omit token totals.

**Fix:** Record every actual provider attempt in one ledger with the actual provider/model, price version, precise units, and search charges. Reject unpriced models or explicitly label estimates. Populate usage on every terminal path.

**Acceptance:** Ledger totals reconcile with all successful, repaired, failed, retried, and cancelled calls, independent of the selected entry point.

### 26. [P2] Search failures are turned into invented source records

Evidence: [FallbackSearchService.java:68](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/tools/FallbackSearchService.java:68), [SearcherAgent.java:102](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/SearcherAgent.java:102).

If fallback search fails or has no results, it constructs a Wikipedia URL from the entire query and assigns a fabricated title, snippet, and score. That is not a discovered result. Searcher also lowercases the entire URL, potentially merging different case-sensitive paths, while ignoring redirects, fragments, and tracking parameters. It does not enforce the documented domain-diversity policy.

**Fix:** Return explicit search failure/no-results states. Canonicalize URLs without changing case-sensitive components, deduplicate final content, and track query coverage and publisher diversity.

**Acceptance:** A provider outage cannot manufacture a source; distinct case-sensitive pages remain distinct and duplicated evidence is not counted twice.

### 27. [P2] Planner and extraction limits are prompt requests, not validated contracts

Evidence: [PlannerAgent.java:105](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/PlannerAgent.java:105), [ReaderAgent.java:149](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ReaderAgent.java:149).

Planner accepts unbounded subquestion/query arrays and can accept an array that filters down to no valid questions. Reader does not enforce the requested five-claim maximum or require a nonempty quote. Invalid verifier JSON only logs a warning. Output format, field types, array sizes, and semantic validation are inconsistent across stages.

**Fix:** Define explicit validated output schemas and semantic invariants per stage. Bound input/output size and classify parse failures. Make any deterministic fallback visible in run metadata.

**Acceptance:** Malformed, oversized, irrelevant, or structurally empty model output cannot silently advance as a successful stage.

### 28. [P2] Web-reader DNS checks are not bound to the actual connection

Evidence: [SafeWebReader.java:70](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/tools/SafeWebReader.java:70), [SafeWebReader.java:169](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/tools/SafeWebReader.java:169).

validateUri resolves and approves addresses, then HttpURLConnection connects using the hostname independently. The code does not pin the approved address or verify the connected peer. Thus the DNS check alone does not establish the documented DNS-rebinding guarantee. Actual exploitability depends on resolver and JVM caching behavior; I did not perform a live DNS-rebinding attack.

**Fix:** Bind resolution and connection through a controlled client/resolver while preserving TLS hostname verification, or enforce an egress policy that denies internal destinations independently of DNS.

**Acceptance:** A controlled resolver-change test cannot cause the request to reach a destination that was not approved.

### 29. [P2] External reads and model calls lack a complete execution envelope

Evidence: [SafeWebReader.java:24](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/tools/SafeWebReader.java:24), [SafeWebReader.java:100](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/tools/SafeWebReader.java:100), [LlmService.java:55](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/llm/LlmService.java:55).

The reader's read timeout is per blocking read, not a whole-run deadline; a slow stream can keep a worker busy. The nominal 2 MB bound counts decoded characters. Early non-200 and unsupported-content exits bypass the disconnect finally block. Source handling rejects PDFs, a material gap for literature research. Model builders expose no application-level deadline, explicit output limit, or cancellation integration.

**Fix:** Add request and run deadlines, byte limits, response cleanup on every path, supported-content rules, and configured model output limits. Decide explicitly whether PDF sources are supported and reflect that scope in the product.

**Acceptance:** Slow streams, oversized output, unsupported documents, and cancellation cannot tie up a worker indefinitely or silently appear as complete research.

### 30. [P2] Live events can be duplicated, and the heartbeat is inactive

Evidence: [RedisEventPublisher.java:79](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/event/RedisEventPublisher.java:79), [RedisSseBridgeService.java:144](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RedisSseBridgeService.java:144).

The publisher sends via Redis and also broadcasts directly to the local bridge. A co-located API/worker subscribed to Redis can deliver each nonterminal event twice. The bridge's heartbeat has @Scheduled but no @EnableScheduling configuration exists in the project. Its listener container is constructed locally rather than managed with an explicit lifecycle, and subscriptions have no run validation or per-user connection limit.

**Fix:** Choose one delivery path or assign IDs and deduplicate. Enable scheduling explicitly or use another managed heartbeat mechanism. Manage the listener lifecycle and authorize/bound subscriptions.

**Acceptance:** One state change yields one event per client, heartbeats occur, and shutdown releases listener resources.

### 31. [P2] The advertised full execution trace is not stored

Evidence: [ReaderAgent.java:125](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/ReaderAgent.java:125), [WriterAgent.java:120](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/WriterAgent.java:120), [RunPersistenceService.java:123](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/RunPersistenceService.java:123), [App.tsx:619](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:619).

Several agents write summaries such as 'Found N claims' in place of the actual prompt/response. Persistence truncates both to 4,000 characters. Failed fetches skip a reader trace entirely. The frontend displays a summary table without an expandable prompt/response inspector. There is insufficient detail to reproduce why a claim was accepted or how a report was revised.

**Fix:** Store access-controlled, redacted provider-call records with model/settings, full structured output or artifact references, parse errors, evidence IDs, and attempt IDs. Distinguish progress messages from model reasoning or a complete audit trace.

**Acceptance:** A reported factual error can be traced from final assertion to model response to source span and fetch result.

### 32. [P2] LangFuse is a configuration stub, not an integration

Evidence: [LangFuseConfig.java:35](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/config/LangFuseConfig.java:35), [Sprints.md:154](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/Sprints.md:154).

The class reads settings and logs whether tracing is enabled. Nothing exports traces, generations, costs, or latency, and no other source uses the class as a tracing client. Docker Compose also contains no LangFuse service despite the completed checklist.

**Fix:** Implement a real exporter with correlation IDs and delivery/error behavior, or mark the feature incomplete and describe database tracing accurately.

**Acceptance:** A real run produces correlated traces in the configured observability destination; a disconnected destination is observable.

### 33. [P2] Several documented configuration settings do not control behavior

Evidence: [CompositeSearchService.java:25](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/tools/CompositeSearchService.java:25), [LlmService.java:55](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/llm/LlmService.java:55), [ResearchDepth.java:3](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/model/ResearchDepth.java:3), [application-test.yml:25](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/resources/application-test.yml:25), [docker-compose.yml:54](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/docker-compose.yml:54).

SEARCH_PROVIDER is not read by CompositeSearchService; an available Tavily key wins. The model initializer can choose Gemini for a configured mock or unavailable OpenAI provider if a Gemini key exists. Depth limits and budget defaults are duplicated in code instead of bound to their YAML settings. The test profile uses llm.provider and cost.global-daily-cap-paise outside the research-agent namespace. Compose forwards keys but not the documented model/provider selectors, so changing them in .env does not configure the container.

**Fix:** Bind validated configuration once, make provider selection exhaustive, and remove unused settings. Forward supported deployment variables explicitly. Ensure test/demo profiles cannot accidentally make paid calls.

**Acceptance:** A configuration contract test proves each supported setting changes behavior and invalid combinations fail at startup.

### 34. [P2] The standalone CLI lifecycle is incomplete

Evidence: [ResearchAgentApplication.java:13](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/ResearchAgentApplication.java:13), [ResearchAgentCliRunner.java:61](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/cli/ResearchAgentCliRunner.java:61), [application-local.yml:1](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/resources/application-local.yml:1).

CLI mode only disables the HTTP server; it still constructs database, queue, Redis, recovery, and listener components. The local profile changes the database but does not remove those infrastructure components, so standalone operation retains their startup requirements/retry behavior. Positional queries do not select non-web mode. Picocli's result code is discarded, the Spring context is not explicitly closed, and CLI execution bypasses the daily-spend/persistence path. Console messages name port 8080 while configuration uses 8085.

**Fix:** Separate CLI and server configuration, run both through the intended accounting policy, close the CLI context, and propagate a meaningful process exit code.

**Acceptance:** Help and a standalone query need only their documented dependencies, terminate correctly, and return nonzero on failure without hanging on background infrastructure.

### 35. [P2] Database schema does not enforce key lifecycle and evidence invariants

Evidence: [RunEntity.java:11](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/entity/RunEntity.java:11), [SourceEntity.java:13](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/entity/SourceEntity.java:13), [ClaimEntity.java:36](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/entity/ClaimEntity.java:36), [ReportEntity.java:13](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/entity/ReportEntity.java:13), [application.yml:21](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/resources/application.yml:21).

Run references are plain strings without declared foreign keys; source IDs in claims are per-run citation numbers, not the SourceEntity primary key. There are no unique constraints for run/source number, run/step sequence, or run/report version, and no optimistic version or conditional lifecycle transition. Hibernate ddl-auto:update replaces reviewed migrations. Concurrent writers and retries can create inconsistent histories that the database will accept.

**Fix:** Define explicit relationships or composite foreign keys, uniqueness and status invariants, indexed user/status queries, and versioned migrations. Make lifecycle transitions atomic.

**Acceptance:** Duplicate evidence/report versions and orphan rows are rejected, and concurrent cancel/complete updates cannot silently overwrite one another.

### 36. [P2] Deployment does not preserve all promised state

Evidence: [docker-compose.yml:13](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/docker-compose.yml:13), [docker-compose.yml:21](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/docker-compose.yml:21), [docker-compose.yml:46](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/docker-compose.yml:46), [Dockerfile:6](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/Dockerfile:6).

Only PostgreSQL has an explicit named volume. RabbitMQ data and report files have no explicit persistence arrangement that survives the intended container recreation flow. Infrastructure ports and local credentials are published broadly, and there is no deployment health/readiness check for the app. No .dockerignore is present while the frontend copies its entire context, potentially including local node_modules, dist, and frontend environment files.

**Fix:** Define durable broker/report storage, recovery and backup procedures; restrict infrastructure exposure; externalize credentials; add application readiness and a precise Docker build context. These requirements matter before serving real users.

**Acceptance:** Recreating containers does not lose queued work or required artifacts, and clean builds do not incorporate host dependencies or unintended environment files.

### 37. [P2] Error and empty-state handling misleads users

Evidence: [GlobalExceptionHandler.java:44](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/controller/GlobalExceptionHandler.java:44), [App.tsx:158](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:158), [App.tsx:380](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:380), [App.tsx:611](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:611).

The API exposes raw exception messages and its generic handler can turn malformed request errors into server errors. The frontend reads err.message while the server uses error and validationErrors, hiding useful validation feedback. Cancellation never checks HTTP success. The online badge is static, failureReason is not rendered, and runs with no report still display 'Report is generating'. Form labels lack explicit input associations, and history rows are mouse-only divs.

**Fix:** Use a stable, safe error contract, handle expected client errors as 4xx, render actual lifecycle/connection state, and make form/history controls accessible.

**Acceptance:** Validation, outages, cancellation failure, failed runs, and keyboard navigation all produce clear, accurate user feedback.

### 38. [P2] Report rendering promises exceed the implemented output

Evidence: [PdfReportService.java:40](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/PdfReportService.java:40), [PdfReportService.java:87](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/service/PdfReportService.java:87), [App.tsx:103](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/frontend/src/App.tsx:103), [WriterAgent.java:148](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/main/java/com/researchagent/agent/WriterAgent.java:148).

Neither Markdown parser is configured for the documented GFM table support. Citation tooltips and a deterministic bibliography/quote table are absent. PDF rendering only consumes ReportResponse, so it cannot build a source/quote appendix from stored evidence. The save-PDF method is not used by the download endpoint, and pdfPath is not persisted by saveReport. The writer also supplies generic success-sounding executive-summary bullets if parsing fails, and the final report keeps the pre-revision summary.

**Fix:** Define a report schema and render the bibliography, evidence appendix, summary, and Markdown features deterministically from final validated data. Align download-versus-save behavior and metadata.

**Acceptance:** A report with tables, citations, revision, and missing evidence renders consistently in the browser, CLI, and PDF without invented summary text.

## Problems in how the project is being built

**Completion is being recorded before user behavior is demonstrated.** [Sprints.md:12](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/Sprints.md:12) marks every sprint complete, but authentication, cancellation, live dashboard behavior, draft history, LangFuse export, and the Docker build do not meet those claims. A class name, endpoint, or checked box is not sufficient evidence. Reopen these deliverables and give each a concrete acceptance scenario and captured result.

**The published benchmark is not supported by the checked-in evaluation.** [ResearchBenchmarkEvalTest.java:57](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/test/java/com/researchagent/eval/ResearchBenchmarkEvalTest.java:57) explicitly creates a mock model. It runs QUICK depth, which caps pages at six, while [README.md:71](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/README.md:71) presents STANDARD runs with 8–10 sources and 84–93% verification. The mock generator has no VERIFIER JSON branch. The test accepts confidence >= 0.5, and the empty-evidence shortcut returns exactly 0.5. Consequently, inability to fetch evidence can satisfy the quality assertion, while successful fetching of mock unverified claims can fail it. The exact README results need independent raw run evidence; the checked-in evaluator cannot substantiate them. Relabel/remove unsupported measurements until a real evaluation exists.

**Tests frequently validate the implementation's assumptions instead of the user promise.** [RunServiceTest.java:343](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/test/java/com/researchagent/service/RunServiceTest.java:343) only checks cancellation status/event. The draft comparison test seeds a history the actual write path never produces. [SafeWebReaderTest.java:97](C:/Users/bhara/OneDrive/Desktop/PEOPLE❤️❤️❤️/me/PROJECTS/Research-Agent/src/test/java/com/researchagent/tools/SafeWebReaderTest.java:97) prepares a redirect stub but calls the metadata URL directly, so it never exercises a redirect. The orchestration test accepts a nonempty report even when verifier parsing fails. These tests can pass while their named features are broken. Test observable invariants and failure scenarios at component boundaries.

**There is no checked-in automated delivery gate.** No CI workflow, frontend test suite, real database/broker integration suite, or dependency/security scanning configuration is present in the reviewed tree. The 39 passing unit tests do not establish HTTP authorization, transaction ordering, reconnection, deployment success, or research quality. The guide's 100% coverage statements also have no measured coverage configuration or report attached. Add repeatable gates for both applications and the important integrated user journeys.

**Architecture complexity has grown ahead of lifecycle correctness.** PostgreSQL, RabbitMQ, Redis, a custom orchestrator, and a separate UI are reasonable choices when needed, but their failure boundaries are currently unresolved. Seven named roles are primarily a fixed sequential pipeline, not independent autonomous workers. A smaller, reliable question-to-evidence-to-report flow would be a better near-term milestone than more agents or infrastructure. Keep distribution only where its job semantics and recovery are explicit.

**Research quality has no defensible acceptance model yet.** There is no reviewed reference dataset, claim-support scoring, source-quality rubric, freshness/relevance measure, or calibrated uncertainty metric. The verifier and critic reuse the same configured model, which can repeat the same mistakes. Search is one pass with page quotas; there is no explicit re-search on uncovered questions or unresolved contradictions. Define the intended product scope (web research versus literature review), how abstention works, and what constitutes a supported answer.

**Documentation needs one evidence-based source of truth.** PROJECT_GUIDE.md has an older incomplete sprint matrix while Sprints.md says everything is complete. Claims about JWT, Redis semaphores, recovery, LangFuse, immutable full traces, and coverage are unsupported or inaccurate. The frontend README remains a template. README says MIT, but no LICENSE file is present. The previous PROJECT_UNDERSTANDING.md was a preliminary folder summary; this audit supersedes its broad descriptions of implemented guardrails.

## Recommended repair order

1. **Make success honest:** isolate mock/demo mode; reject fabricated quotes; make missing evidence explicit; replace misleading confidence and benchmark claims. Put authentication/ownership and PDF resource restrictions in place before sharing the service.
2. **Restore a working local product:** fix TypeScript and Tailwind, make the frontend endpoint configurable, repair live state/reconnection/result loading, and make the container build self-contained.
3. **Make one run reliable:** implement real cancellation, centralized provider budgets, incremental usage/checkpoints, atomic completion, durable job publication, idempotent consumption, and lease-based recovery.
4. **Make evidence inspectable:** canonicalize source identity, validate claim spans and final citations, constrain revisions, persist immutable versions, and store useful traces.
5. **Earn the release claims:** add integration and browser acceptance tests, run a real versioned evaluation with raw evidence, then add measured observability and deployment hardening.

For the next milestone, use a narrow release gate: from a clean checkout, an authenticated user starts a real query, watches stable progress, receives a report whose quotations and citations can be checked, cancels another run without further calls, and recovers correctly from a worker restart. Record provider/model versions, total actual usage, and evidence for every claim of success.

