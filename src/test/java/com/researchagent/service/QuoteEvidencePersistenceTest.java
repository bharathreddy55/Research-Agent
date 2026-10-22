package com.researchagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchagent.agent.ReaderAgent;
import com.researchagent.entity.SourceEntity;
import com.researchagent.evidence.EvidenceValidator;
import com.researchagent.llm.LlmService;
import com.researchagent.model.*;
import com.researchagent.repository.*;
import com.researchagent.tools.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@Import(RunPersistenceService.class)
class QuoteEvidencePersistenceTest {
    @Autowired RunPersistenceService persistence;
    @Autowired SourceRepository sources;
    @Autowired ClaimRepository claims;
    @Autowired EntityManager entityManager;

    private ReaderAgent.ReaderResult extract(String text, String quote) throws Exception {
        String output = new ObjectMapper().writeValueAsString(Map.of("claims", List.of(Map.of("statement", "Claim", "exactQuote", quote))));
        LlmService llm = new LlmService("openai", "", "", "test-key", "gpt-4o-mini", 0.2, new CostTracker());
        llm.setCustomChatModel(messages -> Response.from(AiMessage.from(output)));
        SafeWebReader web = new SafeWebReader() {
            @Override public ExtractedPage readUrl(String url) { return new ExtractedPage(url, "Page", text, true, null); }
        };
        Source source = Source.builder().id(1).runId("evidence-run").url("https://example.org/page").domain("example.org").build();
        var result = new ReaderAgent(web, llm, new ObjectMapper()).readAndExtractClaims("evidence-run", 1, List.of(source), "Question", null);
        persistence.saveSources("evidence-run", result.getSources());
        persistence.saveClaims("evidence-run", result.getClaims());
        return result;
    }

    @Test void savedLongQuoteResolvesToOriginalSnapshotAfterPageChanges() throws Exception {
        String quote = "Evidence ".repeat(150) + "complete.";
        String original = "\uD83D\uDD0E Before. " + quote.replace("Evidence ", "Evidence\n ") + " After.";
        var first = extract(original, quote);
        String claimId = first.getClaims().getFirst().getId();
        String snapshotId = first.getSources().getFirst().getSnapshotId();
        // Same URL/citation number can later contain different text without rewriting the original.
        extract("Changed source content.", "Changed source content.");
        entityManager.flush();
        entityManager.clear();
        var savedClaim = claims.findById(claimId).orElseThrow();
        SourceEntity savedSource = sources.findByRunIdOrderBySourceNumberAsc("evidence-run").stream()
                .filter(s -> snapshotId.equals(s.getSnapshotId())).findFirst().orElseThrow();
        assertThat(savedClaim.getExactQuote()).hasSizeGreaterThan(1000);
        assertThat(savedClaim.getSubmittedQuote()).isEqualTo(quote);
        assertThat(savedClaim.getQuoteMatchStatus()).isEqualTo("WHITESPACE_NORMALIZED");
        assertThat(savedClaim.getQuoteNormalizationVersion()).isEqualTo(EvidenceValidator.VERSION);
        assertThat(savedClaim.getEvidenceContentHash()).isEqualTo(savedSource.getContentHash());
        assertThat(savedClaim.getSourceUrl()).isEqualTo(savedSource.getUrl());
        assertThat(savedSource.getContent()).isEqualTo(original);
        assertThat(savedSource.getContent().substring(savedClaim.getQuoteStart(), savedClaim.getQuoteEnd())).isEqualTo(savedClaim.getExactQuote());
        assertThat(new EvidenceValidator().prepare(savedSource.getContent()).contentHash()).isEqualTo(savedClaim.getEvidenceContentHash());
    }

    @Test void persistedSourceIdentityAndTextCannotBeOverwrittenByOrmUpdate() throws Exception {
        var result = extract("Original text.", "Original text.");
        entityManager.flush();
        entityManager.clear();
        SourceEntity saved = sources.findByRunIdOrderBySourceNumberAsc("evidence-run").getFirst();
        Long id = saved.getId();
        saved.setContent("Replacement");
        saved.setContentHash("wrong");
        saved.setUrl("https://example.org/replaced");
        saved.setSnapshotId("replaced");
        entityManager.flush();
        entityManager.clear();
        SourceEntity reloaded = sources.findById(id).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("Original text.");
        assertThat(reloaded.getContentHash()).isEqualTo(result.getSources().getFirst().getContentHash());
        assertThat(reloaded.getUrl()).isEqualTo("https://example.org/page");
        assertThat(reloaded.getSnapshotId()).isEqualTo(result.getSources().getFirst().getSnapshotId());
    }

    @Test void historicalClaimsRemainReadableWithoutInventedValidationMetadata() {
        Claim legacy = Claim.builder().id("legacy").runId("evidence-run").statement("Old claim")
                .exactQuote("Old quote").status(ClaimStatus.UNVERIFIED).build();
        persistence.saveClaims("evidence-run", List.of(legacy));
        entityManager.flush();
        entityManager.clear();
        var saved = claims.findById("legacy").orElseThrow();
        assertThat(saved.getQuoteMatchStatus()).isNull();
        assertThat(saved.getEvidenceSnapshotId()).isNull();
        assertThat(saved.getExactQuote()).isEqualTo("Old quote");
    }
}
