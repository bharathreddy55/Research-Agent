package com.researchagent.service;

import com.researchagent.dto.*;
import com.researchagent.entity.*;
import com.researchagent.event.RedisEventPublisher;
import com.researchagent.model.AgentType;
import com.researchagent.model.RunStatus;
import com.researchagent.producer.ResearchJobProducer;
import com.researchagent.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private final RunRepository runRepository;
    private final StepRepository stepRepository;
    private final SourceRepository sourceRepository;
    private final ClaimRepository claimRepository;
    private final ReportRepository reportRepository;
    private final RunPersistenceService persistenceService;
    private final ResearchJobProducer jobProducer;
    private final RateLimiterService rateLimiterService;
    private final RedisEventPublisher eventPublisher;

    public RunService(
            RunRepository runRepository,
            StepRepository stepRepository,
            SourceRepository sourceRepository,
            ClaimRepository claimRepository,
            ReportRepository reportRepository,
            RunPersistenceService persistenceService,
            ResearchJobProducer jobProducer,
            RateLimiterService rateLimiterService,
            RedisEventPublisher eventPublisher) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.sourceRepository = sourceRepository;
        this.claimRepository = claimRepository;
        this.reportRepository = reportRepository;
        this.persistenceService = persistenceService;
        this.jobProducer = jobProducer;
        this.rateLimiterService = rateLimiterService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Enqueues a new research run after rate limit verification.
     */
    @Transactional
    public RunResponse createAndEnqueueRun(CreateRunRequest request, String userId) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : "default-user";

        if (!rateLimiterService.allowRun(effectiveUserId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many concurrent active runs. Please wait for an existing run to complete.");
        }

        String runId = UUID.randomUUID().toString();
        long budgetCap = request.getBudgetCapPaise() > 0 ? request.getBudgetCapPaise() : 1500;
        String depth = request.getDepth() != null ? request.getDepth().toUpperCase() : "STANDARD";

        persistenceService.createRun(runId, effectiveUserId, request.getQuery(), depth, budgetCap);
        jobProducer.enqueueJob(runId, effectiveUserId, request.getQuery(), depth, budgetCap);

        log.info("Successfully created and enqueued run {}", runId);
        return getRun(runId);
    }

    /**
     * Fetches details of a specific run.
     */
    @Transactional(readOnly = true)
    public RunResponse getRun(String runId) {
        RunEntity entity = runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found with id: " + runId));

        RunResponse response = mapToRunResponse(entity);
        reportRepository.findTopByRunIdOrderByVersionDesc(runId)
                .ifPresent(r -> response.setConfidenceScore(r.getConfidenceScore()));

        return response;
    }

    /**
     * Lists runs with pagination.
     */
    @Transactional(readOnly = true)
    public List<RunResponse> listRuns(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("startedAt").descending());
        Page<RunEntity> runPage = runRepository.findAll(pageable);
        return runPage.stream().map(this::mapToRunResponse).collect(Collectors.toList());
    }

    /**
     * Cancels an active run.
     */
    @Transactional
    public RunResponse cancelRun(String runId) {
        RunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found with id: " + runId));

        if ("COMPLETED".equalsIgnoreCase(run.getStatus()) || "FAILED".equalsIgnoreCase(run.getStatus())) {
            log.info("Run {} already finished with status {}", runId, run.getStatus());
            return mapToRunResponse(run);
        }

        run.setStatus(RunStatus.CANCELLED.name());
        run.setFinishedAt(Instant.now());
        run.setFailureReason("User requested cancellation");
        runRepository.save(run);

        eventPublisher.onEvent(runId, RunStatus.CANCELLED, AgentType.SYSTEM,
                "Run cancelled by user", run.getTokensIn(), run.getTokensOut(), run.getCostPaise());

        log.info("Cancelled run {}", runId);
        return mapToRunResponse(run);
    }

    /**
     * Retrieves the latest report for a run (and includes previous draft version if v2, Audit B8).
     */
    @Transactional(readOnly = true)
    public ReportResponse getReport(String runId) {
        List<ReportEntity> reports = reportRepository.findByRunIdOrderByVersionDesc(runId);
        if (reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report found for run id: " + runId);
        }

        ReportEntity latest = reports.get(0);
        ReportResponse response = mapToReportResponse(latest);

        // If version > 1, find the previous draft version (v1) for comparison
        if (latest.getVersion() > 1 && reports.size() > 1) {
            ReportEntity prev = reports.get(reports.size() - 1);
            response.setPreviousDraftContent(prev.getMarkdownContent());
        }

        return response;
    }

    /**
     * Retrieves execution step traces for audit.
     */
    @Transactional(readOnly = true)
    public List<StepResponse> getSteps(String runId) {
        return stepRepository.findByRunIdOrderBySeqAsc(runId).stream()
                .map(this::mapToStepResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves discovered sources.
     */
    @Transactional(readOnly = true)
    public List<SourceEntity> getSources(String runId) {
        return sourceRepository.findByRunIdOrderBySourceNumberAsc(runId);
    }

    /**
     * Retrieves extracted and verified claims.
     */
    @Transactional(readOnly = true)
    public List<ClaimEntity> getClaims(String runId) {
        return claimRepository.findByRunId(runId);
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private RunResponse mapToRunResponse(RunEntity entity) {
        RunResponse resp = new RunResponse();
        resp.setId(entity.getId());
        resp.setUserId(entity.getUserId());
        resp.setQuery(entity.getQuery());
        resp.setDepth(entity.getDepth());
        resp.setStatus(entity.getStatus());
        resp.setBudgetCapPaise(entity.getBudgetCapPaise());
        resp.setCostPaise(entity.getCostPaise());
        resp.setTokensIn(entity.getTokensIn());
        resp.setTokensOut(entity.getTokensOut());
        resp.setStartedAt(entity.getStartedAt());
        resp.setFinishedAt(entity.getFinishedAt());
        resp.setFailureReason(entity.getFailureReason());
        return resp;
    }

    private ReportResponse mapToReportResponse(ReportEntity entity) {
        ReportResponse resp = new ReportResponse();
        resp.setId(entity.getId());
        resp.setRunId(entity.getRunId());
        resp.setQuery(entity.getQuery());
        resp.setTitle(entity.getTitle());
        resp.setMarkdownContent(entity.getMarkdownContent());
        resp.setPdfPath(entity.getPdfPath());
        resp.setVersion(entity.getVersion());
        resp.setConfidenceScore(entity.getConfidenceScore());
        resp.setTotalSources(entity.getTotalSources());
        resp.setVerifiedClaimsCount(entity.getVerifiedClaimsCount());
        resp.setUnverifiedClaimsCount(entity.getUnverifiedClaimsCount());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }

    private StepResponse mapToStepResponse(StepEntity entity) {
        StepResponse resp = new StepResponse();
        resp.setId(entity.getId());
        resp.setRunId(entity.getRunId());
        resp.setSeq(entity.getSeq());
        resp.setAgent(entity.getAgent());
        resp.setAction(entity.getAction());
        resp.setPrompt(entity.getPrompt());
        resp.setResponse(entity.getResponse());
        resp.setTokensIn(entity.getTokensIn());
        resp.setTokensOut(entity.getTokensOut());
        resp.setCostPaise(entity.getCostPaise());
        resp.setDurationMs(entity.getDurationMs());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }
}
