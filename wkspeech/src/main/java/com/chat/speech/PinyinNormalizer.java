package com.chat.speech;

import java.text.Normalizer;
import java.util.Locale;

/** Utilities for passing teacher-provided Mandarin pinyin to the offline frontend. */
public final class PinyinNormalizer {
    private PinyinNormalizer() {}

    /**
     * Normalizes real tone-marked Hanyu Pinyin without splitting initials and finals.
     *
     * MultiTTS forwards plain text to the selected engine after normalizing its Unicode form.
     * The imported ByteDance Chinese frontend can recognize complete pinyin syllables such as
     * "bà" and "nǐ hǎo" directly. Splitting "nǐ" into "n ǐ" is incorrect because the bare
     * Latin initial may be routed to the English frontend.
     */
    public static String normalizeNativePinyin(String input) {
        if (input == null) return "";

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("u:", "ü")
                .replace('v', 'ü')
                .replace('’', '\'')
                .replace('‘', '\'');

        // Preserve one space between complete pinyin syllables and normalize once more after edits.
        normalized = normalized.replaceAll("\\s+", " ");
        return Normalizer.normalize(normalized, Normalizer.Form.NFC);
    }

    /** NFC-normalizes arbitrary test text without otherwise changing its contents. */
    public static String normalizePlainText(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.trim(), Normalizer.Form.NFC);
    }
}
