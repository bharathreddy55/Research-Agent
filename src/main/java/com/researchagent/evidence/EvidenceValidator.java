package com.researchagent.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic matching against fetched text. Does not assess truth or entailment.
 * v1 folds whitespace only: case, punctuation, numbers, units and negation remain intact. */
public final class EvidenceValidator {
    public static final String VERSION = "literal-whitespace-v1";

    public PreparedSource prepare(String text) { return new PreparedSource(text); }

    public record Match(QuoteMatchStatus status, int start, int end, String originalQuote, String rejectionReason) {
        public boolean accepted() { return status != null; }
        private static Match rejected(String reason) { return new Match(null, -1, -1, null, reason); }
    }

    public static final class PreparedSource {
        private final String original;
        private final String contentHash;
        private NormalizedText normalized;

        private PreparedSource(String text) {
            original = text == null ? "" : text;
            try {
                contentHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(original.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is required for evidence snapshots", ex);
            }
        }

        public String contentHash() { return contentHash; }

        public Match match(String submittedQuote) {
            if (original.isEmpty()) return Match.rejected("EMPTY_SOURCE");
            String quote = trimWhitespace(submittedQuote == null ? "" : submittedQuote);
            if (quote.isEmpty()) return Match.rejected("EMPTY_QUOTE");
            int exact = findWholeSpan(original, quote);
            if (exact >= 0) return new Match(QuoteMatchStatus.EXACT, exact, exact + quote.length(),
                    original.substring(exact, exact + quote.length()), null);

            // Prepare the source mapping at most once, only when literal matching fails.
            if (normalized == null) normalized = normalize(original);
            String normalizedQuote = normalize(quote).text();
            int index = findWholeSpan(normalized.text(), normalizedQuote);
            if (index < 0) return Match.rejected("QUOTE_NOT_FOUND");
            int start = normalized.starts()[index];
            int end = normalized.ends()[index + normalizedQuote.length() - 1];
            return new Match(QuoteMatchStatus.WHITESPACE_NORMALIZED, start, end, original.substring(start, end), null);
        }
    }

    private record NormalizedText(String text, int[] starts, int[] ends) {}

    private static NormalizedText normalize(String original) {
        StringBuilder text = new StringBuilder(original.length());
        int[] starts = new int[original.length()];
        int[] ends = new int[original.length()];
        int output = 0;
        for (int i = 0; i < original.length();) {
            int start = i;
            if (isWhitespace(original.charAt(i))) {
                do { i++; } while (i < original.length() && isWhitespace(original.charAt(i)));
                text.append(' ');
            } else {
                text.append(original.charAt(i++));
            }
            starts[output] = start;
            ends[output++] = i;
        }
        return new NormalizedText(text.toString(), starts, ends);
    }

    private static String trimWhitespace(String value) {
        int start = 0, end = value.length();
        while (start < end && isWhitespace(value.charAt(start))) start++;
        while (end > start && isWhitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch);
    }

    private static int findWholeSpan(String source, String quote) {
        for (int index = source.indexOf(quote); index >= 0; index = source.indexOf(quote, index + 1)) {
            int end = index + quote.length();
            if (index > 0 && wordCharacter(quote.codePointAt(0)) && wordCharacter(source.codePointBefore(index))) continue;
            if (end < source.length() && wordCharacter(quote.codePointBefore(quote.length())) && wordCharacter(source.codePointAt(end))) continue;
            // Do not match a numeric prefix/suffix inside a decimal value.
            if (end + 1 < source.length() && Character.isDigit(quote.charAt(quote.length() - 1))
                    && (source.charAt(end) == '.' || source.charAt(end) == ',') && Character.isDigit(source.charAt(end + 1))) continue;
            if (index > 1 && Character.isDigit(quote.charAt(0)) && (source.charAt(index - 1) == '.' || source.charAt(index - 1) == ',')
                    && Character.isDigit(source.charAt(index - 2))) continue;
            return index;
        }
        return -1;
    }

    private static boolean wordCharacter(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_'
                || Character.getType(codePoint) == Character.NON_SPACING_MARK
                || Character.getType(codePoint) == Character.COMBINING_SPACING_MARK;
    }
}
