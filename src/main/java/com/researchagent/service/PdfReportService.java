package com.researchagent.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.researchagent.dto.ReportResponse;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Sprint 4: Publication-Quality Styled PDF Export Engine.
 *
 * <p>Converts verified research Markdown reports into styled XHTML and renders
 * crisp PDF documents with cover headers, confidence badges, executive summary
 * callout boxes, and formatted citations bibliographies using Flexmark and OpenHTMLtoPDF.</p>
 */
@Service
public class PdfReportService {

    private static final Logger log = LoggerFactory.getLogger(PdfReportService.class);

    private final Parser flexmarkParser;
    private final HtmlRenderer flexmarkRenderer;
    private final String outputDir;

    public PdfReportService(@Value("${research-agent.reports.output-dir:./reports}") String outputDir) {
        this.outputDir = outputDir;
        MutableDataSet options = new MutableDataSet();
        this.flexmarkParser = Parser.builder(options).build();
        this.flexmarkRenderer = HtmlRenderer.builder(options).build();
    }

    /**
     * Renders a {@link ReportResponse} to a PDF byte array.
     */
    public byte[] generatePdfBytes(ReportResponse report) {
        String xhtml = buildStyledXhtml(report);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render PDF for run {}: {}", report.getRunId(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates and writes the PDF file to disk, returning the saved path.
     */
    public Path generateAndSavePdf(ReportResponse report) {
        byte[] pdfBytes = generatePdfBytes(report);
        try {
            Path dir = Paths.get(outputDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path pdfPath = dir.resolve("report-" + report.getRunId() + ".pdf");
            Files.write(pdfPath, pdfBytes);
            log.info("Saved PDF report to {}", pdfPath);
            return pdfPath;
        } catch (IOException e) {
            log.error("Failed to write PDF report to disk for run {}: {}", report.getRunId(), e.getMessage());
            throw new RuntimeException("Could not save PDF report: " + e.getMessage(), e);
        }
    }

    /**
     * Builds clean, standards-compliant XHTML from Markdown report content.
     */
    String buildStyledXhtml(ReportResponse report) {
        String rawMarkdown = report.getMarkdownContent() != null ? report.getMarkdownContent() : "";
        com.vladsch.flexmark.util.ast.Node document = flexmarkParser.parse(rawMarkdown);
        String bodyHtml = flexmarkRenderer.render(document);

        // Convert bodyHtml to valid XHTML using Jsoup
        Document jsoupDoc = Jsoup.parseBodyFragment(bodyHtml);
        jsoupDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        String cleanBodyXml = jsoupDoc.body().html();

        String dateStr = report.getCreatedAt() != null
                ? DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(ZoneId.systemDefault()).format(report.getCreatedAt())
                : "Recent";

        int confidencePct = (int) Math.round(report.getConfidenceScore() * 100);
        String confidenceClass = confidencePct >= 75 ? "badge-high" : (confidencePct >= 50 ? "badge-med" : "badge-low");

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                    <title>%s</title>
                    <style type="text/css">
                        @page {
                            size: A4;
                            margin: 20mm 15mm 20mm 15mm;
                            @bottom-right {
                                content: "Page " counter(page) " of " counter(pages);
                                font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                                font-size: 8pt;
                                color: #888888;
                            }
                            @bottom-left {
                                content: "ResearchAgent • Autonomous Multi-Agent Research System";
                                font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                                font-size: 8pt;
                                color: #888888;
                            }
                        }
                        body {
                            font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                            font-size: 10pt;
                            line-height: 1.6;
                            color: #1f2937;
                        }
                        .header-container {
                            border-bottom: 2px solid #2563eb;
                            padding-bottom: 12px;
                            margin-bottom: 20px;
                        }
                        .brand-tag {
                            font-size: 8pt;
                            font-weight: bold;
                            color: #2563eb;
                            text-transform: uppercase;
                            letter-spacing: 1px;
                            margin-bottom: 4px;
                        }
                        h1.report-title {
                            font-size: 18pt;
                            font-weight: 700;
                            color: #111827;
                            margin: 4px 0 8px 0;
                            line-height: 1.25;
                        }
                        .meta-bar {
                            font-size: 8.5pt;
                            color: #4b5563;
                            margin-top: 6px;
                        }
                        .badge {
                            display: inline-block;
                            padding: 2px 8px;
                            border-radius: 4px;
                            font-weight: bold;
                            font-size: 8pt;
                        }
                        .badge-high { background-color: #dcfce7; color: #15803d; border: 1px solid #86efac; }
                        .badge-med  { background-color: #fef9c3; color: #a16207; border: 1px solid #fde047; }
                        .badge-low  { background-color: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; }
                        h2 {
                            font-size: 13pt;
                            color: #1e3a8a;
                            border-bottom: 1px solid #e5e7eb;
                            padding-bottom: 4px;
                            margin-top: 18px;
                            margin-bottom: 8px;
                        }
                        h3 {
                            font-size: 11pt;
                            color: #1f2937;
                            margin-top: 12px;
                            margin-bottom: 6px;
                        }
                        p { margin: 0 0 10px 0; }
                        ul, ol { margin: 0 0 10px 0; padding-left: 20px; }
                        li { margin-bottom: 4px; }
                        blockquote {
                            background-color: #f8fafc;
                            border-left: 3px solid #3b82f6;
                            margin: 12px 0;
                            padding: 8px 14px;
                            font-style: italic;
                            color: #334155;
                        }
                        table {
                            width: 100%%;
                            border-collapse: collapse;
                            margin: 14px 0;
                            font-size: 8.5pt;
                        }
                        th {
                            background-color: #f1f5f9;
                            color: #1e293b;
                            font-weight: 600;
                            text-align: left;
                            padding: 6px 8px;
                            border: 1px solid #cbd5e1;
                        }
                        td {
                            padding: 6px 8px;
                            border: 1px solid #cbd5e1;
                            vertical-align: top;
                        }
                        tr:nth-child(even) { background-color: #f8fafc; }
                        code {
                            font-family: 'Courier New', Courier, monospace;
                            background-color: #f1f5f9;
                            padding: 1px 4px;
                            border-radius: 3px;
                            font-size: 9pt;
                        }
                    </style>
                </head>
                <body>
                    <div class="header-container">
                        <div class="brand-tag">RESEARCHAGENT • INTELLIGENCE BRIEF</div>
                        <h1 class="report-title">%s</h1>
                        <div class="meta-bar">
                            <strong>Date:</strong> %s &nbsp;|&nbsp;
                            <strong>Sources Analyzed:</strong> %d &nbsp;|&nbsp;
                            <strong>Corroborated Claims:</strong> %d &nbsp;|&nbsp;
                            <strong>Confidence:</strong> <span class="badge %s">%d%% Verified</span>
                        </div>
                    </div>

                    <div class="content-body">
                        %s
                    </div>
                </body>
                </html>
                """.formatted(
                escapeXml(report.getTitle() != null ? report.getTitle() : "Research Brief"),
                escapeXml(report.getTitle() != null ? report.getTitle() : "Research Brief"),
                escapeXml(dateStr),
                report.getTotalSources(),
                report.getVerifiedClaimsCount(),
                confidenceClass,
                confidencePct,
                cleanBodyXml
        );
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
