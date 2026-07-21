package com.chat.speech.engine;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** One Edge WebSocket turn. A new instance is used for each <=4096-byte SSML chunk. */
public final class EdgeTtsWebSocketListener extends WebSocketListener {
    private final EdgeProtocolConfig config;
    private final OutputStream output;
    private final String escapedText;
    private final String voice;
    private final int ratePercent;
    private final int pitchValue;
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Object writeLock = new Object();

    private volatile WebSocket socket;
    private volatile Exception error;
    private volatile boolean audioReceived;
    private volatile boolean turnEnded;
    private volatile boolean cancelled;

    public EdgeTtsWebSocketListener(
            EdgeProtocolConfig config,
            OutputStream output,
            String escapedText,
            String voice,
            int ratePercent,
            int pitchValue
    ) {
        this.config = config;
        this.output = output;
        this.escapedText = escapedText;
        this.voice = voice;
        this.ratePercent = ratePercent;
        this.pitchValue = pitchValue;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        socket = webSocket;
        if (cancelled) {
            webSocket.cancel();
            finished.countDown();
            return;
        }
        try {
            boolean configSent = webSocket.send(buildSpeechConfigMessage());
            boolean ssmlSent = webSocket.send(buildSsmlMessage());
            if (!configSent || !ssmlSent) {
                fail(new EdgeWebSocketException("Edge 请求发送失败", 0, null, null));
            }
        } catch (Exception exception) {
            fail(exception);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (cancelled || turnEnded || error != null) return;
        try {
            EdgeFrameParser.TextFrame frame = EdgeFrameParser.parseText(text);
            String path = frame.path();
            if ("turn.end".equalsIgnoreCase(path)) {
                if (!audioReceived) {
                    fail(new EdgeWebSocketException("Edge 没有返回音频", 0, null, null));
                    return;
                }
                turnEnded = true;
                webSocket.close(1000, "turn.end");
                finished.countDown();
                return;
            }
            if ("response".equalsIgnoreCase(path)
                    || "turn.start".equalsIgnoreCase(path)
                    || "audio.metadata".equalsIgnoreCase(path)) {
                return;
            }
            fail(new EdgeWebSocketException("Edge 返回未知消息：" + path, 0, null, null));
        } catch (Exception exception) {
            fail(exception);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        if (cancelled || turnEnded || error != null) return;
        try {
            EdgeFrameParser.AudioFrame frame = EdgeFrameParser.parseBinary(bytes.toByteArray());
            if (frame.terminal) return;
            synchronized (writeLock) {
                output.write(frame.audio);
            }
            audioReceived = true;
        } catch (Exception exception) {
            fail(exception);
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        if (!turnEnded && !cancelled) {
            fail(new EdgeWebSocketException(
                    "Edge 连接提前关闭：" + code + " " + safe(reason),
                    code,
                    null,
                    null
            ));
        }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        if (!turnEnded && !cancelled && error == null) {
            error = new EdgeWebSocketException(
                    "Edge 连接已关闭：" + code + " " + safe(reason),
                    code,
                    null,
                    null
            );
        }
        finished.countDown();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
        if (!cancelled && error == null) {
            int statusCode = response == null ? 0 : response.code();
            String serverDate = response == null ? null : response.header("Date");
            String detail = throwable == null ? "未知网络错误" : safe(throwable.getMessage());
            error = new EdgeWebSocketException(
                    "Edge WebSocket 失败" + (statusCode > 0 ? "（HTTP " + statusCode + "）" : "")
                            + (detail.isEmpty() ? "" : "：" + detail),
                    statusCode,
                    serverDate,
                    throwable
            );
        }
        finished.countDown();
    }

    public void awaitCompletion(int timeoutSeconds) throws Exception {
        boolean completed;
        try {
            completed = finished.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            cancel();
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        if (!completed) {
            cancel();
            throw new EdgeWebSocketException("Edge 语音接收超时", 0, null, null);
        }
        if (cancelled) throw new InterruptedException("Edge TTS 已取消");
        if (error != null) throw error;
        if (!turnEnded || !audioReceived) {
            throw new EdgeWebSocketException("Edge 语音响应不完整", 0, null, null);
        }
    }

    public void attach(WebSocket webSocket) {
        if (socket == null) socket = webSocket;
        if (cancelled && webSocket != null) webSocket.cancel();
    }

    public void cancel() {
        cancelled = true;
        WebSocket current = socket;
        if (current != null) current.cancel();
        finished.countDown();
    }

    private void fail(Exception exception) {
        if (error == null) error = exception;
        WebSocket current = socket;
        if (current != null) current.cancel();
        finished.countDown();
    }

    private String buildSpeechConfigMessage() {
        String timestamp = edgeDateString();
        return "X-Timestamp:" + timestamp + "\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                + "\"sentenceBoundaryEnabled\":\"true\","
                + "\"wordBoundaryEnabled\":\"false\"},"
                + "\"outputFormat\":\"" + jsonEscape(config.outputFormat) + "\"}}}}\r\n";
    }

    private String buildSsmlMessage() {
        String timestamp = edgeDateString();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String safeVoice = EdgeTextSplitter.escapeXmlAttribute(voice);
        String rate = signed(ratePercent) + "%";
        String pitch = signed(pitchValue) + "Hz";
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
                + "<voice name='" + safeVoice + "'>"
                + "<prosody pitch='" + pitch + "' rate='" + rate + "' volume='+0%'>"
                + escapedText
                + "</prosody></voice></speak>";
        return "X-RequestId:" + requestId + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + timestamp + "Z\r\n"
                + "Path:ssml\r\n\r\n"
                + ssml;
    }

    private static String edgeDateString() {
        SimpleDateFormat format = new SimpleDateFormat(
                "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                Locale.US
        );
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date());
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class EdgeWebSocketException extends Exception {
        public final int statusCode;
        public final String serverDate;

        EdgeWebSocketException(
                String message,
                int statusCode,
                String serverDate,
                Throwable cause
        ) {
            super(message, cause);
            this.statusCode = statusCode;
            this.serverDate = serverDate;
        }
    }
}
