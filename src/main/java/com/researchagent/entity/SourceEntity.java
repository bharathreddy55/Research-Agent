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
    @Column(name = "resolved_url", columnDefinition = "TEXT", updatable = false)
    private String resolvedUrl;
    @Column(name = "hostname", length = 253, updatable = false)
    private String hostname;
    @Column(name = "registrable_domain", length = 253, updatable = false)
    private String registrableDomain;
    @Column(name = "publisher_group", length = 128, updatable = false)
    private String publisherGroup;
    @Column(name = "evidence_family_id", length = 64, updatable = false)
    private String evidenceFamilyId;
    @Column(name = "provenance_policy", length = 32, updatable = false)
    private String provenancePolicy;
    public String getResolvedUrl() { return resolvedUrl; }
    public void setResolvedUrl(String value) { resolvedUrl = value; }
    public String getHostname() { return hostname; }
    public void setHostname(String value) { hostname = value; }
    public String getRegistrableDomain() { return registrableDomain; }
    public void setRegistrableDomain(String value) { registrableDomain = value; }
    public String getPublisherGroup() { return publisherGroup; }
    public void setPublisherGroup(String value) { publisherGroup = value; }
    public String getEvidenceFamilyId() { return evidenceFamilyId; }
    public void setEvidenceFamilyId(String value) { evidenceFamilyId = value; }
    public String getProvenancePolicy() { return provenancePolicy; }
    public void setProvenancePolicy(String value) { provenancePolicy = value; }
    @Column(name = "snapshot_id", length = 36, unique = true, updatable = false)
    private String snapshotId;

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", length = 64, nullable = false, updatable = false)
    private String runId;

    @Column(name = "source_number", nullable = false, updatable = false)
    private int sourceNumber; // 1-indexed citation ID

    @Column(name = "url", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String url;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "snippet", columnDefinition = "TEXT")
    private String snippet;

    @Column(name = "domain", length = 128)
    private String domain;

    @Column(name = "content", columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "content_hash", length = 64, updatable = false)
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
