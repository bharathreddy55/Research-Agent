package com.researchagent.service;

import com.researchagent.repository.RunRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Enforces the global daily spend cap (PRD §4 / Audit B4).
 *
 * <p>Before the worker starts any research run it checks whether the projected
 * daily spend (all runs started today + the new run's expected cost) would
 * exceed {@code research-agent.limits.global-daily-spend-cap-paise}.  If so the
 * new run is rejected with a {@code FAILED} status instead of being executed.</p>
 *
 * <p>The "today" window is computed from the server's local clock at startup and
 * is re-anchored on the next calendar day (handled by the worker via a scheduled
 * {@link #resetForNewDay()} call).</p>
 */
@Service
public class DailySpendCapService {

    private static final Logger log = LoggerFactory.getLogger(DailySpendCapService.class);

    private final RunRepository runRepository;

    @Value("${research-agent.limits.global-daily-spend-cap-paise:50000}")
    private long globalDailySpendCapPaise;

    private Instant todayStartOfDay;

    public DailySpendCapService(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @PostConstruct
    public void init() {
        this.todayStartOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        log.info("Daily spend cap initialized: {} paise (₹{}); today starts at {}", globalDailySpendCapPaise,
                globalDailySpendCapPaise / 100.0, todayStartOfDay);
    }

    /**
     * Returns true if adding {@code additionalPaise} to the cumulative spend of
     * all runs started today would exceed the global daily spend cap.
     */
    @Transactional(readOnly = true)
    public boolean wouldExceedGlobalDailyCap(long additionalPaise) {
        long todayTotal = runRepository.sumCostPaiseByStartedAtAfter(todayStartOfDay);
        long projectedTotal = todayTotal + additionalPaise;
        boolean exceeds = projectedTotal > globalDailySpendCapPaise;
        if (exceeds) {
            log.warn("Daily spend cap check: projected total {} paise (> cap {} paise) for additional {} paise",
                    projectedTotal, globalDailySpendCapPaise, additionalPaise);
        } else {
            log.debug("Daily spend cap check: projected {} paise within cap {} paise",
                    projectedTotal, globalDailySpendCapPaise);
        }
        return exceeds;
    }

    /** Re-anchors the "today" window so the cap rolls over at midnight. */
    public void resetForNewDay() {
        this.todayStartOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        log.info("Daily spend cap reset for new day, cap = {} paise", globalDailySpendCapPaise);
    }
}