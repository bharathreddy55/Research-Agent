package com.researchagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class SecurityConfigurationTest {
    private final SecurityConfig config = new SecurityConfig();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiresConfiguredAccountsAndDoesNotEchoMalformedSecrets() {
        for (String input : new String[]{"", "{}", "null", "private-malformed-secret"}) {
            assertThatThrownBy(() -> config.userDetailsService(mapper, input))
                    .isInstanceOf(IllegalStateException.class).hasNoCause()
                    .hasMessageNotContaining("private-malformed-secret");
        }
    }

    @Test
    void rejectsPlaintextCredentialsInvalidNamesAndWeakHashes() throws Exception {
        String validHash = new BCryptPasswordEncoder(10).encode("test-only-password");
        for (var account : java.util.List.of(
                java.util.Map.of("alice", "plaintext-secret"), java.util.Map.of("Alice", validHash),
                java.util.Map.of("x".repeat(65), validHash), java.util.Map.of("alice", validHash.replace("$10$", "$04$")))) {
            assertThatThrownBy(() -> config.userDetailsService(mapper, mapper.writeValueAsString(account)))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("plaintext-secret");
        }
        var users = config.userDetailsService(mapper, mapper.writeValueAsString(java.util.Map.of("alice", validHash)));
        assertThat(config.passwordEncoder().matches("test-only-password", users.loadUserByUsername("alice").getPassword())).isTrue();
    }

    @Test
    void refusesWildcardOrEmptyCorsConfiguration() {
        for (String origin : new String[]{"", "*", "https://*.example.com"}) {
            assertThatThrownBy(() -> config.corsConfigurationSource(origin)).isInstanceOf(IllegalStateException.class);
        }
    }
}
