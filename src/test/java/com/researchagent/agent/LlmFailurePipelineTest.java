package com.researchagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.llm.LlmService;
import com.researchagent.model.*;
import com.researchagent.tools.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmFailurePipelineTest {
    @TempDir Path reportsDir;

    @ParameterizedTest
    @ValueSource(strings = {"PLANNER", "READER", "VERIFIER", "WRITER", "REFLECTION"})
    void providerFailureAtEveryModelStageFailsTheRunWithoutPublishingAReport(String failedStage) {
        CostTracker tracker = new CostTracker();
        LlmService llm = new LlmService("openai", "", "", "test-key", "gpt-4o-mini", 0.2, tracker);
        llm.setCustomChatModel(messages -> {
            String stage = ((SystemMessage) messages.get(0)).text();
            if (stage.contains(failedStage)) throw new RuntimeException("Simulated provider outage");
            String output;
            if (stage.contains("PLANNER")) {
                output = "{\"subQuestions\":[\"Question\"],\"searchQueries\":[\"Question\"],\"rationale\":\"Test\"}";
            } else if (stage.contains("READER")) {
                output = "{\"claims\":[{\"statement\":\"Test evidence\",\"exactQuote\":\"Test evidence\"}]}";
            } else if (stage.contains("VERIFIER")) {
                output = "{\"clusters\":[]}";
            } else {
                output = "## Executive Summary\n- Test evidence [1]\n";
            }
            return Response.from(AiMessage.from(output));
        });
        ObjectMapper mapper = new ObjectMapper();
        SearchService search = (query, count) -> List.of(SearchResult.builder()
                .url("https://example.org/test").domain("example.org").title("Test source").build());
        SafeWebReader web = new SafeWebReader() {
            @Override public ExtractedPage readUrl(String url) {
                return new ExtractedPage(url, "Test source", "Test evidence", true, null);
            }
        };
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(
                new PlannerAgent(llm, mapper), new SearcherAgent(search), new ReaderAgent(web, llm, mapper),
                new VerifierAgent(llm, mapper), new WriterAgent(llm), new ReflectionCriticAgent(llm, mapper, 1),
                new CitationValidator(), tracker, reportsDir.toString());
        List<RunStatus> events = new ArrayList<>();
        Run run = orchestrator.executeRun("Test query", ResearchDepth.QUICK, 1500,
                (id, status, agent, message, tokensIn, tokensOut, cost) -> events.add(status));

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(run.getFailureReason()).contains("LLM request failed", "Research stopped");
        assertThat(run.getReport()).isNull();
        assertThat(events).contains(RunStatus.FAILED).doesNotContain(RunStatus.COMPLETED);
        assertThat(reportsDir.resolve("report-" + run.getId() + ".md")).doesNotExist();
    }
}
