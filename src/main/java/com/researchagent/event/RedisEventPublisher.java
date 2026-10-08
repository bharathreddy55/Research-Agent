package com.researchagent.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.agent.RunEventListener;
import com.researchagent.model.AgentType;
import com.researchagent.model.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implements {@link RunEventListener} by publishing run lifecycle events to a
 * per-run Redis Pub/Sub channel: {@code run:{runId}:events}.
 *
 * <p>The SSE endpoint (Sprint 3) will subscribe to this channel and forward
 * each JSON event to the browser via a {@code SseEmitter}.</p>
 *
 * <p>Event JSON schema:
 * <pre>
 * {
 *   "runId":     "uuid",
 *   "status":    "PLANNING | SEARCHING | ... | COMPLETED | FAILED",
 *   "agent":     "PLANNER | SEARCHER | ... | SYSTEM",
 *   "message":   "human-readable status text",
 *   "tokensIn":  42,
 *   "tokensOut": 137,
 *   "costPaise": 12,
 *   "ts":        "2025-01-01T00:00:00Z"
 * }
 * </pre>
 * </p>
 */
@Component
public class RedisEventPublisher implements RunEventListener {

    private static final Logger log = LoggerFactory.getLogger(RedisEventPublisher.class);
    static final String CHANNEL_PREFIX = "run:";
    static final String CHANNEL_SUFFIX = ":events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final org.springframework.beans.factory.ObjectProvider<com.researchagent.service.RedisSseBridgeService> sseBridgeProvider;

    public RedisEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RedisEventPublisher(
            @org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<com.researchagent.service.RedisSseBridgeService> sseBridgeProvider) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.sseBridgeProvider = sseBridgeProvider;
    }

    /**
     * Publishes a JSON event to the Redis channel for {@code runId}.
     * Implements the {@link RunEventListener} 7-arg signature — status and
     * agent enums are serialized as their {@code name()} strings.
     */
    @Override
    public void onEvent(String runId, RunStatus status, AgentType agent, String message,
                        int tokensIn, int tokensOut, long costPaise) {
        String channel = CHANNEL_PREFIX + runId + CHANNEL_SUFFIX;
        String agentName  = agent  != null ? agent.name()  : "SYSTEM";
        String statusName = status != null ? status.name() : "UNKNOWN";
        String payload = buildPayload(runId, statusName, agentName, message, tokensIn, tokensOut, costPaise);
        if (payload == null) return;
        try {
            if (redisTemplate != null) {
                redisTemplate.convertAndSend(channel, payload);
                log.debug("Published event to {} — agent={} msg={}", channel, agentName, message);
            }
        } catch (Exception e) {
            // Non-fatal: SSE is best-effort; do not fail the worker task
            log.warn("Failed to publish Redis event for run {} agent {}: {}", runId, agentName, e.getMessage());
        }

        try {
            com.researchagent.service.RedisSseBridgeService sseBridge = sseBridgeProvider.getIfAvailable();
            if (sseBridge != null) {
                sseBridge.forwardPayload(payload);
            }
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildPayload(String runId, String status, String agent, String message,
                                int tokensIn, int tokensOut, long costPaise) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId",     runId);
        payload.put("status",    status);
        payload.put("agent",     agent);
        payload.put("message",   message);
        payload.put("tokensIn",  tokensIn);
        payload.put("tokensOut", tokensOut);
        payload.put("costPaise", costPaise);
        payload.put("ts",        Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Cannot serialize Redis event for run {}: {}", runId, e.getMessage());
            return null;
        }
    }
}
