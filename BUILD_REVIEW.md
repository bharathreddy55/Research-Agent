# Build review — 22 October 2026

## Verdict

The project has seven agents. Quotation matching and saved-source provenance are implemented. Source-family grouping now replaces hostname counting; confirming independence and report-level factual support still need separate assessment.

Authentication, PDF resource isolation, cancellation, and report commit ordering have recent regression coverage. Preserve those changes. The next work should establish evidence integrity and reliable execution, with acceptance criteria that describe user-visible behavior.

The original review was based on source inspection. Comment 1 now has offline regression coverage and a frontend build check; browser, live-provider, and deployment checks have not been performed. Budgeting and model pricing are outside this work queue.

## Ordered work queue

| Order | Priority | Finding and location | Required outcome |
|---|---|---|---|
| 1 | P1 | Implemented 22 October: unchecked reader quotations. | Exact/whitespace matching rejects missing quotations; saved snapshot/hash/span metadata and Claims evidence view preserve provenance. See README for policy and limits. |
| 2 | P1 | Implemented 22 October: hostname counting as independent corroboration. | Registrable-domain, configured publisher, and copy grouping; direct quote-grounded links; unconfirmed independence shown and no automatic `VERIFIED`. Regression acceptance pending. |
| 3 | P1 | Implemented 22 October: citation IDs alone do not establish statement support. | The model selects claim IDs only; source excerpts, citation numbers, bibliography, and an evidence manifest are application-rendered after snapshot/span validation. Candidate interpretations are explicitly unassessed. Semantic entailment remains future work. |
| 4 | P1 | Implemented 27 October: FallbackSearchService.java manufactured a Wikipedia URL and snippet after empty results. | Empty and unavailable searches now return explicit outcomes with no guessed source. Provider attempts remain in the search trace; a fallback success after an outage is labelled partial. Eight focused regression tests pass. |
| 5 | P1 | RunService.java:82–83 creates a database row and publishes a queue message before the transaction commits. | Use a durable outbox with confirmed delivery and idempotent consumption. Cover publication failure, rollback, early delivery, and restart recovery. The current missing-row retry only reduces one symptom. |
| 6 | P1 | WorkerCrashRecoveryService.java:57 scans every run and can fail another worker's active job. | Recover only expired worker leases using ownership and heartbeat evidence. Starting a second instance must leave healthy work running. Cancellation-safe conditional updates do not establish worker death. |
| 7 | P2 | RunPersistenceService.java:260–268 updates the existing report entity and increments its version. | Store immutable report revisions as separate rows. Saving v2 must preserve and allow retrieval of v1. |
| 8 | P2 | frontend/src/App.tsx:244–246 treats an event-stream error as terminal and closes the connection. | Separate transport failure from run completion. Reconnect with bounded backoff and reconcile current state/missed progress without interrupting the server job. |
| 9 | P1 | ResearchBenchmarkEvalTest.java:57–59,115–118 uses a mock model and permits zero confidence; README.md:75–88 publishes verification percentages. | Separate demo smoke checks from research evaluation. Publish accuracy figures only with recorded inputs, outputs, evidence labels, measurement definitions, and reproducible results. |

## Orchestration rules

Comment 2 implementation and comment 3 grounded report rendering are in the working tree. Backend and frontend builds pass; regression acceptance remains unchecked because test authorization has not been given for this turn. Semantic claim support and true source independence remain unconfirmed.

1. Finish evidence extraction and provenance before expanding verification, report polish, or agent count.
2. Define acceptance cases before implementation. A successful method return, a class name, and a checked sprint box are insufficient completion evidence.
3. Keep reliability work separate from research evaluation: queue correctness needs failure/race scenarios; research accuracy needs labelled evidence and supported conclusions.
4. Record implementation, offline verification, browser verification, and deployment verification separately. Reopen claims that exceed the available evidence.

## The roast

- **Reader (original finding, now addressed):** asked the model for receipts without checking them. Quotation matching now checks those receipts against saved text.
- **Verifier (original finding, partly addressed):** used different hostnames as separate witnesses. Candidate evidence groups now expose shared domains, configured publishers, and similar text; independence remains unconfirmed.
- **Citation auditor (original finding, addressed at the rendering boundary):** accepted a valid reference number for any sentence. The writer now selects existing evidence IDs; application code renders only saved quotations as citations. Interpretations stay explicitly unassessed.
- **Report history:** overwrites the whiteboard and calls the new number an archive.
- **Crash recovery:** a new worker can announce that the other workers' active jobs have died.
- **Benchmark:** zero-confidence output can pass. The README's verification percentages need a reproducible measurement trail.

Comments 1, 3, and 4 are implemented. Comment 2 has source grouping and explicit uncertainty, with regression acceptance pending. The next task is **comment 5: make database and queue publication consistent**.
