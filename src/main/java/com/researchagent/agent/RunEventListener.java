package com.researchagent.agent;

import com.researchagent.model.AgentType;
import com.researchagent.model.RunStatus;

@FunctionalInterface
public interface RunEventListener {
    void onEvent(String runId, RunStatus status, AgentType agent, String message, int tokensIn, int tokensOut, long costPaise);
}
