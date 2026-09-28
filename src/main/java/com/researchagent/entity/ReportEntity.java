package com.researchagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_run_id", columnList = "run_id"),
        @Index(name = "idx_reports_version", columnList = "run_id, version")
})
public class ReportEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "query", columnDefinition = "TEXT", nullable = false)
    private String query;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "markdown_content", columnDefinition = "TEXT", nullable = false)
    private String markdownContent;

    @Column(name = "pdf_path", length = 512)
    private String pdfPath;

    @Column(name = "version", nullable = false)
    private int version; // 1 = draft, 2 = revised (Audit B8)

    @Column(name = "confidence_score")
    private double confidenceScore;

    @Column(name = "total_sources")
    private int totalSources;

    @Column(name = "verified_claims_count")
    private int verifiedClaimsCount;

    @Column(name = "unverified_claims_count")
    private int unverifiedClaimsCount;

    @Column(name = "created_at")
    private Instant createdAt;

    public ReportEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMarkdownContent() { return markdownContent; }
    public void setMarkdownContent(String markdownContent) { this.markdownContent = markdownContent; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public int getTotalSources() { return totalSources; }
    public void setTotalSources(int totalSources) { this.totalSources = totalSources; }

    public int getVerifiedClaimsCount() { return verifiedClaimsCount; }
    public void setVerifiedClaimsCount(int verifiedClaimsCount) { this.verifiedClaimsCount = verifiedClaimsCount; }

    public int getUnverifiedClaimsCount() { return unverifiedClaimsCount; }
    public void setUnverifiedClaimsCount(int unverifiedClaimsCount) { this.unverifiedClaimsCount = unverifiedClaimsCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
