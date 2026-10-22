package com.researchagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.execution.RunControl;
import com.researchagent.llm.LlmService;
import com.researchagent.model.*;
import com.researchagent.tools.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.assertThat;

class CancellationPipelineTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"PLANNER", "READER", "REFLECTION"})
    void cancellationDuringACallKeepsUsageAndStopsFurtherCallsAndPageReads(String cancelAt) {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger callsAtCancellation = new AtomicInteger();
        AtomicInteger readsAtCancellation = new AtomicInteger();
        CostTracker tracker = new CostTracker();
        LlmService llm = new LlmService("openai", "", "", "test-key", "gpt-4o-mini", 0.2, tracker);
        llm.setCustomChatModel(messages -> {
            modelCalls.incrementAndGet();
            String stage = ((SystemMessage) messages.get(0)).text();
            String output = stage.contains("PLANNER")
                    ? "{\"subQuestions\":[\"Question\"],\"searchQueries\":[\"Question\"],\"rationale\":\"Test\"}"
                    : stage.contains("READER") ? "{\"claims\":[]}"
                    : stage.contains("VERIFIER") ? "{\"clusters\":[]}"
                    : stage.contains("REFLECTION") ? "{\"needsRevision\":true,\"critique\":\"Revise\"}"
                    : "## Findings\nTest evidence [1]";
            if (stage.contains(cancelAt)) {
                cancelled.set(true); callsAtCancellation.set(modelCalls.get()); readsAtCancellation.set(reads.get());
            }
            // This in-flight request succeeds after cancellation, with billable usage.
            return Response.from(AiMessage.from(output), new TokenUsage(100, 50));
        });
        SearchService search = (query, count) -> List.of(
                SearchResult.builder().url("https://example.org/a").domain("example.org").title("A").build(),
                SearchResult.builder().url("https://example.net/b").domain("example.net").title("B").build());
        SafeWebReader web = new SafeWebReader() {
            @Override public ExtractedPage readUrl(String url) {
                reads.incrementAndGet();
                return new ExtractedPage(url, "Test source", "Test evidence", true, null);
            }
        };
        ObjectMapper mapper = new ObjectMapper();
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(new PlannerAgent(llm, mapper),
                new SearcherAgent(search), new ReaderAgent(web, llm, mapper), new VerifierAgent(llm, mapper),
                new WriterAgent(llm), new ReflectionCriticAgent(llm, mapper, 1), new CitationValidator(), tracker, directory.toString());
        List<RunStatus> events = new ArrayList<>();
        Run result;
        try (var scope = RunControl.open(cancelled::get)) {
            result = orchestrator.executeRun("cancel-test", "Question", ResearchDepth.QUICK, 1500,
                    (id, status, agent, message, in, out, cost) -> events.add(status));
        }
        assertThat(cancelled.get()).isTrue();
        assertThat(result.getStatus()).isEqualTo(RunStatus.CANCELLED);
        assertThat(result.getReport()).isNull();
        assertThat(result.getTokensIn()).isEqualTo(100 * modelCalls.get());
        assertThat(result.getTokensOut()).isEqualTo(50 * modelCalls.get());
        assertThat(result.getCostPaise()).isPositive();
        assertThat(modelCalls.get()).isEqualTo(callsAtCancellation.get());
        assertThat(reads.get()).isEqualTo(readsAtCancellation.get());
        assertThat(events).contains(RunStatus.CANCELLED).doesNotContain(RunStatus.COMPLETED, RunStatus.FAILED);
        assertThat(directory.resolve("report-cancel-test.md")).doesNotExist();
    }
}
