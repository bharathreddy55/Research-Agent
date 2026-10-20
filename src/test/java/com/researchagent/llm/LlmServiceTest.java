package com.researchagent.llm;

import com.researchagent.tools.CostTracker;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmServiceTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "your_openai_api_key_here"})
    void rejectsMissingOpenAiKeyEvenWhenGeminiKeyExists(String key) {
        assertThatThrownBy(() -> service("openai", "test-gemini-key", key))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "your_gemini_api_key_here"})
    void rejectsMissingGeminiKeyEvenWhenOpenAiKeyExists(String key) {
        assertThatThrownBy(() -> service("gemini", key, "test-openai-key"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"typo", "demo"})
    void rejectsUnknownProvidersInsteadOfChoosingGemini(String provider) {
        assertThatThrownBy(() -> service(provider, "test-gemini-key", "test-openai-key"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Unsupported LLM provider");
    }

    @Test
    void rejectsAnEmptyModelName() {
        assertThatThrownBy(() -> new LlmService("openai", "", "", "test-key", " ", 0.2, new CostTracker()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("nonempty model name");
    }

    @Test
    void explicitMockIgnoresRealKeysAndReturnsAnHonestUnchargedDemo() {
        LlmService service = service("mock", "test-gemini-key", "test-openai-key");
        LlmService.LlmCallResult result = service.generate("WRITER", "Investigate a real topic");
        assertThat(service.getActiveModelName()).isEqualTo("mock");
        assertThat(result.getContent()).contains("DEMO MODE", "SYNTHETIC OUTPUT", "No real model was called");
        assertThat(result.getTokensIn()).isZero();
        assertThat(result.getTokensOut()).isZero();
        assertThat(result.getCostPaise()).isZero();
        assertThat(service.generate("READER", "Source text").getContent()).isEqualTo("{\"claims\": []}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP 401 invalid key", "HTTP 429 rate limit", "request timed out", "provider offline"})
    void providerFailureIsPropagatedWithoutSyntheticContent(String reason) {
        LlmService service = service("openai", "", "test-openai-key");
        RuntimeException providerFailure = new RuntimeException(reason);
        service.setCustomChatModel(messages -> { throw providerFailure; });
        assertThatThrownBy(() -> service.generate("WRITER", "Query"))
                .isInstanceOfSatisfying(LlmException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo(LlmException.Code.GENERATION);
                    assertThat(failure.getProvider()).isEqualTo("openai");
                    assertThat(failure.getCause()).isSameAs(providerFailure);
                    assertThat(failure.getMessage()).contains("Research stopped").doesNotContain(reason);
                });
    }

    @Test
    void missingResponseFailsInsteadOfProducingMockOutput() {
        LlmService service = service("openai", "", "test-openai-key");
        service.setCustomChatModel(messages -> null);
        assertThatThrownBy(() -> service.generate("READER", "Query"))
                .isInstanceOfSatisfying(LlmException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo(LlmException.Code.INVALID_RESPONSE));
    }

    @Test
    void successfulProviderContentAndUsageArePreserved() {
        LlmService service = service("  OPENAI  ", "", "test-openai-key");
        service.setCustomChatModel(messages -> Response.from(AiMessage.from("Actual provider answer"), new TokenUsage(20, 10)));
        LlmService.LlmCallResult result = service.generate("WRITER", "Query");
        assertThat(result.getContent()).isEqualTo("Actual provider answer");
        assertThat(result.getTokensIn()).isEqualTo(20);
        assertThat(result.getTokensOut()).isEqualTo(10);
        assertThat(result.getCostPaise()).isEqualTo(CostTracker.calculateCostPaise(20, 10, "gpt-4o-mini"));
    }

    @Test
    void customModelCannotBeResetToNull() {
        assertThatThrownBy(() -> service("mock", "", "").setCustomChatModel(null))
                .isInstanceOf(NullPointerException.class);
    }

    private LlmService service(String provider, String geminiKey, String openAiKey) {
        return new LlmService(provider, geminiKey, "gemini-1.5-flash", openAiKey, "gpt-4o-mini", 0.2, new CostTracker());
    }
}
