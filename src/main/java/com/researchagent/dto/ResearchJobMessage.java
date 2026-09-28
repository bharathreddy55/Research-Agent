package com.researchagent.dto;

import java.io.Serializable;

public class ResearchJobMessage implements Serializable {
    private String runId;
    private String userId;
    private String query;
    private String depth;
    private long budgetCapPaise;

    public ResearchJobMessage() {}

    public ResearchJobMessage(String runId, String userId, String query, String depth, long budgetCapPaise) {
        this.runId = runId;
        this.userId = userId;
        this.query = query;
        this.depth = depth;
        this.budgetCapPaise = budgetCapPaise;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getDepth() { return depth; }
    public void setDepth(String depth) { this.depth = depth; }

    public long getBudgetCapPaise() { return budgetCapPaise; }
    public void setBudgetCapPaise(long budgetCapPaise) { this.budgetCapPaise = budgetCapPaise; }
}
