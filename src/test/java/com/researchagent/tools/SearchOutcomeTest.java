package com.researchagent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchOutcomeTest {
    @Test void emptyPublicSearchDoesNotInventWikipediaResult() {
        FallbackSearchService service = new FallbackSearchService(url -> Jsoup.parse("<html><body>No results</body></html>"));

        SearchOutcome result = service.searchOutcome("made-up query", 3);

        assertThat(result.status()).isEqualTo(SearchOutcome.Status.EMPTY);
        assertThat(result.results()).isEmpty();
        assertThat(result.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.provider()).isEqualTo("DuckDuckGo HTML");
            assertThat(attempt.status()).isEqualTo(SearchOutcome.Status.EMPTY);
        });
    }

    @Test void providerFailureStaysUnavailableAndDoesNotInventResult() {
        FallbackSearchService service = new FallbackSearchService(url -> { throw new IllegalStateException("offline"); });

        SearchOutcome result = service.searchOutcome("battery research", 3);

        assertThat(result.status()).isEqualTo(SearchOutcome.Status.UNAVAILABLE);
        assertThat(result.results()).isEmpty();
        assertThat(result.attempts().getFirst().diagnostic()).contains("IllegalStateException");
    }

    @Test void parsesOnlyActualSearchLinksAndDoesNotInventScores() {
        String html = """
                <div class='result'><div class='result__title'><a href='/l/?uddg=https%3A%2F%2Fexample.org%2Fpaper'>Actual study</a></div>
                <div class='result__snippet'>Observed result</div></div>
                <div class='result'><div class='result__title'><a href='javascript:alert(1)'>Invalid</a></div></div>
                """;
        FallbackSearchService service = new FallbackSearchService(url -> Jsoup.parse(html));

        SearchOutcome result = service.searchOutcome("study", 5);

        assertThat(result.status()).isEqualTo(SearchOutcome.Status.SUCCESS);
        assertThat(result.results()).singleElement().satisfies(found -> {
            assertThat(found.getUrl()).isEqualTo("https://example.org/paper");
            assertThat(found.getTitle()).isEqualTo("Actual study");
            assertThat(found.getSnippet()).isEqualTo("Observed result");
            assertThat(found.getScore()).isZero();
        });
    }

    @Test void compositeReportsPartialCoverageWhenSecondarySucceedsAfterPrimaryFailure() {
        SearchOutcome unavailable = SearchOutcome.provider("Tavily", List.of(), SearchOutcome.Status.UNAVAILABLE, "HTTP 503");
        SearchResult found = SearchResult.builder().url("https://example.org/paper").build();
        SearchOutcome success = SearchOutcome.provider("DuckDuckGo HTML", List.of(found), SearchOutcome.Status.EMPTY, "");
        StubTavily primary = new StubTavily(unavailable);
        StubFallback secondary = new StubFallback(success);

        SearchOutcome result = new CompositeSearchService(primary, secondary).searchOutcome("q", 2);

        assertThat(result.status()).isEqualTo(SearchOutcome.Status.PARTIAL);
        assertThat(result.results()).containsExactly(found);
        assertThat(result.attempts()).extracting(SearchOutcome.ProviderAttempt::status)
                .containsExactly(SearchOutcome.Status.UNAVAILABLE, SearchOutcome.Status.SUCCESS);
    }

    @Test void compositePreservesEmptyVersusUnavailableAndRespectsFallbackSwitch() {
        SearchOutcome empty = SearchOutcome.provider("Tavily", List.of(), SearchOutcome.Status.EMPTY, "No results");
        SearchOutcome secondaryEmpty = SearchOutcome.provider("DuckDuckGo HTML", List.of(), SearchOutcome.Status.EMPTY, "No results");
        StubTavily primary = new StubTavily(empty);
        StubFallback secondary = new StubFallback(secondaryEmpty);

        assertThat(new CompositeSearchService(primary, secondary).searchOutcome("q", 2).status())
                .isEqualTo(SearchOutcome.Status.EMPTY);
        assertThat(new CompositeSearchService(primary, secondary, false).searchOutcome("q", 2).attempts())
                .hasSize(1);
        assertThat(secondary.calls).isEqualTo(1);
    }

    @Test void primarySuccessDoesNotQuerySecondary() {
        SearchResult found = SearchResult.builder().url("https://example.org/paper").build();
        StubTavily primary = new StubTavily(
                SearchOutcome.provider("Tavily", List.of(found), SearchOutcome.Status.EMPTY, ""));
        StubFallback secondary = new StubFallback(
                SearchOutcome.provider("DuckDuckGo HTML", List.of(), SearchOutcome.Status.EMPTY, ""));

        assertThat(new CompositeSearchService(primary, secondary).searchOutcome("q", 2).results())
                .containsExactly(found);
        assertThat(secondary.calls).isZero();
    }

    private static final class StubTavily extends TavilySearchService {
        private final SearchOutcome outcome;
        private int calls;

        private StubTavily(SearchOutcome outcome) {
            super("test-key", 1, new ObjectMapper());
            this.outcome = outcome;
        }

        @Override public SearchOutcome searchOutcome(String query, int maxResults) {
            calls++;
            return outcome;
        }
    }

    private static final class StubFallback extends FallbackSearchService {
        private final SearchOutcome outcome;
        private int calls;

        private StubFallback(SearchOutcome outcome) {
            super(url -> Jsoup.parse(""));
            this.outcome = outcome;
        }

        @Override public SearchOutcome searchOutcome(String query, int maxResults) {
            calls++;
            return outcome;
        }
    }
}
