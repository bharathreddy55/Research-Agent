package com.researchagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.llm.LlmService;
import com.researchagent.model.AgentType;
import com.researchagent.model.ResearchDepth;
import com.researchagent.model.ResearchPlan;
import com.researchagent.model.StepTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PlannerAgent {
    private static final Logger log = LoggerFactory.getLogger(PlannerAgent.class);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public PlannerAgent(LlmService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public static class PlannerResult {
        private final ResearchPlan plan;
        private final StepTrace stepTrace;

        public PlannerResult(ResearchPlan plan, StepTrace stepTrace) {
            this.plan = plan;
            this.stepTrace = stepTrace;
        }

        public ResearchPlan getPlan() { return plan; }
        public StepTrace getStepTrace() { return stepTrace; }
    }

    public PlannerResult plan(String runId, int seq, String query, ResearchDepth depth) {
        long startTime = System.currentTimeMillis();
        int maxSubQuestions = depth != null ? depth.getMaxSubQuestions() : 4;

        String systemPrompt = """
        You are the PLANNER agent of an elite research system.
        Your task is to decompose a complex research question into 3 to %d targeted sub-questions
        and search queries to gather comprehensive, empirical evidence.

        Return strictly valid JSON with this schema:
        {
          "subQuestions": [
            "Sub-question 1",
            "Sub-question 2"
          ],
          "searchQueries": [
            "targeted search query 1",
            "targeted search query 2"
          ],
          "rationale": "Brief 1-2 sentence explanation of research angles"
        }
        Do not include markdown code fence formatting outside the JSON if possible. Only return the JSON.
        """.formatted(maxSubQuestions);

        String userPrompt = "Research Question: \"" + query + "\"\nDepth Level: " + depth;

        LlmService.LlmCallResult callResult = llmService.generate(systemPrompt, userPrompt);
        ResearchPlan plan = parseAndValidateJson(callResult.getContent());

        // 1 retry on format breakage (PRD F4)
        if (plan == null) {
            log.warn("Planner output parsing failed. Executing 1 retry with schema repair prompt.");
            String repairPrompt = "The previous response was invalid JSON. Please return strictly valid JSON matching the schema:\n" +
                    "{\"subQuestions\": [\"...\"], \"searchQueries\": [\"...\"], \"rationale\": \"...\"}\n" +
                    "Query: " + query;
            LlmService.LlmCallResult retryResult = llmService.generate(systemPrompt, repairPrompt);
            plan = parseAndValidateJson(retryResult.getContent());

            if (plan == null) {
                log.warn("Planner retry failed. Utilizing robust heuristic fallback decomposition.");
                plan = createFallbackPlan(query, maxSubQuestions);
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        StepTrace trace = StepTrace.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId)
                .seq(seq)
                .agent(AgentType.PLANNER)
                .action("Decomposed question into " + plan.getSubQuestions().size() + " sub-questions")
                .prompt(userPrompt)
                .response(callResult.getContent())
                .tokensIn(callResult.getTokensIn())
                .tokensOut(callResult.getTokensOut())
                .costPaise(callResult.getCostPaise())
                .durationMs(durationMs)
                .build();

        return new PlannerResult(plan, trace);
    }

    private ResearchPlan parseAndValidateJson(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        try {
            String cleaned = cleanJsonString(rawText);
            JsonNode root = objectMapper.readTree(cleaned);

            JsonNode subQuestionsNode = root.path("subQuestions");
            JsonNode searchQueriesNode = root.path("searchQueries");

            if (!subQuestionsNode.isArray() || subQuestionsNode.isEmpty()) {
                return null;
            }

            List<String> subQuestions = new ArrayList<>();
            for (JsonNode n : subQuestionsNode) {
                if (n.isTextual() && !n.asText().isBlank()) {
                    subQuestions.add(n.asText().trim());
                }
            }

            List<String> searchQueries = new ArrayList<>();
            if (searchQueriesNode.isArray()) {
                for (JsonNode n : searchQueriesNode) {
                    if (n.isTextual() && !n.asText().isBlank()) {
                        searchQueries.add(n.asText().trim());
                    }
                }
            }

            if (searchQueries.isEmpty()) {
                searchQueries.addAll(subQuestions);
            }

            String rationale = root.path("rationale").asText("Research decomposition");

            return ResearchPlan.builder()
                    .subQuestions(subQuestions)
                    .searchQueries(searchQueries)
                    .rationale(rationale)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Planner JSON: {}", e.getMessage());
            return null;
        }
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

    private ResearchPlan createFallbackPlan(String query, int maxSubQuestions) {
        List<String> subQuestions = new ArrayList<>();
        subQuestions.add("What is the current technical state and recent developments regarding: " + query);
        subQuestions.add("What are the key empirical evidence, metrics, and benchmarks for: " + query);
        subQuestions.add("What are the primary challenges, limitations, and future outlook for: " + query);

        if (maxSubQuestions >= 4) {
            subQuestions.add("Who are the principal organizations, companies, or researchers leading: " + query);
        }

        return ResearchPlan.builder()
                .subQuestions(subQuestions)
                .searchQueries(new ArrayList<>(subQuestions))
                .rationale("Deterministic fallback decomposition for query: " + query)
                .build();
    }
}
