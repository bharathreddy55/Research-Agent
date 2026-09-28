package com.researchagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "claims", indexes = {
        @Index(name = "idx_claims_run_id", columnList = "run_id")
})
public class ClaimEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "statement", columnDefinition = "TEXT", nullable = false)
    private String statement;

    @Column(name = "exact_quote", columnDefinition = "TEXT")
    private String exactQuote;

    @Column(name = "source_id")
    private int sourceId;

    @Column(name = "source_domain", length = 128)
    private String sourceDomain;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "supporting_domains", columnDefinition = "TEXT")
    private String supportingDomains;

    @Column(name = "supporting_source_ids", columnDefinition = "TEXT")
    private String supportingSourceIds;

    @Column(name = "contradiction_notes", columnDefinition = "TEXT")
    private String contradictionNotes;

    public ClaimEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public String getExactQuote() { return exactQuote; }
    public void setExactQuote(String exactQuote) { this.exactQuote = exactQuote; }

    public int getSourceId() { return sourceId; }
    public void setSourceId(int sourceId) { this.sourceId = sourceId; }

    public String getSourceDomain() { return sourceDomain; }
    public void setSourceDomain(String sourceDomain) { this.sourceDomain = sourceDomain; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSupportingDomains() { return supportingDomains; }
    public void setSupportingDomains(String supportingDomains) { this.supportingDomains = supportingDomains; }

    public String getSupportingSourceIds() { return supportingSourceIds; }
    public void setSupportingSourceIds(String supportingSourceIds) { this.supportingSourceIds = supportingSourceIds; }

    public String getContradictionNotes() { return contradictionNotes; }
    public void setContradictionNotes(String contradictionNotes) { this.contradictionNotes = contradictionNotes; }
}
