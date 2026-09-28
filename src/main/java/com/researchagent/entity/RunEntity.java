package com.researchagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "runs")
public class RunEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "query", columnDefinition = "TEXT", nullable = false)
    private String query;

    @Column(name = "depth", length = 32, nullable = false)
    private String depth;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "budget_cap_paise")
    private long budgetCapPaise;

    @Column(name = "cost_paise")
    private long costPaise;

    @Column(name = "tokens_in")
    private int tokensIn;

    @Column(name = "tokens_out")
    private int tokensOut;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    public RunEntity() {}

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
}
