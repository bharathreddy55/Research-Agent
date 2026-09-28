package com.researchagent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateRunRequest {

    @NotBlank(message = "Query must not be empty")
    @Size(max = 500, message = "Query length must not exceed 500 characters")
    private String query;

    @Pattern(regexp = "(?i)^(QUICK|STANDARD|DEEP)$", message = "Depth must be QUICK, STANDARD, or DEEP")
    private String depth = "STANDARD";

    @Min(value = 100, message = "Budget cap must be at least 100 paise (₹1)")
    @Max(value = 100000, message = "Budget cap must not exceed 100,000 paise (₹1000)")
    private long budgetCapPaise = 1500;

    public CreateRunRequest() {}

    public CreateRunRequest(String query, String depth, long budgetCapPaise) {
        this.query = query;
        this.depth = depth;
        this.budgetCapPaise = budgetCapPaise;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getDepth() { return depth; }
    public void setDepth(String depth) { this.depth = depth; }

    public long getBudgetCapPaise() { return budgetCapPaise; }
    public void setBudgetCapPaise(long budgetCapPaise) { this.budgetCapPaise = budgetCapPaise; }
}
