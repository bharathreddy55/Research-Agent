package com.researchagent.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(ObjectMapper mapper,
            @Value("${research-agent.auth.users-json:}") String usersJson) {
        Map<String, String> accounts;
        try {
            accounts = mapper.readValue(usersJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            // Never include credentials or the supplied JSON in exceptions/logs.
            throw new IllegalStateException("Configure AUTH_USERS_JSON with usernames and BCrypt password hashes");
        }
        if (accounts == null || accounts.isEmpty()) {
            throw new IllegalStateException("At least one account is required in AUTH_USERS_JSON");
        }
        List<UserDetails> users = new ArrayList<>();
        accounts.forEach((name, hash) -> {
            if (name == null || !name.matches("[a-z0-9][a-z0-9._-]{0,63}") || hash == null
                    || !hash.matches("\\$2[aby]\\$(1[0-6])\\$[./A-Za-z0-9]{53}")) {
                throw new IllegalStateException("Accounts require lowercase usernames and BCrypt hashes (cost 10–16)");
            }
            users.add(User.withUsername(name).password(hash).roles("USER").build());
        });
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> jsonError(res, 401, "Sign in required"))
                        .accessDeniedHandler((req, res, ex) -> jsonError(res, 403, "Request denied")))
                .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                        .successHandler((req, res, auth) -> {
                            SessionStreams.close(req.getSession(false));
                            res.setContentType("application/json");
                            mapper.writeValue(res.getOutputStream(), Map.of("username", auth.getName()));
                        })
                        .failureHandler((req, res, ex) -> jsonError(res, 401, "Invalid username or password")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${research-agent.auth.allowed-origins:http://localhost:3000,http://localhost:5173}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allowed.isEmpty() || allowed.stream().anyMatch(s -> s.contains("*"))) {
            throw new IllegalStateException("AUTH_ALLOWED_ORIGINS requires explicit browser origins");
        }
        config.setAllowedOrigins(allowed);
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void jsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
