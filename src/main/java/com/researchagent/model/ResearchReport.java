package com.researchagent.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ResearchReport {
    private String id;
    private String runId;
    private String query;
    private String title;
    private List<String> executiveSummary = new ArrayList<>();
    private String markdownContent;
    private String pdfPath;
    private int version = 1;
    private double confidenceScore;
    private int totalSources;
    private int verifiedClaimsCount;
    private int unverifiedClaimsCount;
    private List<Source> sources = new ArrayList<>();
    private List<Claim> claims = new ArrayList<>();
    private Instant createdAt = Instant.now();

    public ResearchReport() {}

    public ResearchReport(String id, String runId, String query, String title, List<String> executiveSummary, String markdownContent, String pdfPath, int version, double confidenceScore, int totalSources, int verifiedClaimsCount, int unverifiedClaimsCount, List<Source> sources, List<Claim> claims, Instant createdAt) {
        this.id = id;
        this.runId = runId;
        this.query = query;
        this.title = title;
        this.executiveSummary = executiveSummary != null ? executiveSummary : new ArrayList<>();
        this.markdownContent = markdownContent;
        this.pdfPath = pdfPath;
        this.version = version;
        this.confidenceScore = confidenceScore;
        this.totalSources = totalSources;
        this.verifiedClaimsCount = verifiedClaimsCount;
        this.unverifiedClaimsCount = unverifiedClaimsCount;
        this.sources = sources != null ? sources : new ArrayList<>();
        this.claims = claims != null ? claims : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String runId;
        private String query;
        private String title;
        private List<String> executiveSummary = new ArrayList<>();
        private String markdownContent;
        private String pdfPath;
        private int version = 1;
        private double confidenceScore;
        private int totalSources;
        private int verifiedClaimsCount;
        private int unverifiedClaimsCount;
        private List<Source> sources = new ArrayList<>();
        private List<Claim> claims = new ArrayList<>();
        private Instant createdAt = Instant.now();

        public Builder id(String id) { this.id = id; return this; }
        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder query(String query) { this.query = query; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder executiveSummary(List<String> executiveSummary) { this.executiveSummary = executiveSummary; return this; }
        public Builder markdownContent(String markdownContent) { this.markdownContent = markdownContent; return this; }
        public Builder pdfPath(String pdfPath) { this.pdfPath = pdfPath; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder confidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; return this; }
        public Builder totalSources(int totalSources) { this.totalSources = totalSources; return this; }
        public Builder verifiedClaimsCount(int verifiedClaimsCount) { this.verifiedClaimsCount = verifiedClaimsCount; return this; }
        public Builder unverifiedClaimsCount(int unverifiedClaimsCount) { this.unverifiedClaimsCount = unverifiedClaimsCount; return this; }
        public Builder sources(List<Source> sources) { this.sources = sources; return this; }
        public Builder claims(List<Claim> claims) { this.claims = claims; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public ResearchReport build() {
            return new ResearchReport(id, runId, query, title, executiveSummary, markdownContent, pdfPath, version, confidenceScore, totalSources, verifiedClaimsCount, unverifiedClaimsCount, sources, claims, createdAt);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getExecutiveSummary() { return executiveSummary; }
    public void setExecutiveSummary(List<String> executiveSummary) { this.executiveSummary = executiveSummary; }

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

    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> sources) { this.sources = sources; }

    public List<Claim> getClaims() { return claims; }
    public void setClaims(List<Claim> claims) { this.claims = claims; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
