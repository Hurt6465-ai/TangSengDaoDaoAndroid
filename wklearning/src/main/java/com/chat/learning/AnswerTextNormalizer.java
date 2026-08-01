package com.chat.learning;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Language-aware answer normalization shared by all lesson question types. */
final class AnswerTextNormalizer {
    private AnswerTextNormalizer() { }

    /**
     * Creates a compact key for fixed option values and duplicate detection.
     * Whitespace and punctuation are deliberately ignored here because the value is selected,
     * not typed by the learner.
     */
    static String normalizeKey(String value) {
        String normalized = canonical(value);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || isPunctuation(codePoint)) continue;
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    /**
     * Normalizes typed answers without collapsing word boundaries in space-delimited languages.
     * Chinese answers remain tolerant of optional spaces, while English and Myanmar answers keep
     * meaningful spaces so strings such as "a nice cream" and "an ice cream" do not collide.
     */
    static String normalizeAnswer(String value) {
        String normalized = canonical(value);
        boolean containsHan = false;
        boolean containsLatin = false;
        StringBuilder result = new StringBuilder(normalized.length());
        boolean pendingSpace = false;

        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            containsHan |= isHan(codePoint);
            containsLatin |= isLatin(codePoint);

            if (Character.isWhitespace(codePoint)) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (isPunctuation(codePoint)) {
                if (!isApostrophe(codePoint)) pendingSpace = result.length() > 0;
                continue;
            }
            if (pendingSpace) result.append(' ');
            result.appendCodePoint(codePoint);
            pendingSpace = false;
        }

        String answer = result.toString().trim().replaceAll("\\s+", " ");
        if (containsHan && !containsLatin) return answer.replace(" ", "");
        return answer;
    }

    static boolean sameAnswer(String left, String right) {
        return normalizeAnswer(left).equals(normalizeAnswer(right));
    }

    static boolean sameTokenSequence(List<String> actual, List<String> expected) {
        if (actual == null || expected == null || actual.size() != expected.size()) return false;
        for (int i = 0; i < actual.size(); i++) {
            if (!sameAnswer(actual.get(i), expected.get(i))) return false;
        }
        return true;
    }

    private static String canonical(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isHan(int codePoint) {
        return (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x20000 && codePoint <= 0x2FA1F);
    }

    private static boolean isLatin(int codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= 0x00C0 && codePoint <= 0x024F)
                || (codePoint >= 0x1E00 && codePoint <= 0x1EFF);
    }

    private static boolean isApostrophe(int codePoint) {
        return codePoint == '\'' || codePoint == 0x2019 || codePoint == 0x02BC
                || codePoint == 0xFF07;
    }

    private static boolean isPunctuation(int codePoint) {
        switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
                return true;
            default:
                return false;
        }
    }
}
