package com.researchagent.evidence;

import java.util.List;

/** The model can select/order claims; it cannot author factual text or citation numbers. */
public record ReportDraft(List<String> summaryClaimIds, List<String> findingClaimIds, boolean demoMode) {
    public static final String SCHEMA = "claim-selections-v1";
    public ReportDraft {
        summaryClaimIds = List.copyOf(summaryClaimIds);
        findingClaimIds = List.copyOf(findingClaimIds);
        if (summaryClaimIds.size() > 5 || findingClaimIds.size() > 100
                || summaryClaimIds.stream().distinct().count() != summaryClaimIds.size()
                || findingClaimIds.stream().distinct().count() != findingClaimIds.size()
                || !findingClaimIds.containsAll(summaryClaimIds))
            throw new IllegalArgumentException("Invalid report claim selection");
    }
}
