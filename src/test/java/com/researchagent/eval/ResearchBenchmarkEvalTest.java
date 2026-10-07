package com.researchagent.eval;

import com.researchagent.agent.*;
import com.researchagent.model.*;
import com.researchagent.tools.CostTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 6 Benchmark Evaluation Suite (PRD §11 & §15).
 *
 * Runs the 7-agent pipeline across 10 diverse research queries (Science, Technology,
 * Economics, Policy) and measures execution time, token cost in paise, citation coverage,
 * and verified claim ratios.
 */
class ResearchBenchmarkEvalTest {

    private record BenchmarkResult(
            int id,
            String category,
            String query,
            ResearchDepth depth,
            RunStatus status,
            long durationMs,
            long costPaise,
            int totalSources,
            int verifiedClaims,
            int unverifiedClaims,
            double confidenceScore
    ) {}

    private static final List<String[]> BENCHMARK_QUESTIONS = List.of(
            new String[]{"Genetics & Biotech", "CRISPR gene editing advances for sickle cell disease in 2026"},
            new String[]{"Cybersecurity", "Quantum key distribution vs lattice cryptography security bounds"},
            new String[]{"Automotive & Energy", "Solid-state battery commercialization timelines by major EV manufacturers"},
            new String[]{"AI Governance", "EU AI Act compliance requirements for foundation model developers"},
            new String[]{"Finance & Trading", "Impact of high-frequency trading algorithms on treasury bond market liquidity"},
            new String[]{"Physics & Energy", "Fusion energy Q-factor milestones achieved by tokamak facilities"},
            new String[]{"Medicine", "Microbiome-targeted therapeutics in clinical trials for inflammatory bowel disease"},
            new String[]{"Hardware Tech", "Global semiconductor fab capacity expansion in Southeast Asia"},
            new String[]{"Nuclear Energy", "Small Modular Reactor (SMR) licensing status across US and European regulators"},
            new String[]{"AI Engineering", "Autonomous agent orchestration frameworks comparative latency and overhead"}
    );

    private ResearchOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        CostTracker costTracker = new CostTracker();

        // Stubbed LLM service returning structured mock agent outputs
        com.researchagent.llm.LlmService llmService = new com.researchagent.llm.LlmService(
                "mock", "", "gemini-1.5-flash", "", "gpt-4o-mini", 0.2, costTracker);

        PlannerAgent planner = new PlannerAgent(llmService, new com.fasterxml.jackson.databind.ObjectMapper());
        com.researchagent.tools.SearchService searchService = new com.researchagent.tools.FallbackSearchService();
        SearcherAgent searcher = new SearcherAgent(searchService);

        com.researchagent.tools.SafeWebReader readerTool = new com.researchagent.tools.SafeWebReader();
        ReaderAgent reader = new ReaderAgent(readerTool, llmService, new com.fasterxml.jackson.databind.ObjectMapper());
        VerifierAgent verifier = new VerifierAgent(llmService, new com.fasterxml.jackson.databind.ObjectMapper());
        WriterAgent writer = new WriterAgent(llmService);
        ReflectionCriticAgent critic = new ReflectionCriticAgent(llmService, new com.fasterxml.jackson.databind.ObjectMapper(), 1);
        CitationValidator citationValidator = new CitationValidator();

        orchestrator = new ResearchOrchestrator(
                planner, searcher, reader, verifier, writer, critic, citationValidator, costTracker, "./reports");
    }

    @Test
    @DisplayName("Run 10-Question Evaluation Benchmark Suite & Validate Metrics")
    void runBenchmarkEvaluationSuite() {
        List<BenchmarkResult> results = new ArrayList<>();
        int qId = 1;

        System.out.println("\n==========================================================================");
        System.out.println("📊 RESEARCHAGENT 10-QUESTION EVALUATION BENCHMARK SUITE");
        System.out.println("==========================================================================");

        for (String[] q : BENCHMARK_QUESTIONS) {
            String category = q[0];
            String query = q[1];
            long startTime = System.currentTimeMillis();

            Run run = orchestrator.executeRun(query, ResearchDepth.QUICK, 1500L,
                    (runId, status, agent, message, tIn, tOut, cost) -> {});

            long duration = System.currentTimeMillis() - startTime;

            ResearchReport report = run.getReport();
            int totalSources = report != null ? report.getTotalSources() : 0;
            int verified = report != null ? report.getVerifiedClaimsCount() : 0;
            int unverified = report != null ? report.getUnverifiedClaimsCount() : 0;
            double confidence = report != null ? report.getConfidenceScore() : 0.0;

            BenchmarkResult res = new BenchmarkResult(
                    qId++, category, query, ResearchDepth.QUICK, run.getStatus(),
                    duration, run.getCostPaise(), totalSources, verified, unverified, confidence);
            results.add(res);

            System.out.printf("#%d [%s] %s -> Status: %s | Cost: ₹%.2f (%d paise) | Sources: %d | Verified: %d/%d (%.0f%%)\n",
                    res.id(), res.category(), res.query(), res.status(),
                    (double) res.costPaise() / 100.0, res.costPaise(), res.totalSources(),
                    res.verifiedClaims(), res.verifiedClaims() + res.unverifiedClaims(), res.confidenceScore() * 100);
        }

        System.out.println("==========================================================================");

        // Assertions: All 10 benchmark queries must complete successfully
        assertThat(results).hasSize(10);
        assertThat(results).allMatch(r -> r.status() == RunStatus.COMPLETED);
        assertThat(results).allMatch(r -> r.costPaise() <= 1500L); // Max ₹15 cost cap
        assertThat(results).allMatch(r -> r.confidenceScore() >= 0.5); // Minimum 50% verified claim ratio
    }
}
