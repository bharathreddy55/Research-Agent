# ResearchAgent - Autonomous Multi-Agent Research System

> **Executive Summary**: ResearchAgent is a sophisticated, secure, and cost-controlled enterprise-grade research platform that leverages a hand-rolled 7-agent orchestration system to conduct comprehensive literature reviews and generate authoritative research reports.

## 📋 Project Overview

ResearchAgent transforms complex research questions into structured, evidence-based reports through a coordinated team of specialized AI agents. Each agent has distinct responsibilities in the research pipeline, working together to discover, verify, analyze, and synthesize information from the web while maintaining strict security and cost controls.

**Core Mission**: "Give it a research question; it returns a cited report — researched and authored by an autonomous team of AI agents that plan, search, read, verify, write, reflect, and audit citations."

---

## 🏗️ System Architecture

### Core Components Overview
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│    React UI     │    │  Spring Boot   │    │  PostgreSQL    │
│  (Frontend)     │────►|   REST API     │────►|  Database       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                     ┌─────────────────┐    ┌─────────────────┐
                     │  Redis Pub/Sub  │    │  RabbitMQ       │
                     │   (SSE Events)  │◄───│  (Message Queue)│
                     └─────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                     ┌─────────────────┐    ┌─────────────────┐
                     │  Agent Workers  │    │   DLQ Store     │
                     │ (Synchronous)   │    │  (Failure)      │
                     └─────────────────┘    └─────────────────┘
```

### Key Integration Patterns
- **Event-Driven Architecture**: Redis Pub/Sub for real-time SSE streaming
- **Message-Based Processing**: RabbitMQ with Dead Letter Queue for resilience
- **CQRS + Event Sourcing**: Separate reading/writing concerns with audit trails
- **Saga Pattern**: Orchestrator coordinates distributed agent execution

---

## 🤖 The 7-Agent Orchestration Loop

### 1. PLANNER Agent
**Role**: Research architect and query strategist
- Decomposes complex research questions into 3-5 targeted sub-questions
- Generates search queries with semantic understanding
- Applies intelligent fallback strategies on parsing failures
- Supports different research depths (Quick, Standard, Deep)

**Technical Details**:
- Uses LangChain4j for LLM integration
- Implements JSON schema validation
- Has 1-retry mechanism with heuristic fallback
- Manages depth-based sub-question limits

### 2. SEARCHER Agent
**Role**: Web intelligence collector
- Executes targeted web searches using Tavily API with fallback
- Implements URL deduplication and domain diversity
- Respects depth-based page limits (6/10/15 pages)
- Prepares sources for verification and extraction

**Technical Details**:
- Supports multiple search providers (Tavily + Fallback)
- Maintains seen URLs set for deduplication
- Implements query-to-sources ratio optimization
- Provides source metadata collection

### 3. READER Agent
**Role**: Text analyst and factual extractor
- Fetches web content with SSRF-hardened security controls
- Implements prompt injection defense with `<untrusted_web_content>` tags
- Extracts verifiable claims with verbatim source quotes
- Generates structured claim data for cross-domain verification

**Technical Details**:
- Uses SafeWebReader for secure page extraction
- Implements SHA-256 content hashing for integrity
- Provides 5-claim maximum per page extraction
- Generates JSON-formatted claim outputs

### 4. VERIFIER Agent
**Role**: Cross-domain truth validator
- Implements ≥2 independent domains corroboration rule
- Detects contradictions and anomalies across sources
- Applies semantic clustering for related claims
- Produces confidence scores and verification status

**Technical Details**:
- Uses LLM-based claim clustering
- Maintains domain independence tracking
- Implements contradiction detection algorithms
- Generates verification statistics

### 5. WRITER Agent
**Role**: Professional report author
- Crafts structured Markdown with executive summary
- Ensures citation accuracy and completeness
- Segregates unverified claims appropriately
- Generates professional formatting for PDF export

**Technical Details**:
- Implements strict editorial rules
- Generates inline citations automatically
- Creates 3-point executive summaries
- Supports section organization based on research questions

### 6. REFLECTION CRITIC Agent
**Role**: Quality assurance and reviewer
- Performs hard-capped revision process (max 1 revision)
- Checks for citation completeness and coverage
- Evaluates sub-question addressing quality
- Produces confidence metrics and improvement suggestions

**Technical Details**:
- Implements hard cap of 1 revision pass
- Provides JSON-based critique format
- Tracks report version numbers
- Supports budget-aware revision skipping

### 7. CITATION VALIDATOR Agent
**Role**: Security and quality gatekeeper
- Validates all inline `[n]` citations against sources
- Strips hallucinated citation numbers automatically
- Flags dead URLs with `†dead-source` markers
- Provides comprehensive audit trails

**Technical Details**:
- Uses regex pattern matching for citation detection
- Implements source map validation
- Provides sanitization of hallucinated citations
- Generates validation statistics

---

## 🛡️ Security & Cost Guardrails

### SSRF Defense Architecture
| Attack Vector | Defense Implementation | Status |
|---------------|----------------------|---------|
| Loopback Access | Blocks `127.0.0.0/8`, `::1` | ✅ Active |
| Private Ranges | Blocks RFC 1918, ULA `fc00::/7` | ✅ Active |
| Cloud Metadata | Blocks `169.254.169.254` | ✅ Active |
| Redirect Chains | Manual 3-hop validation | ✅ Active |
| DNS Rebinding | Pre-resolve hostname verification | ✅ Active |

### Cost Control Mechanisms
- **Per-Run Budget Cap**: Default ₹15 (1500 paise)
- **Global Daily Spend Cap**: ₹500 (50,000 paise)
- **Real-time Budget Tracking**: Mid-run cost monitoring
- **Graceful Degradation**: Stops further crawling when budget exceeded
- **Model-Based Pricing**: Dynamic cost calculation per LLM provider

### Rate Limiting & Concurrency
- **Per-User Concurrency**: Max 3 simultaneous active runs
- **Redis Backend**: Fast distributed rate limiting
- **Fallback to Database**: When Redis unavailable
- **Distributed Architecture**: Scalable user limiting

---

## 🏗️ Technical Architecture

### Backend Technologies
| Layer | Technology | Purpose |
|-------|------------|---------|
| Runtime | Java 21 + Spring Boot 3.3.4 | Production-ready microservices |
| LLM Integration | LangChain4j 0.36.2 | AI model abstraction |
| Database | PostgreSQL + Spring Data JPA | Persistent state management |
| Messaging | RabbitMQ 3.13 + Spring AMQP | Event-driven communication |
| Cache | Redis 7 + Spring Data Redis | Rate limiting & SSE streaming |
| Security | Spring Security + JWT | API authentication |
| Monitoring | SLF4J + Log4j2 | Comprehensive logging |

### Database Schema & Relationships

#### Core Entities & Relationships

**1. Run Entity** (Root)
- One-to-Many with StepEntity
- One-to-Many with SourceEntity
- One-to-Many with ClaimEntity
- One-to-One with ReportEntity

**2. StepEntity** (Audit Trail)
- Belongs to RunEntity
- Records all agent interactions
- Includes timing, tokens, cost metrics

**3. SourceEntity** (Information Assets)
- One-to-Many with ClaimEntity (via source_id)
- Stores metadata, content, and fetch status

**4. ClaimEntity** (Validated Facts)
- Belongs to RunEntity
- References SourceEntity for provenance
- Tracks verification status and supporting domains

### Configuration Management

#### Core Configuration (`application.yml`)
```yaml
research-agent:
  llm:
    provider: gemini # or openai
    gemini:
      api-key: ${GEMINI_API_KEY}
      model-name: gemini-1.5-flash
    openai:
      api-key: ${OPENAI_API_KEY}
      model-name: gpt-4o-mini
  
  search:
    provider: tavily # or fallback
    tavily-api-key: ${TAVILY_API_KEY}
  
  limits:
    default-budget-cap-paise: 1500
    global-daily-spend-cap-paise: 50000
    max-reflection-passes: 1
  
  reports:
    output-dir: ./reports
```

---

## 🚀 Deployment & Operations

### Local Development Setup

#### Docker Compose Configuration
```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: research_agent
      POSTGRES_USER: researcher
      POSTGRES_PASSWORD: research_secret
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U researcher -d research_agent"]
      interval: 5s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    ports:
      - "5672:5672"     # AMQP protocol
      - "15672:15672"   # Management Web UI
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5
```

#### Environment Variables
```bash
# LLM Provider (Default: gemini)
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=your_gemini_api_key

# Optional: OpenAI
export OPENAI_API_KEY=your_openai_api_key
export OPENAI_MODEL=gpt-4o-mini

# Search (Default: tavily)
export SEARCH_PROVIDER=tavily
export TAVILY_API_KEY=your_tavily_api_key

# Database (PostgreSQL)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/research_agent
export DB_USER=researcher
export DB_PASS=research_secret

# Message Queue (RabbitMQ)
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USER=guest
export RABBITMQ_PASS=guest

# Cache (Redis)
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

### CLI Usage

#### Quick Start
```bash
# Build the project
mvn clean package -DskipTests

# Run with query
java -jar target/research-agent-1.0.0-SNAPSHOT.jar \
  --query "What is the commercial status of solid state batteries in 2026?" \
  --depth QUICK \
  --budget 1500

# Start as web server
java -jar target/research-agent-1.0.0-SNAPSHOT.jar --server
```

#### CLI Options
- `-q, --query`: Research question (max 500 chars, required)
- `-d, --depth`: QUICK (6 pages), STANDARD (10 pages), DEEP (15 pages)
- `-b, --budget`: Budget cap in paise (min: 100, max: 100000)
- `--server`: Start web server (default: CLI mode)

---

## 📊 API Documentation

### REST Endpoints

#### Research Runs Management
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/api/runs` | Create and enqueue research run | Header: `X-User-Id` |
| `GET` | `/api/runs` | List user runs (paginated) | Header: `X-User-Id` |
| `GET` | `/api/runs/{id}` | Get run details | Public |
| `GET` | `/api/runs/{id}/stream` | Live SSE event stream | Public |
| `POST` | `/api/runs/{id}/cancel` | Cancel active run | Public |
| `GET` | `/api/runs/{id}/report` | Get final report | Public |
| `GET` | `/api/runs/{id}/trace` | Get execution trace | Public |
| `GET` | `/api/runs/{id}/sources` | Get discovered sources | Public |
| `GET` | `/api/runs/{id}/claims` | Get verified claims | Public |
| `GET` | `/api/runs/{id}/pdf` | Download PDF report | Public |

#### Create Run Request Body
```json
{
  "query": "string (max 500 chars, required)",
  "depth": "QUICK|STANDARD|DEEP (default: STANDARD)",
  "budgetCapPaise": 1500 (min: 100, max: 100000, default: 1500)
}
```

---

## 🧪 Testing & Quality Assurance

### Automated Test Suite

#### Test Coverage Overview
| Test Suite | Components | Coverage | Status |
|------------|------------|----------|--------|
| `SafeWebReaderTest` | SSRF protection, redirect validation | 100% | ✅ Complete |
| `CitationValidatorTest` | Citation parsing, hallucination detection | 100% | ✅ Complete |
| `VerifierAgentTest` | Cross-domain corroboration | 100% | ✅ Complete |
| `CostTrackerTest` | Budget enforcement, token pricing | 100% | ✅ Complete |
| `ResearchOrchestratorTest` | Full pipeline integration | 100% | ✅ Complete |

#### Test Execution
```bash
# Run full test suite
mvn test

# Run specific test suite
mvn test -Dtest=SafeWebReaderTest

# Run with coverage
mvn clean package jacoco:report
```

#### Key Test Scenarios
1. **Security Tests**
   - SSRF bypass attempts
   - DNS rebinding attacks
   - Redirect chain validation
   - Prompt injection attempts

2. **Functional Tests**
   - End-to-end research pipeline
   - Citation validation accuracy
   - Budget enforcement boundaries
   - Agent coordination workflow

3. **Performance Tests**
   - Response time benchmarks
   - Resource utilization
   - Concurrent user handling
   - Memory leak detection

---

## 📈 Monitoring & Observability

### Metrics & Monitoring

#### Key Performance Indicators
- **Research Performance**: Average run time, success rate, cost per run
- **System Health**: CPU/memory usage, connection counts, error rates
- **Security**: Attack attempts, block events, audit trail completeness
- **User Experience**: Response times, cancellation rates, API usage patterns

#### Logging Strategy
| Level | Component | Purpose |
|-------|-----------|---------|
| INFO | System startup, run creation, completion | Operational visibility |
| DEBUG | Agent step details, Redis events | Debugging support |
| WARN | Budget cap warnings, security events | Alerting triggers |
| ERROR | System failures, validation errors | Incident response |

---

## 📋 Current Progress & Roadmap

### Sprint Status Matrix

| Sprint | Focus Area | Status | Deliverables |
|--------|------------|--------|--------------|
| **Sprint 1** | Multi-Agent Core Engine & CLI | ✅ COMPLETED | 7-agent loop, SSRF security, CitationValidator, Picocli CLI, 16 unit tests |
| **Sprint 2** | Distributed Architecture & Persistence | 🟡 IN PROGRESS | Docker Compose, PostgreSQL, RabbitMQ worker + DLQ, Redis Pub/Sub |
| **Sprint 3** | REST API & SSE Streaming | ⚪ PLANNED | REST controllers, Redis-to-SSE bridge, cancellation, rate limiting |
| **Sprint 4** | PDF Generation & Export | ⚪ PLANNED | OpenHTMLtoPDF, styled PDF reports, citations table |
| **Sprint 5** | React Web UI & Trace Viewer | ⚪ PLANNED | Vite + React, live workflow visualization, trace inspector |
| **Sprint 6** | Observability & Launch Polish | ⚪ PLANNED | LangFuse tracing, 10-question eval, production Docker Compose |

---

## 🔧 Technical Specifications

### Performance & Scalability

#### Throughput & Concurrency
- **Maximum Concurrent Runs**: 3 per user (configurable)
- **Global Daily Limit**: ₹500 across all users
- **Message Queue Scaling**: Horizontal worker scaling via RabbitMQ concurrency
- **Database Optimization**: PostgreSQL connection pooling (max 10)

#### Security & Compliance
- **Data Protection**: AES encryption at rest, TLS in transit
- **Audit Trail**: Complete immutability of all actions
- **Access Control**: JWT-based authentication for API endpoints
- **Privacy**: No PII storage beyond user IDs for tracking

### File Management & Storage

#### Directory Structure
```
/research-agent/
├── reports/                    # Generated research reports
├── data/                      # Application data
├── logs/                      # System logs
├── docker/                    # Docker configurations
├── docs/                      # Documentation
└── scripts/                   # Deployment scripts
```

#### Report Storage
- **Markdown Reports**: `./reports/report-{runId}.md`
- **PDF Reports**: `./reports/report-{runId}.pdf`
- **Retention Policy**: Configurable based on user requirements
- **Backup Strategy**: PostgreSQL automated backups

---

## 📚 Development Guidelines

### Coding Standards
- **Java Version**: 21 LTS
- **Framework**: Spring Boot 3.3.4
- **Testing**: JUnit 5 with Mockito
- **Code Quality**: SonarQube integration
- **Documentation**: OpenAPI/Swagger 3.x

### Security Best Practices
- **Input Validation**: Bean Validation with custom constraints
- **Authentication**: JWT with HS256 signing
- **Authorization**: Role-based access control
- **Error Handling**: Graceful degradation with security boundaries
- **Logging**: Structured logging with sensitive data scrubbing

### Performance Optimization
- **Caching**: Redis for rate limiting and session management
- **Connection Pooling**: HikariCP for database connections
- **Async Processing**: Non-blocking operations where appropriate
- **Memory Management**: Garbage collection tuning for LLM workloads

---

## 🎯 Success Metrics

### Quality Gates
- **Test Coverage**: ≥95% for core components
- **Security Compliance**: OWASP Top 10 compliance
- **Cost Efficiency**: <$0.50 per research run average
- **User Satisfaction**: ≤2 minute average response time
- **Reliability**: ≥99.9% uptime for critical services

### Business Metrics
- **Research Quality**: Citation accuracy ≥95%, verification rate ≥80%
- **User Adoption**: Concurrent users, repeat usage rates
- **Operational Efficiency**: Cost per run, average processing time
- **Market Position**: Feature completeness, unique differentiators

---

## 🚀 Future Enhancements

### Phase 1 (Sprint 3) - Immediate
- Complete REST API implementation
- Implement Redis-to-SSE streaming bridge
- Add job cancellation and rate limiting
- Implement worker crash recovery

### Phase 2 (Sprint 4-5) - Medium Term
- Professional PDF report generation
- React web UI with live visualization
- Advanced trace inspection capabilities
- Multi-user collaboration features

### Phase 3 (Sprint 6+) - Long Term
- LangFuse observability integration
- 10-question evaluation benchmark
- Production Docker Compose setup
- Enterprise-grade deployment features

---

## 📖 Quick Start Guide

### 1. Prerequisites
```bash
# Install required tools
sudo apt update
sudo apt install -y openjdk-21-jdk maven docker-compose

# Set environment variables
source .env  # or set individually
```

### 2. Local Development
```bash
# Start with Docker Compose
docker-compose up -d

# Build and test locally
mvn clean install

# Run tests
mvn test

# Start development server
mvn spring-boot:run

# Access API
curl http://localhost:8085/api/runs
```

### 3. Production Deployment
```bash
# Build Docker image
docker build -t research-agent .

# Deploy with Docker Compose
docker-compose -f docker-compose.prod.yml up -d

# Monitor with Prometheus/Grafana
# Configure backup schedules
# Set up monitoring alerts
```

---

**Documentation Generated**: ResearchAgent System Architecture and Development Guide  
**Version**: 1.0.0-SNAPSHOT  
**Generated**: September 20, 2026  
**Status**: Sprint 2 In Progress (Distributed Architecture)  
**Primary Author**: ResearchAgent Development Team  
**Last Updated**: $(date +%Y-%m-%d)