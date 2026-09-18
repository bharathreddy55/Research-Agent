package com.researchagent.model;

import java.util.HashSet;
import java.util.Set;

public class Claim {
    private String id;
    private String runId;
    private String subQuestion;
    private String statement;
    private String exactQuote;
    private int sourceId;
    private String sourceUrl;
    private String sourceDomain;
    private ClaimStatus status = ClaimStatus.UNVERIFIED;
    private Set<String> supportingDomains = new HashSet<>();
    private Set<Integer> supportingSourceIds = new HashSet<>();
    private String contradictionNotes;

    public Claim() {}

    public Claim(String id, String runId, String subQuestion, String statement, String exactQuote, int sourceId, String sourceUrl, String sourceDomain, ClaimStatus status, Set<String> supportingDomains, Set<Integer> supportingSourceIds, String contradictionNotes) {
        this.id = id;
        this.runId = runId;
        this.subQuestion = subQuestion;
        this.statement = statement;
        this.exactQuote = exactQuote;
        this.sourceId = sourceId;
        this.sourceUrl = sourceUrl;
        this.sourceDomain = sourceDomain;
        this.status = status != null ? status : ClaimStatus.UNVERIFIED;
        this.supportingDomains = supportingDomains != null ? supportingDomains : new HashSet<>();
        this.supportingSourceIds = supportingSourceIds != null ? supportingSourceIds : new HashSet<>();
        this.contradictionNotes = contradictionNotes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String runId;
        private String subQuestion;
        private String statement;
        private String exactQuote;
        private int sourceId;
        private String sourceUrl;
        private String sourceDomain;
        private ClaimStatus status = ClaimStatus.UNVERIFIED;
        private Set<String> supportingDomains = new HashSet<>();
        private Set<Integer> supportingSourceIds = new HashSet<>();
        private String contradictionNotes;

        public Builder id(String id) { this.id = id; return this; }
        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder subQuestion(String subQuestion) { this.subQuestion = subQuestion; return this; }
        public Builder statement(String statement) { this.statement = statement; return this; }
        public Builder exactQuote(String exactQuote) { this.exactQuote = exactQuote; return this; }
        public Builder sourceId(int sourceId) { this.sourceId = sourceId; return this; }
        public Builder sourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
        public Builder sourceDomain(String sourceDomain) { this.sourceDomain = sourceDomain; return this; }
        public Builder status(ClaimStatus status) { this.status = status; return this; }
        public Builder supportingDomains(Set<String> supportingDomains) { this.supportingDomains = supportingDomains; return this; }
        public Builder supportingSourceIds(Set<Integer> supportingSourceIds) { this.supportingSourceIds = supportingSourceIds; return this; }
        public Builder contradictionNotes(String contradictionNotes) { this.contradictionNotes = contradictionNotes; return this; }

        public Claim build() {
            return new Claim(id, runId, subQuestion, statement, exactQuote, sourceId, sourceUrl, sourceDomain, status, supportingDomains, supportingSourceIds, contradictionNotes);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getSubQuestion() { return subQuestion; }
    public void setSubQuestion(String subQuestion) { this.subQuestion = subQuestion; }

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public String getExactQuote() { return exactQuote; }
    public void setExactQuote(String exactQuote) { this.exactQuote = exactQuote; }

    public int getSourceId() { return sourceId; }
    public void setSourceId(int sourceId) { this.sourceId = sourceId; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getSourceDomain() { return sourceDomain; }
    public void setSourceDomain(String sourceDomain) { this.sourceDomain = sourceDomain; }

    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }

    public Set<String> getSupportingDomains() { return supportingDomains; }
    public void setSupportingDomains(Set<String> supportingDomains) { this.supportingDomains = supportingDomains; }

    public Set<Integer> getSupportingSourceIds() { return supportingSourceIds; }
    public void setSupportingSourceIds(Set<Integer> supportingSourceIds) { this.supportingSourceIds = supportingSourceIds; }

    public String getContradictionNotes() { return contradictionNotes; }
    public void setContradictionNotes(String contradictionNotes) { this.contradictionNotes = contradictionNotes; }
}
