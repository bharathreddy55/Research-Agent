package com.researchagent.evidence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class ReportDraftCodec {
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public ReportDraft parse(String raw, boolean demoMode) {
        try {
            String json = raw == null ? "" : raw.trim();
            if (json.startsWith("```json") && json.endsWith("```")) json = json.substring(7, json.length() - 3).trim();
            else if (json.startsWith("```") && json.endsWith("```")) json = json.substring(3, json.length() - 3).trim();
            JsonNode root = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
            if (root == null || !root.isObject() || root.size() != 3
                    || !ReportDraft.SCHEMA.equals(root.path("schemaVersion").asText())
                    || !root.has("summaryClaimIds") || !root.has("findingClaimIds")) throw new IllegalArgumentException();
            return new ReportDraft(ids(root.path("summaryClaimIds"), 5), ids(root.path("findingClaimIds"), 100), demoMode);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid structured report draft; no free-form factual report was accepted", ex);
        }
    }

    private static List<String> ids(JsonNode array, int max) {
        if (!array.isArray() || array.size() > max) throw new IllegalArgumentException();
        List<String> ids = new ArrayList<>();
        for (JsonNode id : array) {
            if (!id.isTextual() || !id.textValue().matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
            ids.add(id.textValue());
        }
        return ids;
    }

    public String serialize(ReportDraft draft) {
        return json(Map.of("schemaVersion", ReportDraft.SCHEMA, "summaryClaimIds", draft.summaryClaimIds(), "findingClaimIds", draft.findingClaimIds()));
    }
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Could not serialize report evidence", ex); }
    }
}
