package com.researchagent.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.controller.*;
import com.researchagent.entity.*;
import com.researchagent.event.RedisEventPublisher;
import com.researchagent.producer.ResearchJobProducer;
import com.researchagent.repository.*;
import com.researchagent.service.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.domain.*;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Uses the actual security filters, login, controllers, and ownership service.
 * Only infrastructure is replaced; no database, Redis, RabbitMQ, or model calls. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RunApiSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private Fixture data;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private static final String PASSWORD = "test-only-password-2026";
    private static final String REQUEST = "{\"query\":\"Research secure software systems\",\"depth\":\"QUICK\",\"budgetCapPaise\":1500}";

    @BeforeAll
    void start() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        String hash = new BCryptPasswordEncoder(10).encode(PASSWORD);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-accounts", Map.of(
                "research-agent.auth.users-json", mapper.writeValueAsString(Map.of("alice", hash, "bob", hash)))));
        context.register(WebConfig.class);
        context.refresh();
        mvc = webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        data = context.getBean(Fixture.class);
    }

    @AfterAll
    void stop() { if (context != null) context.close(); }

    @BeforeEach
    void seed() {
        data.runs.clear(); data.jobs.clear();
        for (String user : List.of("alice", "bob")) {
            RunEntity run = new RunEntity();
            run.setId(user + "-run"); run.setUserId(user); run.setQuery(user + " private research");
            run.setStatus("SEARCHING"); run.setDepth("QUICK"); run.setStartedAt(Instant.now());
            data.runs.put(run.getId(), run);
        }
    }

    @Test
    void anonymousCannotReadAnyRunRouteOrStartPaidWork() throws Exception {
        for (String path : List.of("", "/alice-run", "/alice-run/report", "/alice-run/trace",
                "/alice-run/sources", "/alice-run/claims", "/alice-run/pdf", "/alice-run/stream")) {
            mvc.perform(get("/api/runs" + path)).andExpect(status().isUnauthorized());
        }
        MockHttpSession anonymous = new MockHttpSession(context.getServletContext());
        JsonNode csrf = csrf(anonymous);
        mvc.perform(post("/api/runs").session(anonymous).contentType("application/json").content(REQUEST)
                .header(csrf.get("headerName").asText(), csrf.get("token").asText()))
                .andExpect(status().isUnauthorized());
        assertThat(data.jobs).isEmpty();
    }

    @Test
    void loginRejectsWrongPasswordAndRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "alice").param("password", PASSWORD))
                .andExpect(status().isForbidden());
        MockHttpSession session = new MockHttpSession(context.getServletContext());
        JsonNode csrf = csrf(session);
        mvc.perform(post("/api/auth/login").session(session).param("username", "alice").param("password", "wrong")
                .header(csrf.get("headerName").asText(), csrf.get("token").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void historyAndAllRunOperationsAreScopedToTheSignedInOwner() throws Exception {
        for (String user : List.of("alice", "bob")) {
            MockHttpSession session = login(user);
            String other = user.equals("alice") ? "bob" : "alice";
            mvc.perform(get("/api/runs").session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].userId").value(user));
            for (String suffix : List.of("", "/report", "/trace", "/sources", "/claims", "/pdf", "/stream")) {
                mvc.perform(get("/api/runs/" + other + "-run" + suffix).session(session))
                        .andExpect(status().isNotFound());
            }
            JsonNode token = csrf(session);
            mvc.perform(post("/api/runs/" + other + "-run/cancel").session(session)
                    .header(token.get("headerName").asText(), token.get("token").asText()))
                    .andExpect(status().isNotFound());
            assertThat(data.runs.get(other + "-run").getStatus()).isEqualTo("SEARCHING");
            mvc.perform(get("/api/runs/" + user + "-run").session(session)).andExpect(status().isOk());
            mvc.perform(get("/api/runs/" + user + "-run/report").session(session)).andExpect(status().isOk());
            mvc.perform(get("/api/runs/" + user + "-run/pdf").session(session)).andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"));
            session.invalidate();
        }
        assertThat(context.getBean(RedisSseBridgeService.class).getActiveClientCount("alice-run")).isZero();
        assertThat(context.getBean(RedisSseBridgeService.class).getActiveClientCount("bob-run")).isZero();
    }

    @Test
    void creationIgnoresSpoofedIdentityAndRejectsMissingOrStaleCsrf() throws Exception {
        MockHttpSession session = new MockHttpSession(context.getServletContext());
        JsonNode beforeLogin = csrf(session);
        session = login("alice", session);
        mvc.perform(post("/api/runs").session(session).contentType("application/json").content(REQUEST))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/runs").session(session).contentType("application/json").content(REQUEST)
                .header(beforeLogin.get("headerName").asText(), beforeLogin.get("token").asText()))
                .andExpect(status().isForbidden());
        assertThat(data.jobs).isEmpty();
        JsonNode token = csrf(session);
        mvc.perform(post("/api/runs").session(session).header("X-User-Id", "bob")
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType("application/json").content(REQUEST))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value("alice"));
        assertThat(data.jobs).containsExactly("alice");
    }

    @Test
    void logoutRevokesTheSessionAndExistingStream() throws Exception {
        MockHttpSession session = login("alice");
        String sessionId = session.getId();
        MvcResult stream = mvc.perform(get("/api/runs/alice-run/stream").session(session))
                .andExpect(request().asyncStarted()).andReturn();
        JsonNode token = csrf(session);
        mvc.perform(post("/api/auth/logout").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        stream.getAsyncResult(1000);
        mvc.perform(asyncDispatch(stream)).andExpect(status().isOk());
        assertThat(context.getBean(RedisSseBridgeService.class).getActiveClientCount("alice-run")).isZero();
        mvc.perform(get("/api/runs").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", sessionId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionExpirationAndNewLoginCloseOldStreams() throws Exception {
        MockHttpSession session = login("alice");
        MvcResult stream = mvc.perform(get("/api/runs/alice-run/stream").session(session))
                .andExpect(request().asyncStarted()).andReturn();
        session = login("bob", session);
        stream.getAsyncResult(1000);
        mvc.perform(asyncDispatch(stream));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.username").value("bob"));
        assertThat(context.getBean(RedisSseBridgeService.class).getActiveClientCount("alice-run")).isZero();
        MvcResult expiring = mvc.perform(get("/api/runs/bob-run/stream").session(session))
                .andExpect(request().asyncStarted()).andReturn();
        session.invalidate(); // Container expiry invokes the same binding listeners.
        expiring.getAsyncResult(1000);
        mvc.perform(asyncDispatch(expiring));
        assertThat(context.getBean(RedisSseBridgeService.class).getActiveClientCount("bob-run")).isZero();
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/runs").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "X-CSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/runs").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private JsonNode csrf(MockHttpSession session) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpSession login(String user) throws Exception {
        return login(user, new MockHttpSession(context.getServletContext()));
    }

    private MockHttpSession login(String user, MockHttpSession session) throws Exception {
        String oldId = session.getId();
        JsonNode token = csrf(session);
        MvcResult result = mvc.perform(post("/api/auth/login").session(session).param("username", user).param("password", PASSWORD)
                .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(user)).andReturn();
        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(authenticated.getId()).isNotEqualTo(oldId);
        return authenticated;
    }

    static class Fixture {
        final Map<String, RunEntity> runs = new LinkedHashMap<>();
        final List<String> jobs = new ArrayList<>();
        @SuppressWarnings("unchecked")
        <T> T repository(Class<T> type) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("toString")) return "Security test " + type.getSimpleName();
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("equals")) return proxy == args[0];
                if (name.equals("findByIdAndUserId")) return Optional.ofNullable(runs.get(args[0]))
                        .filter(run -> run.getUserId().equals(args[1]));
                if (name.equals("findById") || name.equals("findByIdForUpdate")) return Optional.ofNullable(runs.get(args[0]));
                if (name.equals("findStatusById")) return Optional.ofNullable(runs.get(args[0])).map(RunEntity::getStatus);
                if (name.equals("findByUserId")) {
                    List<RunEntity> owned = runs.values().stream().filter(run -> run.getUserId().equals(args[0])).toList();
                    return new PageImpl<>(owned, (Pageable) args[1], owned.size());
                }
                if (name.equals("save") && args[0] instanceof RunEntity run) {
                    runs.put(run.getId(), run); return run;
                }
                if (name.equals("findTopByRunIdOrderByVersionDesc")) return Optional.empty();
                if (name.equals("findByRunIdOrderByVersionDesc")) {
                    ReportEntity report = new ReportEntity(); report.setRunId((String) args[0]);
                    report.setTitle("Private brief"); report.setMarkdownContent("## Private findings\nOwner content."); report.setVersion(1);
                    return List.of(report);
                }
                if (List.class.isAssignableFrom(method.getReturnType())) return List.of();
                throw new UnsupportedOperationException(method.toString());
            });
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, RunController.class, AuthController.class, GlobalExceptionHandler.class, RunService.class})
    static class WebConfig {
        @Bean Fixture fixture() { return new Fixture(); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean RunRepository runs(Fixture f) { return f.repository(RunRepository.class); }
        @Bean StepRepository steps(Fixture f) { return f.repository(StepRepository.class); }
        @Bean SourceRepository sources(Fixture f) { return f.repository(SourceRepository.class); }
        @Bean ClaimRepository claims(Fixture f) { return f.repository(ClaimRepository.class); }
        @Bean ReportRepository reports(Fixture f) { return f.repository(ReportRepository.class); }
        @Bean RunPersistenceService persistence(RunRepository r, StepRepository s, SourceRepository so, ClaimRepository c, ReportRepository re) {
            return new RunPersistenceService(r, s, so, c, re);
        }
        @Bean ResearchJobProducer producer(Fixture f) { return new ResearchJobProducer(null) {
            @Override public void enqueueJob(String id, String user, String query, String depth, long budget) { f.jobs.add(user); }
        }; }
        @Bean RateLimiterService limiter() { return new RateLimiterService(null, null) {
            @Override public boolean allowRun(String user) { return true; }
        }; }
        @Bean RedisEventPublisher publisher(ObjectMapper mapper) { return new RedisEventPublisher(null, mapper); }
        @Bean RedisSseBridgeService bridge(ObjectMapper mapper) { return new RedisSseBridgeService(mapper, null); }
        @Bean PdfReportService pdf() { return new PdfReportService("target/security-reports"); }
    }
}
