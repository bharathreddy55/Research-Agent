package com.researchagent.service;

import com.researchagent.dto.CreateRunRequest;
import com.researchagent.dto.ReportResponse;
import com.researchagent.dto.RunResponse;
import com.researchagent.entity.*;
import com.researchagent.event.RedisEventPublisher;
import com.researchagent.model.AgentType;
import com.researchagent.model.RunStatus;
import com.researchagent.producer.ResearchJobProducer;
import com.researchagent.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests RunService with hand-rolled in-memory fakes — no Mockito (JDK 25 incompatible).
 *
 * Uses concrete static inner fake classes that implement the specific repository
 * interfaces, implementing all JpaRepository methods with a HashMap-backed store.
 */
class RunServiceTest {

    // =========================================================================
    // Concrete in-memory fakes (static inner classes)
    // =========================================================================

    /** In-memory fake for RunRepository. */
    private static class FakeRunRepository implements RunRepository {
        final Map<String, RunEntity> store = new LinkedHashMap<>();
        final List<RunEntity> saves = new ArrayList<>();

        @Override public Optional<RunEntity> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public <S extends RunEntity> S save(S e) { store.put(e.getId(), e); saves.add(e); return e; }
        @Override public boolean existsById(String id) { return store.containsKey(id); }
        @Override public List<RunEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public Page<RunEntity> findAll(Pageable p) { List<RunEntity> all = findAll(); return new PageImpl<>(all, p, all.size()); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(String id) { store.remove(id); }
        @Override public void delete(RunEntity e) { store.remove(e.getId()); }
        @Override public void deleteAllById(Iterable<? extends String> ids) { ids.forEach(store::remove); }
        @Override public void deleteAll(Iterable<? extends RunEntity> es) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends RunEntity> List<S> saveAll(Iterable<S> es) { es.forEach(e -> save(e)); return List.of(); }
        @Override public List<RunEntity> findAllById(Iterable<String> ids) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends RunEntity> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends RunEntity> List<S> saveAllAndFlush(Iterable<S> es) { saveAll(es); return List.of(); }
        @Override public void deleteAllInBatch(Iterable<RunEntity> es) {}
        @Override public void deleteAllByIdInBatch(Iterable<String> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public RunEntity getOne(String id) { return store.get(id); }
        @Override public RunEntity getById(String id) { return store.get(id); }
        @Override public RunEntity getReferenceById(String id) { return store.get(id); }
        @Override public List<RunEntity> findAll(Sort s) { return findAll(); }
        @Override public <S extends RunEntity> Optional<S> findOne(Example<S> ex) { return Optional.empty(); }
        @Override public <S extends RunEntity> List<S> findAll(Example<S> ex) { return List.of(); }
        @Override public <S extends RunEntity> List<S> findAll(Example<S> ex, Sort s) { return List.of(); }
        @Override public <S extends RunEntity> Page<S> findAll(Example<S> ex, Pageable p) { return Page.empty(); }
        @Override public <S extends RunEntity> long count(Example<S> ex) { return 0; }
        @Override public <S extends RunEntity> boolean exists(Example<S> ex) { return false; }
        @Override public <S extends RunEntity, R> R findBy(Example<S> ex, Function<FluentQuery.FetchableFluentQuery<S>, R> q) { return null; }
        // Custom query methods
        @Override public List<RunEntity> findByStatus(String status) { return List.of(); }
        @Override public List<RunEntity> findAllByOrderByStartedAtDesc() { return findAll(); }
        @Override public long sumCostPaiseByStartedAtAfter(java.time.Instant t) { return 0L; }
    }

    /** In-memory fake for ReportRepository. */
    private static class FakeReportRepository implements ReportRepository {
        final Map<String, List<ReportEntity>> byRunId = new LinkedHashMap<>();

        void seed(String runId, List<ReportEntity> reports) { byRunId.put(runId, reports); }

        @Override public Optional<ReportEntity> findTopByRunIdOrderByVersionDesc(String runId) {
            List<ReportEntity> list = byRunId.getOrDefault(runId, List.of());
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
        @Override public List<ReportEntity> findByRunIdOrderByVersionDesc(String runId) {
            return byRunId.getOrDefault(runId, List.of());
        }
        @Override public Optional<ReportEntity> findById(String id) { return Optional.empty(); }
        @Override public <S extends ReportEntity> S save(S e) { return e; }
        @Override public boolean existsById(String id) { return false; }
        @Override public List<ReportEntity> findAll() { return List.of(); }
        @Override public Page<ReportEntity> findAll(Pageable p) { return Page.empty(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(String id) {}
        @Override public void delete(ReportEntity e) {}
        @Override public void deleteAllById(Iterable<? extends String> ids) {}
        @Override public void deleteAll(Iterable<? extends ReportEntity> es) {}
        @Override public void deleteAll() {}
        @Override public <S extends ReportEntity> List<S> saveAll(Iterable<S> es) { return List.of(); }
        @Override public List<ReportEntity> findAllById(Iterable<String> ids) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends ReportEntity> S saveAndFlush(S e) { return e; }
        @Override public <S extends ReportEntity> List<S> saveAllAndFlush(Iterable<S> es) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<ReportEntity> es) {}
        @Override public void deleteAllByIdInBatch(Iterable<String> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public ReportEntity getOne(String id) { return null; }
        @Override public ReportEntity getById(String id) { return null; }
        @Override public ReportEntity getReferenceById(String id) { return null; }
        @Override public List<ReportEntity> findAll(Sort s) { return List.of(); }
        @Override public <S extends ReportEntity> Optional<S> findOne(Example<S> ex) { return Optional.empty(); }
        @Override public <S extends ReportEntity> List<S> findAll(Example<S> ex) { return List.of(); }
        @Override public <S extends ReportEntity> List<S> findAll(Example<S> ex, Sort s) { return List.of(); }
        @Override public <S extends ReportEntity> Page<S> findAll(Example<S> ex, Pageable p) { return Page.empty(); }
        @Override public <S extends ReportEntity> long count(Example<S> ex) { return 0; }
        @Override public <S extends ReportEntity> boolean exists(Example<S> ex) { return false; }
        @Override public <S extends ReportEntity, R> R findBy(Example<S> ex, Function<FluentQuery.FetchableFluentQuery<S>, R> q) { return null; }
    }

    /** No-op fake for StepRepository (String PK). */
    private static class FakeStepRepository implements StepRepository {
        @Override public List<StepEntity> findByRunIdOrderBySeqAsc(String runId) { return List.of(); }
        @Override public Optional<StepEntity> findById(String id) { return Optional.empty(); }
        @Override public <S extends StepEntity> S save(S e) { return e; }
        @Override public boolean existsById(String id) { return false; }
        @Override public List<StepEntity> findAll() { return List.of(); }
        @Override public Page<StepEntity> findAll(Pageable p) { return Page.empty(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(String id) {}
        @Override public void delete(StepEntity e) {}
        @Override public void deleteAllById(Iterable<? extends String> ids) {}
        @Override public void deleteAll(Iterable<? extends StepEntity> es) {}
        @Override public void deleteAll() {}
        @Override public <S extends StepEntity> List<S> saveAll(Iterable<S> es) { return List.of(); }
        @Override public List<StepEntity> findAllById(Iterable<String> ids) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends StepEntity> S saveAndFlush(S e) { return e; }
        @Override public <S extends StepEntity> List<S> saveAllAndFlush(Iterable<S> es) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<StepEntity> es) {}
        @Override public void deleteAllByIdInBatch(Iterable<String> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public StepEntity getOne(String id) { return null; }
        @Override public StepEntity getById(String id) { return null; }
        @Override public StepEntity getReferenceById(String id) { return null; }
        @Override public List<StepEntity> findAll(Sort s) { return List.of(); }
        @Override public <S extends StepEntity> Optional<S> findOne(Example<S> ex) { return Optional.empty(); }
        @Override public <S extends StepEntity> List<S> findAll(Example<S> ex) { return List.of(); }
        @Override public <S extends StepEntity> List<S> findAll(Example<S> ex, Sort s) { return List.of(); }
        @Override public <S extends StepEntity> Page<S> findAll(Example<S> ex, Pageable p) { return Page.empty(); }
        @Override public <S extends StepEntity> long count(Example<S> ex) { return 0; }
        @Override public <S extends StepEntity> boolean exists(Example<S> ex) { return false; }
        @Override public <S extends StepEntity, R> R findBy(Example<S> ex, Function<FluentQuery.FetchableFluentQuery<S>, R> q) { return null; }
    }

    /** No-op fake for SourceRepository (Long PK). */
    private static class FakeSourceRepository implements SourceRepository {
        @Override public List<SourceEntity> findByRunIdOrderBySourceNumberAsc(String runId) { return List.of(); }
        @Override public Optional<SourceEntity> findById(Long id) { return Optional.empty(); }
        @Override public <S extends SourceEntity> S save(S e) { return e; }
        @Override public boolean existsById(Long id) { return false; }
        @Override public List<SourceEntity> findAll() { return List.of(); }
        @Override public Page<SourceEntity> findAll(Pageable p) { return Page.empty(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(SourceEntity e) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends SourceEntity> es) {}
        @Override public void deleteAll() {}
        @Override public <S extends SourceEntity> List<S> saveAll(Iterable<S> es) { return List.of(); }
        @Override public List<SourceEntity> findAllById(Iterable<Long> ids) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends SourceEntity> S saveAndFlush(S e) { return e; }
        @Override public <S extends SourceEntity> List<S> saveAllAndFlush(Iterable<S> es) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<SourceEntity> es) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public SourceEntity getOne(Long id) { return null; }
        @Override public SourceEntity getById(Long id) { return null; }
        @Override public SourceEntity getReferenceById(Long id) { return null; }
        @Override public List<SourceEntity> findAll(Sort s) { return List.of(); }
        @Override public <S extends SourceEntity> Optional<S> findOne(Example<S> ex) { return Optional.empty(); }
        @Override public <S extends SourceEntity> List<S> findAll(Example<S> ex) { return List.of(); }
        @Override public <S extends SourceEntity> List<S> findAll(Example<S> ex, Sort s) { return List.of(); }
        @Override public <S extends SourceEntity> Page<S> findAll(Example<S> ex, Pageable p) { return Page.empty(); }
        @Override public <S extends SourceEntity> long count(Example<S> ex) { return 0; }
        @Override public <S extends SourceEntity> boolean exists(Example<S> ex) { return false; }
        @Override public <S extends SourceEntity, R> R findBy(Example<S> ex, Function<FluentQuery.FetchableFluentQuery<S>, R> q) { return null; }
    }

    /** No-op fake for ClaimRepository (String PK). */
    private static class FakeClaimRepository implements ClaimRepository {
        @Override public List<ClaimEntity> findByRunId(String runId) { return List.of(); }
        @Override public Optional<ClaimEntity> findById(String id) { return Optional.empty(); }
        @Override public <S extends ClaimEntity> S save(S e) { return e; }
        @Override public boolean existsById(String id) { return false; }
        @Override public List<ClaimEntity> findAll() { return List.of(); }
        @Override public Page<ClaimEntity> findAll(Pageable p) { return Page.empty(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(String id) {}
        @Override public void delete(ClaimEntity e) {}
        @Override public void deleteAllById(Iterable<? extends String> ids) {}
        @Override public void deleteAll(Iterable<? extends ClaimEntity> es) {}
        @Override public void deleteAll() {}
        @Override public <S extends ClaimEntity> List<S> saveAll(Iterable<S> es) { return List.of(); }
        @Override public List<ClaimEntity> findAllById(Iterable<String> ids) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends ClaimEntity> S saveAndFlush(S e) { return e; }
        @Override public <S extends ClaimEntity> List<S> saveAllAndFlush(Iterable<S> es) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<ClaimEntity> es) {}
        @Override public void deleteAllByIdInBatch(Iterable<String> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public ClaimEntity getOne(String id) { return null; }
        @Override public ClaimEntity getById(String id) { return null; }
        @Override public ClaimEntity getReferenceById(String id) { return null; }
        @Override public List<ClaimEntity> findAll(Sort s) { return List.of(); }
        @Override public <S extends ClaimEntity> Optional<S> findOne(Example<S> ex) { return Optional.empty(); }
        @Override public <S extends ClaimEntity> List<S> findAll(Example<S> ex) { return List.of(); }
        @Override public <S extends ClaimEntity> List<S> findAll(Example<S> ex, Sort s) { return List.of(); }
        @Override public <S extends ClaimEntity> Page<S> findAll(Example<S> ex, Pageable p) { return Page.empty(); }
        @Override public <S extends ClaimEntity> long count(Example<S> ex) { return 0; }
        @Override public <S extends ClaimEntity> boolean exists(Example<S> ex) { return false; }
        @Override public <S extends ClaimEntity, R> R findBy(Example<S> ex, Function<FluentQuery.FetchableFluentQuery<S>, R> q) { return null; }
    }

    // =========================================================================
    // Test setup & state
    // =========================================================================

    private final List<String> createdRuns  = new ArrayList<>();
    private final List<String> enqueuedJobs = new ArrayList<>();
    private final AtomicReference<Boolean> rateLimitAllow = new AtomicReference<>(true);
    private final List<String[]> publishedEvents = new ArrayList<>();

    private FakeRunRepository    fakeRunRepo;
    private FakeReportRepository fakeReportRepo;
    private RunService           runService;

    @BeforeEach
    void setUp() {
        createdRuns.clear(); enqueuedJobs.clear(); publishedEvents.clear();
        rateLimitAllow.set(true);

        fakeRunRepo    = new FakeRunRepository();
        fakeReportRepo = new FakeReportRepository();

        RunPersistenceService stubPersistence = new RunPersistenceService(null, null, null, null, null) {
            @Override
            public void createRun(String runId, String userId, String query, String depth, long budgetCapPaise) {
                createdRuns.add(runId);
                RunEntity e = new RunEntity();
                e.setId(runId); e.setUserId(userId); e.setQuery(query);
                e.setDepth(depth); e.setStatus("PENDING");
                e.setBudgetCapPaise(budgetCapPaise); e.setStartedAt(Instant.now());
                fakeRunRepo.store.put(runId, e);
            }
        };

        ResearchJobProducer stubJobProducer = new ResearchJobProducer(null) {
            @Override
            public void enqueueJob(String runId, String userId, String query, String depth, long budgetCapPaise) {
                enqueuedJobs.add(runId);
            }
        };

        RateLimiterService stubRateLimiter = new RateLimiterService(null, null) {
            @Override public boolean allowRun(String userId) { return rateLimitAllow.get(); }
        };

        RedisEventPublisher stubEventPublisher =
                new RedisEventPublisher(null, new com.fasterxml.jackson.databind.ObjectMapper()) {
            @Override
            public void onEvent(String runId, RunStatus status, AgentType agent, String message,
                                int tokensIn, int tokensOut, long costPaise) {
                publishedEvents.add(new String[]{runId, status.name()});
            }
        };

        runService = new RunService(
                fakeRunRepo, new FakeStepRepository(), new FakeSourceRepository(),
                new FakeClaimRepository(), fakeReportRepo,
                stubPersistence, stubJobProducer, stubRateLimiter, stubEventPublisher);
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    @DisplayName("createAndEnqueueRun should succeed when rate limit permits")
    void shouldCreateAndEnqueueRun() {
        rateLimitAllow.set(true);
        CreateRunRequest req = new CreateRunRequest("CRISPR therapeutic advances 2026", "QUICK", 1200);

        RunResponse resp = runService.createAndEnqueueRun(req, "user-1");

        assertThat(resp).isNotNull();
        assertThat(resp.getQuery()).isEqualTo("CRISPR therapeutic advances 2026");
        assertThat(createdRuns).hasSize(1);
        assertThat(enqueuedJobs).hasSize(1);
        assertThat(createdRuns.get(0)).isEqualTo(enqueuedJobs.get(0));
    }

    @Test
    @DisplayName("createAndEnqueueRun should throw 429 when rate limit exceeded")
    void shouldThrowWhenRateLimitExceeded() {
        rateLimitAllow.set(false);
        CreateRunRequest req = new CreateRunRequest("Quantum key distribution", "DEEP", 2000);

        assertThatThrownBy(() -> runService.createAndEnqueueRun(req, "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many concurrent active runs");
    }

    @Test
    @DisplayName("getReport should return previous draft content when version > 1 (Audit B8)")
    void shouldReturnDraftDiffOnRevisedReport() {
        ReportEntity v2 = new ReportEntity();
        v2.setId("rep-v2"); v2.setRunId("run-diff"); v2.setQuery("Q");
        v2.setTitle("Final Revised Report");
        v2.setMarkdownContent("# Revised Report Content with more citations [1][2]");
        v2.setVersion(2); v2.setConfidenceScore(0.9); v2.setCreatedAt(Instant.now());

        ReportEntity v1 = new ReportEntity();
        v1.setId("rep-v1"); v1.setRunId("run-diff"); v1.setQuery("Q");
        v1.setTitle("Draft Report");
        v1.setMarkdownContent("# Initial Draft Content [1]");
        v1.setVersion(1); v1.setConfidenceScore(0.7); v1.setCreatedAt(Instant.now());

        fakeReportRepo.seed("run-diff", List.of(v2, v1));

        ReportResponse report = runService.getReport("run-diff");

        assertThat(report.getVersion()).isEqualTo(2);
        assertThat(report.getMarkdownContent()).contains("Revised Report Content");
        assertThat(report.getPreviousDraftContent()).contains("Initial Draft Content");
    }

    @Test
    @DisplayName("cancelRun should mark run CANCELLED and publish event")
    void shouldCancelRun() {
        RunEntity activeRun = new RunEntity();
        activeRun.setId("run-cancel"); activeRun.setStatus("SEARCHING");
        activeRun.setStartedAt(Instant.now());
        fakeRunRepo.store.put("run-cancel", activeRun);

        RunResponse cancelled = runService.cancelRun("run-cancel");

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(publishedEvents).anyMatch(e -> e[0].equals("run-cancel") && e[1].equals("CANCELLED"));
    }
}
