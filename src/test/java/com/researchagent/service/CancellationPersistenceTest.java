package com.researchagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.entity.RunEntity;
import com.researchagent.event.RedisEventPublisher;
import com.researchagent.model.*;
import com.researchagent.repository.*;
import com.researchagent.agent.*;
import com.researchagent.llm.LlmService;
import com.researchagent.tools.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@Import(RunPersistenceService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CancellationPersistenceTest {
    @Autowired RunRepository runs;
    @Autowired StepRepository steps;
    @Autowired SourceRepository sources;
    @Autowired ClaimRepository claims;
    @Autowired ReportRepository reports;
    @Autowired RunPersistenceService persistence;
    @Autowired PlatformTransactionManager transactions;
    private RunService api;
    private final List<RunStatus> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setup() {
        events.clear();
        RedisEventPublisher publisher = new RedisEventPublisher(null, new ObjectMapper()) {
            @Override public void onEvent(String id, RunStatus status, AgentType agent, String message, int in, int out, long cost) {
                // afterCommit must expose the same status to a separate DB read.
                assertThat(runs.findStatusById(id)).contains(status.name());
                events.add(status);
            }
        };
        api = new RunService(runs, steps, sources, claims, reports, persistence, null, null, publisher);
        authenticate();
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void actualWorkerObservesPersistedCancellationDuringACallAndKeepsItsUsage() {
        String id = create();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        CostTracker tracker = new CostTracker();
        LlmService llm = new LlmService("openai", "", "", "test-key", "gpt-4o-mini", 0.2, tracker);
        llm.setCustomChatModel(messages -> {
            calls.incrementAndGet();
            String stage = ((SystemMessage) messages.get(0)).text();
            if (stage.contains("PLANNER")) {
                return Response.from(AiMessage.from("{\"subQuestions\":[\"Question\"],\"searchQueries\":[\"Question\"],\"rationale\":\"Test\"}"), new TokenUsage(100, 50));
            }
            // Real API cancellation transaction while the first reader call is in flight.
            cancel(id);
            return Response.from(AiMessage.from("{\"claims\":[]}"), new TokenUsage(100, 50));
        });
        SearchService search = (query, max) -> List.of(
                SearchResult.builder().url("https://example.org/a").domain("example.org").title("A").build(),
                SearchResult.builder().url("https://example.net/b").domain("example.net").title("B").build());
        SafeWebReader web = new SafeWebReader() {
            @Override public ExtractedPage readUrl(String url) {
                reads.incrementAndGet(); return new ExtractedPage(url, "Test", "Test evidence", true, null);
            }
        };
        ObjectMapper mapper = new ObjectMapper();
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(new PlannerAgent(llm, mapper),
                new SearcherAgent(search), new ReaderAgent(web, llm, mapper), new VerifierAgent(llm, mapper),
                new WriterAgent(llm), new ReflectionCriticAgent(llm, mapper, 1), new CitationValidator(), tracker, "target/cancellation-reports");
        RedisEventPublisher publisher = new RedisEventPublisher(null, mapper) {
            @Override public void onEvent(String runId, RunStatus status, AgentType agent, String message, int in, int out, long cost) {
                events.add(status);
            }
        };
        DailySpendCapService cap = new DailySpendCapService(null) {
            @Override public boolean wouldExceedGlobalDailyCap(long extra) { return false; }
        };
        new com.researchagent.worker.ResearchWorkerConsumer(orchestrator, persistence, publisher, cap)
                .handleResearchJob(new com.researchagent.dto.ResearchJobMessage(id, "alice", "Question", "QUICK", 1500));
        RunEntity stored = runs.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("CANCELLED");
        assertThat(stored.getTokensIn()).isEqualTo(200);
        assertThat(stored.getTokensOut()).isEqualTo(100);
        assertThat(stored.getCostPaise()).isPositive();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(reads.get()).isEqualTo(1);
        assertThat(reports.findByRunIdOrderByVersionDesc(id)).isEmpty();
        assertThat(events).contains(RunStatus.CANCELLED).doesNotContain(RunStatus.COMPLETED, RunStatus.FAILED);
        assertThat(persistence.tryStartRun(id)).isFalse();
    }

    @Test
    void recoveryConditionalUpdateOnlyFailsAnActiveRun() {
        String id = create();
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                assertThat(runs.failIfActive(id, "restart", Instant.now())).isEqualTo(1));
        assertThat(runs.findStatusById(id)).contains("FAILED");
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                assertThat(runs.failIfActive(id, "another restart", Instant.now())).isZero());
        assertThat(runs.findById(id).orElseThrow().getFailureReason()).isEqualTo("restart");
    }

    @Test
    void aMessageWhoseRowIsNotCommittedMustRetryInsteadOfBeingDropped() {
        assertThatThrownBy(() -> persistence.tryStartRun("not-visible-yet"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not yet available");
    }

    @Test
    void completionEventCanImmediatelyReadTheCommittedReport() {
        String id = create();
        java.util.concurrent.atomic.AtomicInteger completions = new java.util.concurrent.atomic.AtomicInteger();
        com.researchagent.agent.ResearchOrchestrator orchestrator = new com.researchagent.agent.ResearchOrchestrator(
                null, null, null, null, null, null, null, null, "./target/reports") {
            @Override public Run executeRun(String runId, String query, ResearchDepth depth, long budget,
                    com.researchagent.agent.RunEventListener listener) {
                listener.onEvent(runId, RunStatus.COMPLETED, AgentType.SYSTEM, "too early", 0, 0, 0);
                return completed(runId);
            }
        };
        RedisEventPublisher committedEvent = new RedisEventPublisher(null, new ObjectMapper()) {
            @Override public void onEvent(String runId, RunStatus status, AgentType agent, String message, int in, int out, long cost) {
                if (status == RunStatus.COMPLETED) {
                    assertThat(api.getReport(runId).getMarkdownContent()).isEqualTo("# Final report");
                    assertThat(runs.findStatusById(runId)).contains("COMPLETED");
                    completions.incrementAndGet();
                }
            }
        };
        DailySpendCapService cap = new DailySpendCapService(null) {
            @Override public boolean wouldExceedGlobalDailyCap(long extra) { return false; }
        };
        new com.researchagent.worker.ResearchWorkerConsumer(orchestrator, persistence, committedEvent, cap)
                .handleResearchJob(new com.researchagent.dto.ResearchJobMessage(id, "alice", "Question", "QUICK", 1500));
        assertThat(completions.get()).isEqualTo(1);
    }

    @Test
    void queuedCancellationIsIdempotentAndCannotBeClaimed() {
        String id = create();
        cancel(id); cancel(id);
        assertThat(persistence.tryStartRun(id)).isFalse();
        assertThat(persistence.isCancellationRequested(id)).isTrue();
        assertThat(events).containsExactly(RunStatus.CANCELLED);
    }

    @Test
    void cancelledRunKeepsStatusAndInFlightUsageWithoutSavingAReport() {
        String id = create();
        assertThat(persistence.tryStartRun(id)).isTrue();
        assertThat(persistence.tryStartRun(id)).isFalse();
        cancel(id);
        assertThat(persistence.persistRunResult(id, completed(id))).isEqualTo(RunStatus.CANCELLED);
        persistence.updateRunStatus(id, RunStatus.COMPLETED);
        persistence.updateRunStatus(id, RunStatus.RUNNING);
        persistence.failRun(id, "late provider failure");
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                assertThat(runs.failIfActive(id, "stale startup recovery", Instant.now())).isZero());
        RunEntity stored = runs.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("CANCELLED");
        assertThat(stored.getCostPaise()).isEqualTo(12);
        assertThat(stored.getTokensIn()).isEqualTo(100);
        assertThat(stored.getTokensOut()).isEqualTo(50);
        assertThat(reports.findByRunIdOrderByVersionDesc(id)).isEmpty();
        assertThat(stored.getFailureReason()).isEqualTo("User requested cancellation");
    }

    @Test
    void completionAndCancellationRaceRespectTheFirstCommittedTerminalState() throws Exception {
        for (boolean cancelFirst : new boolean[]{true, false}) {
            String id = create();
            assertThat(persistence.tryStartRun(id)).isTrue();
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<?> first = workers.submit(() -> {
                    authenticate();
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                            runs.findByIdForUpdate(id).orElseThrow();
                            locked.countDown();
                            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Race release timed out"); }
                            catch (InterruptedException ex) { throw new AssertionError(ex); }
                            if (cancelFirst) api.cancelRun(id); else persistence.persistRunResult(id, completed(id));
                        });
                    } finally { SecurityContextHolder.clearContext(); }
                });
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> second = workers.submit(() -> {
                    authenticate();
                    try {
                        secondStarted.countDown();
                        if (cancelFirst) persistence.persistRunResult(id, completed(id)); else cancel(id);
                    } finally { SecurityContextHolder.clearContext(); }
                });
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
                assertThat(runs.findStatusById(id)).contains(cancelFirst ? "CANCELLED" : "COMPLETED");
                assertThat(reports.findByRunIdOrderByVersionDesc(id)).hasSize(cancelFirst ? 0 : 1);
            } finally {
                release.countDown(); workers.shutdownNow();
                assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private String create() {
        String id = UUID.randomUUID().toString();
        persistence.createRun(id, "alice", "Cancellation check", "QUICK", 1500);
        return id;
    }

    private void cancel(String id) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> api.cancelRun(id));
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));
    }

    private static Run completed(String id) {
        return Run.builder().id(id).status(RunStatus.COMPLETED).costPaise(12).tokensIn(100).tokensOut(50)
                .finishedAt(Instant.now()).report(ResearchReport.builder().query("Cancellation check")
                        .title("Finished report").markdownContent("# Final report").build()).build();
    }
}
