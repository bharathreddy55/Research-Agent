package com.researchagent.llm;

/** Synthetic responses available only through an explicitly selected mock provider. */
final class DemoLlmResponses {
    private DemoLlmResponses() {}

    static String generate(String systemPrompt) {
        String prompt = systemPrompt != null ? systemPrompt : "";
        if (prompt.contains("PLANNER")) {
            return """
                    {
                      "subQuestions": ["Demo overview", "Demo limitations", "Demo evidence"],
                      "searchQueries": ["demo research overview"],
                      "rationale": "Synthetic plan for an explicitly selected demo run."
                    }
                    """.trim();
        }
        if (prompt.contains("READER")) {
            return "{\"claims\": []}";
        }
        if (prompt.contains("VERIFIER")) {
            return "{\"clusters\": []}";
        }
        if (prompt.contains("CLAIM_SELECTION_DRAFT")) {
            return "{\"schemaVersion\":\"claim-selections-v1\",\"summaryClaimIds\":[],\"findingClaimIds\":[]}";
        }
        if (prompt.contains("REFLECTION")) {
            return """
                    {"needsRevision": false, "critique": "Demo content contains no real research findings."}
                    """.trim();
        }
        return """
                > **DEMO MODE — SYNTHETIC OUTPUT.** No real model was called. This document is not a researched answer.

                ## Executive Summary
                - The mock provider was selected explicitly.
                - This run demonstrates the application's workflow only.
                - No factual claims or supporting quotations were generated.

                ## Demo Report
                Configure a real provider and its API key to generate research findings.
                """.trim();
    }
}
