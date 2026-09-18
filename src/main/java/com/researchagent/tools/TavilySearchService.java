package com.researchagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TavilySearchService implements SearchService {
    private static final Logger log = LoggerFactory.getLogger(TavilySearchService.class);
    private static final String TAVILY_ENDPOINT = "https://api.tavily.com/search";

    private final String apiKey;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TavilySearchService(
            @Value("${research-agent.search.tavily-api-key:}") String apiKey,
            @Value("${research-agent.search.timeout-seconds:15}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.contains("your_tavily_api_key");
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (!isConfigured()) {
            log.warn("Tavily API key is not configured. Returning empty results.");
            return List.of();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("api_key", apiKey);
            payload.put("query", query);
            payload.put("search_depth", "basic");
            payload.put("max_results", Math.max(1, maxResults));
            payload.put("include_answer", false);

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Tavily search returned HTTP {}: {}", response.statusCode(), response.body());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resultsNode = root.path("results");
            List<SearchResult> results = new ArrayList<>();

            if (resultsNode.isArray()) {
                for (JsonNode item : resultsNode) {
                    String title = item.path("title").asText("");
                    String url = item.path("url").asText("");
                    String content = item.path("content").asText("");
                    double score = item.path("score").asDouble(0.0);

                    if (!url.isBlank()) {
                        results.add(SearchResult.builder()
                                .title(title)
                                .url(url)
                                .snippet(content)
                                .domain(SearchResult.extractDomain(url))
                                .score(score)
                                .build());
                    }
                }
            }

            log.info("Tavily search for '{}' returned {} results", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("Error executing Tavily search for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }
}
