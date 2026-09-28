package com.researchagent.service;

import com.researchagent.entity.RunEntity;
import com.researchagent.repository.RunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RunRepository runRepository;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(runRepository, null);
        ReflectionTestUtils.setField(rateLimiterService, "maxConcurrentRunsPerUser", 3);
    }

    @Test
    @DisplayName("Should permit run when user has less than 3 active runs")
    void shouldPermitWhenUnderLimit() {
        RunEntity r1 = new RunEntity();
        r1.setUserId("user-1");
        r1.setStatus("RUNNING");

        RunEntity r2 = new RunEntity();
        r2.setUserId("user-1");
        r2.setStatus("COMPLETED");

        when(runRepository.findAll()).thenReturn(List.of(r1, r2));

        boolean allowed = rateLimiterService.allowRun("user-1");
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("Should block run when user has 3 or more active runs")
    void shouldBlockWhenAtLimit() {
        RunEntity r1 = new RunEntity();
        r1.setUserId("user-1");
        r1.setStatus("PLANNING");

        RunEntity r2 = new RunEntity();
        r2.setUserId("user-1");
        r2.setStatus("SEARCHING");

        RunEntity r3 = new RunEntity();
        r3.setUserId("user-1");
        r3.setStatus("WRITING");

        when(runRepository.findAll()).thenReturn(List.of(r1, r2, r3));

        boolean allowed = rateLimiterService.allowRun("user-1");
        assertThat(allowed).isFalse();
    }
}
