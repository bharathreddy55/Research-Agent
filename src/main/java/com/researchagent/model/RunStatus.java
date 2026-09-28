package com.researchagent.model;

public enum RunStatus {
    PENDING,             // job row created, not yet picked up by worker
    QUEUED,              // enqueued in orchestrator, about to start
    RUNNING,             // worker is actively executing the pipeline
    PLANNING,
    SEARCHING,
    READING,
    VERIFYING,
    WRITING,
    REFLECTING,
    VALIDATING_CITATIONS,
    COMPLETED,
    FAILED,
    CANCELLED
}
