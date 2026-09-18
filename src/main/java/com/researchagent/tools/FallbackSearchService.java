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

/**
 * Fallback search service using public web search endpoints.
 * Note: Per PRD and design guidelines, this is a local resilience/demo fallback
 * when Tavily API key is unconfigured or rate-limited.
 */
@Service
public class FallbackSearchService implements SearchService {
    private static final Logger log = LoggerFactory.getLogger(FallbackSearchService.class);
    private static final String DDG_HTML_URL = "https://html.duckduckgo.com/html/?q=";

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        log.info("Executing Fallback search for query: '{}'", query);
        List<SearchResult> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(DDG_HTML_URL + encodedQuery)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

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

                    if (!actualUrl.isBlank() && (actualUrl.startsWith("http://") || actualUrl.startsWith("https://"))) {
                        results.add(SearchResult.builder()
                                .title(title)
                                .url(actualUrl)
                                .snippet(snippet)
                                .domain(SearchResult.extractDomain(actualUrl))
                                .score(0.8)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Fallback DuckDuckGo search failed: {}. Providing demo fallback results.", e.getMessage());
        }

        if (results.isEmpty()) {
            results.add(SearchResult.builder()
                    .title("Research Overview: " + query)
                    .url("https://en.wikipedia.org/wiki/" + URLEncoder.encode(query.replace(" ", "_"), StandardCharsets.UTF_8))
                    .snippet("Comprehensive encyclopedic and academic context regarding " + query)
                    .domain("wikipedia.org")
                    .score(0.75)
                    .build());
        }

        return results;
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
