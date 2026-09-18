package com.researchagent.tools;

import java.net.URI;

public class SearchResult {
    private String title;
    private String url;
    private String snippet;
    private String domain;
    private double score;

    public SearchResult() {}

    public SearchResult(String title, String url, String snippet, String domain, double score) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.domain = domain;
        this.score = score;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String title;
        private String url;
        private String snippet;
        private String domain;
        private double score;

        public Builder title(String title) { this.title = title; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder snippet(String snippet) { this.snippet = snippet; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder score(double score) { this.score = score; return this; }

        public SearchResult build() {
            return new SearchResult(title, url, snippet, domain, score);
        }
    }

    public static String extractDomain(String urlString) {
        try {
            URI uri = new URI(urlString);
            String host = uri.getHost();
            if (host == null) return "unknown";
            if (host.startsWith("www.")) {
                return host.substring(4);
            }
            return host.toLowerCase();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
