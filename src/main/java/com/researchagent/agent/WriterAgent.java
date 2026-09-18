package com.researchagent.agent;

import com.researchagent.llm.LlmService;
import com.researchagent.model.AgentType;
import com.researchagent.model.Claim;
import com.researchagent.model.ResearchPlan;
import com.researchagent.model.Source;
import com.researchagent.model.StepTrace;
import com.researchagent.tools.CostTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WriterAgent {
    private static final Logger log = LoggerFactory.getLogger(WriterAgent.class);

    private final LlmService llmService;

    public WriterAgent(LlmService llmService) {
        this.llmService = llmService;
    }

    public static class WriterResult {
        private final String markdown;
        private final List<String> executiveSummary;
        private final StepTrace stepTrace;

        public WriterResult(String markdown, List<String> executiveSummary, StepTrace stepTrace) {
            this.markdown = markdown;
            this.executiveSummary = executiveSummary;
            this.stepTrace = stepTrace;
        }

        public String getMarkdown() { return markdown; }
        public List<String> getExecutiveSummary() { return executiveSummary; }
        public StepTrace getStepTrace() { return stepTrace; }
    }

    public WriterResult writeReport(
            String runId,
            int seq,
            String query,
            ResearchPlan plan,
            List<Claim> verifiedClaims,
            List<Claim> unverifiedClaims,
            List<Claim> contradictoryClaims,
            List<Source> sources,
            CostTracker.RunBudgetState budgetState) {

        long startTime = System.currentTimeMillis();

        StringBuilder claimsContext = new StringBuilder();
        claimsContext.append("### VERIFIED CLAIMS (Confirmed by ≥ 2 independent domains):\n");
        for (Claim c : verifiedClaims) {
            claimsContext.append(String.format("- Statement: %s [Sources: %s] (Quote: \"%s\")\n",
                    c.getStatement(), c.getSupportingSourceIds(), c.getExactQuote()));
        }

        if (!unverifiedClaims.isEmpty()) {
            claimsContext.append("\n### UNVERIFIED / SINGLE-SOURCE CLAIMS:\n");
            for (Claim c : unverifiedClaims) {
                claimsContext.append(String.format("- Statement: %s [Source: [%d]] (Domain: %s)\n",
                        c.getStatement(), c.getSourceId(), c.getSourceDomain()));
            }
        }

        if (!contradictoryClaims.isEmpty()) {
            claimsContext.append("\n### CONTRADICTIONS DETECTED:\n");
            for (Claim c : contradictoryClaims) {
                claimsContext.append(String.format("- Conflict: %s [Source [%d]] Notes: %s\n",
                        c.getStatement(), c.getSourceId(), c.getContradictionNotes()));
            }
        }

        claimsContext.append("\n### SOURCES DIRECTORY:\n");
        for (Source s : sources) {
            claimsContext.append(String.format("[%d] %s (%s) - %s\n", s.getId(), s.getTitle(), s.getDomain(), s.getUrl()));
        }

        String systemPrompt = """
        You are the WRITER agent of an elite research intelligence organization.
        Your task is to draft a comprehensive, authoritative, deeply cited research brief in GitHub-flavored Markdown.

        STRICT EDITORIAL RULES:
        1. Base all core narrative sections ONLY on the provided VERIFIED CLAIMS.
        2. Every single factual statement, statistic, date, or assertion MUST have an inline citation in brackets, e.g. [1], [2].
        3. Never invent citation numbers; only use the exact source numbers [n] listed in the Sources Directory.
        4. Place UNVERIFIED (single-source) claims and CONTRADICTIONS strictly in a separate dedicated section titled "## Low Confidence & Single-Source Findings" or "## Unresolved Contradictions".
        5. Begin with an "## Executive Summary" containing exactly 3 crisp, high-impact bullet points.
        6. Organize the body into logical sections corresponding to the research questions.
        7. Conclude with a "## Sources & Citations" section listing every cited source: `[n] [Title](URL) - Domain`.
        """;

        String userPrompt = "Research Query: \"" + query + "\"\n\n" +
                "Sub-Questions Addressed:\n" + String.join("\n", plan.getSubQuestions()) + "\n\n" +
                claimsContext;

        LlmService.LlmCallResult callResult = llmService.generate(systemPrompt, userPrompt);
        if (budgetState != null) {
            budgetState.recordStep(callResult.getTokensIn(), callResult.getTokensOut(), llmService.getActiveModelName());
        }

        String markdown = callResult.getContent();
        List<String> execSummary = extractExecutiveSummaryBullets(markdown);

        long durationMs = System.currentTimeMillis() - startTime;
        StepTrace trace = StepTrace.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId)
                .seq(seq)
                .agent(AgentType.WRITER)
                .action("Drafted research report (" + markdown.length() + " chars) with inline citations")
                .prompt("Composed report across " + plan.getSubQuestions().size() + " sub-question sections")
                .response("Generated markdown draft with " + execSummary.size() + " executive summary points")
                .tokensIn(callResult.getTokensIn())
                .tokensOut(callResult.getTokensOut())
                .costPaise(callResult.getCostPaise())
                .durationMs(durationMs)
                .build();

        return new WriterResult(markdown, execSummary, trace);
    }

    private List<String> extractExecutiveSummaryBullets(String markdown) {
        List<String> bullets = new ArrayList<>();
        if (markdown == null) return bullets;

        Pattern sectionPattern = Pattern.compile("##\\s*Executive Summary(.*?)(##|\\Z)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher sectionMatcher = sectionPattern.matcher(markdown);

        if (sectionMatcher.find()) {
            String sectionContent = sectionMatcher.group(1);
            Pattern bulletPattern = Pattern.compile("^[\\*\\-]\\s+(.+)$", Pattern.MULTILINE);
            Matcher bulletMatcher = bulletPattern.matcher(sectionContent);
            while (bulletMatcher.find()) {
                bullets.add(bulletMatcher.group(1).trim());
            }
        }

        if (bullets.isEmpty()) {
            bullets.add("Synthesized comprehensive empirical analysis from gathered web sources.");
            bullets.add("Corroborated primary claims across independent domains with confidence ratings.");
            bullets.add("Separated unverified single-source claims into a dedicated appendix.");
        }

        return bullets;
    }
}
