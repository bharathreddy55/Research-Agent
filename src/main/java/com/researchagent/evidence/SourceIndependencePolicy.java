package com.researchagent.evidence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.InternetDomainName;
import com.researchagent.model.Claim;
import com.researchagent.model.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.URI;
import java.util.*;

/** Conservative evidence-family grouping. Distinct families are candidates, not proof of independence. */
@Component
public class SourceIndependencePolicy {
    public static final String VERSION = "source-families-v1";
    private final Map<String, String> publisherGroups;

    @Autowired
    public SourceIndependencePolicy(@Value("${research-agent.evidence.publisher-groups-json:{}}") String json,
                                    ObjectMapper mapper) {
        this(parseGroups(json, mapper));
    }

    public SourceIndependencePolicy(Map<String, String> groups) {
        Map<String, String> checked = new TreeMap<>();
        groups.forEach((domain, group) -> {
            String canonical = registrableDomain("https://" + domain);
            if (canonical == null || !canonical.equals(domain) || group == null || group.isBlank() || group.length() > 128)
                throw new IllegalArgumentException("Publisher groups require canonical registrable domains and nonempty group IDs (max 128 characters)");
            checked.put(canonical, group);
        });
        publisherGroups = Map.copyOf(checked);
    }

    private static Map<String, String> parseGroups(String json, ObjectMapper mapper) {
        try {
            var root = mapper.readTree(json);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            var fields = root.fields();
            while (fields.hasNext()) if (!fields.next().getValue().isTextual()) throw new IllegalArgumentException();
            return mapper.convertValue(root, new TypeReference<Map<String, String>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid publisher-groups-json configuration", ex);
        }
    }

    public static String hostname(String url) {
        try {
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getRawUserInfo() != null) return null;
            String host = uri.getHost();
            if (host == null) {
                String authority = uri.getRawAuthority();
                if (authority == null || authority.contains("@") || authority.contains(":")) return null;
                host = authority;
            }
            host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        } catch (Exception ex) { return null; }
    }

    public static String registrableDomain(String url) {
        try {
            String host = hostname(url);
            if (host == null) return null;
            InternetDomainName domain = InternetDomainName.from(host);
            return domain.isUnderPublicSuffix() ? domain.topPrivateDomain().toString() : null;
        } catch (IllegalArgumentException ex) { return null; }
    }

    public Assessment assess(List<Source> input) {
        List<Source> sources = input == null ? List.of() : input.stream()
                .filter(Objects::nonNull).sorted(Comparator.comparingInt(Source::getId)).toList();
        Map<Integer, Source> byId = new HashMap<>();
        Set<Integer> duplicateIds = new HashSet<>();
        Map<String, Integer> snapshotCounts = new HashMap<>();
        for (Source source : sources) {
            source.setProvenance(null);
            if (byId.put(source.getId(), source) != null) duplicateIds.add(source.getId());
            if (source.getSnapshotId() != null) snapshotCounts.merge(source.getSnapshotId(), 1, Integer::sum);
        }
        List<Snapshot> valid = new ArrayList<>();
        EvidenceValidator validator = new EvidenceValidator();
        for (Source source : sources) {
            if (source.getId() <= 0 || duplicateIds.contains(source.getId()) || !source.isFetchedOk()
                    || source.getContent() == null || source.getContent().isBlank() || source.getSnapshotId() == null
                    || snapshotCounts.get(source.getSnapshotId()) != 1
                    || !validator.prepare(source.getContent()).contentHash().equals(source.getContentHash())) continue;
            String url = source.getResolvedUrl() == null ? source.getUrl() : source.getResolvedUrl();
            String host = hostname(url);
            String domain = registrableDomain(url);
            String publisher = domain == null ? null : publisherGroups.get(domain);
            String normalized = normalized(source.getContent());
            valid.add(new Snapshot(source, host, domain, publisher, normalized, shingles(normalized)));
        }
        int[] parents = new int[valid.size()];
        for (int i = 0; i < parents.length; i++) parents[i] = i;
        for (int i = 0; i < valid.size(); i++) for (int j = i + 1; j < valid.size(); j++) {
            Snapshot a = valid.get(i), b = valid.get(j);
            if (same(a.domain, b.domain) || same(a.publisher, b.publisher)
                    || a.normalized.equals(b.normalized) || nearCopy(a.shingles, b.shingles)) union(parents, i, j);
        }
        for (int i = 0; i < valid.size(); i++) {
            Snapshot snapshot = valid.get(i);
            String family = "family-" + valid.get(root(parents, i)).source.getSnapshotId();
            snapshot.source.setProvenance(new SourceProvenance(snapshot.host, snapshot.domain, snapshot.publisher, family, VERSION));
        }
        return new Assessment(byId, duplicateIds);
    }

    private record Snapshot(Source source, String host, String domain, String publisher, String normalized, Set<String> shingles) {}

    public static final class Assessment {
        private final Map<Integer, Source> sources;
        private final Set<Integer> duplicateIds;
        private Assessment(Map<Integer, Source> sources, Set<Integer> duplicateIds) {
            this.sources = sources; this.duplicateIds = duplicateIds;
        }
        public Source sourceFor(Claim claim) {
            Source source = sources.get(claim.getSourceId());
            QuoteEvidence quote = claim.getQuoteEvidence();
            if (source == null || duplicateIds.contains(claim.getSourceId()) || source.getProvenance() == null || quote == null
                    || !Objects.equals(claim.getRunId(), source.getRunId())
                    || !Objects.equals(claim.getSourceUrl(), source.getUrl())
                    || !Objects.equals(quote.sourceUrl(), source.getUrl())
                    || !Objects.equals(quote.sourceSnapshotId(), source.getSnapshotId())
                    || !Objects.equals(quote.sourceContentHash(), source.getContentHash())
                    || quote.start() < 0 || quote.end() <= quote.start() || quote.end() > source.getContent().length()
                    || !source.getContent().substring(quote.start(), quote.end()).equals(claim.getExactQuote())
                    || !EvidenceValidator.VERSION.equals(quote.normalizationVersion()) || quote.matchStatus() == null) return null;
            var match = new EvidenceValidator().prepare(source.getContent()).match(quote.submittedQuote());
            return match.accepted() && match.start() == quote.start() && match.end() == quote.end()
                    && match.status() == quote.matchStatus() ? source : null;
        }
    }

    /** Long repeated passages are conservatively treated as potentially shared origin. */
    public static boolean sharedQuotation(Claim a, Claim b) {
        String first = normalized(a.getExactQuote()), second = normalized(b.getExactQuote());
        return first.split(" ").length >= 20 && second.split(" ").length >= 20
                && (first.equals(second) || nearCopy(shingles(first), shingles(second)));
    }

    private static String normalized(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    private static Set<String> shingles(String text) {
        String[] words = text.split(" ");
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 5 <= words.length; i++) result.add(String.join(" ", Arrays.copyOfRange(words, i, i + 5)));
        return result;
    }
    private static boolean nearCopy(Set<String> a, Set<String> b) {
        if (Math.min(a.size(), b.size()) < 20) return false;
        long common = a.stream().filter(b::contains).count();
        return (double) common / Math.min(a.size(), b.size()) >= 0.85;
    }
    private static boolean same(String a, String b) { return a != null && a.equals(b); }
    public static int root(int[] parents, int i) {
        while (parents[i] != i) { parents[i] = parents[parents[i]]; i = parents[i]; }
        return i;
    }
    public static void union(int[] parents, int a, int b) {
        int first = root(parents, a), second = root(parents, b);
        parents[Math.max(first, second)] = Math.min(first, second);
    }
}
