package com.researchagent.model;

import java.util.ArrayList;
import java.util.List;

public class ResearchPlan {
    private List<String> subQuestions = new ArrayList<>();
    private List<String> searchQueries = new ArrayList<>();
    private String rationale;

    public ResearchPlan() {}

    public ResearchPlan(List<String> subQuestions, List<String> searchQueries, String rationale) {
        this.subQuestions = subQuestions != null ? subQuestions : new ArrayList<>();
        this.searchQueries = searchQueries != null ? searchQueries : new ArrayList<>();
        this.rationale = rationale;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> subQuestions = new ArrayList<>();
        private List<String> searchQueries = new ArrayList<>();
        private String rationale;

        public Builder subQuestions(List<String> subQuestions) {
            this.subQuestions = subQuestions;
            return this;
        }

        public Builder searchQueries(List<String> searchQueries) {
            this.searchQueries = searchQueries;
            return this;
        }

        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        public ResearchPlan build() {
            return new ResearchPlan(subQuestions, searchQueries, rationale);
        }
    }

    public List<String> getSubQuestions() { return subQuestions; }
    public void setSubQuestions(List<String> subQuestions) { this.subQuestions = subQuestions; }

    public List<String> getSearchQueries() { return searchQueries; }
    public void setSearchQueries(List<String> searchQueries) { this.searchQueries = searchQueries; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
}
