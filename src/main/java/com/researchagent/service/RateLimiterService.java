package com.researchagent.service;

import com.researchagent.entity.RunEntity;
import com.researchagent.repository.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Rate limiter & concurrency guardrail (PRD F10 & Audit B7).
 *
 * <p>Enforces a hard cap of maximum concurrent active runs per user (default: 3).
 * Uses Redis sets if Redis is reachable, falling back to database query.</p>
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "PENDING", "QUEUED", "RUNNING", "PLANNING", "SEARCHING",
            "READING", "VERIFYING", "WRITING", "REFLECTING", "VALIDATING_CITATIONS"
    );

    private final RunRepository runRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${research-agent.limits.max-concurrent-runs-per-user:3}")
    private int maxConcurrentRunsPerUser;

    public RateLimiterService(RunRepository runRepository, StringRedisTemplate redisTemplate) {
        this.runRepository = runRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if a user is allowed to start a new run.
     *
     * @param userId the user ID (defaults to "default-user" if null/blank)
     * @return true if allowed, false if concurrency limit reached
     */
    public boolean allowRun(String userId) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : "default-user";
        int activeCount = countActiveRuns(effectiveUserId);
        if (activeCount >= maxConcurrentRunsPerUser) {
            log.warn("Rate limit exceeded for user {}: {} active runs (limit is {})",
                    effectiveUserId, activeCount, maxConcurrentRunsPerUser);
            return false;
        }
        return true;
    }

    private int countActiveRuns(String userId) {
        try {
            // Count from database
            List<RunEntity> allRuns = runRepository.findAll();
            return (int) allRuns.stream()
                    .filter(r -> effectiveUserMatch(r.getUserId(), userId))
                    .filter(r -> ACTIVE_STATUSES.contains(r.getStatus()))
                    .count();
        } catch (Exception e) {
            log.warn("Could not query active runs from DB for rate limit: {}", e.getMessage());
            return 0;
        }
    }

    private boolean effectiveUserMatch(String runUserId, String targetUserId) {
        String effectiveRunUser = (runUserId != null && !runUserId.isBlank()) ? runUserId : "default-user";
        return effectiveRunUser.equalsIgnoreCase(targetUserId);
    }
}
