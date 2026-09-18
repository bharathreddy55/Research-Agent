package com.researchagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.llm.LlmService;
import com.researchagent.model.AgentType;
import com.researchagent.model.ResearchDepth;
import com.researchagent.model.Run;
import com.researchagent.model.RunStatus;
import com.researchagent.tools.CostTracker;
import com.researchagent.tools.SafeWebReader;
import com.researchagent.tools.SearchResult;
import com.researchagent.tools.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchOrchestratorTest {

    @Test
    @DisplayName("Should execute complete multi-agent pipeline from query to cited markdown report")
    void shouldExecuteCompleteMultiAgentPipeline() {
        LlmService llmService = new LlmService("gemini", "", "gemini-1.5-flash", "", "", 0.2, new CostTracker());
        ObjectMapper objectMapper = new ObjectMapper();
        CostTracker costTracker = new CostTracker();

        SearchService testSearchService = (query, maxResults) -> List.of(
                SearchResult.builder().title("Nature Energy Review").url("https://nature.com/article1").snippet("Solid state battery data").domain("nature.com").score(0.95).build(),
                SearchResult.builder().title("Science Battery Study").url("https://science.org/article2").snippet("Electrolyte stability review").domain("science.org").score(0.92).build()
        );

        SafeWebReader testWebReader = new SafeWebReader() {
            @Override
            public ExtractedPage readUrl(String targetUrl) {
                if (targetUrl.contains("nature.com")) {
                    return new ExtractedPage(targetUrl, "Nature Energy Review", "Solid state electrolytes demonstrated 1000 cycle stability under ambient conditions.", true, null);
                }
                return new ExtractedPage(targetUrl, "Science Battery Study", "Prototypes showed 1000 cycle stability across laboratory trials.", true, null);
            }
        };

        PlannerAgent planner = new PlannerAgent(llmService, objectMapper);
        SearcherAgent searcher = new SearcherAgent(testSearchService);
        ReaderAgent reader = new ReaderAgent(testWebReader, llmService, objectMapper);
        VerifierAgent verifier = new VerifierAgent(llmService, objectMapper);
        WriterAgent writer = new WriterAgent(llmService);
        ReflectionCriticAgent reflectionCritic = new ReflectionCriticAgent(llmService, objectMapper, 1);
        CitationValidator citationValidator = new CitationValidator();

        ResearchOrchestrator orchestrator = new ResearchOrchestrator(
                planner, searcher, reader, verifier, writer, reflectionCritic, citationValidator, costTracker, "./reports"
        );

        Run run = orchestrator.executeRun(
                "What is the commercial state of solid state batteries in 2026?",
                ResearchDepth.QUICK,
                1500,
                (runId, status, agent, message, tIn, tOut, cost) -> {
                    // event callback
                }
        );

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getReport()).isNotNull();
        assertThat(run.getReport().getMarkdownContent()).isNotBlank();
        assertThat(run.getSteps()).isNotEmpty();
        assertThat(run.getCostPaise()).isGreaterThanOrEqualTo(0);

        // Verify that steps include Planner, Searcher, Reader, Verifier, Writer, Reflection, and Citation Validator
        List<AgentType> executedAgents = run.getSteps().stream().map(s -> s.getAgent()).toList();
        assertThat(executedAgents).contains(
                AgentType.PLANNER,
                AgentType.SEARCHER,
                AgentType.READER,
                AgentType.VERIFIER,
                AgentType.WRITER,
                AgentType.REFLECTION_CRITIC,
                AgentType.CITATION_VALIDATOR
        );
    }
}
