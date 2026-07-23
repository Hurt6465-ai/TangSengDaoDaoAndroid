package com.chat.speech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Utilities used by the learning TTS path. */
public final class PinyinNormalizer {
    private static final List<String> INITIALS = Arrays.asList(
            "zh", "ch", "sh",
            "b", "p", "m", "f", "d", "t", "n", "l",
            "g", "k", "h", "j", "q", "x", "r", "z", "c", "s",
            "y", "w"
    );

    private PinyinNormalizer() {}

    /** Converts tone-mark pinyin such as nǐ hǎo to ni3 hao3. */
    public static String toToneNumbers(String input) {
        if (input == null) return "";
        String cleaned = input.trim().toLowerCase(Locale.US)
                .replace('ü', 'v')
                .replace("u:", "v");
        if (cleaned.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        StringBuilder syllable = new StringBuilder();
        int tone = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            Mark mark = mark(c);
            if (mark != null) {
                syllable.append(mark.base);
                tone = mark.tone;
                continue;
            }
            if (isPinyinLetter(c)) {
                syllable.append(c);
                continue;
            }
            if (c >= '1' && c <= '5') {
                tone = c - '0';
                continue;
            }
            flushSyllable(result, syllable, tone);
            tone = 0;
            if (Character.isWhitespace(c) || c == '-' || c == '\'' || c == '’') {
                appendSpace(result);
            }
        }
        flushSyllable(result, syllable, tone);
        return result.toString().trim().replaceAll("\\s+", " ");
    }

    public static List<String> syllables(String input) {
        String normalized = toToneNumbers(input);
        List<String> result = new ArrayList<>();
        if (normalized.isEmpty()) return result;
        for (String part : normalized.split("\\s+")) {
            String value = part.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    /**
     * Builds the same teaching-style text used by the third-party app.
     *
     * Examples:
     * bà      -> b à
     * nǐ hǎo  -> n ǐ，h ǎo
     * zhōng   -> zh ōng
     * ài      -> ài
     *
     * The returned value is intentionally plain text. The ByteDance Chinese frontend recognises
     * this spelling form and reads the initial followed by the tone-marked final.
     */
    public static String buildTeachingSpellingText(String pinyin) {
        List<String> numbered = syllables(pinyin);
        if (numbered.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String syllable : numbered) {
            String teaching = splitSyllableForTeaching(syllable);
            if (teaching.isEmpty()) continue;
            if (result.length() > 0) result.append('，');
            result.append(teaching);
        }
        return result.toString();
    }

    public static String splitSyllableForTeaching(String numberedSyllable) {
        if (numberedSyllable == null) return "";
        String value = numberedSyllable.trim().toLowerCase(Locale.US);
        if (value.isEmpty()) return "";

        int tone = 5;
        char last = value.charAt(value.length() - 1);
        if (last >= '1' && last <= '5') {
            tone = last - '0';
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) return "";

        String initial = findInitial(value);
        String marked = toneNumberToMark(value, tone);
        if (initial.isEmpty() || marked.length() <= initial.length()) return marked;
        return initial + " " + marked.substring(initial.length());
    }

    /** Keeps the older forced-pronunciation SSML path available for future fallback testing. */
    public static String buildSpellingSsml(String hanzi, String pinyin) {
        List<String> syllables = syllables(pinyin);
        if (syllables.isEmpty()) return "";
        List<String> characters = chineseCodePoints(hanzi);
        StringBuilder ssml = new StringBuilder("<speak>");
        if (characters.size() == syllables.size()) {
            for (int i = 0; i < syllables.size(); i++) {
                if (i > 0) ssml.append("<break time=\"260ms\"/>");
                ssml.append("<phoneme alphabet=\"py\" ph=\"")
                        .append(escapeXml(syllables.get(i)))
                        .append("\">")
                        .append(escapeXml(characters.get(i)))
                        .append("</phoneme>");
            }
        } else {
            String display = hanzi == null || hanzi.trim().isEmpty() ? pinyin : hanzi.trim();
            ssml.append("<phoneme alphabet=\"py\" ph=\"")
                    .append(escapeXml(String.join(" ", syllables)))
                    .append("\">")
                    .append(escapeXml(display))
                    .append("</phoneme>");
        }
        return ssml.append("</speak>").toString();
    }

    private static String findInitial(String syllable) {
        for (String initial : INITIALS) {
            if (syllable.startsWith(initial)) return initial;
        }
        return "";
    }

    private static String toneNumberToMark(String syllable, int tone) {
        String visible = syllable.replace('v', 'ü');
        if (tone < 1 || tone > 4) return visible;

        int markIndex = toneMarkIndex(visible);
        if (markIndex < 0) return visible;
        char vowel = visible.charAt(markIndex);
        char marked = toneVowel(vowel, tone);
        if (marked == 0) return visible;
        return visible.substring(0, markIndex) + marked + visible.substring(markIndex + 1);
    }

    private static int toneMarkIndex(String syllable) {
        int index = syllable.indexOf('a');
        if (index >= 0) return index;
        index = syllable.indexOf('e');
        if (index >= 0) return index;
        index = syllable.indexOf("ou");
        if (index >= 0) return index;
        for (int i = syllable.length() - 1; i >= 0; i--) {
            char c = syllable.charAt(i);
            if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'ü') return i;
        }
        return -1;
    }

    private static char toneVowel(char vowel, int tone) {
        switch (vowel) {
            case 'a': return "āáǎà".charAt(tone - 1);
            case 'e': return "ēéěè".charAt(tone - 1);
            case 'i': return "īíǐì".charAt(tone - 1);
            case 'o': return "ōóǒò".charAt(tone - 1);
            case 'u': return "ūúǔù".charAt(tone - 1);
            case 'ü': return "ǖǘǚǜ".charAt(tone - 1);
            default: return 0;
        }
    }

    private static List<String> chineseCodePoints(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (isHan(cp)) result.add(new String(Character.toChars(cp)));
        }
        return result;
    }

    private static boolean isHan(int cp) {
        return (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x20000 && cp <= 0x2FA1F);
    }

    private static void flushSyllable(StringBuilder result, StringBuilder syllable, int tone) {
        if (syllable.length() == 0) return;
        appendSpace(result);
        result.append(syllable);
        if (tone >= 1 && tone <= 5) result.append(tone);
        syllable.setLength(0);
    }

    private static void appendSpace(StringBuilder result) {
        if (result.length() > 0 && result.charAt(result.length() - 1) != ' ') result.append(' ');
    }

    private static boolean isPinyinLetter(char c) {
        return (c >= 'a' && c <= 'z') || c == 'v';
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static Mark mark(char c) {
        switch (c) {
            case 'ā': return new Mark('a', 1);
            case 'á': return new Mark('a', 2);
            case 'ǎ': return new Mark('a', 3);
            case 'à': return new Mark('a', 4);
            case 'ē': return new Mark('e', 1);
            case 'é': return new Mark('e', 2);
            case 'ě': return new Mark('e', 3);
            case 'è': return new Mark('e', 4);
            case 'ī': return new Mark('i', 1);
            case 'í': return new Mark('i', 2);
            case 'ǐ': return new Mark('i', 3);
            case 'ì': return new Mark('i', 4);
            case 'ō': return new Mark('o', 1);
            case 'ó': return new Mark('o', 2);
            case 'ǒ': return new Mark('o', 3);
            case 'ò': return new Mark('o', 4);
            case 'ū': return new Mark('u', 1);
            case 'ú': return new Mark('u', 2);
            case 'ǔ': return new Mark('u', 3);
            case 'ù': return new Mark('u', 4);
            case 'ǖ': return new Mark('v', 1);
            case 'ǘ': return new Mark('v', 2);
            case 'ǚ': return new Mark('v', 3);
            case 'ǜ': return new Mark('v', 4);
            case 'ń': return new Mark('n', 2);
            case 'ň': return new Mark('n', 3);
            case 'ǹ': return new Mark('n', 4);
            case 'ḿ': return new Mark('m', 2);
            default: return null;
        }
    }

    private static final class Mark {
        final char base;
        final int tone;

        Mark(char base, int tone) {
            this.base = base;
            this.tone = tone;
        }
    }
}
