package com.researchagent.model;

import java.time.Instant;

public class StepTrace {
    private String id;
    private String runId;
    private int seq;
    private AgentType agent;
    private String action;
    private String prompt;
    private String response;
    private int tokensIn;
    private int tokensOut;
    private long costPaise;
    private long durationMs;
    private Instant createdAt = Instant.now();

    public StepTrace() {}

    public StepTrace(String id, String runId, int seq, AgentType agent, String action, String prompt, String response, int tokensIn, int tokensOut, long costPaise, long durationMs, Instant createdAt) {
        this.id = id;
        this.runId = runId;
        this.seq = seq;
        this.agent = agent;
        this.action = action;
        this.prompt = prompt;
        this.response = response;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.costPaise = costPaise;
        this.durationMs = durationMs;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String runId;
        private int seq;
        private AgentType agent;
        private String action;
        private String prompt;
        private String response;
        private int tokensIn;
        private int tokensOut;
        private long costPaise;
        private long durationMs;
        private Instant createdAt = Instant.now();

        public Builder id(String id) { this.id = id; return this; }
        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder seq(int seq) { this.seq = seq; return this; }
        public Builder agent(AgentType agent) { this.agent = agent; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder prompt(String prompt) { this.prompt = prompt; return this; }
        public Builder response(String response) { this.response = response; return this; }
        public Builder tokensIn(int tokensIn) { this.tokensIn = tokensIn; return this; }
        public Builder tokensOut(int tokensOut) { this.tokensOut = tokensOut; return this; }
        public Builder costPaise(long costPaise) { this.costPaise = costPaise; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public StepTrace build() {
            return new StepTrace(id, runId, seq, agent, action, prompt, response, tokensIn, tokensOut, costPaise, durationMs, createdAt);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public AgentType getAgent() { return agent; }
    public void setAgent(AgentType agent) { this.agent = agent; }

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
