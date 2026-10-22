package com.researchagent.service;

import com.researchagent.entity.*;
import com.researchagent.model.*;
import com.researchagent.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists domain model objects to the database and loads them back as JPA
 * entities.  Acts as the anti-corruption layer between the pure-domain agent
 * pipeline and the persistence tier.
 *
 * <p>All public write methods are {@code @Transactional} so that a single
 * agent-step save either fully commits or fully rolls back.</p>
 */
@Service
public class RunPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(RunPersistenceService.class);

    private final RunRepository runRepository;
    private final StepRepository stepRepository;
    private final SourceRepository sourceRepository;
    private final ClaimRepository claimRepository;
    private final ReportRepository reportRepository;

    public RunPersistenceService(
            RunRepository runRepository,
            StepRepository stepRepository,
            SourceRepository sourceRepository,
            ClaimRepository claimRepository,
            ReportRepository reportRepository) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.sourceRepository = sourceRepository;
        this.claimRepository = claimRepository;
        this.reportRepository = reportRepository;
    }

    // -------------------------------------------------------------------------
    // Run lifecycle
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@link RunEntity} row with PENDING status.
     * Called by the worker before handing off to the orchestrator.
     */
    @Transactional
    public void createRun(String runId, String userId, String query, String depth, long budgetCapPaise) {
        RunEntity entity = new RunEntity();
        entity.setId(runId);
        entity.setUserId(userId);
        entity.setQuery(query);
        entity.setDepth(depth);
        entity.setBudgetCapPaise(budgetCapPaise);
        entity.setStatus(RunStatus.PENDING.name());
        entity.setStartedAt(Instant.now());
        runRepository.save(entity);
        log.info("Created run {} in DB (status=PENDING)", runId);
    }

    /** Updates the status column for an existing run. */
    @Transactional
    public void updateRunStatus(String runId, RunStatus status) {
        Optional<RunEntity> opt = runRepository.findByIdForUpdate(runId);
        if (opt.isEmpty()) {
            log.warn("updateRunStatus: run {} not found", runId);
            return;
        }
        RunEntity entity = opt.get();
        if (isTerminal(entity.getStatus())) return;
        entity.setStatus(status.name());
        if (status == RunStatus.FAILED || status == RunStatus.COMPLETED || status == RunStatus.CANCELLED) {
            entity.setFinishedAt(Instant.now());
        }
        runRepository.save(entity);
        log.debug("Run {} status → {}", runId, status);
    }

    /** Persists the final cost/token counters from a completed run. */
    @Transactional
    public void updateRunCost(String runId, long totalCostPaise, int tokensIn, int tokensOut) {
        runRepository.findByIdForUpdate(runId).ifPresent(entity -> {
            entity.setCostPaise(Math.max(entity.getCostPaise(), totalCostPaise));
            entity.setTokensIn(Math.max(entity.getTokensIn(), tokensIn));
            entity.setTokensOut(Math.max(entity.getTokensOut(), tokensOut));
            runRepository.save(entity);
        });
    }

    /** Marks a run FAILED with the given reason. */
    @Transactional
    public void failRun(String runId, String reason) {
        runRepository.findByIdForUpdate(runId).ifPresent(entity -> {
            if (isTerminal(entity.getStatus())) return;
            entity.setStatus(RunStatus.FAILED.name());
            entity.setFailureReason(truncate(reason, 1000));
            entity.setFinishedAt(Instant.now());
            runRepository.save(entity);
        });
    }

    /** Atomically claim a queued job. Cancelled, terminal, and duplicate jobs are skipped. */
    @Transactional
    public boolean tryStartRun(String runId) {
        Optional<RunEntity> found = runRepository.findByIdForUpdate(runId);
        // Publication currently precedes the API transaction's commit. Retry a
        // missing row instead of acknowledging a message whose row is not visible yet.
        if (found.isEmpty()) throw new IllegalStateException("Run is not yet available: " + runId);
        RunEntity entity = found.get();
        if (!RunStatus.PENDING.name().equals(entity.getStatus()) && !RunStatus.QUEUED.name().equals(entity.getStatus())) return false;
        entity.setStatus(RunStatus.RUNNING.name());
        runRepository.save(entity);
        return true;
    }

    /** Scalar query in a fresh transaction: do not reuse an old managed entity's status. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean isCancellationRequested(String runId) {
        String status = runRepository.findStatusById(runId)
                .orElseThrow(() -> new IllegalStateException("Run no longer exists: " + runId));
        return RunStatus.CANCELLED.name().equals(status);
    }

    /** Final artifacts and terminal status commit together while holding the same
     * row lock used by cancellation. A cancellation that wins keeps its status. */
    @Transactional
    public RunStatus persistRunResult(String runId, Run result) {
        RunEntity entity = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalStateException("Run no longer exists: " + runId));
        if (RunStatus.COMPLETED.name().equals(entity.getStatus()) || RunStatus.FAILED.name().equals(entity.getStatus())) {
            return RunStatus.valueOf(entity.getStatus());
        }
        entity.setCostPaise(Math.max(entity.getCostPaise(), result.getCostPaise()));
        entity.setTokensIn(Math.max(entity.getTokensIn(), result.getTokensIn()));
        entity.setTokensOut(Math.max(entity.getTokensOut(), result.getTokensOut()));
        if (RunStatus.CANCELLED.name().equals(entity.getStatus())) {
            runRepository.save(entity);
            return RunStatus.CANCELLED;
        }
        if (result.getStatus() == null || !isTerminal(result.getStatus().name())) {
            throw new IllegalArgumentException("Worker result must have a terminal status");
        }
        result.getSteps().forEach(step -> saveStep(runId, step));
        saveSources(runId, result.getSources());
        saveClaims(runId, result.getClaims());
        if (result.getStatus() == RunStatus.COMPLETED && result.getReport() != null) saveReport(runId, result.getReport());
        entity.setStatus(result.getStatus().name());
        entity.setFailureReason(truncate(result.getFailureReason(), 1000));
        entity.setFinishedAt(result.getFinishedAt() != null ? result.getFinishedAt() : Instant.now());
        runRepository.save(entity);
        return result.getStatus();
    }

    private static boolean isTerminal(String status) {
        return RunStatus.CANCELLED.name().equals(status) || RunStatus.COMPLETED.name().equals(status)
                || RunStatus.FAILED.name().equals(status);
    }

    // -------------------------------------------------------------------------
    // Step traces
    // -------------------------------------------------------------------------

    /** Appends a {@link StepTrace} from an agent execution to the steps table. */
    @Transactional
    public void saveStep(String runId, StepTrace trace) {
        StepEntity entity = new StepEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setRunId(runId);
        entity.setSeq(trace.getSeq());
        entity.setAgent(trace.getAgent() != null ? trace.getAgent().name() : "UNKNOWN");
        entity.setAction(truncate(trace.getAction(), 1000));
        entity.setPrompt(truncate(trace.getPrompt(), 4000));
        entity.setResponse(truncate(trace.getResponse(), 4000));
        entity.setTokensIn(trace.getTokensIn());
        entity.setTokensOut(trace.getTokensOut());
        entity.setCostPaise(trace.getCostPaise());
        entity.setDurationMs(trace.getDurationMs());
        entity.setCreatedAt(trace.getCreatedAt() != null ? trace.getCreatedAt() : Instant.now());
        stepRepository.save(entity);
    }

    // -------------------------------------------------------------------------
    // Sources
    // -------------------------------------------------------------------------

    /** Bulk-saves a list of {@link Source} domain objects for a run. */
    @Transactional
    public void saveSources(String runId, List<Source> sources) {
        if (sources == null || sources.isEmpty()) return;
        for (Source s : sources) {
            SourceEntity entity = new SourceEntity();
            entity.setSnapshotId(s.getSnapshotId());
            entity.setRunId(runId);
            entity.setSourceNumber(s.getId());        // 1-indexed citation id
            entity.setUrl(s.getUrl());
            entity.setTitle(truncate(s.getTitle(), 512));
            entity.setSnippet(truncate(s.getSnippet(), 1000));
            entity.setDomain(truncate(s.getDomain(), 128));
            entity.setContent(s.getContent());
            entity.setContentHash(s.getContentHash());
            entity.setFetchedOk(s.isFetchedOk());
            entity.setFetchError(truncate(s.getFetchError(), 500));
            sourceRepository.save(entity);
        }
        log.debug("Saved {} sources for run {}", sources.size(), runId);
    }

    // -------------------------------------------------------------------------
    // Claims
    // -------------------------------------------------------------------------

    /** Bulk-saves verified/unverified {@link Claim} objects for a run. */
    @Transactional
    public void saveClaims(String runId, List<Claim> claims) {
        if (claims == null || claims.isEmpty()) return;
        for (Claim c : claims) {
            ClaimEntity entity = new ClaimEntity();
            entity.setId(c.getId() != null ? c.getId() : UUID.randomUUID().toString());
            entity.setRunId(runId);
            entity.setStatement(c.getStatement());
            entity.setExactQuote(c.getExactQuote());
            entity.setSourceUrl(c.getSourceUrl());
            if (c.getQuoteEvidence() != null) {
                var evidence = c.getQuoteEvidence();
                entity.setSubmittedQuote(evidence.submittedQuote());
                entity.setEvidenceSnapshotId(evidence.sourceSnapshotId());
                entity.setEvidenceContentHash(evidence.sourceContentHash());
                entity.setQuoteStart(evidence.start());
                entity.setQuoteEnd(evidence.end());
                entity.setQuoteMatchStatus(evidence.matchStatus().name());
                entity.setQuoteNormalizationVersion(evidence.normalizationVersion());
            }
            entity.setSourceId(c.getSourceId());
            entity.setSourceDomain(truncate(c.getSourceDomain(), 128));
            entity.setStatus(c.getStatus() != null ? c.getStatus().name() : ClaimStatus.UNVERIFIED.name());
            if (c.getSupportingDomains() != null) {
                entity.setSupportingDomains(String.join(",", c.getSupportingDomains()));
            }
            if (c.getSupportingSourceIds() != null) {
                entity.setSupportingSourceIds(
                        c.getSupportingSourceIds().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(",")));
            }
            entity.setContradictionNotes(truncate(c.getContradictionNotes(), 500));
            claimRepository.save(entity);
        }
        log.debug("Saved {} claims for run {}", claims.size(), runId);
    }

    // -------------------------------------------------------------------------
    // Reports
    // -------------------------------------------------------------------------

    /**
     * Inserts or updates the report for a run.
     * Increments {@code version} on each update (supports draft-diff per audit B8).
     */
    @Transactional
    public void saveReport(String runId, ResearchReport report) {
        Optional<ReportEntity> existing = reportRepository.findTopByRunIdOrderByVersionDesc(runId);
        ReportEntity entity = existing.orElseGet(ReportEntity::new);

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreatedAt(Instant.now());
            entity.setVersion(1);
        } else {
            entity.setVersion(entity.getVersion() + 1);
        }

        entity.setRunId(runId);
        entity.setQuery(report.getQuery() != null ? report.getQuery() : "");
        entity.setTitle(truncate(report.getTitle(), 512));
        entity.setMarkdownContent(report.getMarkdownContent());
        entity.setConfidenceScore(report.getConfidenceScore());
        entity.setTotalSources(report.getTotalSources());
        entity.setVerifiedClaimsCount(report.getVerifiedClaimsCount());
        entity.setUnverifiedClaimsCount(report.getUnverifiedClaimsCount());

        reportRepository.save(entity);
        log.info("Saved report (v{}) for run {}", entity.getVersion(), runId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
