package com.chat.learning;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

/** Chinese-to-pinyin helper. Polyphonic words should provide pinyin_override. */
final class PinyinUtils {
    private PinyinUtils() {}

    static String resolve(String word, String override, String legacyPinyin) {
        if (notEmpty(override)) return normalize(override);
        if (notEmpty(legacyPinyin)) return normalize(legacyPinyin);
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
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static boolean notEmpty(String value) {
        return value != null && value.trim().length() > 0;
    }
}
