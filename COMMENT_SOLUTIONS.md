# Solutions to the nine build-review comments

Recommendations based on the current repository, 22 October 2026. Comment 1 is now implemented with regression coverage; comments 2–9 remain an implementation plan. Existing authentication, ownership checks, PDF isolation, cancellation, and terminal commit ordering must be preserved. Budgeting and model pricing are outside scope.

## 1. Unchecked quotations — ReaderAgent

**Implemented 22 October 2026:** `EvidenceValidator` checks exact text and whitespace-only normalized matches under `literal-whitespace-v1` (no Unicode NFC transformation). Accepted claims preserve the original passage and submitted quote, source snapshot UUID/hash/URL, UTF-16 offsets, match status, and policy version. Persistence retains full quotations and prevents ORM updates to source snapshot identity/text. Rejections appear in bounded trace diagnostics, and the Claims tab highlights saved evidence. Old claims have no invented validation metadata. Regression tests cover fabrication, altered numbers/negation, whitespace mapping, malformed output, long persisted passages, changed live content, immutable source fields, and legacy reads. Quote matching does not establish semantic support.

Add an `EvidenceValidator` before constructing an accepted Claim. Reject empty quotations and quotations absent from the fetched text. Start with exact matching; optionally allow Unicode NFC and whitespace normalization, preserving punctuation, numbers, units, and negation. Keep a mapping back to original text offsets when normalizing.

Persist an immutable source snapshot ID/content hash, source URL, original quotation, original-text offsets, normalization version, and match status. Keep rejected extraction attempts in the trace, excluded from evidence-bearing findings. Separate quote-match status from the existing semantic ClaimStatus.

Quote matching establishes provenance. It does not establish that the quote supports the assertion or that the source is correct; those require separate assessment.

**Acceptance:** fabricated/empty quotations are rejected; allowed whitespace variants resolve to the correct original span; altered numbers and missing negations do not pass; evidence remains inspectable after a source changes.


## 2. Hostnames mistaken for independent evidence — VerifierAgent

Add source provenance fields: hostname, registrable domain, publisher group when known, content hash, and evidence-family ID. Use a maintained Public Suffix List implementation to determine registrable domains; splitting on the final two labels fails for domains under suffixes such as `co.uk`. The [Public Suffix List documentation](https://publicsuffix.org/learn/) explains these boundaries.

Group exact copies by content hash and detect near-duplicate or syndicated evidence. Count one evidence family once, even when copied across hosts. Treat the model's clusters as proposals and validate referenced claim IDs, source spans, contradictions, and provenance in application code. Domain normalization alone does not prove publisher or editorial independence. Label uncertain independence explicitly instead of automatically awarding VERIFIED.

**Acceptance:** subdomains and copied articles do not manufacture corroboration; two independently produced sources can support the same assertion; contradictory evidence remains visible.

## 3. Citation IDs mistaken for factual support — CitationValidator / WriterAgent

Have the writer produce a structured draft whose factual statements reference accepted claim IDs. Generate visible citation numbers from the referenced evidence in application code. Do not let the model freely choose source numbers.

Validate the chain `report statement → accepted claim → source snapshot → evidence span`. A valid claim ID alone is insufficient if the writer changes its meaning. Initially render approved factual claim text deterministically; require separate support assessment for paraphrases or interpretations. Mark evidence as supporting, contradicting, or insufficient, and retain the assessed spans. Such semantic assessments remain fallible and need labelled evaluation.

Make ReflectionCriticAgent preserve these references and revalidate every revised factual statement. Derive bibliography entries from actual referenced evidence.

**Acceptance:** a live unrelated citation is rejected; invented facts introduced during writing or revision cannot inherit a legitimate citation; every displayed factual finding exposes its evidence.

## 4. Invented search fallback — SearchService implementations

Remove the fabricated Wikipedia result. Introduce `SearchOutcome` carrying results, provider, status (`SUCCESS`, `EMPTY`, `UNAVAILABLE`), and a safe diagnostic. An empty result and a failed provider request must remain distinguishable.

CompositeSearchService may try an explicitly configured alternative provider. Preserve each provider outcome. If no evidence is found, produce a clear insufficient-evidence outcome or an explicitly partial result from successfully fetched sources. Synthetic search fixtures must be confined to explicit demo/test configuration.

**Acceptance:** empty results produce no guessed URL/title/snippet; a provider outage is visible; one successful source and one failed provider yield an honestly labelled partial result when appropriate.

## 5. Database / queue inconsistency — RunService / ResearchJobProducer

In the API transaction, insert the run and an `outbox_events` row together. Publish only committed outbox rows. A publisher claims rows in short transactions, records a publisher lease, sends a persistent message with a stable event ID, and waits for a broker confirmation and successful routing before marking delivery.

For competing publishers, PostgreSQL supports `FOR UPDATE SKIP LOCKED` for queue-like claims; do not hold a database transaction open while waiting on network I/O. See [PostgreSQL locking clauses](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE).

Use publisher confirms and mandatory returns. A broker confirmation alone does not establish consumer processing; [RabbitMQ documents publisher and consumer acknowledgements as separate mechanisms](https://www.rabbitmq.com/docs/confirms).

Assume delivery can repeat, including a crash after publish but before marking the outbox row sent. Coordinate stable message IDs, worker claims, durable checkpoints, and acknowledgements so duplicate delivery cannot restart completed work. A durable outbox provides recoverable intent; it does not provide exactly-once external model calls.

**Acceptance:** rollback creates neither a runnable job nor an outbox record; broker downtime retains pending delivery; publisher crashes recover; duplicate delivery does not repeat completed research.

## 6. Recovery fails healthy jobs — worker ownership and leases

Replace the startup-wide failure sweep with claims containing `workerId`, `attemptId`/monotonic fencing token, `leaseExpiresAt`, and `lastHeartbeatAt`. Use database time for expiry comparisons. Heartbeats must run independently of a potentially blocking provider call and update only the current active owner/attempt.

Run a recovery sweep for expired leases only. Acquire recovery ownership atomically. Every checkpoint and final status write must verify the fencing token and preserve cancellation, preventing an expired worker from writing after a replacement starts. A stale worker must stop at its next operation-boundary check.

Initially mark genuinely expired work interrupted. Add resumable, idempotent stage checkpoints before promising automatic continuation; current end-of-run persistence cannot support that promise. Requeue recovery intent durably through the outbox. A fencing token cannot retract an external request that was already sent.

**Acceptance:** starting worker B leaves A's healthy work untouched; expired work is recovered once; resumed stale workers cannot write results; cancellation remains terminal.

## 7. Report revisions overwrite history — persistence and API

Insert a new ReportEntity with a new ID for each revision. Allocate version numbers under the existing run lock and enforce a database unique constraint on `(run_id, version)` through a schema migration. Preserve actual draft and revised content from the pipeline; the current final-only report object cannot reconstruct the initial draft.

Store revision state (`DRAFT`/`FINAL`) and its evidence references. Retrieve the latest version for the default endpoint and provide version-specific reads behind the existing ownership checks. Define comparison as the immediately preceding version, or let the user select two versions explicitly. PDF export must select the corresponding authorized revision.

Existing overwritten drafts are unrecoverable unless backups contain them. Preserve existing rows honestly and show unavailable history; do not manufacture a v1 draft.

**Acceptance:** saving v2 leaves v1 unchanged; concurrent writes cannot duplicate version numbers; version reads and PDF export cannot cross owners.

## 8. Disconnection treated as completion — frontend and event storage

Separate connection state (`CONNECTING`, `CONNECTED`, `RECONNECTING`, `OFFLINE`) from the run's lifecycle. A transport error must not invoke the terminal-run callback. As an immediate improvement, retain EventSource's native reconnection instead of closing it on every transient error.

For complete recovery, store ordered run events durably; Redis Pub/Sub alone cannot replay them. Give events a monotonic per-run sequence and deduplicate delivery. Resume using SSE IDs and `Last-Event-ID`; if custom reconnection recreates EventSource, carry the cursor in a validated query parameter because the new object does not inherit the previous object's cursor. The [HTML event-stream standard](https://html.spec.whatwg.org/multipage/server-sent-events.html) defines reconnection and cursor handling.

Use one reconnection strategy, with capped retry behavior and jitter where managed by the client. Reconcile an authoritative snapshot on reconnect; if retention has removed older events, show the snapshot and indicate the history gap. Close on logout, revoked ownership, or confirmed terminal status. Preserve session authentication and ownership checks for replay reads.

**Acceptance:** a temporary network drop does not finish the run; completion during disconnection appears after reconnect; replay does not duplicate entries; expired sessions leave the authenticated view cleanly.

## 9. Mock benchmark cannot establish research accuracy — evaluation and docs

Reclassify the current suite as a demo/pipeline smoke check and make it deterministic using fixture search/page data. Remove or clearly mark the README's numerical verification claims as unvalidated until reproducible evaluation artifacts exist.

Build a labelled evidence dataset containing supported facts, contradictions, unrelated citations, invented quotes, duplicated publishers, unavailable sources, and prompt-injected pages. Keep development examples separate from held-out evaluation cases. Define quote validity, factual support precision, coverage/recall, citation correctness, and correct insufficient-evidence handling explicitly.

Record dataset version, prompts, model configuration, source snapshots, outputs, and scoring procedure. Preserve human-reviewed labels and use model judges only as supplementary evidence. A truthful insufficient-evidence result may be correct; requiring every question to produce a successful research brief encourages fabricated completeness.

**Acceptance:** deliberately corrupted quotes/citations lower the appropriate scores; zero-evidence output cannot earn a high research-quality score; a documented dataset/configuration reproduces the reported measurements without relying on changing live pages.

## Recommended delivery sequence

1. **Immediate:** remove invented fallback results and correct unsupported documentation claims (4, 9).
2. **Evidence foundation:** quote validation and immutable source snapshots (1).
3. **Research integrity:** provenance-aware corroboration, structured writing, and citation support checks (2, 3).
4. **Execution reliability:** outbox delivery and worker leases/checkpoints (5, 6), designed together.
5. **History and dashboard:** immutable revisions and event recovery (7, 8).
6. **Release evidence:** expand the labelled evaluation from step 1 as each feature ships (9).

Implement these as separate, reviewable changes. Keep the original comment IDs in each change's acceptance record. Verify database concurrency with PostgreSQL and messaging behavior with RabbitMQ before declaring the distributed path complete; isolated unit checks alone do not establish those deployment properties.
