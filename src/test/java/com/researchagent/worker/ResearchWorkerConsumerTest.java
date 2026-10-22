package com.researchagent.worker;

import com.researchagent.agent.ResearchOrchestrator;
import com.researchagent.dto.ResearchJobMessage;
import com.researchagent.event.RedisEventPublisher;
import com.researchagent.model.*;
import com.researchagent.service.DailySpendCapService;
import com.researchagent.service.RunPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests ResearchWorkerConsumer using hand-rolled anonymous-class stubs.
 * (Mockito inline mocking of concrete classes fails on JDK 25.)
 */
class ResearchWorkerConsumerTest {

    // -------------------------------------------------------------------------
    // Captured calls for assertion
    // -------------------------------------------------------------------------

    private final List<String> statusUpdates    = new ArrayList<>();
    private final List<String> failedRuns       = new ArrayList<>();
    private final AtomicBoolean orchestratorCalled = new AtomicBoolean(false);
    private final AtomicReference<String> executedRunId = new AtomicReference<>();
    private final AtomicBoolean dailyCapWouldExceed = new AtomicBoolean(false);
    private final AtomicBoolean canClaim = new AtomicBoolean(true);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<String> executionOrder = new ArrayList<>();

    // The Run object the stub orchestrator will return
    private Run stubbedRun;

    // -------------------------------------------------------------------------
    // Stubs
    // -------------------------------------------------------------------------

    private ResearchOrchestrator stubOrchestrator;
    private RunPersistenceService stubPersistenceService;
    private RedisEventPublisher stubEventPublisher;
    private DailySpendCapService stubDailySpendCapService;
    private ResearchWorkerConsumer workerConsumer;

    @BeforeEach
    void setUp() {
        statusUpdates.clear();
        failedRuns.clear();
        orchestratorCalled.set(false);
        executedRunId.set(null);
        dailyCapWouldExceed.set(false);
        canClaim.set(true); cancelled.set(false); executionOrder.clear();

        // Stub orchestrator — overrides the 5-arg executeRun
        stubOrchestrator = new ResearchOrchestrator(null, null, null, null, null, null, null, null, "./reports") {
            @Override
            public Run executeRun(String runId, String query, ResearchDepth depth,
                                  long budgetCapPaise, com.researchagent.agent.RunEventListener listener) {
                orchestratorCalled.set(true);
                executedRunId.set(runId);
                return stubbedRun;
            }
        };

        // Stub persistence — records what was persisted
        stubPersistenceService = new RunPersistenceService(null, null, null, null, null) {
            @Override public boolean tryStartRun(String runId) {
                if (!canClaim.get()) return false;
                statusUpdates.add(runId + ":RUNNING"); return true;
            }
            @Override public boolean isCancellationRequested(String runId) { return cancelled.get(); }
            @Override public RunStatus persistRunResult(String runId, Run result) {
                executionOrder.add("persist");
                RunStatus status = cancelled.get() ? RunStatus.CANCELLED : result.getStatus();
                statusUpdates.add(runId + ":" + status.name()); return status;
            }
            @Override
            public void updateRunStatus(String runId, RunStatus status) {
                statusUpdates.add(runId + ":" + status.name());
            }
            @Override
            public void updateRunCost(String runId, long totalCostPaise, int tokensIn, int tokensOut) { /* no-op */ }
            @Override
            public void saveStep(String runId, StepTrace trace) { /* no-op */ }
            @Override
            public void saveSources(String runId, List<Source> sources) { /* no-op */ }
            @Override
            public void saveClaims(String runId, List<Claim> claims) { /* no-op */ }
            @Override
            public void saveReport(String runId, ResearchReport report) { /* no-op */ }
            @Override
            public void failRun(String runId, String reason) {
                failedRuns.add(runId);
            }
        };

        // Stub event publisher — no-op (just needs to not throw)
        stubEventPublisher = new RedisEventPublisher(null, new com.fasterxml.jackson.databind.ObjectMapper()) {
            @Override
            public void onEvent(String runId, RunStatus status, AgentType agent, String message,
                                int tokensIn, int tokensOut, long costPaise) { executionOrder.add("event:" + status); }
        };

        // Stub daily spend cap service
        stubDailySpendCapService = new DailySpendCapService(null) {
            @Override
            public boolean wouldExceedGlobalDailyCap(long additionalPaise) {
                return dailyCapWouldExceed.get();
            }
        };

        workerConsumer = new ResearchWorkerConsumer(
                stubOrchestrator, stubPersistenceService, stubEventPublisher, stubDailySpendCapService);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should execute orchestrator and persist run when message received")
    void shouldProcessResearchJobSuccessfully() {
        ResearchJobMessage message = new ResearchJobMessage(
                "run-100", "user-1", "Quantum computing 2026", "QUICK", 1500);

        dailyCapWouldExceed.set(false);

        stubbedRun = Run.builder()
                .id("run-100")
                .query("Quantum computing 2026")
                .depth(ResearchDepth.QUICK)
                .budgetCapPaise(1500)
                .status(RunStatus.COMPLETED)
                .costPaise(12)
                .tokensIn(500)
                .tokensOut(250)
                .steps(List.of(StepTrace.builder().seq(1).agent(AgentType.PLANNER).build()))
                .sources(List.of(Source.builder().id(1).url("https://nature.com/qc").build()))
                .claims(List.of(Claim.builder().id("c1").statement("Claim 1").status(ClaimStatus.VERIFIED).build()))
                .report(ResearchReport.builder().query("Quantum computing 2026").markdownContent("# Report").build())
                .finishedAt(Instant.now())
                .build();

        workerConsumer.handleResearchJob(message);

        assertThat(orchestratorCalled.get()).isTrue();
        assertThat(executedRunId.get()).isEqualTo("run-100");
        assertThat(statusUpdates).contains("run-100:RUNNING", "run-100:COMPLETED");
    }

    @Test
    @DisplayName("Should reject job when daily spend cap would be exceeded")
    void shouldRejectJobWhenDailyCapExceeded() {
        ResearchJobMessage message = new ResearchJobMessage(
                "run-200", "user-1", "Query", "DEEP", 2500);

        dailyCapWouldExceed.set(true);

        workerConsumer.handleResearchJob(message);

        assertThat(orchestratorCalled.get()).isFalse();
        assertThat(failedRuns).contains("run-200");
        // RUNNING was set before the cap check
        assertThat(statusUpdates).contains("run-200:RUNNING");
    }

    @Test
    @DisplayName("Should mark run FAILED and re-throw when orchestrator throws")
    void shouldFailRunWhenOrchestratorThrows() {
        ResearchJobMessage message = new ResearchJobMessage(
                "run-300", "user-1", "Query that fails", "STANDARD", 1500);

        dailyCapWouldExceed.set(false);

        stubOrchestrator = new ResearchOrchestrator(null, null, null, null, null, null, null, null, "./reports") {
            @Override
            public Run executeRun(String runId, String query, ResearchDepth depth,
                                  long budgetCapPaise, com.researchagent.agent.RunEventListener listener) {
                throw new RuntimeException("LLM API timeout");
            }
        };
        workerConsumer = new ResearchWorkerConsumer(
                stubOrchestrator, stubPersistenceService, stubEventPublisher, stubDailySpendCapService);

        assertThatThrownBy(() -> workerConsumer.handleResearchJob(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("run-300");

        assertThat(failedRuns).contains("run-300");
    }
    @Test
    void cancelledQueuedJobIsAcknowledgedWithoutStartingThePipeline() {
        canClaim.set(false);
        workerConsumer.handleResearchJob(new ResearchJobMessage("queued-cancel", "user-1", "Query", "QUICK", 1500));
        assertThat(orchestratorCalled.get()).isFalse();
        assertThat(statusUpdates).isEmpty();
        assertThat(executionOrder).isEmpty();
    }

    @Test
    void cancellationAfterClaimStopsBeforeThePipelineAndDoesNotRetry() {
        cancelled.set(true);
        workerConsumer.handleResearchJob(new ResearchJobMessage("claimed-cancel", "user-1", "Query", "QUICK", 1500));
        assertThat(orchestratorCalled.get()).isFalse();
        assertThat(statusUpdates).contains("claimed-cancel:CANCELLED");
        assertThat(failedRuns).isEmpty();
    }

    @Test
    void terminalEventWaitsForPersistenceAndReflectsCancellationWinningTheRace() {
        ResearchOrchestrator finishing = new ResearchOrchestrator(null, null, null, null, null, null, null, null, "./reports") {
            @Override public Run executeRun(String id, String query, ResearchDepth depth, long budget,
                    com.researchagent.agent.RunEventListener listener) {
                listener.onEvent(id, RunStatus.COMPLETED, AgentType.SYSTEM, "premature completion", 0, 0, 0);
                cancelled.set(true);
                return Run.builder().id(id).status(RunStatus.COMPLETED).build();
            }
        };
        new ResearchWorkerConsumer(finishing, stubPersistenceService, stubEventPublisher, stubDailySpendCapService)
                .handleResearchJob(new ResearchJobMessage("finish-race", "user-1", "Query", "QUICK", 1500));
        assertThat(executionOrder).containsExactly("persist", "event:CANCELLED");
        assertThat(statusUpdates).doesNotContain("finish-race:COMPLETED");
    }

    @Test
    void providerFailureAfterCancellationDoesNotTriggerJobRetry() {
        ResearchOrchestrator failing = new ResearchOrchestrator(null, null, null, null, null, null, null, null, "./reports") {
            @Override public Run executeRun(String id, String query, ResearchDepth depth, long budget,
                    com.researchagent.agent.RunEventListener listener) {
                cancelled.set(true);
                throw new RuntimeException("In-flight provider call failed");
            }
        };
        new ResearchWorkerConsumer(failing, stubPersistenceService, stubEventPublisher, stubDailySpendCapService)
                .handleResearchJob(new ResearchJobMessage("failure-cancel", "user-1", "Query", "QUICK", 1500));
        assertThat(executionOrder).doesNotContain("event:FAILED", "event:COMPLETED");
    }

}
