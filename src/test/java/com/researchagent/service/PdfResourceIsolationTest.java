package com.researchagent.service;

import com.researchagent.dto.ReportResponse;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PdfResourceIsolationTest {
    @TempDir Path directory;

    @Test
    void untrustedMarkdownKeepsTextAndCitationsButRemovesActiveResources() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ReportResponse report = report("""
                    ## Preserved heading
                    **Preserved finding** and [citation](https://example.org/evidence).
                    ![remote image](%s/image)
                    <style>@import url('%s/style'); p { background-image: url('%s/background'); }</style>
                    <link rel="stylesheet" href="%s/redirect" />
                    <img src="http://169.254.169.254/latest/meta-data/" />
                    <img src="http://10.0.0.1/private" />
                    <img src="file:///etc/passwd" />
                    <img src="data:image/svg+xml,anything" />
                    <iframe src="%s/frame"></iframe>
                    <object data="%s/object"></object>
                    <svg><image href="%s/svg" /></svg>
                    <p style="background: url('%s/inline')" onclick="alert(1)">Safe paragraph</p>
                    <a href="javascript:alert(1)">Unsafe link text</a>
                    <a href="file:///etc/passwd">Local link text</a>
                    """.formatted(base, base, base, base, base, base, base, base));
            PdfReportService service = new PdfReportService(directory.toString());
            String xhtml = service.buildStyledXhtml(report);
            assertThat(xhtml).contains("Preserved heading", "<strong>Preserved finding</strong>",
                    "https://example.org/evidence", "Safe paragraph", "Unsafe link text");
            assertThat(xhtml).doesNotContain(base, "169.254.169.254", "10.0.0.1", "file:", "data:image",
                    "javascript:", "onclick", "<img", "<svg", "<iframe", "<object", "<!DOCTYPE", "@import");
            byte[] pdf = service.generatePdfBytes(report);
            assertThat(text(pdf)).contains("Preserved heading", "Preserved finding", "Safe paragraph");
            try (PDDocument document = PDDocument.load(pdf)) {
                assertThat(document.getPage(0).getAnnotations()).anySatisfy(annotation -> {
                    assertThat(annotation).isInstanceOf(PDAnnotationLink.class);
                    assertThat(((PDAnnotationLink) annotation).getAction()).isInstanceOf(PDActionURI.class);
                    assertThat(((PDActionURI) ((PDAnnotationLink) annotation).getAction()).getURI())
                            .isEqualTo("https://example.org/evidence");
                });
            }
            assertThat(requests.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rendererDeniesHttpRedirectsAndFileResourcesEvenIfSanitizationIsBypassed() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests);
        Path stylesheet = directory.resolve("private.css");
        Files.writeString(stylesheet, "@page { @top-center { content: 'LOCAL_FILE_SECRET_MARKER'; } }");
        Path image = directory.resolve("private.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", image.toFile());
        String fileUri = stylesheet.toUri().toASCIIString();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String xhtml = """
                <html xmlns="http://www.w3.org/1999/xhtml"><head>
                <link rel="stylesheet" type="text/css" href="%s/style" />
                <link rel="stylesheet" type="text/css" href="%s" />
                <style>@import url('%s'); @import url('%s/redirect');
                p { background-image: url('%s/background'); }</style>
                </head><body><p>Renderer guard active</p>
                <img src="%s/image" /><img src="%s/redirect" />
                <img src="%s" />
                <object type="application/pdf" data="%s"></object>
                </body></html>
                """.formatted(base, fileUri, fileUri, base, base, base, base, image.toUri().toASCIIString(), fileUri);
        PdfReportService rendererOnly = new PdfReportService(directory.toString()) {
            @Override String buildStyledXhtml(ReportResponse ignored) { return xhtml; }
        };
        try {
            byte[] pdf = rendererOnly.generatePdfBytes(report("Ignored"));
            String rendered = text(pdf);
            assertThat(rendered).contains("Renderer guard active")
                    .doesNotContain("LOCAL_FILE_SECRET_MARKER", "NETWORK_SECRET_MARKER");
            assertThat(requests.get()).isZero();
            try (PDDocument document = PDDocument.load(pdf)) {
                for (var page : document.getPages()) assertThat(page.getResources().getXObjectNames()).isEmpty();
            }
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(AtomicInteger requests) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            if (exchange.getRequestURI().getPath().equals("/redirect")) {
                exchange.getResponseHeaders().add("Location", "/style");
                exchange.sendResponseHeaders(302, -1);
            } else {
                byte[] css = "@page { @top-center { content: 'NETWORK_SECRET_MARKER'; } }".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/css");
                exchange.sendResponseHeaders(200, css.length);
                exchange.getResponseBody().write(css);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String text(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static ReportResponse report(String markdown) {
        ReportResponse report = new ReportResponse();
        report.setRunId("security-probe"); report.setTitle("PDF resource isolation");
        report.setMarkdownContent(markdown);
        return report;
    }
}
