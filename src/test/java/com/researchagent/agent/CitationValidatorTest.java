package com.researchagent.agent;

import com.researchagent.model.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationValidatorTest {

    private final CitationValidator validator = new CitationValidator();

    @Test
    @DisplayName("Should strip hallucinated citation numbers not present in sources table")
    void shouldStripHallucinatedCitations() {
        List<Source> sources = List.of(
                Source.builder().id(1).url("https://nature.com/article1").domain("nature.com").fetchedOk(true).build(),
                Source.builder().id(2).url("https://science.org/article2").domain("science.org").fetchedOk(true).build()
        );

        String reportWithHallucination = """
        # Summary
        Solid state electrolyte conductivity reached 10 mS/cm [1].
        Commercial manufacturing will reach volume production by 2028 [99].
        Cathode degradation was mitigated through atomic coating [2].
        """;

        CitationValidator.CitationValidationResult result = validator.validateAndSanitize(
                "run-123", 1, reportWithHallucination, sources);

        assertThat(result.getHallucinatedCitationIds()).containsExactly(99);
        assertThat(result.getValidCitationIds()).containsExactlyInAnyOrder(1, 2);
        // Hallucinated citation [99] stripped from output text
        assertThat(result.getSanitizedMarkdown()).doesNotContain("[99]");
        assertThat(result.getSanitizedMarkdown()).contains("[1]");
        assertThat(result.getSanitizedMarkdown()).contains("[2]");
    }

    @Test
    @DisplayName("Should flag dead-URL citations where page fetch failed")
    void shouldFlagDeadUrlCitations() {
        List<Source> sources = List.of(
                Source.builder().id(1).url("https://nature.com/ok").domain("nature.com").fetchedOk(true).build(),
                Source.builder().id(2).url("https://deadsite.org/down").domain("deadsite.org").fetchedOk(false).fetchError("HTTP 404").build()
        );

        String draft = "Electrochemical stability was achieved [1]. Energy density reached 500 Wh/kg [2].";

        CitationValidator.CitationValidationResult result = validator.validateAndSanitize(
                "run-456", 1, draft, sources);

        assertThat(result.getDeadUrlCitationIds()).containsExactly(2);
        assertThat(result.getValidCitationIds()).containsExactly(1);
        assertThat(result.getSanitizedMarkdown()).contains("[2†dead-source]");
    }

    @Test
    @DisplayName("Should leave valid citations completely untouched")
    void shouldPassValidCitationsUntouched() {
        List<Source> sources = List.of(
                Source.builder().id(1).url("https://nature.com/ok").domain("nature.com").fetchedOk(true).build(),
                Source.builder().id(2).url("https://bbc.com/ok").domain("bbc.com").fetchedOk(true).build()
        );

        String draft = "Claim one [1] and claim two [2].";
        CitationValidator.CitationValidationResult result = validator.validateAndSanitize(
                "run-789", 1, draft, sources);

        assertThat(result.getSanitizedMarkdown().trim()).isEqualTo("Claim one [1] and claim two [2].");
        assertThat(result.hasAnomalies()).isFalse();
    }
}
