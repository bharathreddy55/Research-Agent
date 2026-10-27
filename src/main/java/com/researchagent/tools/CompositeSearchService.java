package com.researchagent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Service
@Primary
public class CompositeSearchService implements SearchService {
    private static final Logger log = LoggerFactory.getLogger(CompositeSearchService.class);

    private final TavilySearchService tavilySearchService;
    private final FallbackSearchService fallbackSearchService;
    private final boolean allowSecondary;

    public CompositeSearchService(TavilySearchService tavilySearchService, FallbackSearchService fallbackSearchService) {
        this(tavilySearchService, fallbackSearchService, true);
    }

    @Autowired
    public CompositeSearchService(TavilySearchService tavilySearchService, FallbackSearchService fallbackSearchService,
                                  @Value("${research-agent.search.allow-secondary-fallback:true}") boolean allowSecondary) {
        this.tavilySearchService = tavilySearchService;
        this.fallbackSearchService = fallbackSearchService;
        this.allowSecondary = allowSecondary;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        return searchOutcome(query, maxResults).results();
    }

    @Override
    public SearchOutcome searchOutcome(String query, int maxResults) {
        com.researchagent.execution.RunControl.checkActive();
        SearchOutcome primary = tavilySearchService.searchOutcome(query, maxResults);
        if (!primary.results().isEmpty()) {
            return primary;
        }
        if (!allowSecondary) return primary;
        log.info("Primary search returned {}; trying secondary provider", primary.status());
        com.researchagent.execution.RunControl.checkActive();
        SearchOutcome secondary = fallbackSearchService.searchOutcome(query, maxResults);
        return SearchOutcome.combine(primary, secondary);
    }
}
