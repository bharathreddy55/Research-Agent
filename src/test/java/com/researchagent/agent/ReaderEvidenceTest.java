package com.researchagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.llm.LlmService;
import com.researchagent.model.*;
import com.researchagent.tools.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class ReaderEvidenceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ReaderAgent.ReaderResult extract(String text, String output, AtomicInteger calls) {
        LlmService llm = new LlmService("openai", "", "", "test-key", "gpt-4o-mini", 0.2, new CostTracker());
        llm.setCustomChatModel(messages -> {
            calls.incrementAndGet();
            return Response.from(AiMessage.from(output));
        });
        SafeWebReader web = new SafeWebReader() {
            @Override public ExtractedPage readUrl(String url) { return new ExtractedPage(url, "Source", text, true, null); }
        };
        Source source = Source.builder().id(1).runId("run").url("https://example.org/source").domain("example.org").build();
        return new ReaderAgent(web, llm, mapper).readAndExtractClaims("run", 1, List.of(source), "Question", null);
    }

    @Test void acceptsOnlyMatchedQuotesAndRecordsRejectionReasons() throws Exception {
        var result = extract("Intro. Rates\n fell by 20 percent. End.", """
                {"claims":[
                  {"statement":"Rates fell","exactQuote":"Rates fell by 20 percent."},
                  {"statement":"Made up","exactQuote":"Imaginary evidence"},
                  {"statement":"Changed number","exactQuote":"Rates fell by 50 percent."},
                  {"statement":"No quote","exactQuote":""},
                  {"statement":"Missing quote"},
                  {"statement":" ","exactQuote":"Intro."}
                ]}
                """, new AtomicInteger());
        assertThat(result.getClaims()).hasSize(1);
        Claim claim = result.getClaims().getFirst();
        Source source = result.getSources().getFirst();
        var evidence = claim.getQuoteEvidence();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNVERIFIED);
        assertThat(claim.getExactQuote()).isEqualTo("Rates\n fell by 20 percent.");
        assertThat(evidence.submittedQuote()).isEqualTo("Rates fell by 20 percent.");
        assertThat(evidence.sourceSnapshotId()).isEqualTo(source.getSnapshotId()).isNotBlank();
        assertThat(evidence.sourceContentHash()).isEqualTo(source.getContentHash()).hasSize(64);
        assertThat(evidence.sourceUrl()).isEqualTo(source.getUrl());
        assertThat(source.getContent().substring(evidence.start(), evidence.end())).isEqualTo(claim.getExactQuote());
        var trace = mapper.readTree(result.getTraces().getFirst().getResponse());
        assertThat(trace.path("accepted").asInt()).isEqualTo(1);
        assertThat(trace.path("rejected").asInt()).isEqualTo(5);
        assertThat(trace.toString()).contains("QUOTE_NOT_FOUND", "EMPTY_QUOTE", "INVALID_FIELDS", "EMPTY_STATEMENT");
    }

    @ParameterizedTest @ValueSource(strings = {"broken json", "null", "{}", "{\"claims\":{}}", "{\"claims\":[null,7,{\"statement\":7,\"exactQuote\":\"text\"}]}"})
    void malformedOutputCannotCreateClaims(String output) throws Exception {
        var result = extract("text", output, new AtomicInteger());
        assertThat(result.getClaims()).isEmpty();
        assertThat(mapper.readTree(result.getTraces().getFirst().getResponse()).path("rejected").asInt()).isPositive();
    }

    @Test void boundsRejectedSamplesWhileCountingEveryRejectedAttempt() throws Exception {
        String items = String.join(",", java.util.Collections.nCopies(30, "{\"statement\":\"Claim\",\"exactQuote\":\"Invented\"}"));
        var result = extract("Actual text", "{\"claims\":[" + items + "]}", new AtomicInteger());
        var trace = mapper.readTree(result.getTraces().getFirst().getResponse());
        assertThat(trace.path("rejected").asInt()).isEqualTo(30);
        assertThat(trace.path("rejectedExamples").size()).isEqualTo(5);
        assertThat(trace.path("omittedRejectedExamples").asInt()).isEqualTo(25);
    }

    @Test void emptyPagesDoNotInvokeModel() {
        AtomicInteger calls = new AtomicInteger();
        var result = extract(" \n\t", "{}", calls);
        assertThat(calls.get()).isZero();
        assertThat(result.getClaims()).isEmpty();
        assertThat(result.getSources().getFirst().isFetchedOk()).isFalse();
    }
}
