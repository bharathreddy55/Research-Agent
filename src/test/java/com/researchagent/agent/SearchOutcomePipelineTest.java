package com.researchagent.agent;

import com.researchagent.model.ResearchDepth;
import com.researchagent.model.ResearchPlan;
import com.researchagent.tools.SearchOutcome;
import com.researchagent.tools.SearchResult;
import com.researchagent.tools.SearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchOutcomePipelineTest {
    @Test void searchTracePreservesPartialProviderCoverageAndRealSourceOnly() {
        SearchResult found = SearchResult.builder().url("https://example.org/study")
                .title("Discovered study").domain("example.org").build();
        SearchService search = new SearchService() {
            @Override public List<SearchResult> search(String query, int maxResults) { return List.of(found); }
            @Override public SearchOutcome searchOutcome(String query, int maxResults) {
                return new SearchOutcome(List.of(found), SearchOutcome.Status.PARTIAL, List.of(
                        new SearchOutcome.ProviderAttempt("Tavily", SearchOutcome.Status.UNAVAILABLE, 0, "HTTP 503"),
                        new SearchOutcome.ProviderAttempt("DuckDuckGo HTML", SearchOutcome.Status.SUCCESS, 1, "")));
            }
        };
        ResearchPlan plan = ResearchPlan.builder().searchQueries(List.of("question")).build();

        var result = new SearcherAgent(search).search("run", 1, plan, ResearchDepth.QUICK);

        assertThat(result.getStatus()).isEqualTo(SearchOutcome.Status.PARTIAL);
        assertThat(result.getSources()).singleElement().satisfies(source ->
                assertThat(source.getUrl()).isEqualTo("https://example.org/study"));
        assertThat(result.getStepTrace().getResponse()).contains("PARTIAL", "Tavily: UNAVAILABLE", "DuckDuckGo HTML: SUCCESS");
    }

    @Test void emptyAndUnavailableSearchesStayDistinctWithoutSources() {
        ResearchPlan plan = ResearchPlan.builder().searchQueries(List.of("question")).build();
        for (SearchOutcome.Status status : List.of(SearchOutcome.Status.EMPTY, SearchOutcome.Status.UNAVAILABLE)) {
            SearchService search = new SearchService() {
                @Override public List<SearchResult> search(String query, int maxResults) { return List.of(); }
                @Override public SearchOutcome searchOutcome(String query, int maxResults) {
                    return SearchOutcome.provider("Provider", List.of(), status, "No sources");
                }
            };
            var result = new SearcherAgent(search).search("run", 1, plan, ResearchDepth.QUICK);
            assertThat(result.getStatus()).isEqualTo(status);
            assertThat(result.getSources()).isEmpty();
        }
    }
}
