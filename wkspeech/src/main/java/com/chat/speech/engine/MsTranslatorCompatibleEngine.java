package com.chat.speech.engine;

import android.content.Context;
import android.util.Base64;

import com.chat.speech.SpeechCache;
import com.chat.speech.SpeechPrefs;
import com.chat.speech.model.TtsSource;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MsTranslatorCompatibleEngine {
    private static final String ENDPOINT_URL =
            "https://dev.microsofttranslator.com/apps/endpoint?api-version=1.0";
    private static final String APP_ID = "MSTranslatorAndroidApp";
    private static final String SECRET =
            "oik6PdDdMnOXemTbwvMn9de/h9lFnfBaCWbGMMZqqoSaQaqUOqjVGm5NqsmjcBI1x+sS9ugjB55HEJWRiFXYFw==";

    private Endpoint cachedEndpoint;
    private String cachedEndpointKey = "";
    private volatile HttpURLConnection activeConnection;
    private volatile boolean cancelled;

    public File synthesize(
            Context context,
            String text,
            String voice,
            String locale,
            String audioFormat
    ) throws Exception {
        SpeechPrefs prefs = new SpeechPrefs(context);
        return synthesize(
                context,
                prefs.getActiveSource(),
                text,
                voice,
                locale,
                audioFormat,
                prefs.getRatePercent(),
                prefs.getPitchPercent()
        );
    }

    public File synthesize(
            Context context,
            String text,
            String voice,
            String locale,
            String audioFormat,
            int ratePercent,
            int pitchPercent
    ) throws Exception {
        SpeechPrefs prefs = new SpeechPrefs(context);
        return synthesize(
                context,
                prefs.getActiveSource(),
                text,
                voice,
                locale,
                audioFormat,
                ratePercent,
                pitchPercent
        );
    }

    public synchronized File synthesize(
            Context context,
            TtsSource source,
            String text,
            String voice,
            String locale,
            String audioFormat,
            int ratePercent,
            int pitchPercent
    ) throws Exception {
        cancelled = false;
        ensureNotCancelled();
        if (source == null) source = TtsSource.builtinMsTranslator();
        source.normalize();
        SpeechPrefs prefs = new SpeechPrefs(context);
        String format = audioFormat == null || audioFormat.trim().isEmpty()
                ? source.audioFormat
                : audioFormat;
        if (format == null || format.trim().isEmpty()) format = prefs.getAudioFormat();
        String cacheKey = source.id + "|" + format + "|" + voice + "|" + locale
                + "|r" + ratePercent + "|p" + pitchPercent + "|" + text;
        File output = SpeechCache.audioFile(context, cacheKey);
        if (output.exists() && output.length() > 100) {
            //noinspection ResultOfMethodCallIgnored
            output.setLastModified(System.currentTimeMillis());
            return output;
        }

        Exception lastError = null;
        for (int i = 0; i < 2; i++) {
            ensureNotCancelled();
            try {
                Endpoint endpoint = getEndpoint(source);
                ensureNotCancelled();
                byte[] audio = requestAudio(
                        source,
                        endpoint,
                        buildSsml(text, voice, locale, ratePercent, pitchPercent),
                        format
                );
                ensureNotCancelled();
                File temp = new File(output.getAbsolutePath() + ".tmp");
                try (FileOutputStream fileOutput = new FileOutputStream(temp)) {
                    fileOutput.write(audio);
                }
                if (output.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    output.delete();
                }
                if (!temp.renameTo(output)) {
                    try (FileOutputStream fileOutput = new FileOutputStream(output)) {
                        fileOutput.write(audio);
                    }
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                }
                SpeechCache.trim(context);
                return output;
            } catch (InterruptedException cancelledError) {
                throw cancelledError;
            } catch (Exception error) {
                clearEndpointCache();
                lastError = error;
            }
        }
        throw lastError == null ? new RuntimeException("微软兼容源合成失败") : lastError;
    }

    public void cancelActive() {
        cancelled = true;
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    private Endpoint getEndpoint(TtsSource source) throws Exception {
        ensureNotCancelled();
        if (source == null) source = TtsSource.builtinMsTranslator();
        source.normalize();
        String endpointUrl = TtsSource.isEmpty(source.endpointUrl)
                ? ENDPOINT_URL
                : source.endpointUrl;
        String key = source.id + "|" + endpointUrl + "|" + source.appId;
        if (cachedEndpoint != null && cachedEndpoint.valid() && key.equals(cachedEndpointKey)) {
            return cachedEndpoint;
        }

        String signature = getSign(source);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
        registerConnection(connection);
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept-Language", emptyOr(source.acceptLanguage, "zh-Hans"));
            connection.setRequestProperty("X-ClientVersion", emptyOr(source.clientVersion, "4.0.530a 5fe1dc6c"));
            connection.setRequestProperty("X-UserId", emptyOr(source.userId, "0f04d16a175c411e"));
            connection.setRequestProperty("X-HomeGeographicRegion", emptyOr(source.homeGeographicRegion, "zh-Hans-CN"));
            connection.setRequestProperty("X-ClientTraceId", UUID.randomUUID().toString());
            connection.setRequestProperty("X-MT-Signature", signature);
            connection.setRequestProperty("User-Agent", emptyOr(source.userAgent, "okhttp/4.5.0"));
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", "0");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(new byte[0]);
            }
            ensureNotCancelled();
            int code = connection.getResponseCode();
            String body = readString(connection, code >= 200 && code < 300);
            ensureNotCancelled();
            if (code != 200) {
                throw new RuntimeException("终结点信息获取失败: HTTP-" + code + " " + body);
            }
            JSONObject object = new JSONObject(body);
            Endpoint endpoint = new Endpoint(object.optString("r"), object.optString("t"));
            if (!endpoint.valid()) throw new RuntimeException("终结点信息无效");
            cachedEndpoint = endpoint;
            cachedEndpointKey = key;
            return endpoint;
        } finally {
            clearConnection(connection);
            connection.disconnect();
        }
    }

    private byte[] requestAudio(
            TtsSource source,
            Endpoint endpoint,
            String ssml,
            String format
    ) throws Exception {
        ensureNotCancelled();
        String template = source == null ? "" : source.ttsUrlTemplate;
        if (TtsSource.isEmpty(template)) {
            template = "https://{region}.tts.speech.microsoft.com/cognitiveservices/v1";
        }
        String url = template.replace("{region}", endpoint.region);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        registerConnection(connection);
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", endpoint.token);
            connection.setRequestProperty("Content-Type", "application/ssml+xml");
            connection.setRequestProperty("X-Microsoft-OutputFormat", format);
            connection.setRequestProperty("User-Agent", emptyOr(source.userAgent, "okhttp/4.5.0"));
            byte[] data = ssml.getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(data.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(data);
            }
            ensureNotCancelled();
            int code = connection.getResponseCode();
            byte[] body = readBytes(connection, code >= 200 && code < 300);
            ensureNotCancelled();
            if (code != 200) {
                throw new RuntimeException(
                        "音频获取失败: HTTP-" + code + " " + new String(body, "UTF-8")
                );
            }
            return body;
        } finally {
            clearConnection(connection);
            connection.disconnect();
        }
    }

    private String buildSsml(
            String text,
            String voice,
            String locale,
            int ratePercent,
            int pitchPercent
    ) {
        String safe = escapeXml(text);
        String xmlLang = locale == null || locale.trim().isEmpty() ? "zh-CN" : locale;
        String rate = signedPercent(ratePercent);
        String pitch = signedPercent(pitchPercent);
        return "<speak xmlns=\"http://www.w3.org/2001/10/synthesis\""
                + " xmlns:mstts=\"http://www.w3.org/2001/mstts\""
                + " version=\"1.0\" xml:lang=\"" + xmlLang + "\">"
                + "<voice name=\"" + escapeXml(voice) + "\">"
                + "<prosody rate=\"" + rate + "\" pitch=\"" + pitch
                + "\" volume=\"+0%\">"
                + safe
                + "</prosody></voice></speak>";
    }

    private String signedPercent(int value) {
        return value > 0 ? "+" + value + "%" : value + "%";
    }

    private String getSign(TtsSource source) throws Exception {
        if (source == null) source = TtsSource.builtinMsTranslator();
        source.normalize();
        String endpointUrl = TtsSource.isEmpty(source.endpointUrl)
                ? ENDPOINT_URL
                : source.endpointUrl;
        String appId = TtsSource.isEmpty(source.appId) ? APP_ID : source.appId;
        String secret = TtsSource.isEmpty(source.secretBase64) ? SECRET : source.secretBase64;
        String url = endpointUrl.split("://", 2)[1];
        String encodedUrl = URLEncoder.encode(url, "UTF-8");
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String formattedDate = dateFormat();
        String raw = (appId + encodedUrl + formattedDate + uuid).toLowerCase(Locale.US);
        SecretKeySpec keySpec = new SecretKeySpec(
                Base64.decode(secret, Base64.NO_WRAP),
                "HmacSHA256"
        );
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(keySpec);
        String signature = Base64.encodeToString(
                mac.doFinal(raw.getBytes("UTF-8")),
                Base64.NO_WRAP
        );
        return appId + "::" + signature + "::" + formattedDate + "::" + uuid;
    }

    private String dateFormat() {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date()).toLowerCase(Locale.US) + "GMT";
    }

    private void registerConnection(HttpURLConnection connection) throws InterruptedException {
        ensureNotCancelled();
        activeConnection = connection;
        ensureNotCancelled();
    }

    private void clearConnection(HttpURLConnection connection) {
        if (activeConnection == connection) activeConnection = null;
    }

    private void clearEndpointCache() {
        cachedEndpoint = null;
        cachedEndpointKey = "";
    }

    private void ensureNotCancelled() throws InterruptedException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("微软兼容 TTS 已取消");
        }
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("'", "&apos;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String readString(HttpURLConnection connection, boolean success) throws Exception {
        return new String(readBytes(connection, success), "UTF-8");
    }

    private static byte[] readBytes(HttpURLConnection connection, boolean success) throws Exception {
        InputStream raw = success ? connection.getInputStream() : connection.getErrorStream();
        if (raw == null) raw = connection.getInputStream();
        String encoding = connection.getContentEncoding();
        InputStream input = encoding != null && encoding.toLowerCase(Locale.US).contains("gzip")
                ? new GZIPInputStream(raw)
                : raw;
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = stream.read(buffer)) >= 0) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }

    private static String emptyOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static class Endpoint {
        final String region;
        final String token;

        Endpoint(String region, String token) {
            this.region = region;
            this.token = token;
        }

        boolean valid() {
            return region != null && !region.isEmpty() && token != null && !token.isEmpty();
        }
    }
}
