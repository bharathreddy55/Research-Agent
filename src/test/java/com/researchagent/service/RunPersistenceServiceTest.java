package com.researchagent.service;

import com.researchagent.entity.*;
import com.researchagent.model.*;
import com.researchagent.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RunPersistenceServiceTest {

    @Mock
    private RunRepository runRepository;
    @Mock
    private StepRepository stepRepository;
    @Mock
    private SourceRepository sourceRepository;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ReportRepository reportRepository;

    private RunPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new RunPersistenceService(
                runRepository,
                stepRepository,
                sourceRepository,
                claimRepository,
                reportRepository
        );
    }

    @Test
    @DisplayName("createRun should save RunEntity with PENDING status")
    void shouldCreateRun() {
        persistenceService.createRun("run-1", "user-123", "Test query", "STANDARD", 1500);

        ArgumentCaptor<RunEntity> captor = ArgumentCaptor.forClass(RunEntity.class);
        verify(runRepository).save(captor.capture());

        RunEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("run-1");
        assertThat(saved.getUserId()).isEqualTo("user-123");
        assertThat(saved.getQuery()).isEqualTo("Test query");
        assertThat(saved.getDepth()).isEqualTo("STANDARD");
        assertThat(saved.getStatus()).isEqualTo(RunStatus.PENDING.name());
        assertThat(saved.getBudgetCapPaise()).isEqualTo(1500);
        assertThat(saved.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateRunStatus should update status and finishedAt on terminal states")
    void shouldUpdateRunStatus() {
        RunEntity existing = new RunEntity();
        existing.setId("run-1");
        existing.setStatus(RunStatus.RUNNING.name());

        when(runRepository.findById("run-1")).thenReturn(Optional.of(existing));

        persistenceService.updateRunStatus("run-1", RunStatus.COMPLETED);

        verify(runRepository).save(existing);
        assertThat(existing.getStatus()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(existing.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("saveStep should persist StepEntity with all trace details")
    void shouldSaveStep() {
        StepTrace trace = StepTrace.builder()
                .seq(1)
                .agent(AgentType.PLANNER)
                .action("Decomposing question")
                .prompt("What are the key angles?")
                .response("{\"subQuestions\": []}")
                .tokensIn(120)
                .tokensOut(80)
                .costPaise(2)
                .durationMs(450)
                .createdAt(Instant.now())
                .build();

        persistenceService.saveStep("run-1", trace);

        ArgumentCaptor<StepEntity> captor = ArgumentCaptor.forClass(StepEntity.class);
        verify(stepRepository).save(captor.capture());

        StepEntity saved = captor.getValue();
        assertThat(saved.getRunId()).isEqualTo("run-1");
        assertThat(saved.getSeq()).isEqualTo(1);
        assertThat(saved.getAgent()).isEqualTo("PLANNER");
        assertThat(saved.getTokensIn()).isEqualTo(120);
        assertThat(saved.getTokensOut()).isEqualTo(80);
        assertThat(saved.getCostPaise()).isEqualTo(2);
    }

    @Test
    @DisplayName("saveSources should persist all source records")
    void shouldSaveSources() {
        Source s1 = Source.builder()
                .id(1)
                .url("https://nature.com/paper")
                .title("Nature paper")
                .domain("nature.com")
                .snippet("Sample snippet")
                .content("Full text content")
                .contentHash("hash123")
                .fetchedOk(true)
                .build();

        persistenceService.saveSources("run-1", List.of(s1));

        ArgumentCaptor<SourceEntity> captor = ArgumentCaptor.forClass(SourceEntity.class);
        verify(sourceRepository, times(1)).save(captor.capture());

        SourceEntity saved = captor.getValue();
        assertThat(saved.getRunId()).isEqualTo("run-1");
        assertThat(saved.getSourceNumber()).isEqualTo(1);
        assertThat(saved.getUrl()).isEqualTo("https://nature.com/paper");
        assertThat(saved.isFetchedOk()).isTrue();
    }

    @Test
    @DisplayName("saveClaims should persist claims with supporting domains")
    void shouldSaveClaims() {
        Claim claim = Claim.builder()
                .id("c-1")
                .statement("Solid state cells achieved 1000 cycles")
                .exactQuote("1000 cycle stability under ambient conditions")
                .sourceId(1)
                .sourceDomain("nature.com")
                .status(ClaimStatus.VERIFIED)
                .supportingDomains(Set.of("nature.com", "science.org"))
                .supportingSourceIds(Set.of(1, 2))
                .build();

        persistenceService.saveClaims("run-1", List.of(claim));

        ArgumentCaptor<ClaimEntity> captor = ArgumentCaptor.forClass(ClaimEntity.class);
        verify(claimRepository, times(1)).save(captor.capture());

        ClaimEntity saved = captor.getValue();
        assertThat(saved.getRunId()).isEqualTo("run-1");
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.VERIFIED.name());
        assertThat(saved.getSupportingDomains()).contains("nature.com");
    }

    @Test
    @DisplayName("saveReport should create version 1 and increment version on subsequent saves")
    void shouldSaveReportWithVersioning() {
        ResearchReport report = ResearchReport.builder()
                .query("Solid state batteries")
                .title("Research Brief: Solid state batteries")
                .markdownContent("# Solid State Batteries Report [1]")
                .confidenceScore(0.85)
                .totalSources(5)
                .verifiedClaimsCount(4)
                .unverifiedClaimsCount(1)
                .build();

        // 1. First save -> version 1
        when(reportRepository.findTopByRunIdOrderByVersionDesc("run-1")).thenReturn(Optional.empty());
        persistenceService.saveReport("run-1", report);

        ArgumentCaptor<ReportEntity> captor1 = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository).save(captor1.capture());
        assertThat(captor1.getValue().getVersion()).isEqualTo(1);

        // 2. Second save (reflection pass revision) -> version 2
        ReportEntity v1Entity = captor1.getValue();
        when(reportRepository.findTopByRunIdOrderByVersionDesc("run-1")).thenReturn(Optional.of(v1Entity));
        persistenceService.saveReport("run-1", report);

        ArgumentCaptor<ReportEntity> captor2 = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository, times(2)).save(captor2.capture());
        assertThat(captor2.getValue().getVersion()).isEqualTo(2);
    }
}
