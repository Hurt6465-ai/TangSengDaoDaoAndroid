package com.chat.speech.engine;

import android.content.Context;

import com.chat.speech.SpeechCache;
import com.chat.speech.model.TtsSource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

/** Actual Edge Read Aloud WebSocket TTS implementation. */
public final class EdgeTtsEngine {
    private static final int EDGE_TEXT_BYTE_LIMIT = 4096;
    private static final Pattern SHORT_VOICE_PATTERN = Pattern.compile(
            "^([a-z]{2,})-([A-Z]{2,})-(.+Neural)$"
    );

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // WebSocket lifetime is bounded by EdgeTtsWebSocketListener.awaitCompletion().
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private final AtomicReference<EdgeTtsWebSocketListener> activeListener =
            new AtomicReference<>();

    public File synthesize(
            Context context,
            TtsSource source,
            String text,
            String voice,
            String locale,
            String audioFormat,
            int ratePercent,
            int pitchValue
    ) throws Exception {
        if (context == null) throw new IllegalArgumentException("context 为空");
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("朗读文本为空");
        if (voice == null || voice.trim().isEmpty()) throw new IllegalArgumentException("Edge 发音人为空");

        TtsSource safeSource = source == null ? TtsSource.edgeWebSocketTemplate() : source;
        safeSource.normalize();
        EdgeProtocolConfig config = EdgeProtocolConfig.from(safeSource)
                .withOutputFormat(audioFormat);
        String format = config.outputFormat;
        String edgeVoice = normalizeVoiceName(voice.trim());
        String cacheKey = safeSource.id
                + "|edge|" + config.secMsGecVersion
                + "|" + format
                + "|" + edgeVoice
                + "|" + locale
                + "|r" + ratePercent
                + "|p" + pitchValue
                + "|" + text;
        File output = SpeechCache.audioFile(context, cacheKey);
        if (output.exists() && output.length() > 100L) {
            //noinspection ResultOfMethodCallIgnored
            output.setLastModified(System.currentTimeMillis());
            return output;
        }

        List<String> chunks = EdgeTextSplitter.splitAndEscape(text.trim(), EDGE_TEXT_BYTE_LIMIT);
        if (chunks.isEmpty()) throw new IllegalArgumentException("Edge 朗读文本为空");
        File temp = new File(output.getAbsolutePath() + ".tmp." + UUID.randomUUID());

        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(temp))) {
            for (String escapedChunk : chunks) {
                checkCancelled();
                synthesizeChunk(
                        config,
                        stream,
                        escapedChunk,
                        edgeVoice,
                        ratePercent,
                        pitchValue
                );
            }
            stream.flush();
        } catch (Exception exception) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw exception;
        }

        if (temp.length() <= 100L) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IllegalStateException("Edge 返回的音频文件无效");
        }
        replaceAtomically(temp, output);
        SpeechCache.trim(context);
        return output;
    }

    public void cancelActive() {
        EdgeTtsWebSocketListener listener = activeListener.getAndSet(null);
        if (listener != null) listener.cancel();
    }

    private void synthesizeChunk(
            EdgeProtocolConfig config,
            OutputStream stream,
            String escapedChunk,
            String voice,
            int ratePercent,
            int pitchValue
    ) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            checkCancelled();
            String connectionId = UUID.randomUUID().toString().replace("-", "");
            String secMsGec = EdgeDrm.generateSecMsGec(config.trustedClientToken);
            String url = config.buildWebSocketUrl(connectionId, secMsGec);
            Request request = new Request.Builder()
                    .url(url)
                    .header("Pragma", "no-cache")
                    .header("Cache-Control", "no-cache")
                    .header("Origin", config.origin)
                    .header("User-Agent", config.userAgent)
                    .header("Accept-Language", config.acceptLanguage)
                    .header("Accept-Encoding", "gzip, deflate, br, zstd")
                    .header("Cookie", "muid=" + EdgeDrm.randomMuid() + ";")
                    .build();

            EdgeTtsWebSocketListener listener = new EdgeTtsWebSocketListener(
                    config,
                    stream,
                    escapedChunk,
                    voice,
                    ratePercent,
                    pitchValue
            );
            activeListener.set(listener);
            OkHttpClient requestClient = client.newBuilder()
                    .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                    .build();
            WebSocket socket = requestClient.newWebSocket(request, listener);
            listener.attach(socket);
            try {
                listener.awaitCompletion(config.receiveTimeoutSeconds);
                return;
            } catch (EdgeTtsWebSocketListener.EdgeWebSocketException edgeError) {
                lastError = edgeError;
                boolean adjusted = edgeError.statusCode == 403
                        && attempt == 0
                        && EdgeDrm.adjustFromServerDate(edgeError.serverDate);
                if (!adjusted) throw edgeError;
            } finally {
                activeListener.compareAndSet(listener, null);
                if (Thread.currentThread().isInterrupted()) socket.cancel();
            }
        }
        throw lastError == null ? new IllegalStateException("Edge TTS 合成失败") : lastError;
    }


    /** Converts a short Azure/Edge voice id to the full name emitted by Microsoft Edge. */
    static String normalizeVoiceName(String voice) {
        if (voice == null) return "";
        String value = voice.trim();
        Matcher matcher = SHORT_VOICE_PATTERN.matcher(value);
        if (!matcher.matches()) return value;

        String language = matcher.group(1);
        String region = matcher.group(2);
        String name = matcher.group(3);
        int variantSeparator = name.indexOf('-');
        if (variantSeparator >= 0) {
            region = region + "-" + name.substring(0, variantSeparator);
            name = name.substring(variantSeparator + 1);
        }
        return "Microsoft Server Speech Text to Speech Voice ("
                + language + "-" + region + ", " + name + ")";
    }

    private static void replaceAtomically(File temp, File output) throws Exception {
        if (output.exists() && !output.delete()) {
            throw new IllegalStateException("无法替换旧语音缓存");
        }
        if (temp.renameTo(output)) return;

        try (FileOutputStream destination = new FileOutputStream(output);
             java.io.FileInputStream source = new java.io.FileInputStream(temp)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = source.read(buffer)) >= 0) {
                destination.write(buffer, 0, count);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        temp.delete();
    }

    private static void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Edge TTS 已取消");
        }
    }
}
