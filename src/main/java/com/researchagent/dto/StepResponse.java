package com.researchagent.dto;

import java.time.Instant;

public class StepResponse {
    private String id;
    private String runId;
    private int seq;
    private String agent;
    private String action;
    private String prompt;
    private String response;
    private int tokensIn;
    private int tokensOut;
    private long costPaise;
    private long durationMs;
    private Instant createdAt;

    public StepResponse() {}

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
