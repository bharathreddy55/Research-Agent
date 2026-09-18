package com.researchagent.agent;

import com.researchagent.model.AgentType;
import com.researchagent.model.ResearchDepth;
import com.researchagent.model.ResearchPlan;
import com.researchagent.model.Source;
import com.researchagent.model.StepTrace;
import com.researchagent.tools.SearchResult;
import com.researchagent.tools.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class SearcherAgent {
    private static final Logger log = LoggerFactory.getLogger(SearcherAgent.class);

    private final SearchService searchService;

    public SearcherAgent(SearchService searchService) {
        this.searchService = searchService;
    }

    public static class SearcherResult {
        private final List<Source> sources;
        private final StepTrace stepTrace;

        public SearcherResult(List<Source> sources, StepTrace stepTrace) {
            this.sources = sources;
            this.stepTrace = stepTrace;
        }

        public List<Source> getSources() { return sources; }
        public StepTrace getStepTrace() { return stepTrace; }
    }

    public SearcherResult search(String runId, int seq, ResearchPlan plan, ResearchDepth depth) {
        long startTime = System.currentTimeMillis();
        int maxPages = depth != null ? depth.getMaxPages() : 10;

        List<Source> collectedSources = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        List<String> queries = plan.getSearchQueries();

        int maxPerQuery = Math.max(2, (int) Math.ceil((double) maxPages / Math.max(1, queries.size())));

        for (String query : queries) {
            if (collectedSources.size() >= maxPages) {
                break;
            }

            List<SearchResult> results = searchService.search(query, maxPerQuery);
            for (SearchResult res : results) {
                if (collectedSources.size() >= maxPages) {
                    break;
                }

                String cleanUrl = normalizeUrl(res.getUrl());
                if (!seenUrls.contains(cleanUrl)) {
                    seenUrls.add(cleanUrl);
                    int sourceId = collectedSources.size() + 1; // 1-indexed

                    Source source = Source.builder()
                            .id(sourceId)
                            .runId(runId)
                            .url(res.getUrl())
                            .title(res.getTitle())
                            .snippet(res.getSnippet())
                            .domain(res.getDomain())
                            .fetchedOk(false)
                            .build();

                    collectedSources.add(source);
                }
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        StepTrace trace = StepTrace.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId)
                .seq(seq)
                .agent(AgentType.SEARCHER)
                .action("Gathered and deduplicated " + collectedSources.size() + " web sources across " + queries.size() + " queries")
                .prompt("Queries: " + String.join(" | ", queries))
                .response("Identified " + collectedSources.size() + " candidate URLs (max cap: " + maxPages + ")")
                .tokensIn(0)
                .tokensOut(0)
                .costPaise(0)
                .durationMs(durationMs)
                .build();

        return new SearcherResult(collectedSources, trace);
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        String normalized = url.trim().toLowerCase();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
