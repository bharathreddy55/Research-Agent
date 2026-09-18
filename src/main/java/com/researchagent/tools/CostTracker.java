package com.researchagent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CostTracker {
    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);

    // Pricing in paise per 1,000,000 tokens (1 USD ~ 85 INR, 1 INR = 100 paise)
    // Gemini 1.5 Flash: $0.075 / 1M prompt -> ~638 paise; $0.30 / 1M completion -> ~2550 paise
    private static final long GEMINI_INPUT_PAISE_PER_M = 638;
    private static final long GEMINI_OUTPUT_PAISE_PER_M = 2550;

    // GPT-4o-mini: $0.15 / 1M prompt -> ~1275 paise; $0.60 / 1M completion -> ~5100 paise
    private static final long OPENAI_MINI_INPUT_PAISE_PER_M = 1275;
    private static final long OPENAI_MINI_OUTPUT_PAISE_PER_M = 5100;

    public static class RunBudgetState {
        private final AtomicInteger tokensIn = new AtomicInteger(0);
        private final AtomicInteger tokensOut = new AtomicInteger(0);
        private final AtomicLong totalCostPaise = new AtomicLong(0);
        private final long budgetCapPaise;

        public RunBudgetState(long budgetCapPaise) {
            this.budgetCapPaise = budgetCapPaise;
        }

        public long recordStep(int stepTokensIn, int stepTokensOut, String model) {
            tokensIn.addAndGet(stepTokensIn);
            tokensOut.addAndGet(stepTokensOut);
            long stepCost = calculateCostPaise(stepTokensIn, stepTokensOut, model);
            long newTotal = totalCostPaise.addAndGet(stepCost);
            log.info("Step recorded: in={}, out={}, stepCost={} paise, totalCost={} paise (budget cap: {} paise)",
                    stepTokensIn, stepTokensOut, stepCost, newTotal, budgetCapPaise);
            return stepCost;
        }

        public boolean isBudgetExceeded() {
            return totalCostPaise.get() >= budgetCapPaise;
        }

        public long getTotalCostPaise() {
            return totalCostPaise.get();
        }

        public int getTotalTokensIn() {
            return tokensIn.get();
        }

        public int getTotalTokensOut() {
            return tokensOut.get();
        }

        public long getBudgetCapPaise() {
            return budgetCapPaise;
        }
    }

    public RunBudgetState createBudgetState(long budgetCapPaise) {
        return new RunBudgetState(budgetCapPaise);
    }

    public static long calculateCostPaise(int tokensIn, int tokensOut, String model) {
        long inputRatePerM = GEMINI_INPUT_PAISE_PER_M;
        long outputRatePerM = GEMINI_OUTPUT_PAISE_PER_M;

        if (model != null && (model.contains("gpt") || model.contains("openai"))) {
            inputRatePerM = OPENAI_MINI_INPUT_PAISE_PER_M;
            outputRatePerM = OPENAI_MINI_OUTPUT_PAISE_PER_M;
        }

        // Compute paise using double precision then ceil to integer paise
        double inCost = ((double) tokensIn / 1_000_000.0) * inputRatePerM;
        double outCost = ((double) tokensOut / 1_000_000.0) * outputRatePerM;

        long total = (long) Math.ceil(inCost + outCost);
        // Minimum 1 paise for non-zero token exchange
        if ((tokensIn > 0 || tokensOut > 0) && total == 0) {
            total = 1;
        }
        return total;
    }
}
