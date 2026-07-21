package com.chat.speech.engine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Parses Edge Read Aloud text and binary WebSocket protocol frames. */
public final class EdgeFrameParser {
    private EdgeFrameParser() {
    }

    public static TextFrame parseText(String message) throws ProtocolException {
        if (message == null) throw new ProtocolException("Edge 返回了空文本帧");
        int separator = message.indexOf("\r\n\r\n");
        if (separator < 0) throw new ProtocolException("Edge 文本帧缺少头部分隔符");
        Map<String, String> headers = parseHeaders(message.substring(0, separator));
        return new TextFrame(headers, message.substring(separator + 4));
    }

    public static AudioFrame parseBinary(byte[] message) throws ProtocolException {
        if (message == null || message.length < 2) {
            throw new ProtocolException("Edge 二进制帧缺少头长度");
        }
        int headerLength = ((message[0] & 0xFF) << 8) | (message[1] & 0xFF);
        int headerStart = 2;
        int audioStart = headerStart + headerLength;
        if (headerLength < 0 || audioStart > message.length) {
            throw new ProtocolException("Edge 二进制帧头长度无效");
        }
        String rawHeaders = new String(
                message,
                headerStart,
                headerLength,
                StandardCharsets.UTF_8
        );
        Map<String, String> headers = parseHeaders(rawHeaders);
        String path = header(headers, "Path");
        if (!"audio".equalsIgnoreCase(path)) {
            throw new ProtocolException("Edge 二进制帧不是音频数据");
        }

        byte[] audio = Arrays.copyOfRange(message, audioStart, message.length);
        String contentType = header(headers, "Content-Type");
        if (contentType == null || contentType.trim().isEmpty()) {
            if (audio.length == 0) return new AudioFrame(new byte[0], true);
            throw new ProtocolException("Edge 音频帧缺少 Content-Type");
        }
        if (!"audio/mpeg".equalsIgnoreCase(contentType.trim())) {
            throw new ProtocolException("Edge 返回了不支持的音频格式：" + contentType);
        }
        if (audio.length == 0) throw new ProtocolException("Edge 返回了空音频帧");
        return new AudioFrame(audio, false);
    }

    public static String header(Map<String, String> headers, String name) {
        if (headers == null || name == null) return null;
        return headers.get(name.toLowerCase(Locale.US));
    }

    private static Map<String, String> parseHeaders(String rawHeaders) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawHeaders == null) return result;
        String[] lines = rawHeaders.split("\\r?\\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.US);
            String value = line.substring(colon + 1).trim();
            if (!name.isEmpty()) result.put(name, value);
        }
        return result;
    }

    public static final class TextFrame {
        public final Map<String, String> headers;
        public final String body;

        TextFrame(Map<String, String> headers, String body) {
            this.headers = headers;
            this.body = body;
        }

        public String path() {
            return header(headers, "Path");
        }
    }

    public static final class AudioFrame {
        public final byte[] audio;
        public final boolean terminal;

        AudioFrame(byte[] audio, boolean terminal) {
            this.audio = audio;
            this.terminal = terminal;
        }
    }

    public static class ProtocolException extends Exception {
        ProtocolException(String message) {
            super(message);
        }
    }
}
