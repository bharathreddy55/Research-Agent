package com.researchagent.tools;

import java.util.List;
import java.util.Objects;

/** Search results with explicit provider attempts. Only fetched provider results belong in results. */
public record SearchOutcome(List<SearchResult> results, Status status, List<ProviderAttempt> attempts) {
    public enum Status { SUCCESS, PARTIAL, EMPTY, UNAVAILABLE }

    public record ProviderAttempt(String provider, Status status, int resultCount, String diagnostic) {
        public ProviderAttempt {
            Objects.requireNonNull(provider);
            Objects.requireNonNull(status);
            if (status == Status.PARTIAL || resultCount < 0) throw new IllegalArgumentException("Invalid provider attempt");
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    public SearchOutcome {
        results = List.copyOf(results);
        attempts = List.copyOf(attempts);
        Objects.requireNonNull(status);
        if ((status == Status.SUCCESS || status == Status.PARTIAL) != !results.isEmpty())
            throw new IllegalArgumentException("Search status does not match result count");
    }

    public static SearchOutcome provider(String provider, List<SearchResult> results, Status emptyStatus, String diagnostic) {
        Status status = results.isEmpty() ? emptyStatus : Status.SUCCESS;
        if (emptyStatus != Status.EMPTY && emptyStatus != Status.UNAVAILABLE)
            throw new IllegalArgumentException("Provider empty status must be EMPTY or UNAVAILABLE");
        return new SearchOutcome(results, status,
                List.of(new ProviderAttempt(provider, status, results.size(), diagnostic)));
    }

    public static SearchOutcome combine(SearchOutcome first, SearchOutcome second) {
        List<ProviderAttempt> attempts = new java.util.ArrayList<>(first.attempts());
        attempts.addAll(second.attempts());
        List<SearchResult> selected = second.results().isEmpty() ? first.results() : second.results();
        boolean unavailable = attempts.stream().anyMatch(a -> a.status() == Status.UNAVAILABLE);
        Status status = selected.isEmpty() ? (unavailable ? Status.UNAVAILABLE : Status.EMPTY)
                : unavailable ? Status.PARTIAL : Status.SUCCESS;
        return new SearchOutcome(selected, status, attempts);
    }
}
