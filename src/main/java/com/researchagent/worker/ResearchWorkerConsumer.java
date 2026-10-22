package com.researchagent.worker;

import com.researchagent.agent.ResearchOrchestrator;
import com.researchagent.config.RabbitMQConfig;
import com.researchagent.dto.ResearchJobMessage;
import com.researchagent.event.RedisEventPublisher;
import com.researchagent.model.ResearchDepth;
import com.researchagent.model.Run;
import com.researchagent.model.RunStatus;
import com.researchagent.model.AgentType;
import com.researchagent.execution.RunControl;
import com.researchagent.execution.RunCancelledException;
import com.researchagent.service.DailySpendCapService;
import com.researchagent.service.RunPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer that drives the 7-agent research pipeline.
 *
 * <h3>Message flow</h3>
 * <pre>
 * Producer → research.jobs.queue
 *                  ↓
 *          ResearchWorkerConsumer   (this class)
 *                  ↓
 *          ResearchOrchestrator.executeRun(...)
 *                  ↓ (onEvent callbacks)
 *          RedisEventPublisher → run:{runId}:events  (consumed by SSE in Sprint 3)
 *                  ↓ (after completion)
 *          RunPersistenceService → PostgreSQL
 * </pre>
 *
 * <h3>Retry &amp; DLQ</h3>
 * If this method throws an unchecked exception, Spring AMQP retries the message
 * up to {@code spring.rabbitmq.listener.simple.retry.max-attempts} times (default 3,
 * configured in application.yml), then routes the message to the dead-letter queue
 * {@code research.dlq} via the {@code research.dlx} exchange.
 *
 * <h3>Crash recovery</h3>
 * On startup the worker will re-process any messages that were re-queued or
 * placed in the DLQ before a crash (handled in Sprint 3 via a startup listener).
 */
@Component
public class ResearchWorkerConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResearchWorkerConsumer.class);

    private final ResearchOrchestrator orchestrator;
    private final RunPersistenceService persistenceService;
    private final RedisEventPublisher eventPublisher;
    private final DailySpendCapService dailySpendCapService;

    public ResearchWorkerConsumer(ResearchOrchestrator orchestrator,
                                  RunPersistenceService persistenceService,
                                  RedisEventPublisher eventPublisher,
                                  DailySpendCapService dailySpendCapService) {
        this.orchestrator = orchestrator;
        this.persistenceService = persistenceService;
        this.eventPublisher = eventPublisher;
        this.dailySpendCapService = dailySpendCapService;
    }

    /**
     * Processes a single research job from the RabbitMQ queue.
     *
     * <p>The method is intentionally synchronous — one job runs end-to-end per
     * consumer thread.  Horizontal scaling is achieved by increasing the
     * {@code spring.rabbitmq.listener.simple.concurrency} setting or by adding
     * more worker instances.</p>
     *
     * @param message the deserialized job message from {@code research.jobs.queue}
     */
    @RabbitListener(queues = RabbitMQConfig.RESEARCH_JOBS_QUEUE)
    public void handleResearchJob(ResearchJobMessage message) {
        String runId = message.getRunId();
        log.info("Worker received research job for run {} — query: {}", runId, message.getQuery());

        if (!persistenceService.tryStartRun(runId)) {
            log.info("Skipping run {}: not queued (cancelled, terminal, or already claimed)", runId);
            return;
        }

        // Check global daily spend cap (Audit B4)
        if (dailySpendCapService != null && dailySpendCapService.wouldExceedGlobalDailyCap(message.getBudgetCapPaise())) {
            String reason = "Global daily spend cap exceeded. Run rejected.";
            log.warn("Run {} rejected: {}", runId, reason);
            persistenceService.failRun(runId, reason);
            if (!persistenceService.isCancellationRequested(runId)) {
                eventPublisher.onEvent(runId, RunStatus.FAILED, AgentType.SYSTEM, reason, 0, 0, 0);
            }
            return;
        }

        try {
            ResearchDepth depth = parseDepth(message.getDepth());

            // 2. Execute the full 7-agent pipeline with the worker-assigned runId.
            //    eventPublisher is our RunEventListener — each agent step fires onEvent(),
            //    which publishes to Redis run:{runId}:events for SSE forwarding.
            Run completedRun;
            try (RunControl.Scope ignored = RunControl.open(() -> persistenceService.isCancellationRequested(runId))) {
                RunControl.checkActive();
                completedRun = orchestrator.executeRun(
                        runId, message.getQuery(), depth, message.getBudgetCapPaise(),
                        (id, status, agent, text, in, out, cost) -> {
                            // Terminal events belong to the worker after persistence commits.
                            if (status != RunStatus.COMPLETED && status != RunStatus.FAILED && status != RunStatus.CANCELLED) {
                                RunControl.checkActive();
                                eventPublisher.onEvent(id, status, agent, text, in, out, cost);
                            }
                        });
            }

            RunStatus storedStatus = persistenceService.persistRunResult(runId, completedRun);
            String messageText = storedStatus == RunStatus.CANCELLED
                    ? "Run cancelled. Any cost from an already-started provider call is included when available."
                    : storedStatus == RunStatus.COMPLETED ? "Research completed. Report is available."
                    : "Research failed: " + completedRun.getFailureReason();
            eventPublisher.onEvent(runId, storedStatus, AgentType.SYSTEM, messageText,
                    completedRun.getTokensIn(), completedRun.getTokensOut(), completedRun.getCostPaise());
            log.info("Run {} finished with {} (cost {} paise)", runId, storedStatus, completedRun.getCostPaise());

        } catch (RunCancelledException e) {
            // Cancellation may win after claim but before the orchestrator starts.
            persistenceService.persistRunResult(runId, Run.builder().id(runId).status(RunStatus.CANCELLED)
                    .failureReason("User requested cancellation").build());
            log.info("Run {} cancelled before further work could start", runId);
        } catch (Exception e) {
            // Persist failure unless cancellation/another terminal transition won.
            log.error("Unhandled exception processing run {}: {}", runId, e.getMessage(), e);
            try {
                persistenceService.failRun(runId, e.getMessage());
                if (persistenceService.isCancellationRequested(runId)) {
                    log.info("Run {} was cancelled while an operation failed; acknowledging without retry", runId);
                    return;
                }
            } catch (Exception dbEx) {
                log.error("Failed to persist FAILED status for run {}: {}", runId, dbEx.getMessage());
            }
            // Ask AMQP to redeliver on error; terminal rows will not execute again.
            throw new RuntimeException("Research job failed for runId=" + runId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResearchDepth parseDepth(String depthStr) {
        if (depthStr == null) return ResearchDepth.STANDARD;
        try {
            return ResearchDepth.valueOf(depthStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown depth '{}', defaulting to STANDARD", depthStr);
            return ResearchDepth.STANDARD;
        }
    }
}
