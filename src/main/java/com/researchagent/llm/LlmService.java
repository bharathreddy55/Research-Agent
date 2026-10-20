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
import java.util.Locale;
import java.util.Objects;

@Service
public class LlmService {
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final String provider;
    private final String geminiApiKey;
    private final String geminiModel;
    private final String openAiApiKey;
    private final String openAiModel;
    private final double temperature;

    private ChatLanguageModel chatModel;

    public LlmService(
            @Value("${research-agent.llm.provider:gemini}") String provider,
            @Value("${research-agent.llm.gemini.api-key:}") String geminiApiKey,
            @Value("${research-agent.llm.gemini.model-name:gemini-1.5-flash}") String geminiModel,
            @Value("${research-agent.llm.openai.api-key:}") String openAiApiKey,
            @Value("${research-agent.llm.openai.model-name:gpt-4o-mini}") String openAiModel,
            @Value("${research-agent.llm.gemini.temperature:0.2}") double temperature,
            CostTracker costTracker) {
        this.provider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.temperature = temperature;
        initModel();
    }

    private void initModel() {
        if ("mock".equals(provider)) {
            log.warn("Explicit mock provider selected: synthetic demo output only; no model API calls will be made.");
            return;
        }
        if (!"openai".equals(provider) && !"gemini".equals(provider)) {
            throw new LlmException(LlmException.Code.CONFIGURATION, provider,
                    "Unsupported LLM provider. Set research-agent.llm.provider to gemini, openai, or mock (demo only).");
        }
        String apiKey = "openai".equals(provider) ? openAiApiKey : geminiApiKey;
        String keyVariable = "openai".equals(provider) ? "OPENAI_API_KEY" : "GEMINI_API_KEY";
        String modelName = getActiveModelName();
        if (apiKey == null || apiKey.isBlank() || apiKey.toLowerCase(Locale.ROOT).contains("your_")) {
            throw new LlmException(LlmException.Code.CONFIGURATION, provider,
                    "LLM provider " + provider + " requires a valid " + keyVariable
                            + ". Use provider=mock explicitly for demo output.");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new LlmException(LlmException.Code.CONFIGURATION, provider,
                    "LLM provider " + provider + " requires a nonempty model name.");
        }
        try {
            if ("openai".equals(provider)) {
                log.info("Initializing OpenAI Chat Model with model: {}", openAiModel);
                this.chatModel = OpenAiChatModel.builder()
                        .apiKey(openAiApiKey)
                        .modelName(openAiModel)
                        .temperature(temperature)
                        .build();
            } else {
                log.info("Initializing Google Gemini Chat Model with model: {}", geminiModel);
                this.chatModel = GoogleAiGeminiChatModel.builder()
                        .apiKey(geminiApiKey)
                        .modelName(geminiModel)
                        .temperature(temperature)
                        .build();
            }
        } catch (Exception e) {
            throw new LlmException(LlmException.Code.INITIALIZATION, provider,
                    "Could not initialize LLM provider " + provider + ". Check its configuration.", e);
        }
    }

    public void setCustomChatModel(ChatLanguageModel customModel) {
        this.chatModel = Objects.requireNonNull(customModel, "customModel must not be null");
    }

    public String getActiveModelName() {
        if ("mock".equals(provider)) return "mock";
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
            if ("mock".equals(provider)) {
                return LlmCallResult.builder()
                        .content(DemoLlmResponses.generate(systemPrompt))
                        .tokensIn(0).tokensOut(0).costPaise(0).build();
            }
            throw new LlmException(LlmException.Code.INITIALIZATION, provider,
                    "LLM provider " + provider + " is unavailable.");
        }

        try {
            Response<AiMessage> response = chatModel.generate(messages);
            if (response == null || response.content() == null || response.content().text() == null
                    || response.content().text().isBlank()) {
                throw new LlmException(LlmException.Code.INVALID_RESPONSE, provider,
                        "LLM provider " + provider + " returned an empty response.");
            }
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
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(LlmException.Code.GENERATION, provider,
                    "LLM request failed for provider " + provider + ". Research stopped; no synthetic fallback was used.", e);
        }
    }

}
