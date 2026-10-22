package com.researchagent.evidence;

/** Half-open UTF-16 offsets into the immutable fetched-text snapshot, matching Java/JS slicing. */
public record QuoteEvidence(
        String sourceSnapshotId,
        String sourceContentHash,
        String sourceUrl,
        int start,
        int end,
        QuoteMatchStatus matchStatus,
        String normalizationVersion,
        String submittedQuote) {}
