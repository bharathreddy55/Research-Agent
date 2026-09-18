package com.researchagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.llm.LlmService;
import com.researchagent.model.Claim;
import com.researchagent.model.ClaimStatus;
import com.researchagent.tools.CostTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerifierAgentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should mark claim as VERIFIED when corroborated across ≥2 independent domains")
    void shouldVerifyClaimsAcrossIndependentDomains() {
        Claim claimA = Claim.builder()
                .id("c1")
                .statement("Silicon anodes expanded cycle life to 1000 cycles.")
                .exactQuote("demonstrated 1000 cycle stability")
                .sourceId(1)
                .sourceUrl("https://nature.com/art1")
                .sourceDomain("nature.com")
                .status(ClaimStatus.UNVERIFIED)
                .build();
        claimA.getSupportingDomains().add("nature.com");
        claimA.getSupportingSourceIds().add(1);

        Claim claimB = Claim.builder()
                .id("c2")
                .statement("Silicon anode cells achieved 1000 cycles.")
                .exactQuote("cells survived over 1000 full recharge cycles")
                .sourceId(2)
                .sourceUrl("https://sciencedirect.com/art2")
                .sourceDomain("sciencedirect.com")
                .status(ClaimStatus.UNVERIFIED)
                .build();
        claimB.getSupportingDomains().add("sciencedirect.com");
        claimB.getSupportingSourceIds().add(2);

        String clusterJson = """
        {
          "clusters": [
            {
              "primaryClaimIndex": 0,
              "corroboratingIndices": [1],
              "isContradiction": false,
              "contradictionReason": ""
            }
          ]
        }
        """;

        LlmService stubLlmService = new LlmService("gemini", "", "gemini-1.5-flash", "", "", 0.2, new CostTracker()) {
            @Override
            public LlmCallResult generate(String systemPrompt, String userPrompt) {
                return LlmCallResult.builder()
                        .content(clusterJson)
                        .tokensIn(100)
                        .tokensOut(50)
                        .costPaise(1)
                        .build();
            }
        };

        VerifierAgent verifierAgent = new VerifierAgent(stubLlmService, objectMapper);
        VerifierAgent.VerifierResult result = verifierAgent.verifyClaims("run-1", 1, List.of(claimA, claimB), null);

        assertThat(result.getVerifiedClaims()).hasSize(2);
        assertThat(claimA.getStatus()).isEqualTo(ClaimStatus.VERIFIED);
        assertThat(claimA.getSupportingDomains()).containsExactlyInAnyOrder("nature.com", "sciencedirect.com");
    }

    @Test
    @DisplayName("Should keep claim as UNVERIFIED if multiple mentions are from the SAME domain")
    void shouldRemainUnverifiedIfSameDomainTwice() {
        Claim claim1 = Claim.builder()
                .id("c1")
                .statement("New battery chemistry was discovered.")
                .exactQuote("We found a new compound")
                .sourceId(1)
                .sourceUrl("https://techblog.com/page1")
                .sourceDomain("techblog.com")
                .status(ClaimStatus.UNVERIFIED)
                .build();
        claim1.getSupportingDomains().add("techblog.com");

        Claim claim2 = Claim.builder()
                .id("c2")
                .statement("Our researchers discovered a compound.")
                .exactQuote("Our lab identified this compound")
                .sourceId(2)
                .sourceUrl("https://techblog.com/page2")
                .sourceDomain("techblog.com")
                .status(ClaimStatus.UNVERIFIED)
                .build();
        claim2.getSupportingDomains().add("techblog.com");

        String clusterJson = """
        {
          "clusters": [
            {
              "primaryClaimIndex": 0,
              "corroboratingIndices": [1],
              "isContradiction": false,
              "contradictionReason": ""
            }
          ]
        }
        """;

        LlmService stubLlmService = new LlmService("gemini", "", "gemini-1.5-flash", "", "", 0.2, new CostTracker()) {
            @Override
            public LlmCallResult generate(String systemPrompt, String userPrompt) {
                return LlmCallResult.builder()
                        .content(clusterJson)
                        .tokensIn(100)
                        .tokensOut(50)
                        .costPaise(1)
                        .build();
            }
        };

        VerifierAgent verifierAgent = new VerifierAgent(stubLlmService, objectMapper);
        VerifierAgent.VerifierResult result = verifierAgent.verifyClaims("run-2", 1, List.of(claim1, claim2), null);

        // Even though clustered together, supportingDomains size is only 1 ("techblog.com")
        assertThat(result.getVerifiedClaims()).isEmpty();
        assertThat(result.getUnverifiedClaims()).hasSize(2);
        assertThat(claim1.getStatus()).isEqualTo(ClaimStatus.UNVERIFIED);
    }
}
