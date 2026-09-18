package com.researchagent.model;

public enum ResearchDepth {
    QUICK(6, 3),
    STANDARD(10, 4),
    DEEP(15, 5);

    private final int maxPages;
    private final int maxSubQuestions;

    ResearchDepth(int maxPages, int maxSubQuestions) {
        this.maxPages = maxPages;
        this.maxSubQuestions = maxSubQuestions;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public int getMaxSubQuestions() {
        return maxSubQuestions;
    }
}
