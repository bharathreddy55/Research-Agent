package com.researchagent.service;

import com.researchagent.entity.RunEntity;
import com.researchagent.entity.StepEntity;
import com.researchagent.model.RunStatus;
import com.researchagent.repository.RunRepository;
import com.researchagent.repository.StepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerCrashRecoveryServiceTest {

    @Mock
    private RunRepository runRepository;
    @Mock
    private StepRepository stepRepository;

    private WorkerCrashRecoveryService crashRecoveryService;

    @BeforeEach
    void setUp() {
        crashRecoveryService = new WorkerCrashRecoveryService(runRepository, stepRepository);
    }

    @Test
    @DisplayName("Should detect dangling non-terminal runs on startup and mark them FAILED")
    void shouldRecoverDanglingRuns() {
        RunEntity danglingRun = new RunEntity();
        danglingRun.setId("dangling-1");
        danglingRun.setStatus(RunStatus.READING.name());

        RunEntity completedRun = new RunEntity();
        completedRun.setId("completed-2");
        completedRun.setStatus(RunStatus.COMPLETED.name());

        StepEntity step1 = new StepEntity();
        step1.setRunId("dangling-1");
        step1.setSeq(1);
        step1.setAgent("PLANNER");

        when(runRepository.findAll()).thenReturn(List.of(danglingRun, completedRun));
        when(stepRepository.findByRunIdOrderBySeqAsc("dangling-1")).thenReturn(List.of(step1));

        crashRecoveryService.recoverDanglingRunsOnStartup();

        ArgumentCaptor<RunEntity> captor = ArgumentCaptor.forClass(RunEntity.class);
        verify(runRepository).save(captor.capture());

        RunEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("dangling-1");
        assertThat(saved.getStatus()).isEqualTo(RunStatus.FAILED.name());
        assertThat(saved.getFailureReason()).contains("interrupted by worker restart");
        assertThat(saved.getFinishedAt()).isNotNull();
    }
}
