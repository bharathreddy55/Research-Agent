package com.researchagent.model;

public class Source {
    private int id; // 1-indexed citation number
    private String runId;
    private String url;
    private String title;
    private String snippet;
    private String domain;
    private String content;
    private String contentHash;
    private boolean fetchedOk;
    private String fetchError;

    public Source() {}

    public Source(int id, String runId, String url, String title, String snippet, String domain, String content, String contentHash, boolean fetchedOk, String fetchError) {
        this.id = id;
        this.runId = runId;
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.domain = domain;
        this.content = content;
        this.contentHash = contentHash;
        this.fetchedOk = fetchedOk;
        this.fetchError = fetchError;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private String runId;
        private String url;
        private String title;
        private String snippet;
        private String domain;
        private String content;
        private String contentHash;
        private boolean fetchedOk;
        private String fetchError;

        public Builder id(int id) { this.id = id; return this; }
        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder snippet(String snippet) { this.snippet = snippet; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder contentHash(String contentHash) { this.contentHash = contentHash; return this; }
        public Builder fetchedOk(boolean fetchedOk) { this.fetchedOk = fetchedOk; return this; }
        public Builder fetchError(String fetchError) { this.fetchError = fetchError; return this; }

        public Source build() {
            return new Source(id, runId, url, title, snippet, domain, content, contentHash, fetchedOk, fetchError);
        }
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public boolean isFetchedOk() { return fetchedOk; }
    public void setFetchedOk(boolean fetchedOk) { this.fetchedOk = fetchedOk; }

    public String getFetchError() { return fetchError; }
    public void setFetchError(String fetchError) { this.fetchError = fetchError; }
}
