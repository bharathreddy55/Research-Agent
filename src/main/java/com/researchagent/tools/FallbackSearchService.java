package com.researchagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Fallback search service using public web search endpoints.
 * Secondary public search provider. Empty results and connection failures never
 * create guessed URLs or snippets.
 */
@Service
public class FallbackSearchService implements SearchService {
    private static final Logger log = LoggerFactory.getLogger(FallbackSearchService.class);
    private static final String DDG_HTML_URL = "https://html.duckduckgo.com/html/?q=";
    private final Function<String, Document> fetchPage;

    public FallbackSearchService() {
        this(url -> {
            try {
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .timeout(10000)
                        .get();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    FallbackSearchService(Function<String, Document> fetchPage) {
        this.fetchPage = java.util.Objects.requireNonNull(fetchPage);
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        return searchOutcome(query, maxResults).results();
    }

    @Override
    public SearchOutcome searchOutcome(String query, int maxResults) {
        com.researchagent.execution.RunControl.checkActive();
        log.info("Executing Fallback search for query: '{}'", query);
        List<SearchResult> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = fetchPage.apply(DDG_HTML_URL + encodedQuery);

            Elements resultElements = doc.select(".result");
            for (Element resultElement : resultElements) {
                if (results.size() >= maxResults) {
                    break;
                }

                Element titleElement = resultElement.selectFirst(".result__title a");
                Element snippetElement = resultElement.selectFirst(".result__snippet");

                if (titleElement != null) {
                    String title = titleElement.text();
                    String rawUrl = titleElement.attr("href");
                    String actualUrl = extractDuckDuckGoUrl(rawUrl);
                    String snippet = snippetElement != null ? snippetElement.text() : "";

                    if (isWebUrl(actualUrl)) {
                        results.add(SearchResult.builder()
                                .title(title)
                                .url(actualUrl)
                                .snippet(snippet)
                                .domain(SearchResult.extractDomain(actualUrl))
                                .score(0.0) // DDG HTML does not expose a relevance score.
                                .build());
                    }
                }
            }
        } catch (com.researchagent.execution.RunCancelledException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Secondary search unavailable: {}", e.getClass().getSimpleName());
            return SearchOutcome.provider("DuckDuckGo HTML", List.of(), SearchOutcome.Status.UNAVAILABLE,
                    "Provider request failed: " + e.getClass().getSimpleName());
        }

        return SearchOutcome.provider("DuckDuckGo HTML", results, SearchOutcome.Status.EMPTY,
                results.isEmpty() ? "No parsed search results" : "");
    }

    private static boolean isWebUrl(String url) {
        try {
            URI uri = URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractDuckDuckGoUrl(String href) {
        if (href == null || href.isBlank()) return "";
        try {
            if (href.contains("uddg=")) {
                int start = href.indexOf("uddg=") + 5;
                int end = href.indexOf('&', start);
                String encoded = (end != -1) ? href.substring(start, end) : href.substring(start);
                return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            }
            if (href.startsWith("//")) {
                return "https:" + href;
            }
            return href;
        } catch (Exception e) {
            return href;
        }
    }
}
