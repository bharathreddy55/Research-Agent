package com.researchagent.evidence;

/** Computed from fetched snapshots and operator configuration, never model output. */
public record SourceProvenance(String hostname, String registrableDomain, String publisherGroup,
                               String evidenceFamilyId, String policyVersion) {}
