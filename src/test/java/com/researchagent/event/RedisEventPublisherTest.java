package com.researchagent.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.model.AgentType;
import com.researchagent.model.RunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests RedisEventPublisher without Mockito — uses a hand-rolled stub of
 * StringRedisTemplate to capture published channel/message pairs.
 *
 * Note: StringRedisTemplate.convertAndSend() returns Long (number of subscribers),
 * matching the RedisOperations interface contract.
 */
class RedisEventPublisherTest {

    // -------------------------------------------------------------------------
    // Captured calls
    // -------------------------------------------------------------------------
    private record PublishedEvent(String channel, String payload) {}
    private final List<PublishedEvent> published = new ArrayList<>();

    private ObjectMapper objectMapper;
    private RedisEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        published.clear();
        objectMapper = new ObjectMapper();

        // Anonymous subclass — overrides convertAndSend (returns Long per interface contract)
        StringRedisTemplate stubRedisTemplate = new StringRedisTemplate() {
            @Override
            public Long convertAndSend(String channel, Object message) {
                published.add(new PublishedEvent(channel, (String) message));
                return 1L; // simulates 1 subscriber received the message
            }
        };

        eventPublisher = new RedisEventPublisher(stubRedisTemplate, objectMapper);
    }

    @Test
    @DisplayName("Should publish structured JSON event to correct Redis channel")
    void shouldPublishEventToRedisChannel() throws Exception {
        String runId = "test-run-123";
        eventPublisher.onEvent(runId, RunStatus.PLANNING, AgentType.PLANNER,
                "Decomposing query", 100, 50, 2);

        assertThat(published).hasSize(1);
        PublishedEvent evt = published.get(0);

        assertThat(evt.channel()).isEqualTo("run:test-run-123:events");

        JsonNode json = objectMapper.readTree(evt.payload());
        assertThat(json.get("runId").asText()).isEqualTo(runId);
        assertThat(json.get("status").asText()).isEqualTo("PLANNING");
        assertThat(json.get("agent").asText()).isEqualTo("PLANNER");
        assertThat(json.get("message").asText()).isEqualTo("Decomposing query");
        assertThat(json.get("tokensIn").asInt()).isEqualTo(100);
        assertThat(json.get("tokensOut").asInt()).isEqualTo(50);
        assertThat(json.get("costPaise").asLong()).isEqualTo(2);
        assertThat(json.has("ts")).isTrue();
    }

    @Test
    @DisplayName("Should silently swallow Redis failure without propagating exception")
    void shouldNotThrowOnRedisFailure() {
        StringRedisTemplate failingTemplate = new StringRedisTemplate() {
            @Override
            public Long convertAndSend(String channel, Object message) {
                throw new RuntimeException("Redis connection refused");
            }
        };
        RedisEventPublisher failingPublisher = new RedisEventPublisher(failingTemplate, objectMapper);

        // Must not throw — SSE is best-effort
        failingPublisher.onEvent("run-x", RunStatus.FAILED, AgentType.SYSTEM, "error", 0, 0, 0);
    }
}
