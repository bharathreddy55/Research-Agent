package com.researchagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis Pub/Sub to SSE streaming bridge (Audit A1).
 *
 * <p>Receives real-time JSON agent execution events from Redis channels {@code run:*:events}
 * and fans them out to connected browser {@link SseEmitter} clients.</p>
 */
@Service
public class RedisSseBridgeService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisSseBridgeService.class);
    private static final Long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final Map<String, List<SseEmitter>> activeEmitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final RedisConnectionFactory connectionFactory;

    public RedisSseBridgeService(ObjectMapper objectMapper, RedisConnectionFactory connectionFactory) {
        this.objectMapper = objectMapper;
        this.connectionFactory = connectionFactory;
    }

    @PostConstruct
    public void init() {
        try {
            if (connectionFactory != null) {
                RedisMessageListenerContainer container = new RedisMessageListenerContainer();
                container.setConnectionFactory(connectionFactory);
                container.addMessageListener(this, new PatternTopic("run:*:events"));
                container.afterPropertiesSet();
                container.start();
                log.info("Redis SSE MessageListenerContainer initialized for pattern 'run:*:events'");
            }
        } catch (Exception e) {
            log.warn("Could not register Redis message listener (Redis may be offline or mocked): {}", e.getMessage());
        }
    }

    /**
     * Registers a new SSE client for a specific runId.
     */
    public SseEmitter registerClient(String runId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        activeEmitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for run {}", runId);
            removeEmitter(runId, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE error for run {}: {}", runId, e.getMessage());
            removeEmitter(runId, emitter);
        });

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("{\"runId\":\"" + runId + "\",\"message\":\"Connected to live agent stream\"}"));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE connected event for run {}: {}", runId, e.getMessage());
            removeEmitter(runId, emitter);
        }

        log.info("Registered new SSE client for run {} (total active for run: {})",
                runId, activeEmitters.getOrDefault(runId, List.of()).size());
        return emitter;
    }

    /**
     * Handles inbound Redis pub/sub messages.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        forwardPayload(body);
    }

    /**
     * Forwards an event payload directly to registered clients (used by Redis listener or direct broadcast).
     */
    public void forwardPayload(String payloadJson) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            String runId = node.has("runId") ? node.get("runId").asText() : null;
            if (runId == null) return;

            List<SseEmitter> emitters = activeEmitters.get(runId);
            if (emitters == null || emitters.isEmpty()) return;

            String status = node.has("status") ? node.get("status").asText() : "EVENT";
            boolean isTerminal = "COMPLETED".equalsIgnoreCase(status) ||
                    "FAILED".equalsIgnoreCase(status) ||
                    "CANCELLED".equalsIgnoreCase(status);

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(status)
                            .data(payloadJson));
                    if (isTerminal) {
                        emitter.complete();
                    }
                } catch (Exception e) {
                    log.debug("Error delivering SSE to emitter for run {}: {}", runId, e.getMessage());
                    removeEmitter(runId, emitter);
                }
            }

            if (isTerminal) {
                activeEmitters.remove(runId);
            }
        } catch (Exception e) {
            log.warn("Failed to parse and forward SSE payload: {}", e.getMessage());
        }
    }

    /**
     * Periodic heartbeat to keep connections open across proxies.
     */
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeats() {
        activeEmitters.forEach((runId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    removeEmitter(runId, emitter);
                }
            }
        });
    }

    private void removeEmitter(String runId, SseEmitter emitter) {
        List<SseEmitter> list = activeEmitters.get(runId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                activeEmitters.remove(runId);
            }
        }
    }

    public int getActiveClientCount(String runId) {
        List<SseEmitter> list = activeEmitters.get(runId);
        return list != null ? list.size() : 0;
    }
}
