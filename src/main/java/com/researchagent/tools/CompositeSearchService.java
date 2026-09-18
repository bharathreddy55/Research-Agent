package com.researchagent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class CompositeSearchService implements SearchService {
    private static final Logger log = LoggerFactory.getLogger(CompositeSearchService.class);

    private final TavilySearchService tavilySearchService;
    private final FallbackSearchService fallbackSearchService;

    public CompositeSearchService(TavilySearchService tavilySearchService, FallbackSearchService fallbackSearchService) {
        this.tavilySearchService = tavilySearchService;
        this.fallbackSearchService = fallbackSearchService;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (tavilySearchService.isConfigured()) {
            List<SearchResult> results = tavilySearchService.search(query, maxResults);
            if (!results.isEmpty()) {
                return results;
            }
            log.warn("Tavily search returned 0 results for '{}'. Falling back to secondary search provider.", query);
        } else {
            log.info("Tavily API not configured. Utilizing fallback search provider for '{}'", query);
        }

        return fallbackSearchService.search(query, maxResults);
    }
}
