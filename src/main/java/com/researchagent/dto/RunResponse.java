package com.researchagent.dto;

import java.time.Instant;

public class RunResponse {
    private String id;
    private String userId;
    private String query;
    private String depth;
    private String status;
    private long budgetCapPaise;
    private long costPaise;
    private int tokensIn;
    private int tokensOut;
    private Instant startedAt;
    private Instant finishedAt;
    private String failureReason;
    private Double confidenceScore;

    public RunResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getDepth() { return depth; }
    public void setDepth(String depth) { this.depth = depth; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getBudgetCapPaise() { return budgetCapPaise; }
    public void setBudgetCapPaise(long budgetCapPaise) { this.budgetCapPaise = budgetCapPaise; }

    public long getCostPaise() { return costPaise; }
    public void setCostPaise(long costPaise) { this.costPaise = costPaise; }

    public int getTokensIn() { return tokensIn; }
    public void setTokensIn(int tokensIn) { this.tokensIn = tokensIn; }

    public int getTokensOut() { return tokensOut; }
    public void setTokensOut(int tokensOut) { this.tokensOut = tokensOut; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
}
