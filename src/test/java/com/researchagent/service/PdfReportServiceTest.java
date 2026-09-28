package com.researchagent.service;

import com.researchagent.dto.ReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportServiceTest {

    private PdfReportService pdfReportService;

    @BeforeEach
    void setUp() {
        pdfReportService = new PdfReportService("./target/test-reports");
    }

    @Test
    @DisplayName("Should generate valid non-empty PDF bytes from markdown report")
    void shouldGeneratePdfBytes() {
        ReportResponse report = new ReportResponse();
        report.setId("rep-1");
        report.setRunId("run-101");
        report.setQuery("Commercial state of Solid State Batteries 2026");
        report.setTitle("Research Brief: Solid State Batteries 2026");
        report.setMarkdownContent("""
                # Executive Summary
                Solid state batteries represent a **next-generation energy storage** technology with enhanced safety and energy density [1].

                ## Key Findings
                - Laboratory tests confirmed over 1000 cycles under ambient conditions [1][2].
                - Pilot line production has commenced in automotive supply chains [2].

                ## Citations
                | Citation | Source | Domain | Verbatim Quote |
                |---|---|---|---|
                | [1] | Nature Energy | nature.com | "Solid state cells showed 1000 cycle stability" |
                | [2] | Science Battery Review | science.org | "Pilot manufacturing underway" |
                """);
        report.setConfidenceScore(0.85);
        report.setTotalSources(4);
        report.setVerifiedClaimsCount(3);
        report.setUnverifiedClaimsCount(1);
        report.setCreatedAt(Instant.now());

        byte[] pdfBytes = pdfReportService.generatePdfBytes(report);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1000);
        // PDF header magic bytes: %PDF-
        assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Should build styled XHTML containing header meta, confidence badge, and clean body")
    void shouldBuildStyledXhtml() {
        ReportResponse report = new ReportResponse();
        report.setRunId("run-102");
        report.setTitle("AI in Drug Discovery 2026");
        report.setMarkdownContent("## Discovery Metrics\nAI accelerated target identification by **3x** [1].");
        report.setConfidenceScore(0.92);
        report.setTotalSources(6);
        report.setVerifiedClaimsCount(5);
        report.setCreatedAt(Instant.now());

        String xhtml = pdfReportService.buildStyledXhtml(report);

        assertThat(xhtml).contains("AI in Drug Discovery 2026");
        assertThat(xhtml).contains("92% Verified");
        assertThat(xhtml).contains("Discovery Metrics");
        assertThat(xhtml).contains("AI accelerated target identification by <strong>3x</strong>");
    }
}
