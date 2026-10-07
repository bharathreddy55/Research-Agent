package com.researchagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Observability & Tracing Configuration for LangFuse (PRD §11 & Audit B1).
 *
 * <p>When {@code research-agent.observability.langfuse.enabled=true}, logs tracing metadata
 * and exports token usage, agent latencies, and model parameters to LangFuse.</p>
 */
@Configuration
public class LangFuseConfig {

    private static final Logger log = LoggerFactory.getLogger(LangFuseConfig.class);

    @Value("${research-agent.observability.langfuse.enabled:false}")
    private boolean enabled;

    @Value("${research-agent.observability.langfuse.public-key:}")
    private String publicKey;

    @Value("${research-agent.observability.langfuse.secret-key:}")
    private String secretKey;

    @Value("${research-agent.observability.langfuse.host:https://cloud.langfuse.com}")
    private String host;

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("LangFuse Observability ENABLED — Host: {}, PublicKey: {}", host, truncate(publicKey));
        } else {
            log.info("LangFuse Observability DISABLED (database-only tracing active)");
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getPublicKey() { return publicKey; }
    public String getSecretKey() { return secretKey; }
    public String getHost() { return host; }

    private static String truncate(String val) {
        if (val == null || val.length() <= 6) return "***";
        return val.substring(0, 4) + "***";
    }
}
