package com.researchagent.evidence;

import com.researchagent.model.Claim;
import com.researchagent.model.Source;
import java.util.*;

/** Revalidates the complete claim -> fetched snapshot -> original text span chain. */
public final class ClaimEvidenceIndex {
    public record Entry(Claim claim, Source source) {}
    private final Map<String, Entry> entries;

    public ClaimEvidenceIndex(String runId, List<Claim> claims, List<Source> sources) {
        Map<Integer, Source> bySource = new HashMap<>();
        Set<Integer> ambiguousSources = new HashSet<>();
        Map<String, Integer> snapshots = new HashMap<>();
        for (Source source : sources) {
            if (source == null) continue;
            if (bySource.put(source.getId(), source) != null) ambiguousSources.add(source.getId());
            if (source.getSnapshotId() != null) snapshots.merge(source.getSnapshotId(), 1, Integer::sum);
        }
        Map<String, Entry> checked = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>(), ambiguousClaims = new HashSet<>();
        EvidenceValidator validator = new EvidenceValidator();
        for (Claim claim : claims) {
            if (claim == null || claim.getId() == null) continue;
            if (!seen.add(claim.getId())) { ambiguousClaims.add(claim.getId()); continue; }
            Source source = bySource.get(claim.getSourceId());
            QuoteEvidence quote = claim.getQuoteEvidence();
            if (!claim.getId().matches("[A-Za-z0-9_-]{1,64}") || !Objects.equals(runId, claim.getRunId())
                    || source == null || !Objects.equals(runId, source.getRunId()) || !source.isFetchedOk()
                    || source.getId() <= 0 || ambiguousSources.contains(source.getId())
                    || source.getSnapshotId() == null || snapshots.get(source.getSnapshotId()) != 1
                    || source.getContent() == null || source.getContent().isBlank() || quote == null
                    || !Objects.equals(claim.getSourceUrl(), source.getUrl()) || !Objects.equals(quote.sourceUrl(), source.getUrl())
                    || !Objects.equals(quote.sourceSnapshotId(), source.getSnapshotId())
                    || !Objects.equals(quote.sourceContentHash(), source.getContentHash())
                    || !EvidenceValidator.VERSION.equals(quote.normalizationVersion()) || quote.matchStatus() == null
                    || quote.start() < 0 || quote.end() <= quote.start() || quote.end() > source.getContent().length()
                    || !source.getContent().substring(quote.start(), quote.end()).equals(claim.getExactQuote())) continue;
            var prepared = validator.prepare(source.getContent());
            var match = prepared.match(quote.submittedQuote());
            if (!prepared.contentHash().equals(source.getContentHash()) || !match.accepted()
                    || match.start() != quote.start() || match.end() != quote.end() || match.status() != quote.matchStatus()) continue;
            checked.put(claim.getId(), new Entry(claim, source));
        }
        ambiguousClaims.forEach(checked::remove);
        entries = Collections.unmodifiableMap(checked);
    }

    public Collection<Entry> entries() { return entries.values(); }
    public Entry require(String claimId) {
        Entry entry = entries.get(claimId);
        if (entry == null) throw new IllegalArgumentException("Report references a claim without validated source evidence: " + claimId);
        return entry;
    }
}
