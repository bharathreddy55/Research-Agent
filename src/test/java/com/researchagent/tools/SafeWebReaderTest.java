package com.researchagent.tools;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeWebReaderTest {

    private final SafeWebReader reader = new SafeWebReader();
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should block access to IPv4 loopback (127.0.0.1)")
    void shouldBlockLoopbackIpv4() {
        assertThatThrownBy(() -> reader.validateUri(new URI("http://127.0.0.1:8080/test")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    @DisplayName("Should block access to localhost hostname")
    void shouldBlockLocalhost() {
        assertThatThrownBy(() -> reader.validateUri(new URI("http://localhost:8080/test")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Should block access to AWS/Cloud metadata service (169.254.169.254)")
    void shouldBlockCloudMetadata() {
        assertThatThrownBy(() -> reader.validateUri(new URI("http://169.254.169.254/latest/meta-data/")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("cloud metadata");
    }

    @Test
    @DisplayName("Should block access to private RFC 1918 networks (10.0.0.1, 192.168.1.1, 172.20.0.1)")
    void shouldBlockPrivateNetworks() throws Exception {
        assertThatThrownBy(() -> reader.validateIpAddress(InetAddress.getByName("10.0.0.1")))
                .isInstanceOf(SecurityException.class);

        assertThatThrownBy(() -> reader.validateIpAddress(InetAddress.getByName("192.168.1.1")))
                .isInstanceOf(SecurityException.class);

        assertThatThrownBy(() -> reader.validateIpAddress(InetAddress.getByName("172.20.0.1")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Should block access to IPv6 loopback (::1) and Unique Local Address (fc00::/7)")
    void shouldBlockIpv6LoopbackAndUla() throws Exception {
        assertThatThrownBy(() -> reader.validateIpAddress(InetAddress.getByName("::1")))
                .isInstanceOf(SecurityException.class)
                .hasMessageMatching("(?i).*loopback.*");

        assertThatThrownBy(() -> reader.validateIpAddress(InetAddress.getByName("fc00::1")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unique Local Address");
    }

    @Test
    @DisplayName("Should reject non-HTTP schemes like file:// and ftp://")
    void shouldBlockNonHttpSchemes() {
        assertThatThrownBy(() -> reader.validateUri(new URI("file:///etc/passwd")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsupported URI scheme");

        assertThatThrownBy(() -> reader.validateUri(new URI("ftp://internal-server/data")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsupported URI scheme");
    }

    @Test
    @DisplayName("Should block redirect bypass attempt (A2) pointing to AWS metadata IP 169.254.169.254")
    void shouldBlockRedirectBypassToCloudMetadata() {
        wireMockServer.stubFor(get(urlEqualTo("/safe-public-page"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "http://169.254.169.254/latest/meta-data")));

        // Use custom resolver for the mock server host to pass the first hop check
        SafeWebReader testReader = new SafeWebReader();
        testReader.setDnsResolver(host -> {
            if ("test-public-domain.com".equals(host)) {
                return new InetAddress[]{InetAddress.getByName("93.184.216.34")}; // example.com public IP
            }
            return InetAddress.getAllByName(host);
        });

        SafeWebReader.ExtractedPage page = testReader.readUrl("http://169.254.169.254/latest/meta-data");
        assertThat(page.isSuccess()).isFalse();
        assertThat(page.getErrorMessage()).contains("Blocked by SSRF security guardrail");
    }
}
