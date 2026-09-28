# 🚨 START HERE: Read PROJECT_GUIDE.md First

> **This is the single most important file for understanding the entire project.** Before reading any other documentation, open `PROJECT_GUIDE.md` — it contains the complete project architecture, system design, security guardrails, current sprint status, roadmap, and everything you need to know about ResearchAgent without reading all the individual source files.

---

## 📚 Documentation Navigation

| Document | Purpose | When to Read |
|----------|---------|--------------|
| **[`PROJECT_GUIDE.md`](PROJECT_GUIDE.md)** | **Complete project overview, architecture, security, roadmap, and everything you need** | **⭐ FIRST — Always read this first** |
| [`README.md`](README.md) | Project quick start and architecture summary | Quick overview |
| [`Sprints.md`](Sprints.md) | Detailed sprint-by-sprint development breakdown | Sprint planning and progress tracking |
| [`docker-compose.yml`](docker-compose.yml) | Infrastructure configuration | Local development setup |
| [`pom.xml`](pom.xml) | Dependencies and build configuration | Dependency management |

---

# 🔬 ResearchAgent — "Your AI Research Team, On Demand"

> **One-liner**: Give it a research question; it returns a cited report — researched and authored by an autonomous team of AI agents that plan, search, read, verify, write, reflect, and audit citations.

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.36.2-blue.svg)](https://github.com/langchain4j/langchain4j)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 🏛️ System Architecture

```
                    ┌─────────────┐
   User ──────────► │  React UI   │ (Query Form, Live Agent Stream, Trace Viewer)
                    └──────┬──────┘
                           │ REST + SSE
                    ┌──────▼──────────────────────┐
                    │   Spring Boot REST API      │
                    │  /runs  /runs/{id}/stream   │
                    └──────┬──────────────▲───────┘
                           │ enqueue job  │ Redis Pub/Sub
                    ┌──────▼──────┐       │ (decoupled event bridge)
                    │  RabbitMQ   │───────┼──────────────┐
                    └──────┬──────┘       │ Agent Worker │ (Hand-rolled State Machine)
                           │ (DLQ)        └──────┬───────┘
                           ▼                     │
                     ┌───────────┐               │
                     │ Poison DLQ│               │
                     └───────────┘               │
              ┌──────────────────────────────────┼────────────────────┐
              │                                  │                    │
       ┌──────▼─────┐                     ┌──────▼──────┐     ┌──────▼─────┐
       │ PLANNER    │→ sub-questions +    │ SEARCHER    │     │  Postgres  │
       │ agent      │  search keywords    │ (Tavily API │     │  (runs,    │
       └────────────┘                     │  + dedupe)  │     │  traces,   │
                                          └──────┬──────┘     │  reports)  │
                                          ┌──────▼──────┐     └────────────┘
                                          │ READER      │ SSRF-safe pinned IP,
                                          │             │ prompt-injection defense
                                          └──────┬──────┘
                                          ┌──────▼──────┐
                                          │ VERIFIER    │ Corroboration across
                                          │             │ ≥ 2 independent domains
                                          └──────┬──────┘
                                          ┌──────▼──────┐
                                          │ WRITER      │ Verified claims only in core;
                                          │             │ unverified in appendix
                                          └──────┬──────┘
                                          ┌──────▼──────┐
                                          │ REFLECTION  │ Reviews draft; hard cap
                                          │ CRITIC      │ of exactly 1 revision
                                          └──────┬──────┘
                                          ┌──────▼──────┐
                                          │ CITATION    │ Audits [n] vs sources table;
                                          │ VALIDATOR   │ strips hallucinations & flags dead URLs
                                          └──────┬──────┘
                                                 │
                                                 ▼
                                        Markdown & PDF Export
```

---

## 🔄 The 7-Agent Orchestration Loop

1. **PLANNER**: Decomposes the research question into 3–5 targeted sub-questions and search keywords using structured JSON schema with automated retry and heuristic fallback.
2. **SEARCHER**: Queries web search endpoints (Tavily API with fallback), deduplicates URLs, and enforces depth limits (Quick: 6, Standard: 10, Deep: 15 pages).
3. **READER**: Fetches pages using **SSRF-hardened IP validation** and manual per-hop redirect checking. Sanitizes HTML with Jsoup, wraps content in `<untrusted_web_content>` tags to prevent prompt injection, and prompts LLM to extract empirical factual claims with verbatim quotes.
4. **VERIFIER (Moat Feature)**: Cross-corroborates claims across **independent domains** (claims confirmed by $\ge 2$ independent domains = `VERIFIED`; single-source = `UNVERIFIED`; conflicting claims = `CONTRADICTION`).
5. **WRITER**: Drafts a structured research report with executive summary and inline citations (`[1]`, `[2]`). Verified claims form the main narrative; unverified claims are segregated into a "Low Confidence & Single-Source Findings" section.
6. **REFLECTION CRITIC**: Critiques the draft for missing citations or thin sections. Triggers **at most 1** revision pass (hard cap to eliminate runaway LLM cost).
7. **CITATION VALIDATOR (Security & Quality Gate)**: Post-write validator that parses all `[n]` citations. Rejects and strips hallucinated citation numbers not present in the sources directory; flags citations pointing to dead URLs (`[n†dead-source]`).

---

## 🛡️ Security & Cost Guardrails (Interview Defensibility)

| Guardrail | Implementation | Why It Matters |
|---|---|---|
| **SSRF DNS-Rebinding Defense** | Pre-resolves hostname to IP, verifies against forbidden ranges, and validates every connection. | Prevents TOCTOU DNS rebinding attacks where malicious domains return a public IP on DNS check but resolve to private IP on socket connect. |
| **Manual Redirect Validation** | Disabled auto-redirects (`followRedirects(false)`); inspects `Location` header manually on 3xx responses (max 3 hops). | Stops attackers from using open public redirects to bounce into `http://169.254.169.254/latest/meta-data` (AWS/GCP cloud metadata). |
| **IPv6 ULA & Link-Local Blocking** | Blocks `::1`, `fc00::/7` (ULA), and `fe80::/10`. | Closes IPv6 bypass vectors common in naive IPv4-only SSRF filters. |
| **Prompt Injection Isolation** | Wraps scraped web text in `<untrusted_web_content>` tags with strict system prompt boundaries. | Prevents malicious websites from hijacking agent instructions. |
| **Token Cost Tracking & Budget Cap** | Enforces a per-run budget ceiling (default 1500 paise = ₹15). Mid-run checks halt further web crawling gracefully rather than crashing. | Guarantees predictable operational cost per report. |
| **Distributed SSE via Redis Pub/Sub** | Worker publishes state transition events to Redis channel `run:{id}:events`; API forwards to client `SseEmitter`. | Decouples asynchronous RabbitMQ workers from web API JVMs holding HTTP connections. |

---

## 🚀 Quick Start (CLI Mode)

### 1. Prerequisites
- **Java 21 LTS** or **Java 25**
- **Maven 3.9+**

### 2. Configure Environment Variables
Copy `.env.example` to `.env` (or set environment variables):
```bash
# Recommended: Google Gemini (Free tier covers all testing)
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=your_gemini_api_key_here
export GEMINI_MODEL=gemini-1.5-flash

# Optional: Tavily Search API
export SEARCH_PROVIDER=tavily
export TAVILY_API_KEY=your_tavily_api_key_here
```
*(Note: If no API keys are configured, ResearchAgent runs in test/resilience mode using built-in search fallbacks and offline generator!)*

### 3. Build the Project
```bash
mvn clean package -DskipTests
```

### 4. Execute Research Query via CLI
```bash
java -jar target/research-agent-1.0.0-SNAPSHOT.jar --query "What is the commercial status of solid state batteries in 2026?" --depth QUICK
```

Options:
- `-q, --query`: The research question to investigate.
- `-d, --depth`: Research depth: `QUICK` (6 pages), `STANDARD` (10 pages), `DEEP` (15 pages).
- `-b, --budget`: Budget cap in paise (default: 1500 = ₹15).
- `--server`: Start as web server without executing CLI query.

Reports are saved to `./reports/report-{runId}.md`.

---

## 🧪 Automated Test Suite

Run the full automated test suite (16 tests across 5 test suites):
```bash
mvn test
```

### Test Coverage Highlights:
- **`SafeWebReaderTest`**: SSRF block on loopback (`127.0.0.1`), localhost, AWS metadata (`169.254.169.254`), RFC 1918 private ranges, IPv6 loopback (`::1`), IPv6 ULA (`fc00::/7`), and 302 redirect bypass attempt to cloud metadata.
- **`CitationValidatorTest`**: Hallucinated citation `[99]` stripped; dead URL `[2]` flagged; valid citations untouched.
- **`VerifierAgentTest`**: Multi-domain corroboration vs same-domain-twice edge case.
- **`CostTrackerTest`**: Token-to-paise arithmetic and boundary threshold testing.
- **`ResearchOrchestratorTest`**: Full end-to-end 7-agent pipeline execution.

---

## 🗺️ Master Roadmap

- [x] **Week 13**: CLI prototype with Planner, Searcher, Reader, and SSRF security guardrails.
- [x] **Week 14**: Verifier with cross-domain corroboration, Writer, Reflection Critic with hard cap, and CitationValidator.
- [ ] **Week 15**: PostgreSQL persistence, RabbitMQ worker with Dead Letter Queue (DLQ), Docker Compose.
- [ ] **Week 16**: Redis Pub/Sub to SSE streaming bridge, worker crash recovery, budget enforcement, and JWT authentication.
- [ ] **Week 17**: OpenHTMLtoPDF export service with styling.
- [ ] **Week 18**: React UI (Vite + Tailwind CSS) with live workflow visualizer and trace inspector.
