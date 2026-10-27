package com.researchagent.agent;

import com.researchagent.model.AgentType;
import com.researchagent.model.Claim;
import com.researchagent.model.ResearchDepth;
import com.researchagent.model.ResearchPlan;
import com.researchagent.model.ResearchReport;
import com.researchagent.model.Run;
import com.researchagent.model.RunStatus;
import com.researchagent.model.Source;
import com.researchagent.model.StepTrace;
import com.researchagent.tools.CostTracker;
import com.researchagent.execution.RunControl;
import com.researchagent.execution.RunCancelledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ResearchOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ResearchOrchestrator.class);

    private final PlannerAgent plannerAgent;
    private final SearcherAgent searcherAgent;
    private final ReaderAgent readerAgent;
    private final VerifierAgent verifierAgent;
    private final WriterAgent writerAgent;
    private final ReflectionCriticAgent reflectionCriticAgent;
    private final CitationValidator citationValidator;
    private final CostTracker costTracker;

    private final String reportsOutputDir;
    private final Map<String, AtomicBoolean> cancellationTokens = new ConcurrentHashMap<>();

    public ResearchOrchestrator(
            PlannerAgent plannerAgent,
            SearcherAgent searcherAgent,
            ReaderAgent readerAgent,
            VerifierAgent verifierAgent,
            WriterAgent writerAgent,
            ReflectionCriticAgent reflectionCriticAgent,
            CitationValidator citationValidator,
            CostTracker costTracker,
            @Value("${research-agent.reports.output-dir:./reports}") String reportsOutputDir) {
        this.plannerAgent = plannerAgent;
        this.searcherAgent = searcherAgent;
        this.readerAgent = readerAgent;
        this.verifierAgent = verifierAgent;
        this.writerAgent = writerAgent;
        this.reflectionCriticAgent = reflectionCriticAgent;
        this.citationValidator = citationValidator;
        this.costTracker = costTracker;
        this.reportsOutputDir = reportsOutputDir;
    }

    public void cancelRun(String runId) {
        AtomicBoolean token = cancellationTokens.get(runId);
        if (token != null) {
            token.set(true);
            log.info("Cancellation requested for run: {}", runId);
        }
    }

    public boolean isCancelled(String runId) {
        AtomicBoolean token = cancellationTokens.get(runId);
        return token != null && token.get();
    }

    /**
     * Overload for the async worker path: accepts a pre-assigned {@code runId}
     * (from the RabbitMQ message) so the DB row and the orchestrator's internal
     * state use the same identifier.
     */
    public Run executeRun(String runId, String query, ResearchDepth depth, long budgetCapPaise, RunEventListener listener) {
        AtomicBoolean cancellationToken = new AtomicBoolean(false);
        cancellationTokens.put(runId, cancellationToken);
        try (RunControl.Scope ignored = RunControl.open(cancellationToken::get)) {
            return executeRunInternal(runId, query, depth, budgetCapPaise, listener, cancellationToken);
        }
    }

    public Run executeRun(String query, ResearchDepth depth, long budgetCapPaise, RunEventListener listener) {
        String runId = UUID.randomUUID().toString();
        AtomicBoolean cancellationToken = new AtomicBoolean(false);
        cancellationTokens.put(runId, cancellationToken);
        try (RunControl.Scope ignored = RunControl.open(cancellationToken::get)) {
            return executeRunInternal(runId, query, depth, budgetCapPaise, listener, cancellationToken);
        }
    }

    private Run executeRunInternal(String runId, String query, ResearchDepth depth, long budgetCapPaise,
                                   RunEventListener listener, AtomicBoolean cancellationToken) {

        Run run = Run.builder()
                .id(runId)
                .query(query)
                .depth(depth != null ? depth : ResearchDepth.STANDARD)
                .budgetCapPaise(budgetCapPaise > 0 ? budgetCapPaise : 1500)
                .status(RunStatus.QUEUED)
                .startedAt(Instant.now())
                .build();

        CostTracker.RunBudgetState budgetState = costTracker.createBudgetState(run.getBudgetCapPaise());
        int seq = 1;

        try {
            emitEvent(listener, runId, RunStatus.QUEUED, AgentType.SYSTEM, "Research job enqueued for: " + query, budgetState);

            // ==========================================
            // Step 1: PLANNER
            // ==========================================
            if (checkCancellation(run, listener, budgetState)) return run;
            run.setStatus(RunStatus.PLANNING);
            emitEvent(listener, runId, RunStatus.PLANNING, AgentType.PLANNER, "Planner: Decomposing question into targeted sub-questions...", budgetState);

            PlannerAgent.PlannerResult planResult = plannerAgent.plan(runId, seq++, query, run.getDepth());
            ResearchPlan plan = planResult.getPlan();
            run.getSteps().add(planResult.getStepTrace());
            budgetState.recordStep(planResult.getStepTrace().getTokensIn(), planResult.getStepTrace().getTokensOut(), "planner");
            emitEvent(listener, runId, RunStatus.PLANNING, AgentType.PLANNER,
                    "Planner: Generated " + plan.getSubQuestions().size() + " sub-questions across search angles", budgetState);

            // ==========================================
            // Step 2: SEARCHER
            // ==========================================
            if (checkCancellation(run, listener, budgetState)) return run;
            run.setStatus(RunStatus.SEARCHING);
            emitEvent(listener, runId, RunStatus.SEARCHING, AgentType.SEARCHER, "Searcher: Querying web APIs and ranking sources...", budgetState);

            SearcherAgent.SearcherResult searchResult = searcherAgent.search(runId, seq++, plan, run.getDepth());
            List<Source> sources = searchResult.getSources();
            run.setSources(sources);
            run.getSteps().add(searchResult.getStepTrace());
            emitEvent(listener, runId, RunStatus.SEARCHING, AgentType.SEARCHER,
                    "Searcher: " + searchResult.getStatus() + "; discovered " + sources.size() + " candidate URLs", budgetState);
            if (sources.isEmpty() && searchResult.getStatus() == com.researchagent.tools.SearchOutcome.Status.UNAVAILABLE) {
                throw new IllegalStateException("Search providers unavailable; no research evidence could be collected");
            }

            // ==========================================
            // Step 3: READER
            // ==========================================
            if (checkCancellation(run, listener, budgetState)) return run;
            run.setStatus(RunStatus.READING);
            emitEvent(listener, runId, RunStatus.READING, AgentType.READER, "Reader: Crawling pages with SSRF guardrails and extracting factual claims...", budgetState);

            ReaderAgent.ReaderResult readerResult = readerAgent.readAndExtractClaims(runId, seq, sources, query, budgetState);
            seq += readerResult.getTraces().size();
            run.getSteps().addAll(readerResult.getTraces());
            List<Claim> extractedClaims = readerResult.getClaims();
            run.setClaims(extractedClaims);
            emitEvent(listener, runId, RunStatus.READING, AgentType.READER,
                    "Reader: Extracted " + extractedClaims.size() + " verifiable claims from " + sources.size() + " sources", budgetState);

            // ==========================================
            // Step 4: VERIFIER
            // ==========================================
            if (checkCancellation(run, listener, budgetState)) return run;
            run.setStatus(RunStatus.VERIFYING);
            emitEvent(listener, runId, RunStatus.VERIFYING, AgentType.VERIFIER, "Verifier: Comparing claims and grouping potentially shared evidence...", budgetState);

            VerifierAgent.VerifierResult verifierResult = verifierAgent.verifyClaims(runId, seq++, extractedClaims, sources, budgetState);
            run.getSteps().add(verifierResult.getStepTrace());
            List<Claim> verifiedClaims = verifierResult.getVerifiedClaims();
            List<Claim> unverifiedClaims = verifierResult.getUnverifiedClaims();
            List<Claim> contradictoryClaims = verifierResult.getContradictoryClaims();
            emitEvent(listener, runId, RunStatus.VERIFYING, AgentType.VERIFIER,
                    String.format("Verifier: %d verified claims, %d unverified claims (independence unconfirmed), %d proposed contradictions",
                            verifiedClaims.size(), unverifiedClaims.size(), contradictoryClaims.size()), budgetState);

            // ==========================================
            // Step 5: WRITER
            // ==========================================
            if (checkCancellation(run, listener, budgetState)) return run;
            run.setStatus(RunStatus.WRITING);
            emitEvent(listener, runId, RunStatus.WRITING, AgentType.WRITER, "Writer: Selecting relevant saved evidence for the report...", budgetState);

            WriterAgent.WriterResult writerResult = writerAgent.writeReport(
                    runId, seq++, query, plan, verifiedClaims, unverifiedClaims, contradictoryClaims, sources, budgetState);
            run.getSteps().add(writerResult.getStepTrace());
            emitEvent(listener, runId, RunStatus.WRITING, AgentType.WRITER, "Writer: Evidence selection complete; quotations and citations rendered from saved sources", budgetState);

            // ==========================================
            // Step 6: REFLECTION CRITIC
            // ==========================================
            if (checkCancellation(run, listener, budgetState)) return run;
            run.setStatus(RunStatus.REFLECTING);
            emitEvent(listener, runId, RunStatus.REFLECTING, AgentType.REFLECTION_CRITIC, "Reflection Critic: Evaluating draft completeness and citation density...", budgetState);

            ReflectionCriticAgent.ReflectionResult reflectionResult = reflectionCriticAgent.reflectAndRevise(
                    runId, seq++, query, plan, writerResult.getDraft(), extractedClaims, sources, budgetState);
            run.getSteps().add(reflectionResult.getStepTrace());
            int reportVersion = reflectionResult.getVersion();
            emitEvent(listener, runId, RunStatus.REFLECTING, AgentType.REFLECTION_CRITIC,
                    "Reflection Critic: Evaluated draft (Version " + reportVersion + " finalized, revision triggered: " + reflectionResult.isRevisionTriggered() + ")", budgetState);

            // ==========================================
            // Step 7: CITATION VALIDATOR (A3)
            // ==========================================
            if (checkCancellation(run, listener, budgetState)) return run;
            run.setStatus(RunStatus.VALIDATING_CITATIONS);
            emitEvent(listener, runId, RunStatus.VALIDATING_CITATIONS, AgentType.CITATION_VALIDATOR, "Citation Validator: Auditing citations against fetched sources...", budgetState);

            CitationValidator.CitationValidationResult citationResult = citationValidator.validateAndRender(
                    runId, seq++, reflectionResult.getDraft(), extractedClaims, sources);
            run.getSteps().add(citationResult.getStepTrace());
            String finalMarkdown = citationResult.getSanitizedMarkdown();
            if (searchResult.getStatus() == com.researchagent.tools.SearchOutcome.Status.PARTIAL) {
                finalMarkdown = "> **Partial search coverage.** At least one search provider was unavailable; this report uses sources discovered by an available provider. Citations require fetched evidence.\n\n"
                        + finalMarkdown;
            }
            emitEvent(listener, runId, RunStatus.VALIDATING_CITATIONS, AgentType.CITATION_VALIDATOR,
                    "Citation Validator: Checked quotation provenance for " + citationResult.getValidCitationIds().size()
                            + " cited sources; interpretation support remains unassessed", budgetState);

            // ==========================================
            // Finalize Report & Metrics
            // ==========================================
            double confidenceScore = calculateConfidenceScore(
                    verifiedClaims.size(), unverifiedClaims.size(), contradictoryClaims.size(), sources.size());
            String reportTitle = "Research Brief: " + query;

            Path savedMarkdownPath = saveReportToDisk(runId, finalMarkdown);

            ResearchReport finalReport = ResearchReport.builder()
                    .id(UUID.randomUUID().toString())
                    .runId(runId)
                    .query(query)
                    .title(reportTitle)
                    .executiveSummary(citationResult.getGroundedReport().executiveSummary())
                    .markdownContent(finalMarkdown)
                    .pdfPath(null) // populated in Phase 4
                    .version(reportVersion)
                    .confidenceScore(confidenceScore)
                    .totalSources(sources.size())
                    .verifiedClaimsCount(verifiedClaims.size())
                    .unverifiedClaimsCount(unverifiedClaims.size())
                    .sources(sources)
                    .claims(extractedClaims)
                    .createdAt(Instant.now())
                    .build();

            run.setReport(finalReport);
            finalReport.setEvidencePolicy(com.researchagent.evidence.GroundedReportRenderer.POLICY);
            finalReport.setEvidenceManifestJson(citationResult.getGroundedReport().manifestJson());
            finalReport.setReportDraftJson(citationResult.getGroundedReport().draftJson());
            run.setStatus(RunStatus.COMPLETED);
            run.setFinishedAt(Instant.now());
            run.setCostPaise(budgetState.getTotalCostPaise());
            run.setTokensIn(budgetState.getTotalTokensIn());
            run.setTokensOut(budgetState.getTotalTokensOut());

            emitEvent(listener, runId, RunStatus.COMPLETED, AgentType.SYSTEM,
                    String.format("Research completed successfully! Total cost: %d paise (~₹%.2f). Report saved to: %s",
                            run.getCostPaise(), (double) run.getCostPaise() / 100.0, savedMarkdownPath.toString()), budgetState);

        } catch (RunCancelledException e) {
            markCancelled(run, listener, budgetState);
        } catch (Exception e) {
            log.error("Run {} failed with error: {}", runId, e.getMessage(), e);
            run.setStatus(RunStatus.FAILED);
            run.setFailureReason(e.getMessage());
            run.setFinishedAt(Instant.now());
            run.setCostPaise(budgetState.getTotalCostPaise());
            run.setTokensIn(budgetState.getTotalTokensIn());
            run.setTokensOut(budgetState.getTotalTokensOut());
            emitEvent(listener, runId, RunStatus.FAILED, AgentType.SYSTEM, "Run failed: " + e.getMessage(), budgetState);
        } finally {
            cancellationTokens.remove(runId);
        }

        return run;
    }

    private boolean checkCancellation(Run run, RunEventListener listener, CostTracker.RunBudgetState budgetState) {
        RunControl.checkActive();
        if (isCancelled(run.getId())) {
            markCancelled(run, listener, budgetState);
            return true;
        }
        return false;
    }

    private void markCancelled(Run run, RunEventListener listener, CostTracker.RunBudgetState budgetState) {
        run.setStatus(RunStatus.CANCELLED);
        run.setFailureReason("User requested cancellation; already-started provider calls may still incur cost");
        run.setFinishedAt(Instant.now());
        run.setCostPaise(budgetState.getTotalCostPaise());
        run.setTokensIn(budgetState.getTotalTokensIn());
        run.setTokensOut(budgetState.getTotalTokensOut());
        run.setReport(null);
        emitEvent(listener, run.getId(), RunStatus.CANCELLED, AgentType.SYSTEM, run.getFailureReason(), budgetState);
    }

    private void emitEvent(RunEventListener listener, String runId, RunStatus status, AgentType agent, String message, CostTracker.RunBudgetState budget) {
        if (listener != null) {
            int tIn = budget != null ? budget.getTotalTokensIn() : 0;
            int tOut = budget != null ? budget.getTotalTokensOut() : 0;
            long cost = budget != null ? budget.getTotalCostPaise() : 0;
            listener.onEvent(runId, status, agent, message, tIn, tOut, cost);
        }
    }

    private double calculateConfidenceScore(int verifiedCount, int unverifiedCount, int contradictionCount, int totalSources) {
        if (totalSources == 0) return 0.0;
        int totalClaims = verifiedCount + unverifiedCount + contradictionCount;
        if (totalClaims == 0) return 0.0;
        return (double) verifiedCount / (double) totalClaims;
    }

    private Path saveReportToDisk(String runId, String markdown) {
        RunControl.checkActive();
        try {
            Path outputDir = Paths.get(reportsOutputDir);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            Path filePath = outputDir.resolve("report-" + runId + ".md");
            Files.writeString(filePath, markdown);
            return filePath;
        } catch (IOException e) {
            log.warn("Failed to write report markdown to disk: {}", e.getMessage());
            return Paths.get(reportsOutputDir, "report-" + runId + ".md");
        }
    }
}
