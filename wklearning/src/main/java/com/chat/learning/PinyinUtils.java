package com.chat.learning;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Chinese-to-pinyin helper. Polyphonic words and neutral tones must use word-level rules. */
final class PinyinUtils {
    /**
     * Pinyin4j works character by character. It cannot know word boundaries, neutral tones or
     * context-dependent readings. Keep a small built-in fallback for the bundled HSK preview so
     * cached/remote packs without explicit overrides still sound correct.
     */
    private static final Map<String, String> WORD_PINYIN;
    private static final Map<String, String> WORD_TTS_PINYIN;

    static {
        Map<String, String> display = new HashMap<>();
        display.put("你好", "nǐ hǎo");
        display.put("谢谢", "xiè xie");
        display.put("再见", "zài jiàn");
        display.put("可以", "kě yǐ");
        display.put("朋友", "péng you");
        display.put("学习", "xué xí");
        display.put("工作", "gōng zuò");
        display.put("银行", "yín háng");
        WORD_PINYIN = Collections.unmodifiableMap(display);

        Map<String, String> speech = new HashMap<>();
        speech.put("你好", "nǐ hǎo");
        speech.put("谢谢", "xiè xie");
        speech.put("再见", "zài jiàn");
        // Direct pinyin input bypasses some lexical tone-sandhi logic. Use the actual spoken tone.
        speech.put("可以", "ké yǐ");
        speech.put("朋友", "péng you");
        speech.put("学习", "xué xí");
        speech.put("工作", "gōng zuò");
        speech.put("银行", "yín háng");
        WORD_TTS_PINYIN = Collections.unmodifiableMap(speech);
    }

    private PinyinUtils() {}

    static String resolve(String word, String override, String legacyPinyin) {
        if (notEmpty(override)) return normalize(override);
        if (notEmpty(legacyPinyin)) return normalize(legacyPinyin);
        String known = WORD_PINYIN.get(safeWord(word));
        if (known != null) return known;
        return generatePerCharacter(word);
    }

    /** Separate display orthography from the pinyin string sent to the TTS frontend. */
    static String resolveForSpeech(String word, String override, String displayPinyin) {
        if (notEmpty(override)) return normalize(override);
        String known = WORD_TTS_PINYIN.get(safeWord(word));
        if (known != null) return known;
        return normalize(displayPinyin);
    }

    private static String generatePerCharacter(String word) {
        if (!notEmpty(word)) return "";
        try {
            HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
            format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
            format.setToneType(HanyuPinyinToneType.WITH_TONE_MARK);
            format.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);
            StringBuilder out = new StringBuilder();
            for (int offset = 0; offset < word.length();) {
                int codePoint = word.codePointAt(offset);
                offset += Character.charCount(codePoint);
                if (codePoint <= Character.MAX_VALUE) {
                    String[] values = PinyinHelper.toHanyuPinyinStringArray((char) codePoint, format);
                    if (values != null && values.length > 0) {
                        appendToken(out, values[0]);
                        continue;
                    }
                }
                String raw = new String(Character.toChars(codePoint));
                if (raw.trim().length() > 0) appendToken(out, raw);
            }
            return normalize(out.toString());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void appendToken(StringBuilder out, String token) {
        if (token == null || token.length() == 0) return;
        if (out.length() > 0) out.append(' ');
        out.append(token);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
    }

    private static String safeWord(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean notEmpty(String value) {
        return value != null && value.trim().length() > 0;
    }
}
