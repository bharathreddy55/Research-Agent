package com.researchagent.tools;

import java.util.List;

public interface SearchService {
    List<SearchResult> search(String query, int maxResults);

    /** Existing local adapters can keep returning a list; production providers override this. */
    default SearchOutcome searchOutcome(String query, int maxResults) {
        List<SearchResult> results = search(query, maxResults);
        return SearchOutcome.provider("custom", results, SearchOutcome.Status.EMPTY, "");
    }
}
