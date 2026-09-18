package com.researchagent.tools;

import java.util.List;

public interface SearchService {
    List<SearchResult> search(String query, int maxResults);
}
