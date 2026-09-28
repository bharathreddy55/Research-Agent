package com.researchagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "steps", indexes = {
        @Index(name = "idx_steps_run_id", columnList = "run_id"),
        @Index(name = "idx_steps_seq", columnList = "run_id, seq")
})
public class StepEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "agent", length = 32, nullable = false)
    private String agent;

    @Column(name = "action", columnDefinition = "TEXT")
    private String action;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @Column(name = "tokens_in")
    private int tokensIn;

    @Column(name = "tokens_out")
    private int tokensOut;

    @Column(name = "cost_paise")
    private long costPaise;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "created_at")
    private Instant createdAt;

    public StepEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public int getTokensIn() { return tokensIn; }
    public void setTokensIn(int tokensIn) { this.tokensIn = tokensIn; }

    public int getTokensOut() { return tokensOut; }
    public void setTokensOut(int tokensOut) { this.tokensOut = tokensOut; }

    public long getCostPaise() { return costPaise; }
    public void setCostPaise(long costPaise) { this.costPaise = costPaise; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
