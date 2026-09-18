package com.researchagent.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Run {
    private String id;
    private String query;
    private ResearchDepth depth = ResearchDepth.STANDARD;
    private RunStatus status = RunStatus.QUEUED;
    private long budgetCapPaise = 1500; // default ₹15
    private long costPaise;
    private int tokensIn;
    private int tokensOut;
    private String failureReason;
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
    private ResearchReport report;
    private List<StepTrace> steps = new ArrayList<>();
    private List<Source> sources = new ArrayList<>();
    private List<Claim> claims = new ArrayList<>();

    public Run() {}

    public Run(String id, String query, ResearchDepth depth, RunStatus status, long budgetCapPaise, long costPaise, int tokensIn, int tokensOut, String failureReason, Instant startedAt, Instant finishedAt, ResearchReport report, List<StepTrace> steps, List<Source> sources, List<Claim> claims) {
        this.id = id;
        this.query = query;
        this.depth = depth != null ? depth : ResearchDepth.STANDARD;
        this.status = status != null ? status : RunStatus.QUEUED;
        this.budgetCapPaise = budgetCapPaise;
        this.costPaise = costPaise;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.failureReason = failureReason;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.finishedAt = finishedAt;
        this.report = report;
        this.steps = steps != null ? steps : new ArrayList<>();
        this.sources = sources != null ? sources : new ArrayList<>();
        this.claims = claims != null ? claims : new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String query;
        private ResearchDepth depth = ResearchDepth.STANDARD;
        private RunStatus status = RunStatus.QUEUED;
        private long budgetCapPaise = 1500;
        private long costPaise;
        private int tokensIn;
        private int tokensOut;
        private String failureReason;
        private Instant startedAt = Instant.now();
        private Instant finishedAt;
        private ResearchReport report;
        private List<StepTrace> steps = new ArrayList<>();
        private List<Source> sources = new ArrayList<>();
        private List<Claim> claims = new ArrayList<>();

        public Builder id(String id) { this.id = id; return this; }
        public Builder query(String query) { this.query = query; return this; }
        public Builder depth(ResearchDepth depth) { this.depth = depth; return this; }
        public Builder status(RunStatus status) { this.status = status; return this; }
        public Builder budgetCapPaise(long budgetCapPaise) { this.budgetCapPaise = budgetCapPaise; return this; }
        public Builder costPaise(long costPaise) { this.costPaise = costPaise; return this; }
        public Builder tokensIn(int tokensIn) { this.tokensIn = tokensIn; return this; }
        public Builder tokensOut(int tokensOut) { this.tokensOut = tokensOut; return this; }
        public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder finishedAt(Instant finishedAt) { this.finishedAt = finishedAt; return this; }
        public Builder report(ResearchReport report) { this.report = report; return this; }
        public Builder steps(List<StepTrace> steps) { this.steps = steps; return this; }
        public Builder sources(List<Source> sources) { this.sources = sources; return this; }
        public Builder claims(List<Claim> claims) { this.claims = claims; return this; }

        public Run build() {
            return new Run(id, query, depth, status, budgetCapPaise, costPaise, tokensIn, tokensOut, failureReason, startedAt, finishedAt, report, steps, sources, claims);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public ResearchDepth getDepth() { return depth; }
    public void setDepth(ResearchDepth depth) { this.depth = depth; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }

    public long getBudgetCapPaise() { return budgetCapPaise; }
    public void setBudgetCapPaise(long budgetCapPaise) { this.budgetCapPaise = budgetCapPaise; }

    public long getCostPaise() { return costPaise; }
    public void setCostPaise(long costPaise) { this.costPaise = costPaise; }

    public int getTokensIn() { return tokensIn; }
    public void setTokensIn(int tokensIn) { this.tokensIn = tokensIn; }

    public int getTokensOut() { return tokensOut; }
    public void setTokensOut(int tokensOut) { this.tokensOut = tokensOut; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public ResearchReport getReport() { return report; }
    public void setReport(ResearchReport report) { this.report = report; }

    public List<StepTrace> getSteps() { return steps; }
    public void setSteps(List<StepTrace> steps) { this.steps = steps; }

    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> sources) { this.sources = sources; }

    public List<Claim> getClaims() { return claims; }
    public void setClaims(List<Claim> claims) { this.claims = claims; }
}
