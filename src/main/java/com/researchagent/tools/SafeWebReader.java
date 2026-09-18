package com.researchagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
public class SafeWebReader {
    private static final Logger log = LoggerFactory.getLogger(SafeWebReader.class);
    private static final int MAX_REDIRECTS = 3;
    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_CONTENT_LENGTH = 2 * 1024 * 1024; // 2MB max download
    private static final int MAX_EXTRACTED_CHARS = 16000; // ~4000 tokens for context window safety

    public interface DnsResolver {
        InetAddress[] resolve(String host) throws Exception;
    }

    private DnsResolver dnsResolver = InetAddress::getAllByName;

    public void setDnsResolver(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    public static class ExtractedPage {
        private final String url;
        private final String title;
        private final String cleanText;
        private final boolean success;
        private final String errorMessage;

        public ExtractedPage(String url, String title, String cleanText, boolean success, String errorMessage) {
            this.url = url;
            this.title = title;
            this.cleanText = cleanText;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public String getUrl() { return url; }
        public String getTitle() { return title; }
        public String getCleanText() { return cleanText; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Safely reads and extracts text from a target URL with strict SSRF and redirect validation.
     */
    public ExtractedPage readUrl(String targetUrl) {
        try {
            String currentUrl = targetUrl;
            int redirectCount = 0;

            while (redirectCount <= MAX_REDIRECTS) {
                URI uri = new URI(currentUrl);
                validateUri(uri);

                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false); // manual redirect handling (A2)
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "ResearchAgent/1.0 (+https://github.com/bharathreddy55/Research-Agent)");
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

                int statusCode = conn.getResponseCode();

                if (statusCode >= 300 && statusCode < 400) {
                    redirectCount++;
                    if (redirectCount > MAX_REDIRECTS) {
                        throw new SecurityException("Exceeded maximum redirects (" + MAX_REDIRECTS + ")");
                    }
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw new SecurityException("Redirect response missing Location header");
                    }
                    URI redirectUri = uri.resolve(location);
                    log.info("SafeWebReader following redirect [hop {}] to: {}", redirectCount, redirectUri);
                    // Validate redirect destination immediately before following
                    validateUri(redirectUri);
                    currentUrl = redirectUri.toString();
                    conn.disconnect();
                    continue;
                }

                if (statusCode != 200) {
                    return new ExtractedPage(targetUrl, "", "", false, "HTTP " + statusCode + " error");
                }

                // Verify content type is HTML or text
                String contentType = conn.getContentType();
                if (contentType != null && !contentType.contains("text/html") && !contentType.contains("text/plain") && !contentType.contains("application/xhtml")) {
                    return new ExtractedPage(targetUrl, "", "", false, "Unsupported content type: " + contentType);
                }

                try (InputStream in = conn.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    char[] buffer = new char[8192];
                    StringBuilder sb = new StringBuilder();
                    int charsRead;
                    while ((charsRead = reader.read(buffer)) != -1) {
                        sb.append(buffer, 0, charsRead);
                        if (sb.length() > MAX_CONTENT_LENGTH) {
                            break;
                        }
                    }

                    String rawHtml = sb.toString();
                    Document doc = Jsoup.parse(rawHtml, currentUrl);
                    String pageTitle = doc.title();
                    if (pageTitle == null || pageTitle.isBlank()) {
                        pageTitle = uri.getHost();
                    }

                    // Remove non-content elements to avoid bloat and prompt injection vectors
                    doc.select("script, style, nav, footer, header, aside, iframe, noscript, svg, form").remove();

                    String text = doc.body() != null ? doc.body().text() : doc.text();
                    // Clean up multiple spaces and linebreaks
                    text = text.replaceAll("\\s+", " ").trim();
                    if (text.length() > MAX_EXTRACTED_CHARS) {
                        text = text.substring(0, MAX_EXTRACTED_CHARS) + "... [content truncated]";
                    }

                    return new ExtractedPage(currentUrl, pageTitle, text, true, null);
                } finally {
                    conn.disconnect();
                }
            }

            return new ExtractedPage(targetUrl, "", "", false, "Exceeded maximum redirects");
        } catch (SecurityException se) {
            log.warn("SSRF Security violation blocked for URL {}: {}", targetUrl, se.getMessage());
            return new ExtractedPage(targetUrl, "", "", false, "Blocked by SSRF security guardrail: " + se.getMessage());
        } catch (Exception e) {
            log.warn("Failed to fetch URL {}: {}", targetUrl, e.getMessage());
            return new ExtractedPage(targetUrl, "", "", false, "Fetch failed: " + e.getMessage());
        }
    }

    /**
     * Validates that the URI scheme is HTTP or HTTPS and resolves DNS to verify IP is not in private/reserved ranges.
     */
    public void validateUri(URI uri) throws Exception {
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new SecurityException("Unsupported URI scheme: " + scheme + ". Only HTTP and HTTPS are permitted.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("Missing host in URL: " + uri);
        }

        InetAddress[] addresses = dnsResolver.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new SecurityException("DNS resolution returned no addresses for host: " + host);
        }

        for (InetAddress address : addresses) {
            validateIpAddress(address);
        }
    }

    /**
     * Rejects loopback, private ranges, link-local, cloud metadata, and IPv6 unique local addresses.
     */
    public void validateIpAddress(InetAddress address) {
        if (address.isLoopbackAddress()) {
            if (address instanceof Inet6Address) {
                throw new SecurityException("Access to IPv6 loopback ::1 is forbidden: " + address.getHostAddress());
            }
            throw new SecurityException("Access to loopback address is forbidden: " + address.getHostAddress());
        }
        if (address.isAnyLocalAddress()) {
            throw new SecurityException("Access to wildcard local address is forbidden: " + address.getHostAddress());
        }

        byte[] rawBytes = address.getAddress();

        // Check IPv4 specific restrictions
        if (rawBytes.length == 4) {
            int b0 = rawBytes[0] & 0xFF;
            int b1 = rawBytes[1] & 0xFF;

            // 169.254.0.0/16 (Link Local & Cloud Metadata: 169.254.169.254)
            if (b0 == 169 && b1 == 254) {
                throw new SecurityException("Access to cloud metadata / link-local 169.254.0.0/16 is forbidden: " + address.getHostAddress());
            }
            // 127.0.0.0/8
            if (b0 == 127) {
                throw new SecurityException("Access to loopback 127.0.0.0/8 is forbidden: " + address.getHostAddress());
            }
            // 10.0.0.0/8
            if (b0 == 10) {
                throw new SecurityException("Access to private network 10.0.0.0/8 is forbidden: " + address.getHostAddress());
            }
            // 172.16.0.0/12 (172.16 - 172.31)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
                throw new SecurityException("Access to private network 172.16.0.0/12 is forbidden: " + address.getHostAddress());
            }
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) {
                throw new SecurityException("Access to private network 192.168.0.0/16 is forbidden: " + address.getHostAddress());
            }
            // 0.0.0.0/8
            if (b0 == 0) {
                throw new SecurityException("Access to 0.0.0.0/8 is forbidden: " + address.getHostAddress());
            }
        }

        if (address.isSiteLocalAddress()) {
            throw new SecurityException("Access to private site-local network is forbidden: " + address.getHostAddress());
        }
        if (address.isLinkLocalAddress()) {
            throw new SecurityException("Access to cloud metadata / link-local address is forbidden: " + address.getHostAddress());
        }
        if (address.isMulticastAddress()) {
            throw new SecurityException("Access to multicast address is forbidden: " + address.getHostAddress());
        }

        // Check IPv6 specific restrictions
        if (address instanceof Inet6Address) {
            // ::1 Loopback
            if (address.getHostAddress().equals("0:0:0:0:0:0:0:1") || address.getHostAddress().equals("::1")) {
                throw new SecurityException("Access to IPv6 loopback ::1 is forbidden");
            }
            // fc00::/7 (Unique Local Address - ULA)
            int firstByte = rawBytes[0] & 0xFF;
            if ((firstByte & 0xFE) == 0xFC) {
                throw new SecurityException("Access to IPv6 Unique Local Address (fc00::/7) is forbidden: " + address.getHostAddress());
            }
            // fe80::/10 (Link-Local)
            if (firstByte == 0xFE && ((rawBytes[1] & 0xC0) == 0x80)) {
                throw new SecurityException("Access to IPv6 Link-Local address (fe80::/10) is forbidden: " + address.getHostAddress());
            }
        }
    }
}
