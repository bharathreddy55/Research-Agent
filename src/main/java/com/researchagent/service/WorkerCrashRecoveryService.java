package com.researchagent.service;

import com.researchagent.entity.RunEntity;
import com.researchagent.entity.StepEntity;
import com.researchagent.model.RunStatus;
import com.researchagent.repository.RunRepository;
import com.researchagent.repository.StepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Worker crash recovery listener (PRD §5 & Audit B5).
 *
 * <p>On application startup, detects any runs that were left in an active, non-terminal
 * state due to an unexpected process termination/crash. Inspects the last recorded step
 * and cleanly marks them as FAILED so clients and the database state stay consistent.</p>
 */
@Service
public class WorkerCrashRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(WorkerCrashRecoveryService.class);

    private static final Set<String> NON_TERMINAL_STATUSES = Set.of(
            RunStatus.PENDING.name(),
            RunStatus.QUEUED.name(),
            RunStatus.RUNNING.name(),
            RunStatus.PLANNING.name(),
            RunStatus.SEARCHING.name(),
            RunStatus.READING.name(),
            RunStatus.VERIFYING.name(),
            RunStatus.WRITING.name(),
            RunStatus.REFLECTING.name(),
            RunStatus.VALIDATING_CITATIONS.name()
    );

    private final RunRepository runRepository;
    private final StepRepository stepRepository;

    public WorkerCrashRecoveryService(RunRepository runRepository, StepRepository stepRepository) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverDanglingRunsOnStartup() {
        log.info("Checking for dangling/interrupted runs from prior worker sessions...");

        List<RunEntity> allRuns = runRepository.findAll();
        int recoveredCount = 0;

        for (RunEntity run : allRuns) {
            if (NON_TERMINAL_STATUSES.contains(run.getStatus())) {
                List<StepEntity> steps = stepRepository.findByRunIdOrderBySeqAsc(run.getId());
                String lastStepInfo = steps.isEmpty() ? "before first step"
                        : "after step " + steps.get(steps.size() - 1).getSeq() + " (" + steps.get(steps.size() - 1).getAgent() + ")";

                String reason = "Run interrupted by worker restart (" + lastStepInfo + ")";
                log.warn("Recovering dangling run {} (last status: {}) -> Marking FAILED: {}", run.getId(), run.getStatus(), reason);

                run.setStatus(RunStatus.FAILED.name());
                run.setFailureReason(reason);
                run.setFinishedAt(Instant.now());
                runRepository.save(run);
                recoveredCount++;
            }
        }

        if (recoveredCount > 0) {
            log.info("Crash recovery complete: marked {} interrupted runs as FAILED", recoveredCount);
        } else {
            log.info("Crash recovery check complete: No dangling runs found.");
        }
    }
}
