package com.researchagent.evidence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceValidatorTest {
    private final EvidenceValidator validator = new EvidenceValidator();

    @Test void literalMatchRecordsOriginalUtf16Span() {
        String text = "\uD83D\uDD0E Intro. Rates fell by 20.5 percent. End.";
        var match = validator.prepare(text).match("Rates fell by 20.5 percent.");
        assertThat(match.status()).isEqualTo(QuoteMatchStatus.EXACT);
        assertThat(match.start()).isEqualTo(text.indexOf("Rates"));
        assertThat(text.substring(match.start(), match.end())).isEqualTo(match.originalQuote());
    }

    @Test void whitespaceMatchingMapsBackToUnmodifiedSourceAndCanBeReused() {
        String text = "\uD83D\uDD0E Intro. Rates\r\n\t fell\u00a0by 20.5 percent. End.";
        var prepared = validator.prepare(text);
        for (int i = 0; i < 2; i++) {
            var match = prepared.match("  Rates fell by 20.5 percent.\n");
            assertThat(match.status()).isEqualTo(QuoteMatchStatus.WHITESPACE_NORMALIZED);
            assertThat(text.substring(match.start(), match.end())).isEqualTo("Rates\r\n\t fell\u00a0by 20.5 percent.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \n\t\u00a0", "Invented evidence", "Rates fell by 25.5 percent.",
            "Rates did not fall by 20.5 percent.", "rates fell by 20.5 percent.", "Rates fell by 20,5 percent.", "fell by 20", "5 percent.", "ate"})
    void rejectsFabricationChangedMeaningAndPartialTokens(String quote) {
        assertThat(validator.prepare("Rates fell by 20.5 percent.").match(quote).accepted()).isFalse();
    }

    @Test void skipsPartialWordOccurrencesAndChoosesFirstWholeMatch() {
        var match = validator.prepare("catfish cat cat").match("cat");
        assertThat(match.start()).isEqualTo(8);
        assertThat(match.end()).isEqualTo(11);
    }

    @Test void emptySourcesAndNullQuotesAreRejected() {
        assertThat(validator.prepare(null).match("text").rejectionReason()).isEqualTo("EMPTY_SOURCE");
        assertThat(validator.prepare("text").match(null).rejectionReason()).isEqualTo("EMPTY_QUOTE");
    }

    @Test void usesStableSha256Utf8Hash() {
        assertThat(validator.prepare("abc").contentHash()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(validator.prepare("\u00e9").contentHash()).isEqualTo("4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c");
    }
}
