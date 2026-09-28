package com.researchagent.dto;

import java.time.Instant;

public class ReportResponse {
    private String id;
    private String runId;
    private String query;
    private String title;
    private String markdownContent;
    private String pdfPath;
    private int version;
    private double confidenceScore;
    private int totalSources;
    private int verifiedClaimsCount;
    private int unverifiedClaimsCount;
    private Instant createdAt;
    private String previousDraftContent; // For draft comparison (v1 vs v2, Audit B8)

    public ReportResponse() {}

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

    public String getPreviousDraftContent() { return previousDraftContent; }
    public void setPreviousDraftContent(String previousDraftContent) { this.previousDraftContent = previousDraftContent; }
}
