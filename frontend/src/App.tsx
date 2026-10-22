import { useState, useEffect, useRef } from 'react';
import './index.css';
import ReactMarkdown from 'react-markdown';
import { API_ORIGIN, sessionFetch, login, logout } from './auth';

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
  snapshotId?: string;
  contentHash?: string;
  content?: string;
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
  evidenceSnapshotId?: string;
  evidenceContentHash?: string;
  quoteStart?: number;
  quoteEnd?: number;
  quoteMatchStatus?: string;
}

function QuoteEvidenceView({ claim, sources }: { claim: ClaimEntity; sources: SourceEntity[] }) {
  if (!claim.quoteMatchStatus) return <p className="mt-2 text-xs text-text-muted">Quotation validation unavailable for this older claim.</p>;
  const source = sources.find(s => s.snapshotId === claim.evidenceSnapshotId && s.contentHash === claim.evidenceContentHash);
  const start = claim.quoteStart;
  const end = claim.quoteEnd;
  const text = source?.content;
  if (text == null || start == null || end == null || !Number.isInteger(start) || !Number.isInteger(end)
    || start < 0 || end <= start || end > text.length || text.slice(start, end) !== claim.exactQuote) {
    return <p className="mt-2 text-xs text-text-muted">Saved quotation evidence is unavailable.</p>;
  }
  return (
    <details className="mt-2 text-xs text-text-muted">
      <summary className="cursor-pointer">View quotation in saved source</summary>
      <p className="my-2">{claim.quoteMatchStatus === 'EXACT' ? 'Exact text match' : 'Whitespace differences only'}. This checks the quotation’s origin; it does not verify the claim.</p>
      <p className="whitespace-pre-wrap">{text.slice(Math.max(0, start - 160), start)}<mark>{text.slice(start, end)}</mark>{text.slice(end, Math.min(text.length, end + 160))}</p>
    </details>
  );
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

const API_BASE = `${API_ORIGIN}/api/runs`;

// Components
const MarkdownRenderer = ({ content }: { content: string }) => (
  <div className="markdown-body">
    <ReactMarkdown
      components={{
        h1: ({ children }) => <h1 className="text-2xl font-bold text-text-h mb-4">{children}</h1>,
        h2: ({ children }) => <h2 className="text-xl font-semibold text-text-h mb-3">{children}</h2>,
        h3: ({ children }) => <h3 className="text-lg font-semibold text-text-h mb-2">{children}</h3>,
        p: ({ children }) => <p className="text-text-main mb-4 leading-relaxed">{children}</p>,
        a: ({ href, children }) => (
          <a href={href} className="text-primary hover:text-primary-hover underline" target="_blank" rel="noreferrer">
            {children}
          </a>
        ),
        blockquote: ({ children }) => (
          <blockquote className="border-l-4 border-primary bg-primary/10 pl-4 pr-3 py-2 my-4 italic text-text-muted">{children}</blockquote>
        ),
        code: (props) => {
          const { children } = props;
          return <code className="bg-bg-card-hover px-2 py-1 rounded text-sm font-mono">{children}</code>;
        },
        pre: ({ children }) => <pre className="bg-bg-card-hover p-4 rounded-lg overflow-auto my-4 text-sm font-mono">{children}</pre>,
        ul: ({ children }) => <ul className="list-disc pl-6 my-4 space-y-2">{children}</ul>,
        ol: ({ children }) => <ol className="list-decimal pl-6 my-4 space-y-2">{children}</ol>,
        li: ({ children }) => <li className="text-text-main">{children}</li>,
        table: ({ children }) => <table className="w-full border-collapse my-4">{children}</table>,
        th: ({ children }) => (
          <th className="border border-border-color bg-bg-card-hover px-4 py-2 text-left text-xs font-medium text-text-muted">{children}</th>
        ),
        td: ({ children }) => <td className="border border-border-color px-4 py-2 text-xs text-text-main">{children}</td>,
      }}
      >
        {content}
      </ReactMarkdown>
  </div>
);

// API Service Functions
const runService = {
  listRuns: async (page: number = 0, size: number = 20) => {
    const res = await sessionFetch(`${API_BASE}?page=${page}&size=${size}`);
    if (!res.ok) throw new Error('Failed to fetch runs');
    return res.json();
  },

  createRun: async (query: string, depth: string, budgetCapPaise: number) => {
    const res = await sessionFetch(API_BASE, {
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
      throw new Error(err.error || err.message || 'Failed to create run');
    }
    return res.json();
  },

  getRun: async (runId: string) => {
    const res = await sessionFetch(`${API_BASE}/${runId}`);
    if (!res.ok) throw new Error('Failed to fetch run');
    return res.json();
  },

  cancelRun: async (runId: string) => {
    const res = await sessionFetch(`${API_BASE}/${runId}/cancel`, { method: 'POST' });
    if (!res.ok) throw new Error('Failed to cancel run');
  },

  getReport: async (runId: string) => {
    const res = await sessionFetch(`${API_BASE}/${runId}/report`);
    if (!res.ok) throw new Error('Failed to fetch report');
    return res.json();
  },

  getSteps: async (runId: string) => {
    const res = await sessionFetch(`${API_BASE}/${runId}/trace`);
    if (!res.ok) throw new Error('Failed to fetch steps');
    return res.json();
  },

  getSources: async (runId: string) => {
    const res = await sessionFetch(`${API_BASE}/${runId}/sources`);
    if (!res.ok) throw new Error('Failed to fetch sources');
    return res.json();
  },

  getClaims: async (runId: string) => {
    const res = await sessionFetch(`${API_BASE}/${runId}/claims`);
    if (!res.ok) throw new Error('Failed to fetch claims');
    return res.json();
  },
};

// SSE Hook
const useEventSource = (
  runId: string | null,
  active: boolean,
  onMessage: (data: StreamEvent) => void,
  onTerminal: (status?: string) => void
) => {
  const onMessageRef = useRef(onMessage);
  const onTerminalRef = useRef(onTerminal);
  onMessageRef.current = onMessage;
  onTerminalRef.current = onTerminal;

  useEffect(() => {
    if (!runId || !active) return;

    const eventSource = new EventSource(`${API_BASE}/${runId}/stream`, { withCredentials: true });

    eventSource.onmessage = (event) => {
      try {
        const data: StreamEvent = JSON.parse(event.data);
        onMessageRef.current(data);
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
          onMessageRef.current(data);

          if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(type)) {
            onTerminalRef.current(type);
            eventSource.close();
          }
        } catch (e) {
          console.warn('Could not parse SSE custom event:', e);
        }
      });
    });

    eventSource.onerror = () => {
      onTerminalRef.current();
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [runId, active]);
};

export default function App() {
  const [user, setUser] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    const expired = () => { setUser(null); setError('Your session ended. Please sign in again.'); };
    window.addEventListener('session-expired', expired);
    fetch(`${API_ORIGIN}/api/auth/me`, { credentials: 'include', cache: 'no-store' }).then(async (res) => {
      if (res.ok) {
        const identity = await res.json();
        if (active) setUser(identity.username);
      } else if (res.status !== 401) {
        throw new Error('Cannot connect to the sign-in service');
      }
    }).catch(() => { if (active) setError('Cannot connect to the sign-in service.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; window.removeEventListener('session-expired', expired); };
  }, []);

  if (loading) return <main className="p-8">Loading your session…</main>;
  if (user) return <>
    <div className="flex justify-end items-center gap-4 p-3 bg-bg-card">
      <span>Signed in as {user}</span>
      <button disabled={busy} onClick={async () => {
        setBusy(true); setError('');
        try { await logout(); setUser(null); setPassword(''); }
        catch (e) { setError(e instanceof Error ? e.message : 'Sign-out failed'); }
        finally { setBusy(false); }
      }}>Sign out</button>
      {error && <span role="alert">{error}</span>}
    </div>
    <Dashboard key={user} />
  </>;

  return <main className="min-h-screen flex items-center justify-center p-6">
    <form className="bg-bg-card rounded-xl p-8 flex flex-col gap-4 w-full max-w-md" onSubmit={async (event) => {
      event.preventDefault(); setBusy(true); setError('');
      try { setUser(await login(username.trim(), password)); setPassword(''); }
      catch (e) { setError(e instanceof Error ? e.message : 'Sign-in failed'); }
      finally { setBusy(false); }
    }}>
      <h1 className="text-2xl font-bold">Sign in to ResearchAgent</h1>
      <p>Your research runs are private to your account.</p>
      <label htmlFor="username">Username</label>
      <input id="username" autoComplete="username" value={username} required
        className="p-3 border rounded text-text-main bg-bg-card-hover" onChange={event => setUsername(event.target.value)} />
      <label htmlFor="password">Password</label>
      <input id="password" type="password" autoComplete="current-password" value={password} required
        className="p-3 border rounded text-text-main bg-bg-card-hover" onChange={event => setPassword(event.target.value)} />
      {error && <p role="alert">{error}</p>}
      <button type="submit" disabled={busy} className="bg-primary rounded p-3">{busy ? 'Signing in…' : 'Sign in'}</button>
    </form>
  </main>;
}

function Dashboard() {
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
    runService.listRuns().then(setPastRuns).catch(console.warn);
  }, []);

  // Auto-scroll logs
  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [streamLogs]);

  const isTerminal = (status?: string) =>
    status ? ['COMPLETED', 'FAILED', 'CANCELLED'].includes(status.toUpperCase()) : false;

  // SSE Hook
  useEventSource(
    activeRunId,
    isStreaming,
    (data) => {
      setStreamLogs((prev) => [...prev, data]);
      if (data.agent) setCurrentAgent(data.agent);

      if (isTerminal(data.status)) {
        setIsStreaming(false);
        if (activeRunId) {
          fetchRunDetails(activeRunId);
          fetchPastRuns();
        }
      }
    },
    () => {
      setIsStreaming(false);
      if (activeRunId) {
        fetchRunDetails(activeRunId);
        fetchPastRuns();
      }
    }
  );

  const fetchPastRuns = async () => {
    try {
      const data = await runService.listRuns();
      setPastRuns(data);
    } catch (e) {
      console.warn('Could not fetch past runs:', e);
    }
  };

  const fetchRunDetails = async (runId: string) => {
    try {
      const [runRes, reportRes, traceRes, sourceRes, claimRes] = await Promise.allSettled([
        runService.getRun(runId),
        runService.getReport(runId),
        runService.getSteps(runId),
        runService.getSources(runId),
        runService.getClaims(runId),
      ]);

      if (runRes.status === 'fulfilled') {
        setRunData(runRes.value);
        if (isTerminal(runRes.value.status)) {
          setIsStreaming(false);
        }
      }
      setReport(reportRes.status === 'fulfilled' ? reportRes.value : null);
      setTraces(traceRes.status === 'fulfilled' ? traceRes.value : []);
      setSources(sourceRes.status === 'fulfilled' ? sourceRes.value : []);
      setClaims(claimRes.status === 'fulfilled' ? claimRes.value : []);
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
      const run = await runService.createRun(query.trim(), depth, budgetCapPaise);
      setActiveRunId(run.id);
      setRunData(run);
      setIsStreaming(true);
      await fetchPastRuns();
    } catch (err: any) {
      alert(`Network error: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancelRun = async () => {
    if (!activeRunId) return;
    try {
      await runService.cancelRun(activeRunId);
      setIsStreaming(false);
      await fetchRunDetails(activeRunId);
      await fetchPastRuns();
    } catch (e) {
      console.warn('Cancel failed:', e);
    }
  };

  const selectPastRun = (runId: string) => {
    setActiveRunId(runId);
    setStreamLogs([]);
    const past = pastRuns.find((r) => r.id === runId);
    if (past && !isTerminal(past.status)) {
      setIsStreaming(true);
    } else {
      setIsStreaming(false);
    }
    fetchRunDetails(runId);
  };

  return (
    <div className="app-container min-h-screen flex flex-col">
      {/* Top Navbar */}
      <header className="navbar flex items-center justify-between px-6 py-4 bg-bg-card border border-border-color rounded-xl mb-6">
        <div className="nav-brand flex items-center gap-3">
          <span className="brand-logo text-2xl">⚡</span>
          <div>
            <h1 className="brand-title text-xl font-bold text-text-h">ResearchAgent</h1>
            <p className="brand-subtitle text-sm text-text-muted">Your Autonomous AI Research Team, On Demand</p>
          </div>
        </div>
        <div className="nav-status flex items-center gap-2 text-sm text-text-muted">
          <span className={`status-dot w-2 h-2 rounded-full bg-accent-green shadow-[0_0_8px_var(--accent-green)]`}></span>
          <span>Engine Status: <strong className="font-medium">Online (Port 8085)</strong></span>
        </div>
      </header>

      {/* Main Layout Grid */}
      <main className="main-layout flex-1 flex gap-6">
        {/* Left Column: Form & History */}
        <aside className="sidebar w-72">
          {/* Launch Form Card */}
          <div className="card bg-bg-card border border-border-color rounded-xl p-6 mb-6">
            <h2 className="card-title text-lg font-semibold text-text-h mb-4">New Research Task</h2>
            <form onSubmit={handleSubmitRun} className="space-y-4">
              <div className="form-group">
                <label className="form-label block text-sm font-medium text-text-muted mb-2">Research Query</label>
                <textarea
                  className="form-textarea w-full px-4 py-3 bg-bg-primary border border-border-color rounded-lg text-text-main font-mono resize-y focus:outline-none focus:border-primary"
                  rows={3}
                  placeholder="e.g. Compare quantum key distribution vs post-quantum lattice cryptography in 2026..."
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label block text-sm font-medium text-text-muted mb-2">Depth Level</label>
                <div className="depth-selector grid grid-cols-3 gap-2">
                  {(['QUICK', 'STANDARD', 'DEEP'] as const).map((d) => (
                    <button
                      key={d}
                      type="button"
                      className={`depth-btn w-full px-3 py-2 bg-bg-primary border border-border-color rounded-lg text-text-sm font-medium text-text-muted cursor-pointer flex flex-col items-center transition-all duration-200 ${depth === d ? 'border-primary bg-primary/10 text-primary' : ''}`}
                      onClick={() => setDepth(d)}
                    >
                      {d}
                      <span className="depth-sub mt-1 text-xs font-normal opacity-80">
                        {d === 'QUICK' ? '~2 min' : d === 'STANDARD' ? '~5 min' : '~10 min'}
                      </span>
                    </button>
                  ))}
                </div>
              </div>

              <div className="form-group">
                <label className="form-label block text-sm font-medium text-text-muted mb-2">
                  Budget Cap: <strong className="font-medium">₹{(budgetCapPaise / 100).toFixed(2)}</strong> ({budgetCapPaise} paise)
                </label>
                <input
                  type="range"
                  min={500}
                  max={5000}
                  step={100}
                  value={budgetCapPaise}
                  onChange={(e) => setBudgetCapPaise(Number(e.target.value))}
                  className="form-range w-full accent-primary"
                />
              </div>

              <button
                type="submit"
                className="submit-btn w-full px-6 py-3 bg-primary text-white font-medium rounded-lg transition-colors duration-200 hover:bg-primary-hover disabled:opacity-60 disabled:cursor-not-allowed"
                disabled={isSubmitting || !query.trim()}
              >
                {isSubmitting ? 'Launching Agents...' : '🚀 Start AI Research Team'}
              </button>
            </form>
          </div>

          {/* Past Runs History List */}
          <div className="card history-card bg-bg-card border border-border-color rounded-xl p-6 mb-6">
            <h3 className="card-title text-lg font-semibold text-text-h mb-4">Past Runs</h3>
            <div className="history-list flex flex-col gap-2 max-h-[320px] overflow-y-auto">
              {pastRuns.length === 0 ? (
                <p className="empty-msg text-center text-text-muted py-4">No prior runs recorded yet.</p>
              ) : (
                pastRuns.map((r) => (
                  <div
                    key={r.id}
                    className={`history-item w-full px-4 py-3 bg-bg-primary border border-border-color rounded-lg cursor-pointer transition-colors duration-200 ${activeRunId === r.id ? 'border-primary' : ''}`}
                    onClick={() => selectPastRun(r.id)}
                  >
                    <div className="history-item-header flex justify-between items-start mb-2">
                      <span className={`status-badge px-2 py-1 rounded text-xs font-medium ${r.status.toLowerCase() === 'completed' ? 'bg-accent-green/20 text-accent-green' : r.status.toLowerCase() === 'failed' ? 'bg-accent-red/20 text-accent-red' : 'bg-primary/20 text-primary'}`}>
                        {r.status}
                      </span>
                      <span className="history-cost text-xs text-text-muted">₹{(r.costPaise / 100).toFixed(2)}</span>
                    </div>
                    <div className="history-query text-sm text-text-main truncate">{r.query}</div>
                  </div>
                ))
              )}
            </div>
          </div>
        </aside>

        {/* Right Column: Execution Pipeline, Real-time Stream & Report Tabs */}
        <section className="content-area flex-1 flex flex-col gap-6">
          {/* Agent Pipeline Progress Bar */}
          {activeRunId && (
            <div className="card pipeline-card bg-bg-card border border-border-color rounded-xl p-6">
              <div className="pipeline-header flex items-center justify-between mb-4">
                <div>
                  <h2 className="run-headline text-lg font-semibold text-text-h">{runData?.query || 'Executing Research Task'}</h2>
                  <span className="run-id-tag text-xs text-text-muted">ID: {activeRunId}</span>
                </div>
                {isStreaming && (
                  <button
                    className="cancel-btn px-4 py-2 bg-accent-red/10 border border-accent-red text-accent-red rounded-lg font-medium hover:bg-accent-red/20 transition-colors duration-200"
                    onClick={handleCancelRun}
                  >
                    Cancel research
                  </button>
                )}
              </div>

              {runData?.status === 'CANCELLED' && (
                <p role="status" className="text-sm text-text-muted mb-4">
                  Cancellation requested. A call already in progress may finish and incur a charge.
                  Final usage may update after that call returns.
                </p>
              )}

              {/* Step Pipeline Visualization */}
              <div className="pipeline-steps flex items-center justify-between relative">
                <div className="absolute inset-0 h-0.5 bg-border-color"></div>
                {AGENT_ORDER.map((agentName, idx) => {
                  const isCurrent = currentAgent === agentName && isStreaming;
                  const currentIdx = AGENT_ORDER.indexOf(currentAgent);
                  const isDone = currentIdx > idx || runData?.status === 'COMPLETED';

                  return (
                    <div
                      key={agentName}
                      className={`pipeline-node flex flex-col items-center relative z-10 ${isCurrent ? 'current' : ''} ${isDone ? 'done' : ''}`}
                    >
                      <div className={`node-icon w-8 h-8 flex items-center justify-center rounded-full bg-bg-primary border border-border-color text-text-sm font-medium text-text-muted ${isDone ? 'bg-accent-green border-accent-green text-white' : ''} ${isCurrent ? 'bg-primary border-primary text-white shadow-[0_0_12px_var(--primary)]' : ''}`}>
                        {isDone ? '✓' : idx + 1}
                      </div>
                      <span className="node-label mt-2 text-xs font-medium text-text-muted">{agentName.replace('_', ' ')}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Live Agent Stream Console */}
          {isStreaming && (
            <div className="card console-card bg-[#050b14] border border-border-color rounded-xl p-6">
              <div className="console-header flex items-center justify-between mb-3">
                <h3 className="text-lg font-semibold text-text-h">⚡ Real-time Agent Thought Stream</h3>
                <span className="live-indicator text-xs font-semibold bg-accent-green/20 text-accent-green px-3 py-1 rounded">LIVE SSE</span>
              </div>
              <div className="console-logs font-mono text-sm flex flex-col gap-1.5 max-h-[220px] overflow-y-auto">
                {streamLogs.map((log, index) => (
                  <div key={index} className="log-line flex items-start">
                    <span className="log-ts text-xs text-muted-400 mr-2">[{log.ts ? log.ts.split('T')[1].slice(0, 8) : 'LOG'}]</span>
                    <span className="log-agent text-xs font-medium text-blue-400 mr-2">[{log.agent || 'SYSTEM'}]</span>
                    <span className="log-msg flex-1 text-text-muted">{log.message}</span>
                  </div>
                ))}
                <div ref={logEndRef} className="h-0.5" />
              </div>
            </div>
          )}

          {/* Results Tab View */}
          {runData && (
            <div className="card results-card bg-bg-card border border-border-color rounded-xl p-6">
              {/* Tab Navigation */}
              <div className="tab-nav flex border-b border-border-color pb-4 mb-6">
                <button
                  className={`tab-btn px-4 py-2 text-sm font-medium text-text-muted hover:bg-bg-card-hover hover:text-primary rounded-lg transition-colors duration-200 ${activeTab === 'brief' ? 'bg-bg-card-hover text-primary' : ''}`}
                  onClick={() => setActiveTab('brief')}
                >
                  📄 Research Brief
                </button>
                <button
                  className={`tab-btn px-4 py-2 text-sm font-medium text-text-muted hover:bg-bg-card-hover hover:text-primary rounded-lg transition-colors duration-200 ${activeTab === 'traces' ? 'bg-bg-card-hover text-primary' : ''}`}
                  onClick={() => setActiveTab('traces')}
                >
                  🔍 Step Traces ({traces.length})
                </button>
                <button
                  className={`tab-btn px-4 py-2 text-sm font-medium text-text-muted hover:bg-bg-card-hover hover:text-primary rounded-lg transition-colors duration-200 ${activeTab === 'sources' ? 'bg-bg-card-hover text-primary' : ''}`}
                  onClick={() => setActiveTab('sources')}
                >
                  🌐 Sources ({sources.length})
                </button>
                <button
                  className={`tab-btn px-4 py-2 text-sm font-medium text-text-muted hover:bg-bg-card-hover hover:text-primary rounded-lg transition-colors duration-200 ${activeTab === 'claims' ? 'bg-bg-card-hover text-primary' : ''}`}
                  onClick={() => setActiveTab('claims')}
                >
                  ⚖️ Verified Claims ({claims.length})
                </button>
              </div>

              {/* Tab 1: Brief & PDF Export */}
              {activeTab === 'brief' && (
                <div className="tab-content">
                  {report ? (
                    <div className="space-y-6">
                      <div className="report-toolbar flex items-center justify-between">
                        <div className="confidence-pill px-4 py-2 bg-accent-green/20 text-accent-green font-medium rounded-full">
                          Confidence Score: <strong className="font-bold">{(report.confidenceScore * 100).toFixed(0)}% Verified</strong>
                        </div>

                        {report.version > 1 && (
                          <button
                            className="toggle-diff-btn px-4 py-2 text-sm font-medium bg-bg-primary border border-border-color rounded-lg hover:bg-bg-card-hover transition-colors duration-200"
                            onClick={() => setShowDraftDiff(!showDraftDiff)}
                          >
                            {showDraftDiff ? 'Show Final Version' : `Draft Diff (v${report.version} vs v1)`}
                          </button>
                        )}

                        <a
                          href={`${API_BASE}/${activeRunId}/pdf`}
                          target="_blank"
                          rel="noreferrer"
                          className="pdf-dl-btn px-5 py-2 bg-primary text-white font-medium rounded-lg hover:bg-primary-hover transition-colors duration-200"
                        >
                          📥 Download Publication PDF
                        </a>
                      </div>

                      {showDraftDiff && report.previousDraftContent ? (
                        <div className="draft-diff-container bg-bg-primary border border-border-color rounded-lg p-4">
                          <h3 className="text-lg font-semibold text-text-h mb-3">Initial Draft (v1) Comparison</h3>
                          <pre className="markdown-pre w-full bg-bg-card-hover p-3 rounded text-sm font-mono text-text-main overflow-auto">{report.previousDraftContent}</pre>
                        </div>
                      ) : (
                        <div className="markdown-body">
                          <h1 className="text-2xl font-bold text-text-h mb-4">{report.title}</h1>
                          <MarkdownRenderer content={report.markdownContent} />
                        </div>
                      )}
                    </div>
                  ) : (
                    <p className="empty-msg text-center text-text-muted py-8">Report is generating... waiting for Writer & Reflection Critic agents.</p>
                  )}
                </div>
              )}

              {/* Tab 2: Step Traces Audit */}
              {activeTab === 'traces' && (
                <div className="tab-content">
                  <table className="data-table w-full border-collapse">
                    <thead>
                      <tr className="bg-bg-card-hover">
                        <th className="px-4 py-3 text-left text-xs font-medium text-text-muted">Seq</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-text-muted">Agent</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-text-muted">Action</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-text-muted">Tokens In/Out</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-text-muted">Cost</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-text-muted">Duration</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border-color">
                      {traces.map((t) => (
                        <tr key={t.id || t.seq} className="hover:bg-bg-primary">
                          <td className="px-4 py-3 text-xs font-medium text-text-main">{t.seq}</td>
                          <td className="px-4 py-3 text-xs font-medium text-primary">{t.agent}</td>
                          <td className="px-4 py-3 text-xs font-medium text-text-main">{t.action}</td>
                          <td className="px-4 py-3 text-xs font-medium text-text-main">{t.tokensIn} / {t.tokensOut}</td>
                          <td className="px-4 py-3 text-xs font-medium text-text-main">₹{(t.costPaise / 100).toFixed(2)}</td>
                          <td className="px-4 py-3 text-xs font-medium text-text-main">{t.durationMs}ms</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Tab 3: Sources */}
              {activeTab === 'sources' && (
                <div className="tab-content">
                  <div className="sources-grid grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    {sources.map((s) => (
                      <div key={s.id} className="source-card bg-bg-primary border border-border-color rounded-lg p-4">
                        <div className="source-header flex items-center justify-between mb-2">
                          <span className="source-num text-xs font-medium bg-bg-primary/50 px-2 py-1 rounded">[{s.sourceNumber}]</span>
                          <span className="source-domain text-xs font-medium text-text-muted">{s.domain}</span>
                        </div>
                        <a href={s.url} target="_blank" rel="noreferrer" className="source-title block text-sm font-medium text-primary hover:text-primary-hover mb-2">
                          {s.title || s.url}
                        </a>
                        <p className="source-snippet text-xs text-text-muted">{s.snippet}</p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Tab 4: Claims Corroboration */}
              {activeTab === 'claims' && (
                <div className="tab-content">
                  <div className="claims-list flex flex-col gap-3">
                    {claims.map((c) => (
                      <div key={c.id} className={`claim-card p-4 bg-bg-primary rounded-lg border-l-4 ${c.status.toLowerCase() === 'verified' ? 'border-accent-green' : c.status.toLowerCase() === 'unverified' ? 'border-accent-amber' : 'border-accent-red'}`}>
                        <div className="claim-header flex items-start gap-3 mb-2">
                          <span className={`claim-badge px-2 py-1 text-xs font-medium rounded ${c.status.toLowerCase() === 'verified' ? 'bg-accent-green/20 text-accent-green' : c.status.toLowerCase() === 'unverified' ? 'bg-accent-amber/20 text-accent-amber' : 'bg-accent-red/20 text-accent-red'}`}>
                            {c.status}
                          </span>
                          {c.sourceDomain && <span className="claim-domain text-xs font-medium text-text-muted">Domain: {c.sourceDomain}</span>}
                        </div>
                        <p className="claim-statement text-xs font-medium text-text-main">{c.statement}</p>
                        {c.exactQuote && (
                          <blockquote className="claim-quote mt-2 pl-4 border-l-2 border-accent-amber italic text-xs text-text-muted">
                            "{c.exactQuote}"
                          </blockquote>
                        )}
                        {c.supportingDomains && (
                          <div className="supporting-domains mt-2 text-xs font-medium text-text-muted">
                            Corroborated across: <strong className="font-medium">{c.supportingDomains}</strong>
                          </div>
                        )}
                        <QuoteEvidenceView claim={c} sources={sources} />
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
