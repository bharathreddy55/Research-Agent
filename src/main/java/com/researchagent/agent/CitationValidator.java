package com.researchagent.agent;

import com.researchagent.model.AgentType;
import com.researchagent.model.Source;
import com.researchagent.model.StepTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CitationValidator {
    private static final Logger log = LoggerFactory.getLogger(CitationValidator.class);
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)\\]");

    public static class CitationValidationResult {
        private final String sanitizedMarkdown;
        private final Set<Integer> validCitationIds;
        private final Set<Integer> hallucinatedCitationIds;
        private final Set<Integer> deadUrlCitationIds;
        private final StepTrace stepTrace;

        public CitationValidationResult(
                String sanitizedMarkdown,
                Set<Integer> validCitationIds,
                Set<Integer> hallucinatedCitationIds,
                Set<Integer> deadUrlCitationIds,
                StepTrace stepTrace) {
            this.sanitizedMarkdown = sanitizedMarkdown;
            this.validCitationIds = validCitationIds;
            this.hallucinatedCitationIds = hallucinatedCitationIds;
            this.deadUrlCitationIds = deadUrlCitationIds;
            this.stepTrace = stepTrace;
        }

        public String getSanitizedMarkdown() { return sanitizedMarkdown; }
        public Set<Integer> getValidCitationIds() { return validCitationIds; }
        public Set<Integer> getHallucinatedCitationIds() { return hallucinatedCitationIds; }
        public Set<Integer> getDeadUrlCitationIds() { return deadUrlCitationIds; }
        public StepTrace getStepTrace() { return stepTrace; }
        public boolean hasAnomalies() {
            return !hallucinatedCitationIds.isEmpty() || !deadUrlCitationIds.isEmpty();
        }
    }

    public CitationValidationResult validateAndSanitize(
            String runId,
            int seq,
            String rawMarkdown,
            List<Source> sources) {

        long startTime = System.currentTimeMillis();

        Map<Integer, Source> sourceMap = sources.stream()
                .collect(Collectors.toMap(Source::getId, Function.identity(), (s1, s2) -> s1));

        Set<Integer> validIds = new HashSet<>();
        Set<Integer> hallucinatedIds = new HashSet<>();
        Set<Integer> deadUrlIds = new HashSet<>();

        // Scan all [n] citations
        Matcher matcher = CITATION_PATTERN.matcher(rawMarkdown);
        while (matcher.find()) {
            try {
                int id = Integer.parseInt(matcher.group(1));
                Source s = sourceMap.get(id);
                if (s == null) {
                    hallucinatedIds.add(id);
                } else if (!s.isFetchedOk()) {
                    deadUrlIds.add(id);
                } else {
                    validIds.add(id);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Sanitize markdown: strip hallucinated citations and flag dead URLs
        StringBuffer sb = new StringBuffer();
        matcher.reset();
        while (matcher.find()) {
            try {
                int id = Integer.parseInt(matcher.group(1));
                if (hallucinatedIds.contains(id)) {
                    // Strip hallucinated citation
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
                } else if (deadUrlIds.contains(id)) {
                    // Flag dead URL
                    matcher.appendReplacement(sb, Matcher.quoteReplacement("[" + id + "†dead-source]"));
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                }
            } catch (NumberFormatException ignored) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        String sanitized = sb.toString();

        // Remove double spaces introduced by stripped citations
        sanitized = sanitized.replaceAll(" {2,}", " ");

        long durationMs = System.currentTimeMillis() - startTime;
        String actionSummary = String.format("Validated citations: %d valid, %d hallucinated (stripped), %d dead-URL",
                validIds.size(), hallucinatedIds.size(), deadUrlIds.size());

        if (!hallucinatedIds.isEmpty()) {
            log.warn("CitationValidator caught hallucinated citation IDs in run {}: {}", runId, hallucinatedIds);
        }
        if (!deadUrlIds.isEmpty()) {
            log.warn("CitationValidator flagged dead-URL citation IDs in run {}: {}", runId, deadUrlIds);
        }

        StepTrace trace = StepTrace.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId)
                .seq(seq)
                .agent(AgentType.CITATION_VALIDATOR)
                .action(actionSummary)
                .prompt("Verified all [n] inline citations against sources table (" + sources.size() + " total sources)")
                .response(String.format("Passed: %s | Stripped: %s | Dead-URL: %s", validIds, hallucinatedIds, deadUrlIds))
                .tokensIn(0)
                .tokensOut(0)
                .costPaise(0)
                .durationMs(durationMs)
                .build();

        return new CitationValidationResult(sanitized, validIds, hallucinatedIds, deadUrlIds, trace);
    }
}
