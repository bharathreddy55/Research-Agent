package com.researchagent.service;

import com.researchagent.repository.RunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailySpendCapServiceTest {

    @Mock
    private RunRepository runRepository;

    private DailySpendCapService dailySpendCapService;

    @BeforeEach
    void setUp() {
        dailySpendCapService = new DailySpendCapService(runRepository);
        ReflectionTestUtils.setField(dailySpendCapService, "globalDailySpendCapPaise", 50000L);
        dailySpendCapService.init();
    }

    @Test
    @DisplayName("Should permit run if projected spend is within daily cap")
    void shouldPermitWithinCap() {
        when(runRepository.sumCostPaiseByStartedAtAfter(any(Instant.class))).thenReturn(20000L);

        boolean wouldExceed = dailySpendCapService.wouldExceedGlobalDailyCap(1500L);
        assertThat(wouldExceed).isFalse();
    }

    @Test
    @DisplayName("Should reject run if projected spend exceeds daily cap")
    void shouldRejectWhenExceedsCap() {
        when(runRepository.sumCostPaiseByStartedAtAfter(any(Instant.class))).thenReturn(49000L);

        boolean wouldExceed = dailySpendCapService.wouldExceedGlobalDailyCap(1500L);
        assertThat(wouldExceed).isTrue();
    }

    @Test
    @DisplayName("Should allow resetting for a new day")
    void shouldResetForNewDay() {
        dailySpendCapService.resetForNewDay();
        // verify reset executes without error
        assertThat(dailySpendCapService).isNotNull();
    }
}
