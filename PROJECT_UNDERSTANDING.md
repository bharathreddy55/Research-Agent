# ResearchAgent: Project Understanding

## What this project is

ResearchAgent is a Java and React application intended to turn a research question into a cited report. Its central idea is to split research into a sequence of focused AI roles, preserve an audit trail of their work, and let a user follow a run in a web dashboard.

## How a research run is meant to work

The backend orchestrator coordinates seven roles:

1. **Planner** breaks the question into smaller questions and search queries.
2. **Searcher** finds candidate web pages using Tavily or a fallback provider.
3. **Reader** fetches pages and extracts claims and supporting quotations.
4. **Verifier** compares claims across sources and marks support, uncertainty, or contradictions.
5. **Writer** drafts a structured Markdown report with citations.
6. **Reflection Critic** reviews the draft and can make one revision.
7. **Citation Validator** checks citation numbers against the available sources.

The code in `ResearchOrchestrator` runs these stages in sequence, tracks cost and cancellation, and emits progress events. Research depth controls the intended scope: quick, standard, or deep.

## Application structure

- **Backend:** Java 21, Spring Boot, and LangChain4j. The source includes the agents, CLI runner, REST API, persistence services, and PDF generation.
- **Frontend:** React, TypeScript, and Vite. The dashboard submits questions, streams run progress over Server-Sent Events, and presents run history, reports, and traces.
- **Persistence and job processing:** PostgreSQL stores runs and their steps, sources, claims, and reports. RabbitMQ queues research jobs and Redis carries live events to the API's SSE connections.
- **Outputs:** Markdown reports are written under `reports/`; the API can also generate a PDF.
- **Deployment:** Docker Compose describes PostgreSQL, RabbitMQ, Redis, the backend, and frontend. API keys are configured through environment variables, with `.env.example` as a template.

## Guardrails visible in the project

The design includes URL/IP checks and redirect validation in `SafeWebReader`, prompt boundaries around fetched page content, per-run and daily spend limits, per-user concurrency limits, cancellation, and citation-number validation. These are useful layers of protection, though their presence in code does not by itself establish that every edge case is covered or that the service is production-secure.

## What I found in the folder

The folder contains the backend implementation, unit-test source files, a React frontend, Docker configuration, project documentation, and 30 generated Markdown report files. `PROJECT_GUIDE.md` is a broad architecture and product guide; `Sprints.md` and `README.md` describe the six planned milestones as complete.

The saved report files are highly repetitive examples: they use generic placeholder-style findings, and many citations are marked `dead-source`. They do not read like evidence-rich reports tied to identifiable research questions and full source lists. I would therefore treat them as output samples or test artifacts, not as proof that the stated research quality or benchmark results have been independently demonstrated.

## Points to keep in mind

- The documentation says JWT authentication is implemented, but the visible `RunController` accepts a caller-supplied `X-User-Id` header with a default user and marks the API as open to cross-origin requests. The implementation should be checked before describing the API as authenticated.
- The README reports benchmark outcomes and passing-test counts. Test classes are present, but this folder review did not execute the tests or independently reproduce the benchmark; those figures should be described as project-reported results.
- The frontend README is still the default Vite template, so it does not document the actual dashboard or how to configure it.
- The Docker Compose file uses simple local-development credentials and exposes service ports. It is convenient for development, but should not be treated as a production deployment configuration without further hardening.

## Short summary

This is a substantial prototype for an asynchronous, multi-agent research-reporting product. It has a coherent end-to-end shape: question intake, agent workflow, persisted trace, streaming progress, report display, and PDF export. The main gap between the project narrative and the checked-in evidence is confidence: some documentation makes strong claims about authentication, evaluation, and report quality that the code and saved report examples do not yet substantiate on their own.
