package com.researchagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.llm.LlmService;
import com.researchagent.model.AgentType;
import com.researchagent.model.Claim;
import com.researchagent.model.ClaimStatus;
import com.researchagent.model.StepTrace;
import com.researchagent.tools.CostTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class VerifierAgent {
    private static final Logger log = LoggerFactory.getLogger(VerifierAgent.class);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public VerifierAgent(LlmService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public static class VerifierResult {
        private final List<Claim> verifiedClaims;
        private final List<Claim> unverifiedClaims;
        private final List<Claim> contradictoryClaims;
        private final StepTrace stepTrace;

        public VerifierResult(List<Claim> verifiedClaims, List<Claim> unverifiedClaims, List<Claim> contradictoryClaims, StepTrace stepTrace) {
            this.verifiedClaims = verifiedClaims;
            this.unverifiedClaims = unverifiedClaims;
            this.contradictoryClaims = contradictoryClaims;
            this.stepTrace = stepTrace;
        }

        public List<Claim> getVerifiedClaims() { return verifiedClaims; }
        public List<Claim> getUnverifiedClaims() { return unverifiedClaims; }
        public List<Claim> getContradictoryClaims() { return contradictoryClaims; }
        public StepTrace getStepTrace() { return stepTrace; }
    }

    public VerifierResult verifyClaims(String runId, int seq, List<Claim> rawClaims, CostTracker.RunBudgetState budgetState) {
        long startTime = System.currentTimeMillis();

        if (rawClaims == null || rawClaims.isEmpty()) {
            StepTrace emptyTrace = StepTrace.builder()
                    .id(UUID.randomUUID().toString())
                    .runId(runId)
                    .seq(seq)
                    .agent(AgentType.VERIFIER)
                    .action("No claims extracted to verify")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
            return new VerifierResult(List.of(), List.of(), List.of(), emptyTrace);
        }

        // Corroborate claims using LLM clustering & semantic equivalence
        Map<Integer, Claim> claimMap = new HashMap<>();
        for (int i = 0; i < rawClaims.size(); i++) {
            claimMap.put(i, rawClaims.get(i));
        }

        StringBuilder claimsListPrompt = new StringBuilder();
        for (int i = 0; i < rawClaims.size(); i++) {
            Claim c = rawClaims.get(i);
            claimsListPrompt.append(String.format("[%d] Domain: %s | Source ID: %d | Claim: %s (Quote: \"%s\")\n",
                    i, c.getSourceDomain(), c.getSourceId(), c.getStatement(), c.getExactQuote()));
        }

        String systemPrompt = """
        You are the VERIFIER agent in an autonomous scientific investigation team.
        Review the candidate factual claims extracted across web sources.
        Identify:
        1. Claims that make essentially the same factual assertion (corroboration).
        2. Claims that directly contradict one another on dates, numbers, outcomes, or conclusions.

        Return strictly valid JSON:
        {
          "clusters": [
            {
              "primaryClaimIndex": 0,
              "corroboratingIndices": [1, 3],
              "isContradiction": false,
              "contradictionReason": ""
            }
          ]
        }
        Do not include markdown format outside the JSON.
        """;

        LlmService.LlmCallResult callResult = llmService.generate(systemPrompt, claimsListPrompt.toString());
        if (budgetState != null) {
            budgetState.recordStep(callResult.getTokensIn(), callResult.getTokensOut(), llmService.getActiveModelName());
        }

        // Apply clustering and domain independence rule (≥2 independent domains)
        applyCorroborationClusters(rawClaims, callResult.getContent());

        List<Claim> verified = new ArrayList<>();
        List<Claim> unverified = new ArrayList<>();
        List<Claim> contradictory = new ArrayList<>();

        for (Claim c : rawClaims) {
            if (c.getStatus() == ClaimStatus.CONTRADICTION) {
                contradictory.add(c);
            } else if (c.getSupportingDomains().size() >= 2) {
                c.setStatus(ClaimStatus.VERIFIED);
                verified.add(c);
            } else {
                c.setStatus(ClaimStatus.UNVERIFIED);
                unverified.add(c);
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        StepTrace trace = StepTrace.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId)
                .seq(seq)
                .agent(AgentType.VERIFIER)
                .action(String.format("Cross-verified %d claims: %d verified (≥2 independent domains), %d unverified, %d contradictions",
                        rawClaims.size(), verified.size(), unverified.size(), contradictory.size()))
                .prompt("Assessed " + rawClaims.size() + " extracted claims across domains")
                .response(String.format("Verified: %d, Unverified: %d, Contradictions: %d", verified.size(), unverified.size(), contradictory.size()))
                .tokensIn(callResult.getTokensIn())
                .tokensOut(callResult.getTokensOut())
                .costPaise(callResult.getCostPaise())
                .durationMs(durationMs)
                .build();

        return new VerifierResult(verified, unverified, contradictory, trace);
    }

    private void applyCorroborationClusters(List<Claim> claims, String jsonOutput) {
        if (jsonOutput == null || jsonOutput.isBlank()) return;

        try {
            String cleaned = cleanJsonString(jsonOutput);
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode clustersNode = root.path("clusters");

            if (clustersNode.isArray()) {
                for (JsonNode cluster : clustersNode) {
                    int primaryIdx = cluster.path("primaryClaimIndex").asInt(-1);
                    boolean isContradiction = cluster.path("isContradiction").asBoolean(false);
                    String reason = cluster.path("contradictionReason").asText("");

                    if (primaryIdx >= 0 && primaryIdx < claims.size()) {
                        Claim primary = claims.get(primaryIdx);

                        if (isContradiction) {
                            primary.setStatus(ClaimStatus.CONTRADICTION);
                            primary.setContradictionNotes(reason);
                        }

                        JsonNode corroborating = cluster.path("corroboratingIndices");
                        if (corroborating.isArray()) {
                            for (JsonNode idxNode : corroborating) {
                                int cIdx = idxNode.asInt(-1);
                                if (cIdx >= 0 && cIdx < claims.size() && cIdx != primaryIdx) {
                                    Claim corroborator = claims.get(cIdx);
                                    if (isContradiction) {
                                        corroborator.setStatus(ClaimStatus.CONTRADICTION);
                                        corroborator.setContradictionNotes(reason);
                                    } else {
                                        // Merge supporting domains and sources
                                        primary.getSupportingDomains().addAll(corroborator.getSupportingDomains());
                                        primary.getSupportingSourceIds().addAll(corroborator.getSupportingSourceIds());
                                        corroborator.getSupportingDomains().addAll(primary.getSupportingDomains());
                                        corroborator.getSupportingSourceIds().addAll(primary.getSupportingSourceIds());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse verification clusters: {}", e.getMessage());
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
}
