package com.researchagent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CostTrackerTest {

    private final CostTracker costTracker = new CostTracker();

    @Test
    @DisplayName("Should accurately compute paise cost for Gemini Flash tokens")
    void shouldComputeGeminiCostAccurately() {
        // 1,000,000 in = 638 paise, 1,000,000 out = 2550 paise
        long cost = CostTracker.calculateCostPaise(1_000_000, 1_000_000, "gemini-1.5-flash");
        assertThat(cost).isEqualTo(638 + 2550);

        // Typical research step: 4,000 in, 1,000 out
        // in: (4,000 / 1,000,000) * 638 = 2.552 paise
        // out: (1,000 / 1,000,000) * 2550 = 2.55 paise
        // total ceil(5.102) = 6 paise
        long stepCost = CostTracker.calculateCostPaise(4_000, 1_000, "gemini-1.5-flash");
        assertThat(stepCost).isEqualTo(6);
    }

    @Test
    @DisplayName("Should enforce minimum 1 paise cost for non-zero token exchange")
    void shouldEnforceMinimumOnePaise() {
        long minimalCost = CostTracker.calculateCostPaise(10, 10, "gemini-1.5-flash");
        assertThat(minimalCost).isEqualTo(1);
    }

    @Test
    @DisplayName("Should trigger budget exceeded flag exactly at or above budget cap")
    void shouldTriggerBudgetExceededAtBoundary() {
        long budgetCapPaise = 50;
        CostTracker.RunBudgetState budgetState = costTracker.createBudgetState(budgetCapPaise);

        assertThat(budgetState.isBudgetExceeded()).isFalse();

        // Add 20 paise
        budgetState.recordStep(2000, 1000, "gemini-1.5-flash");
        assertThat(budgetState.isBudgetExceeded()).isFalse();

        // Add enough to reach/exceed 50 paise
        budgetState.recordStep(20_000, 20_000, "gemini-1.5-flash");
        assertThat(budgetState.isBudgetExceeded()).isTrue();
    }
}
