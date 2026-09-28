import { useState, useEffect, useRef } from 'react';
import './App.css';

// -----------------------------------------------------------------------------
// Interfaces
// -----------------------------------------------------------------------------
interface RunResponse {
  id: string;
  userId?: string;
  query: string;
  depth: string;
  status: string;
  budgetCapPaise: number;
  costPaise: number;
  tokensIn: number;
  tokensOut: number;
  startedAt?: string;
  finishedAt?: string;
  failureReason?: string;
  confidenceScore?: number;
}

interface StepResponse {
  id: string;
  runId: string;
  seq: number;
  agent: string;
  action: string;
  prompt?: string;
  response?: string;
  tokensIn: number;
  tokensOut: number;
  costPaise: number;
  durationMs: number;
  createdAt: string;
}

interface SourceEntity {
  id: number;
  runId: string;
  sourceNumber: number;
  url: string;
  title?: string;
  snippet?: string;
  domain?: string;
  fetchedOk: boolean;
  fetchError?: string;
}

interface ClaimEntity {
  id: string;
  runId: string;
  statement: string;
  exactQuote?: string;
  sourceDomain?: string;
  status: string;
  supportingDomains?: string;
  contradictionNotes?: string;
}

interface ReportResponse {
  id: string;
  runId: string;
  query: string;
  title: string;
  markdownContent: string;
  pdfPath?: string;
  version: number;
  confidenceScore: number;
  totalSources: number;
  verifiedClaimsCount: number;
  unverifiedClaimsCount: number;
  previousDraftContent?: string;
  createdAt: string;
}

interface StreamEvent {
  runId: string;
  status: string;
  agent: string;
  message: string;
  tokensIn: number;
  tokensOut: number;
  costPaise: number;
  ts: string;
}

const AGENT_ORDER = [
  'PLANNER',
  'SEARCHER',
  'READER',
  'VERIFIER',
  'WRITER',
  'REFLECTION_CRITIC',
  'CITATION_VALIDATOR',
];

const API_BASE = 'http://localhost:8085/api/runs';

export default function App() {
  // Form State
  const [query, setQuery] = useState('');
  const [depth, setDepth] = useState<'QUICK' | 'STANDARD' | 'DEEP'>('STANDARD');
  const [budgetCapPaise, setBudgetCapPaise] = useState(1500); // ₹15 default

  // Active Run State
  const [activeRunId, setActiveRunId] = useState<string | null>(null);
  const [runData, setRunData] = useState<RunResponse | null>(null);
  const [streamLogs, setStreamLogs] = useState<StreamEvent[]>([]);
  const [currentAgent, setCurrentAgent] = useState<string>('PLANNER');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);

  // Tabs & Views
  const [activeTab, setActiveTab] = useState<'brief' | 'traces' | 'sources' | 'claims'>('brief');
  const [report, setReport] = useState<ReportResponse | null>(null);
  const [traces, setTraces] = useState<StepResponse[]>([]);
  const [sources, setSources] = useState<SourceEntity[]>([]);
  const [claims, setClaims] = useState<ClaimEntity[]>([]);
  const [pastRuns, setPastRuns] = useState<RunResponse[]>([]);
  const [showDraftDiff, setShowDraftDiff] = useState(false);

  const logEndRef = useRef<HTMLDivElement>(null);

  // Load past runs on startup
  useEffect(() => {
    fetchPastRuns();
  }, []);

  // Auto-scroll logs
  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [streamLogs]);

  // Subscribe to SSE stream when activeRunId changes
  useEffect(() => {
    if (!activeRunId) return;

    setIsStreaming(true);
    const eventSource = new EventSource(`${API_BASE}/${activeRunId}/stream`);

    eventSource.onmessage = (event) => {
      try {
        const data: StreamEvent = JSON.parse(event.data);
        setStreamLogs((prev) => [...prev, data]);
        if (data.agent) setCurrentAgent(data.agent);

        if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(data.status)) {
          setIsStreaming(false);
          eventSource.close();
          fetchRunDetails(activeRunId);
        }
      } catch (e) {
        console.warn('Could not parse SSE message:', e);
      }
    };

    // Custom named events from backend SseEmitter
    const eventTypes = ['PLANNING', 'SEARCHING', 'READING', 'VERIFYING', 'WRITING', 'REFLECTING', 'VALIDATING_CITATIONS', 'COMPLETED', 'FAILED', 'CANCELLED'];
    eventTypes.forEach((type) => {
      eventSource.addEventListener(type, (event: any) => {
        try {
          const data: StreamEvent = JSON.parse(event.data);
          setStreamLogs((prev) => [...prev, data]);
          if (data.agent) setCurrentAgent(data.agent);

          if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(type)) {
            setIsStreaming(false);
            eventSource.close();
            fetchRunDetails(activeRunId);
          }
        } catch (e) {
          console.warn('Could not parse SSE custom event:', e);
        }
      });
    });

    eventSource.onerror = () => {
      setIsStreaming(false);
      eventSource.close();
      fetchRunDetails(activeRunId);
    };

    return () => {
      eventSource.close();
    };
  }, [activeRunId]);

  const fetchPastRuns = async () => {
    try {
      const res = await fetch(`${API_BASE}?page=0&size=20`);
      if (res.ok) {
        const data = await res.json();
        setPastRuns(data);
      }
    } catch (e) {
      console.warn('Could not fetch past runs:', e);
    }
  };

  const fetchRunDetails = async (runId: string) => {
    try {
      const [runRes, reportRes, traceRes, sourceRes, claimRes] = await Promise.all([
        fetch(`${API_BASE}/${runId}`),
        fetch(`${API_BASE}/${runId}/report`),
        fetch(`${API_BASE}/${runId}/trace`),
        fetch(`${API_BASE}/${runId}/sources`),
        fetch(`${API_BASE}/${runId}/claims`),
      ]);

      if (runRes.ok) setRunData(await runRes.json());
      if (reportRes.ok) setReport(await reportRes.json());
      if (traceRes.ok) setTraces(await traceRes.json());
      if (sourceRes.ok) setSources(await sourceRes.json());
      if (claimRes.ok) setClaims(await claimRes.json());
    } catch (e) {
      console.warn('Error fetching run details:', e);
    }
  };

  const handleSubmitRun = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim() || isSubmitting) return;

    setIsSubmitting(true);
    setStreamLogs([]);
    setReport(null);
    setTraces([]);
    setSources([]);
    setClaims([]);

    try {
      const res = await fetch(API_BASE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: query.trim(),
          depth,
          budgetCapPaise,
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        alert(`Error launching run: ${err.message || res.statusText}`);
        setIsSubmitting(false);
        return;
      }

      const run: RunResponse = await res.json();
      setActiveRunId(run.id);
      setRunData(run);
      fetchPastRuns();
    } catch (err: any) {
      alert(`Network error: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancelRun = async () => {
    if (!activeRunId) return;
    try {
      await fetch(`${API_BASE}/${activeRunId}/cancel`, { method: 'POST' });
      fetchRunDetails(activeRunId);
    } catch (e) {
      console.warn('Cancel failed:', e);
    }
  };

  const selectPastRun = (runId: string) => {
    setActiveRunId(runId);
    setStreamLogs([]);
    fetchRunDetails(runId);
  };

  return (
    <div className="app-container">
      {/* Top Navbar */}
      <header className="navbar">
        <div className="nav-brand">
          <span className="brand-logo">⚡</span>
          <div>
            <h1 className="brand-title">ResearchAgent</h1>
            <p className="brand-subtitle">Your Autonomous AI Research Team, On Demand</p>
          </div>
        </div>
        <div className="nav-status">
          <span className="status-dot"></span>
          <span>Engine Status: <strong>Online (Port 8085)</strong></span>
        </div>
      </header>

      {/* Main Layout Grid */}
      <div className="main-layout">
        {/* Left Column: Form & History */}
        <aside className="sidebar">
          {/* Launch Form Card */}
          <div className="card">
            <h2 className="card-title">New Research Task</h2>
            <form onSubmit={handleSubmitRun}>
              <div className="form-group">
                <label className="form-label">Research Query</label>
                <textarea
                  className="form-textarea"
                  rows={3}
                  placeholder="e.g. Compare quantum key distribution vs post-quantum lattice cryptography in 2026..."
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Depth Level</label>
                <div className="depth-selector">
                  {(['QUICK', 'STANDARD', 'DEEP'] as const).map((d) => (
                    <button
                      key={d}
                      type="button"
                      className={`depth-btn ${depth === d ? 'active' : ''}`}
                      onClick={() => setDepth(d)}
                    >
                      {d}
                      <span className="depth-sub">
                        {d === 'QUICK' ? '~2 min' : d === 'STANDARD' ? '~5 min' : '~10 min'}
                      </span>
                    </button>
                  ))}
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">
                  Budget Cap: <strong>₹{(budgetCapPaise / 100).toFixed(2)}</strong> ({budgetCapPaise} paise)
                </label>
                <input
                  type="range"
                  min={500}
                  max={5000}
                  step={100}
                  value={budgetCapPaise}
                  onChange={(e) => setBudgetCapPaise(Number(e.target.value))}
                  className="form-range"
                />
              </div>

              <button type="submit" className="submit-btn" disabled={isSubmitting || !query.trim()}>
                {isSubmitting ? 'Launching Agents...' : '🚀 Start AI Research Team'}
              </button>
            </form>
          </div>

          {/* Past Runs History List */}
          <div className="card history-card">
            <h3 className="card-title">Past Runs</h3>
            <div className="history-list">
              {pastRuns.length === 0 ? (
                <p className="empty-msg">No prior runs recorded yet.</p>
              ) : (
                pastRuns.map((r) => (
                  <div
                    key={r.id}
                    className={`history-item ${activeRunId === r.id ? 'active' : ''}`}
                    onClick={() => selectPastRun(r.id)}
                  >
                    <div className="history-item-header">
                      <span className={`status-badge status-${r.status.toLowerCase()}`}>
                        {r.status}
                      </span>
                      <span className="history-cost">₹{(r.costPaise / 100).toFixed(2)}</span>
                    </div>
                    <div className="history-query">{r.query}</div>
                  </div>
                ))
              )}
            </div>
          </div>
        </aside>

        {/* Right Column: Execution Pipeline, Real-time Stream & Report Tabs */}
        <main className="content-area">
          {/* Agent Pipeline Progress Bar */}
          {activeRunId && (
            <div className="card pipeline-card">
              <div className="pipeline-header">
                <div>
                  <h2 className="run-headline">{runData?.query || 'Executing Research Task'}</h2>
                  <span className="run-id-tag">ID: {activeRunId}</span>
                </div>
                {isStreaming && (
                  <button className="cancel-btn" onClick={handleCancelRun}>
                    Stop LLM Spend (Cancel)
                  </button>
                )}
              </div>

              {/* Step Pipeline Visualization */}
              <div className="pipeline-steps">
                {AGENT_ORDER.map((agentName, idx) => {
                  const isCurrent = currentAgent === agentName && isStreaming;
                  const currentIdx = AGENT_ORDER.indexOf(currentAgent);
                  const isDone = currentIdx > idx || runData?.status === 'COMPLETED';

                  return (
                    <div
                      key={agentName}
                      className={`pipeline-node ${isCurrent ? 'current' : ''} ${isDone ? 'done' : ''}`}
                    >
                      <div className="node-icon">{isDone ? '✓' : idx + 1}</div>
                      <span className="node-label">{agentName.replace('_', ' ')}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Live Agent Stream Console */}
          {isStreaming && (
            <div className="card console-card">
              <div className="console-header">
                <h3>⚡ Real-time Agent Thought Stream</h3>
                <span className="live-indicator">LIVE SSE</span>
              </div>
              <div className="console-logs">
                {streamLogs.map((log, index) => (
                  <div key={index} className="log-line">
                    <span className="log-ts">[{log.ts ? log.ts.split('T')[1].slice(0, 8) : 'LOG'}]</span>
                    <span className="log-agent">[{log.agent || 'SYSTEM'}]</span>
                    <span className="log-msg">{log.message}</span>
                  </div>
                ))}
                <div ref={logEndRef} />
              </div>
            </div>
          )}

          {/* Results Tab View */}
          {runData && (
            <div className="card results-card">
              {/* Tab Navigation */}
              <div className="tab-nav">
                <button
                  className={`tab-btn ${activeTab === 'brief' ? 'active' : ''}`}
                  onClick={() => setActiveTab('brief')}
                >
                  📄 Research Brief
                </button>
                <button
                  className={`tab-btn ${activeTab === 'traces' ? 'active' : ''}`}
                  onClick={() => setActiveTab('traces')}
                >
                  🔍 Step Traces ({traces.length})
                </button>
                <button
                  className={`tab-btn ${activeTab === 'sources' ? 'active' : ''}`}
                  onClick={() => setActiveTab('sources')}
                >
                  🌐 Sources ({sources.length})
                </button>
                <button
                  className={`tab-btn ${activeTab === 'claims' ? 'active' : ''}`}
                  onClick={() => setActiveTab('claims')}
                >
                  ⚖️ Verified Claims ({claims.length})
                </button>
              </div>

              {/* Tab 1: Brief & PDF Export */}
              {activeTab === 'brief' && (
                <div className="tab-content">
                  {report ? (
                    <div>
                      <div className="report-toolbar">
                        <div className="confidence-pill">
                          Confidence Score: <strong>{(report.confidenceScore * 100).toFixed(0)}% Verified</strong>
                        </div>

                        {report.version > 1 && (
                          <button
                            className="toggle-diff-btn"
                            onClick={() => setShowDraftDiff(!showDraftDiff)}
                          >
                            {showDraftDiff ? 'Show Final Version' : `Draft Diff (v${report.version} vs v1)`}
                          </button>
                        )}

                        <a
                          href={`${API_BASE}/${activeRunId}/pdf`}
                          target="_blank"
                          rel="noreferrer"
                          className="pdf-dl-btn"
                        >
                          📥 Download Publication PDF
                        </a>
                      </div>

                      {showDraftDiff && report.previousDraftContent ? (
                        <div className="draft-diff-container">
                          <h3>Initial Draft (v1) Comparison</h3>
                          <pre className="markdown-pre">{report.previousDraftContent}</pre>
                        </div>
                      ) : (
                        <div className="markdown-body">
                          <h1>{report.title}</h1>
                          <pre className="markdown-pre">{report.markdownContent}</pre>
                        </div>
                      )}
                    </div>
                  ) : (
                    <p className="empty-msg">Report is generating... waiting for Writer & Reflection Critic agents.</p>
                  )}
                </div>
              )}

              {/* Tab 2: Step Traces Audit */}
              {activeTab === 'traces' && (
                <div className="tab-content">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Seq</th>
                        <th>Agent</th>
                        <th>Action</th>
                        <th>Tokens In/Out</th>
                        <th>Cost</th>
                        <th>Duration</th>
                      </tr>
                    </thead>
                    <tbody>
                      {traces.map((t) => (
                        <tr key={t.id || t.seq}>
                          <td>{t.seq}</td>
                          <td><span className="agent-tag">{t.agent}</span></td>
                          <td>{t.action}</td>
                          <td>{t.tokensIn} / {t.tokensOut}</td>
                          <td>₹{(t.costPaise / 100).toFixed(2)}</td>
                          <td>{t.durationMs}ms</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Tab 3: Sources */}
              {activeTab === 'sources' && (
                <div className="tab-content">
                  <div className="sources-grid">
                    {sources.map((s) => (
                      <div key={s.id} className="source-card">
                        <div className="source-header">
                          <span className="source-num">[{s.sourceNumber}]</span>
                          <span className="source-domain">{s.domain}</span>
                        </div>
                        <a href={s.url} target="_blank" rel="noreferrer" className="source-title">
                          {s.title || s.url}
                        </a>
                        <p className="source-snippet">{s.snippet}</p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Tab 4: Claims Corroboration */}
              {activeTab === 'claims' && (
                <div className="tab-content">
                  <div className="claims-list">
                    {claims.map((c) => (
                      <div key={c.id} className={`claim-card claim-${c.status.toLowerCase()}`}>
                        <div className="claim-header">
                          <span className={`claim-badge badge-${c.status.toLowerCase()}`}>
                            {c.status}
                          </span>
                          {c.sourceDomain && <span className="claim-domain">Domain: {c.sourceDomain}</span>}
                        </div>
                        <p className="claim-statement">{c.statement}</p>
                        {c.exactQuote && <blockquote className="claim-quote">"{c.exactQuote}"</blockquote>}
                        {c.supportingDomains && (
                          <div className="supporting-domains">
                            Corroborated across: <strong>{c.supportingDomains}</strong>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
