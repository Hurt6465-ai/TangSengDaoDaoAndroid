package com.chat.speech.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** UTF-8 and XML-safe text preparation matching Edge Read Aloud's payload limit. */
public final class EdgeTextSplitter {
    private EdgeTextSplitter() {
    }

    public static List<String> splitAndEscape(String text, int byteLimit) {
        if (byteLimit <= 0) throw new IllegalArgumentException("byteLimit must be positive");
        String escaped = escapeXml(removeIncompatibleCharacters(text));
        byte[] remaining = escaped.getBytes(StandardCharsets.UTF_8);
        List<String> chunks = new ArrayList<>();

        while (remaining.length > byteLimit) {
            int splitAt = findPreferredSplit(remaining, byteLimit);
            if (splitAt <= 0) splitAt = findSafeUtf8Split(remaining, byteLimit);
            splitAt = avoidSplittingXmlEntity(remaining, splitAt);
            if (splitAt <= 0) splitAt = findSafeUtf8Split(remaining, byteLimit);
            if (splitAt <= 0) throw new IllegalArgumentException("无法安全切分 Edge TTS 文本");

            addChunk(chunks, Arrays.copyOfRange(remaining, 0, splitAt));
            int next = splitAt;
            while (next < remaining.length && isAsciiWhitespace(remaining[next])) next++;
            remaining = Arrays.copyOfRange(remaining, next, remaining.length);
        }
        addChunk(chunks, remaining);
        return chunks;
    }

    public static String escapeXmlAttribute(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void addChunk(List<String> chunks, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        String value = new String(bytes, StandardCharsets.UTF_8).trim();
        if (!value.isEmpty()) chunks.add(value);
    }

    private static int findPreferredSplit(byte[] bytes, int limit) {
        int upper = Math.min(limit, bytes.length);
        for (int i = upper - 1; i > 0; i--) {
            if (bytes[i] == '\n') return i;
        }
        for (int i = upper - 1; i > 0; i--) {
            if (bytes[i] == ' ' || bytes[i] == '\t') return i;
        }
        return -1;
    }

    private static int findSafeUtf8Split(byte[] bytes, int limit) {
        int split = Math.min(limit, bytes.length);
        while (split > 0 && split < bytes.length && (bytes[split] & 0xC0) == 0x80) {
            split--;
        }
        return split;
    }

    private static int avoidSplittingXmlEntity(byte[] bytes, int splitAt) {
        int ampersand = -1;
        for (int i = splitAt - 1; i >= 0; i--) {
            if (bytes[i] == ';') return splitAt;
            if (bytes[i] == '&') {
                ampersand = i;
                break;
            }
            if (splitAt - i > 16) break;
        }
        return ampersand >= 0 ? ampersand : splitAt;
    }

    private static boolean isAsciiWhitespace(byte value) {
        return value == ' ' || value == '\n' || value == '\r' || value == '\t';
    }

    private static String removeIncompatibleCharacters(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if ((codePoint >= 0 && codePoint <= 8)
                    || (codePoint >= 11 && codePoint <= 12)
                    || (codePoint >= 14 && codePoint <= 31)) {
                result.append(' ');
            } else {
                result.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
