package com.researchagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.llm.LlmService;
import com.researchagent.model.AgentType;
import com.researchagent.model.ResearchPlan;
import com.researchagent.model.Source;
import com.researchagent.model.StepTrace;
import com.researchagent.tools.CostTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ReflectionCriticAgent {
    private static final Logger log = LoggerFactory.getLogger(ReflectionCriticAgent.class);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final int maxReflectionPasses;

    public ReflectionCriticAgent(
            LlmService llmService,
            ObjectMapper objectMapper,
            @Value("${research-agent.limits.max-reflection-passes:1}") int maxReflectionPasses) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.maxReflectionPasses = maxReflectionPasses;
    }

    public static class ReflectionResult {
        private final String finalMarkdown;
        private final int version;
        private final boolean revisionTriggered;
        private final String critique;
        private final StepTrace stepTrace;

        public ReflectionResult(String finalMarkdown, int version, boolean revisionTriggered, String critique, StepTrace stepTrace) {
            this.finalMarkdown = finalMarkdown;
            this.version = version;
            this.revisionTriggered = revisionTriggered;
            this.critique = critique;
            this.stepTrace = stepTrace;
        }

        public String getFinalMarkdown() { return finalMarkdown; }
        public int getVersion() { return version; }
        public boolean isRevisionTriggered() { return revisionTriggered; }
        public String getCritique() { return critique; }
        public StepTrace getStepTrace() { return stepTrace; }
    }

    public ReflectionResult reflectAndRevise(
            String runId,
            int seq,
            String query,
            ResearchPlan plan,
            String draftMarkdown,
            List<Source> sources,
            CostTracker.RunBudgetState budgetState) {

        long startTime = System.currentTimeMillis();

        // Check if budget already tight
        if (budgetState != null && budgetState.isBudgetExceeded()) {
            log.warn("Budget cap reached before reflection pass. Bypassing revision pass.");
            StepTrace skipTrace = StepTrace.builder()
                    .id(UUID.randomUUID().toString())
                    .runId(runId)
                    .seq(seq)
                    .agent(AgentType.REFLECTION_CRITIC)
                    .action("Reflection revision skipped due to budget cap ceiling")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
            return new ReflectionResult(draftMarkdown, 1, false, "Bypassed due to budget cap", skipTrace);
        }

        String critiqueSystemPrompt = """
        You are the REFLECTION CRITIC agent in an AI research laboratory.
        Critique the provided research draft.
        Ask:
        1. Are there claims that lack inline [n] citations?
        2. Are any key sub-questions thin or unaddressed?
        3. Are unverified claims properly segregated?

        Return strictly valid JSON:
        {
          "needsRevision": true,
          "critique": "Specific critique of thin areas or missing citations"
        }
        """;

        String critiqueUserPrompt = "Research Query: " + query + "\n\n" +
                "Sub-questions to cover:\n" + String.join("\n", plan.getSubQuestions()) + "\n\n" +
                "Draft to critique:\n" + draftMarkdown;

        LlmService.LlmCallResult critiqueCall = llmService.generate(critiqueSystemPrompt, critiqueUserPrompt);
        if (budgetState != null) {
            budgetState.recordStep(critiqueCall.getTokensIn(), critiqueCall.getTokensOut(), llmService.getActiveModelName());
        }

        boolean needsRevision = false;
        String critiqueText = "Draft is solid and adequately cited.";

        try {
            String cleaned = cleanJsonString(critiqueCall.getContent());
            JsonNode root = objectMapper.readTree(cleaned);
            needsRevision = root.path("needsRevision").asBoolean(false);
            critiqueText = root.path("critique").asText(critiqueText);
        } catch (Exception e) {
            log.warn("Failed to parse reflection critique JSON: {}", e.getMessage());
        }

        // HARD CAP: At most 1 revision pass (maxReflectionPasses <= 1)
        if (!needsRevision || maxReflectionPasses <= 0) {
            long durationMs = System.currentTimeMillis() - startTime;
            StepTrace trace = StepTrace.builder()
                    .id(UUID.randomUUID().toString())
                    .runId(runId)
                    .seq(seq)
                    .agent(AgentType.REFLECTION_CRITIC)
                    .action("Evaluated draft: approved without revision (Version 1)")
                    .prompt("Critiqued draft against " + plan.getSubQuestions().size() + " sub-questions")
                    .response("Critique: " + critiqueText + " -> Approved draft v1")
                    .tokensIn(critiqueCall.getTokensIn())
                    .tokensOut(critiqueCall.getTokensOut())
                    .costPaise(critiqueCall.getCostPaise())
                    .durationMs(durationMs)
                    .build();

            return new ReflectionResult(draftMarkdown, 1, false, critiqueText, trace);
        }

        log.info("Reflection critic requested revision. Executing hard-capped revision pass (Version 2).");

        String revisionSystemPrompt = """
        You are the REVISION WRITER agent.
        Revise and polish the research draft based on the provided critique.
        Ensure every claim has an inline citation [n], clarify any thin explanations,
        and preserve all valid markdown headers and citations.
        Return the complete updated Markdown document.
        """;

        String revisionUserPrompt = "Original Query: " + query + "\n\n" +
                "Critique Feedback:\n" + critiqueText + "\n\n" +
                "Current Draft:\n" + draftMarkdown;

        LlmService.LlmCallResult revisionCall = llmService.generate(revisionSystemPrompt, revisionUserPrompt);
        if (budgetState != null) {
            budgetState.recordStep(revisionCall.getTokensIn(), revisionCall.getTokensOut(), llmService.getActiveModelName());
        }

        String revisedMarkdown = revisionCall.getContent();
        long durationMs = System.currentTimeMillis() - startTime;

        StepTrace trace = StepTrace.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId)
                .seq(seq)
                .agent(AgentType.REFLECTION_CRITIC)
                .action("Revised draft based on critique (Version 2 created)")
                .prompt("Critique applied: " + critiqueText)
                .response("Generated polished revised markdown (Version 2)")
                .tokensIn(critiqueCall.getTokensIn() + revisionCall.getTokensIn())
                .tokensOut(critiqueCall.getTokensOut() + revisionCall.getTokensOut())
                .costPaise(critiqueCall.getCostPaise() + revisionCall.getCostPaise())
                .durationMs(durationMs)
                .build();

        return new ReflectionResult(revisedMarkdown, 2, true, critiqueText, trace);
    }

    private String cleanJsonString(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
