package com.researchagent.execution;

import com.researchagent.llm.LlmService;
import com.researchagent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class RunControlTest {
    @Test
    void providerBuildersDisableHiddenRetriesThatCouldOutliveCancellation() {
        for (String provider : new String[]{"openai", "gemini"}) {
            LlmService llm = new LlmService(provider, "test-key", "gemini-test", "test-key", "gpt-test", 0.2, new CostTracker());
            Object model = org.springframework.test.util.ReflectionTestUtils.getField(llm, "chatModel");
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(model, "maxRetries")).isEqualTo(1);
        }
    }

    @Test
    void nestedScopesCombineSignalsAndCleanUpOnWorkerThreadReuse() {
        AtomicBoolean cancelled = new AtomicBoolean();
        try (var outer = RunControl.open(cancelled::get)) {
            try (var inner = RunControl.open(() -> false)) {
                RunControl.checkActive(); cancelled.set(true);
                assertThatThrownBy(RunControl::checkActive).isInstanceOf(RunCancelledException.class);
            }
            assertThatThrownBy(RunControl::checkActive).isInstanceOf(RunCancelledException.class);
        }
        assertThatCode(RunControl::checkActive).doesNotThrowAnyException();
    }

    @Test
    void cancelledScopeStopsModelWebAndSearchBoundariesBeforeAnyIo() {
        LlmService llm = new LlmService("mock", "", "", "", "", 0.2, new CostTracker());
        try (var scope = RunControl.open(() -> true)) {
            assertThatThrownBy(() -> llm.generate("READER", "text")).isInstanceOf(RunCancelledException.class);
            assertThatThrownBy(() -> new SafeWebReader().readUrl("http://127.0.0.1/never-connect"))
                    .isInstanceOf(RunCancelledException.class);
            assertThatThrownBy(() -> new FallbackSearchService().search("never-search", 1))
                    .isInstanceOf(RunCancelledException.class);
        }
        assertThatCode(() -> llm.generate("READER", "text")).doesNotThrowAnyException();
    }
}
