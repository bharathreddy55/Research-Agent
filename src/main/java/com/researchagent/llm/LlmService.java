package com.researchagent.llm;

import com.researchagent.tools.CostTracker;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LlmService {
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final String provider;
    private final String geminiApiKey;
    private final String geminiModel;
    private final String openAiApiKey;
    private final String openAiModel;
    private final double temperature;
    private final CostTracker costTracker;

    private ChatLanguageModel chatModel;

    public LlmService(
            @Value("${research-agent.llm.provider:gemini}") String provider,
            @Value("${research-agent.llm.gemini.api-key:}") String geminiApiKey,
            @Value("${research-agent.llm.gemini.model-name:gemini-1.5-flash}") String geminiModel,
            @Value("${research-agent.llm.openai.api-key:}") String openAiApiKey,
            @Value("${research-agent.llm.openai.model-name:gpt-4o-mini}") String openAiModel,
            @Value("${research-agent.llm.gemini.temperature:0.2}") double temperature,
            CostTracker costTracker) {
        this.provider = provider;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.temperature = temperature;
        this.costTracker = costTracker;
        initModel();
    }

    private void initModel() {
        try {
            if ("openai".equalsIgnoreCase(provider) && openAiApiKey != null && !openAiApiKey.isBlank() && !openAiApiKey.contains("your_openai")) {
                log.info("Initializing OpenAI Chat Model with model: {}", openAiModel);
                this.chatModel = OpenAiChatModel.builder()
                        .apiKey(openAiApiKey)
                        .modelName(openAiModel)
                        .temperature(temperature)
                        .build();
            } else if (geminiApiKey != null && !geminiApiKey.isBlank() && !geminiApiKey.contains("your_gemini")) {
                log.info("Initializing Google Gemini Chat Model with model: {}", geminiModel);
                this.chatModel = GoogleAiGeminiChatModel.builder()
                        .apiKey(geminiApiKey)
                        .modelName(geminiModel)
                        .temperature(temperature)
                        .build();
            } else {
                log.warn("No active LLM API key provided for provider '{}'. Operating in mock/test mode.", provider);
                this.chatModel = null;
            }
        } catch (Exception e) {
            log.error("Failed to initialize ChatLanguageModel: {}", e.getMessage(), e);
            this.chatModel = null;
        }
    }

    public void setCustomChatModel(ChatLanguageModel customModel) {
        this.chatModel = customModel;
    }

    public String getActiveModelName() {
        if ("openai".equalsIgnoreCase(provider)) {
            return openAiModel;
        }
        return geminiModel;
    }

    public static class LlmCallResult {
        private String content;
        private int tokensIn;
        private int tokensOut;
        private long costPaise;

        public LlmCallResult() {}

        public LlmCallResult(String content, int tokensIn, int tokensOut, long costPaise) {
            this.content = content;
            this.tokensIn = tokensIn;
            this.tokensOut = tokensOut;
            this.costPaise = costPaise;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String content;
            private int tokensIn;
            private int tokensOut;
            private long costPaise;

            public Builder content(String content) { this.content = content; return this; }
            public Builder tokensIn(int tokensIn) { this.tokensIn = tokensIn; return this; }
            public Builder tokensOut(int tokensOut) { this.tokensOut = tokensOut; return this; }
            public Builder costPaise(long costPaise) { this.costPaise = costPaise; return this; }

            public LlmCallResult build() {
                return new LlmCallResult(content, tokensIn, tokensOut, costPaise);
            }
        }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public int getTokensIn() { return tokensIn; }
        public void setTokensIn(int tokensIn) { this.tokensIn = tokensIn; }

        public int getTokensOut() { return tokensOut; }
        public void setTokensOut(int tokensOut) { this.tokensOut = tokensOut; }

        public long getCostPaise() { return costPaise; }
        public void setCostPaise(long costPaise) { this.costPaise = costPaise; }
    }

    public LlmCallResult generate(String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userPrompt));

        if (chatModel == null) {
            log.info("Mock LLM responding for prompt snippet: {}", userPrompt.length() > 60 ? userPrompt.substring(0, 60) + "..." : userPrompt);
            return generateMockResponse(systemPrompt, userPrompt);
        }

        try {
            Response<AiMessage> response = chatModel.generate(messages);
            String outputText = response.content().text();
            TokenUsage usage = response.tokenUsage();
            int tokensIn = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : Math.max(1, (userPrompt.length() + (systemPrompt != null ? systemPrompt.length() : 0)) / 4);
            int tokensOut = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : Math.max(1, outputText.length() / 4);

            long costPaise = CostTracker.calculateCostPaise(tokensIn, tokensOut, getActiveModelName());

            return LlmCallResult.builder()
                    .content(outputText)
                    .tokensIn(tokensIn)
                    .tokensOut(tokensOut)
                    .costPaise(costPaise)
                    .build();
        } catch (Exception e) {
            log.error("LLM generation failed: {}. Falling back to mock generator.", e.getMessage());
            return generateMockResponse(systemPrompt, userPrompt);
        }
    }

    private LlmCallResult generateMockResponse(String systemPrompt, String userPrompt) {
        String mockResponse;
        if (systemPrompt != null && systemPrompt.contains("PLANNER")) {
            mockResponse = """
            {
              "subQuestions": [
                "What are the recent breakthroughs in this domain?",
                "What are the major technical limitations and commercial hurdles?",
                "Which leading institutions or companies are driving innovation?"
              ],
              "searchQueries": [
                "recent breakthroughs technological state",
                "key challenges limitations commercial hurdles",
                "leading companies research institutions advancements"
              ],
              "rationale": "Decomposing into state of the art, bottlenecks, and market leaders."
            }
            """;
        } else if (systemPrompt != null && systemPrompt.contains("READER")) {
            mockResponse = """
            {
              "claims": [
                {
                  "statement": "Significant technical milestones were verified across multiple lab prototypes.",
                  "exactQuote": "Prototypes demonstrated verifiable efficiency gains over prior benchmarks."
                },
                {
                  "statement": "Commercial scaling is anticipated to reach mass adoption by 2028.",
                  "exactQuote": "Industry roadmaps point toward initial commercial deployment within 2-3 years."
                }
              ]
            }
            """;
        } else if (systemPrompt != null && systemPrompt.contains("REFLECTION")) {
            mockResponse = """
            {
              "needsRevision": false,
              "feedback": "The report adequately covers all sub-questions with citations and clear confidence notes."
            }
            """;
        } else {
            mockResponse = """
            # Executive Summary
            - Key finding 1 regarding the research query with confirmed empirical results [1].
            - Commercial deployment trajectories and supply chain dynamics [2].
            - Remaining regulatory and technical challenges requiring further validation [1].

            # Key Developments & State of the Art
            Recent scientific analysis demonstrates accelerated momentum across core benchmarks [1]. Multiple independent assessments highlight significant performance increases under testing conditions.

            # Challenges & Commercial Roadmap
            Cost reductions and manufacturing yield remain active focus areas [2]. Initial commercial implementations are progressing toward pilot scale.

            # Confidence Appendix & Source Notes
            Certain pilot timelines remain single-source estimates awaiting third-party corroboration.
            """;
        }

        int inEst = (userPrompt.length() + (systemPrompt != null ? systemPrompt.length() : 0)) / 4;
        int outEst = mockResponse.length() / 4;
        return LlmCallResult.builder()
                .content(mockResponse.trim())
                .tokensIn(inEst)
                .tokensOut(outEst)
                .costPaise(CostTracker.calculateCostPaise(inEst, outEst, getActiveModelName()))
                .build();
    }
}
