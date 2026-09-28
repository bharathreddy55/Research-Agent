package com.researchagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "sources", indexes = {
        @Index(name = "idx_sources_run_id", columnList = "run_id")
})
public class SourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "source_number", nullable = false)
    private int sourceNumber; // 1-indexed citation ID

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "snippet", columnDefinition = "TEXT")
    private String snippet;

    @Column(name = "domain", length = 128)
    private String domain;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "fetched_ok")
    private boolean fetchedOk;

    @Column(name = "fetch_error", columnDefinition = "TEXT")
    private String fetchError;

    public SourceEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public int getSourceNumber() { return sourceNumber; }
    public void setSourceNumber(int sourceNumber) { this.sourceNumber = sourceNumber; }

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
