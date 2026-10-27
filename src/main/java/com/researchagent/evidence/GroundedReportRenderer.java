package com.researchagent.evidence;

import com.researchagent.model.Claim;
import com.researchagent.model.Source;
import java.net.URI;
import java.util.*;

/** Renders source excerpts, never model-authored findings disguised by a valid citation. */
public final class GroundedReportRenderer {
    public static final String POLICY = "quotation-report-v1";
    public record EvidenceReference(String claimId, int sourceNumber, String snapshotId, String contentHash,
                                    int quoteStart, int quoteEnd, String exactQuote, String supportAssessment) {}
    public record Rendered(String markdown, List<String> executiveSummary, List<EvidenceReference> references,
                           String manifestJson, String draftJson) {}

    public Rendered render(String runId, ReportDraft draft, List<Claim> claims, List<Source> sources) {
        ClaimEvidenceIndex index = new ClaimEvidenceIndex(runId, claims, sources);
        List<EvidenceReference> references = new ArrayList<>();
        Map<Integer, Source> cited = new TreeMap<>();
        for (String id : draft.findingClaimIds()) {
            var entry = index.require(id);
            var evidence = entry.claim().getQuoteEvidence();
            references.add(new EvidenceReference(id, entry.source().getId(), evidence.sourceSnapshotId(),
                    evidence.sourceContentHash(), evidence.start(), evidence.end(), entry.claim().getExactQuote(), "QUOTATION_ONLY"));
            // Validate links even when a source title or URL was supplied by an untrusted provider.
            safeUrl(entry.source().getUrl());
            cited.put(entry.source().getId(), entry.source());
        }
        for (String id : draft.summaryClaimIds()) index.require(id);
        StringBuilder md = new StringBuilder();
        if (draft.demoMode()) md.append("> **DEMO MODE — SYNTHETIC OUTPUT.** No real model was called. This document is not a researched answer.\n\n");
        md.append("## Executive Summary\n");
        List<String> summary = new ArrayList<>();
        summary.add(references.isEmpty() ? "No source-grounded findings were selected."
                : references.size() + " source quotations selected from " + cited.size() + " fetched sources.");
        summary.add("Quotations were matched to saved source text. Claim support and source independence remain unconfirmed.");
        summary.forEach(line -> md.append("- ").append(line).append('\n'));
        if (!draft.summaryClaimIds().isEmpty()) {
            md.append("\n### Selected source excerpts\n");
            for (String id : draft.summaryClaimIds()) appendQuote(md, index.require(id));
        }
        md.append("\n## Source Findings & Evidence Limits\n\n");
        md.append("Citations identify the origin of quoted text. They do not certify an interpretation, source reliability, or scientific truth.\n\n");
        if (references.isEmpty()) md.append("No accepted evidence is included in this report.\n\n");
        int finding = 0;
        for (String id : draft.findingClaimIds()) {
            var entry = index.require(id);
            Claim claim = entry.claim();
            md.append("### Source excerpt ").append(++finding).append("\n\n");
            appendQuote(md, entry);
            // Candidate assertions are explicitly unassessed and never inherit the quotation's citation.
            md.append("Unassessed interpretation (not established by this citation): ")
                    .append(text(claim.getStatement())).append("\n\n");
            md.append("Support assessment: quotation origin checked; interpretation not assessed.\n\n");
            md.append("Source independence: ").append(text(claim.getIndependenceNotes() == null
                    ? "Unconfirmed." : claim.getIndependenceNotes())).append("\n\n");
            if (claim.getContradictionNotes() != null) md.append("Proposed conflict (not independently assessed): ")
                    .append(text(claim.getContradictionNotes())).append("\n\n");
        }
        md.append("## Sources & Citations\n\n");
        for (Source source : cited.values()) md.append("- [").append(source.getId()).append("] [")
                .append(text(source.getTitle() == null || source.getTitle().isBlank() ? "Source " + source.getId() : source.getTitle()))
                .append("](<").append(safeUrl(source.getUrl())).append(">)\n");
        if (cited.isEmpty()) md.append("No cited sources.\n");
        var codec = new ReportDraftCodec();
        String manifest = codec.json(Map.of("policyVersion", POLICY, "demoMode", draft.demoMode(), "references", references));
        return new Rendered(md.toString(), List.copyOf(summary), List.copyOf(references), manifest, codec.serialize(draft));
    }

    private static void appendQuote(StringBuilder md, ClaimEvidenceIndex.Entry entry) {
        md.append("> ").append(text(entry.claim().getExactQuote())).append(" [").append(entry.source().getId()).append("]\n\n");
    }

    /** Entity-encode punctuation so quotes/titles cannot inject Markdown, HTML, links or fake citations. */
    private static String text(String value) {
        if (value == null) return "";
        StringBuilder safe = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp) || cp == ' ') safe.appendCodePoint(cp);
            else if (Character.isWhitespace(cp) || Character.isISOControl(cp)) safe.append(' ');
            else safe.append("&#").append(cp).append(';');
        });
        return safe.toString();
    }

    private static String safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || value.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException();
            return uri.toASCIIString().replace("<", "%3C").replace(">", "%3E").replace("\"", "%22");
        } catch (Exception ex) { throw new IllegalArgumentException("Report evidence has an invalid source URL", ex); }
    }
}
