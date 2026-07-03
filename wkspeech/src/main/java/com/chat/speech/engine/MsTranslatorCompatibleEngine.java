package com.chat.speech.engine;

import android.content.Context;
import android.util.Base64;

import com.chat.speech.SpeechCache;
import com.chat.speech.SpeechPrefs;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MsTranslatorCompatibleEngine {
    private static final String ENDPOINT_URL = "https://dev.microsofttranslator.com/apps/endpoint?api-version=1.0";
    private static final String APP_ID = "MSTranslatorAndroidApp";
    private static final String SECRET = "oik6PdDdMnOXemTbwvMn9de/h9lFnfBaCWbGMMZqqoSaQaqUOqjVGm5NqsmjcBI1x+sS9ugjB55HEJWRiFXYFw==";

    private Endpoint cachedEndpoint;

    public synchronized File synthesize(Context context, String text, String voice, String locale, String audioFormat) throws Exception {
        SpeechPrefs prefs = new SpeechPrefs(context);
        String format = audioFormat == null || audioFormat.trim().isEmpty() ? prefs.getAudioFormat() : audioFormat;
        String cacheKey = "ms-translator|" + format + "|" + voice + "|" + locale + "|" + text;
        File output = SpeechCache.audioFile(context, cacheKey);
        if (output.exists() && output.length() > 100) {
            output.setLastModified(System.currentTimeMillis());
            return output;
        }
        Exception lastError = null;
        for (int i = 0; i < 2; i++) {
            try {
                Endpoint endpoint = getEndpoint();
                byte[] audio = requestAudio(endpoint, buildSsml(text, voice, locale), format);
                File tmp = new File(output.getAbsolutePath() + ".tmp");
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    fos.write(audio);
                }
                if (output.exists()) output.delete();
                if (!tmp.renameTo(output)) {
                    try (FileOutputStream fos = new FileOutputStream(output)) {
                        fos.write(audio);
                    }
                    tmp.delete();
                }
                SpeechCache.trim(context);
                return output;
            } catch (Exception e) {
                cachedEndpoint = null;
                lastError = e;
            }
        }
        throw lastError == null ? new RuntimeException("微软兼容源合成失败") : lastError;
    }

    private Endpoint getEndpoint() throws Exception {
        if (cachedEndpoint != null && cachedEndpoint.valid()) return cachedEndpoint;
        String signature = getSign();
        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT_URL).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept-Language", "zh-Hans");
        conn.setRequestProperty("X-ClientVersion", "4.0.530a 5fe1dc6c");
        conn.setRequestProperty("X-UserId", "0f04d16a175c411e");
        conn.setRequestProperty("X-HomeGeographicRegion", "zh-Hans-CN");
        conn.setRequestProperty("X-ClientTraceId", UUID.randomUUID().toString());
        conn.setRequestProperty("X-MT-Signature", signature);
        conn.setRequestProperty("User-Agent", "okhttp/4.5.0");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Content-Length", "0");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(new byte[0]);
        }
        int code = conn.getResponseCode();
        String body = readString(conn, code >= 200 && code < 300);
        if (code != 200) throw new RuntimeException("终结点信息获取失败: HTTP-" + code + " " + body);
        JSONObject object = new JSONObject(body);
        cachedEndpoint = new Endpoint(object.optString("r"), object.optString("t"));
        if (!cachedEndpoint.valid()) throw new RuntimeException("终结点信息无效");
        return cachedEndpoint;
    }

    private byte[] requestAudio(Endpoint endpoint, String ssml, String format) throws Exception {
        String url = "https://" + endpoint.region + ".tts.speech.microsoft.com/cognitiveservices/v1";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", endpoint.token);
        conn.setRequestProperty("Content-Type", "application/ssml+xml");
        conn.setRequestProperty("X-Microsoft-OutputFormat", format);
        conn.setRequestProperty("User-Agent", "okhttp/4.5.0");
        byte[] data = ssml.getBytes("UTF-8");
        conn.setFixedLengthStreamingMode(data.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
        }
        int code = conn.getResponseCode();
        byte[] body = readBytes(conn, code >= 200 && code < 300);
        if (code != 200) throw new RuntimeException("音频获取失败: HTTP-" + code + " " + new String(body, "UTF-8"));
        return body;
    }

    private String buildSsml(String text, String voice, String locale) {
        String safe = escapeXml(text);
        String xmlLang = (locale == null || locale.trim().isEmpty()) ? "zh-CN" : locale;
        return "<speak xmlns=\"http://www.w3.org/2001/10/synthesis\" version=\"1.0\" xml:lang=\"" + xmlLang + "\">"
                + "<voice name=\"" + escapeXml(voice) + "\">"
                + "<prosody rate=\"+0%\" pitch=\"+0%\" volume=\"+0%\">"
                + safe
                + "</prosody></voice></speak>";
    }

    private String getSign() throws Exception {
        String url = ENDPOINT_URL.split("://", 2)[1];
        String encodeUrl = URLEncoder.encode(url, "UTF-8");
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String formattedDate = dateFormat();
        String raw = (APP_ID + encodeUrl + formattedDate + uuid).toLowerCase(Locale.US);
        SecretKeySpec keySpec = new SecretKeySpec(Base64.decode(SECRET, Base64.NO_WRAP), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(keySpec);
        String signBase64 = Base64.encodeToString(mac.doFinal(raw.getBytes("UTF-8")), Base64.NO_WRAP);
        return APP_ID + "::" + signBase64 + "::" + formattedDate + "::" + uuid;
    }

    private String dateFormat() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date()).toLowerCase(Locale.US) + "GMT";
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("'", "&apos;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String readString(HttpURLConnection conn, boolean success) throws Exception {
        return new String(readBytes(conn, success), "UTF-8");
    }

    private static byte[] readBytes(HttpURLConnection conn, boolean success) throws Exception {
        InputStream raw = success ? conn.getInputStream() : conn.getErrorStream();
        if (raw == null) raw = conn.getInputStream();
        String encoding = conn.getContentEncoding();
        InputStream in = encoding != null && encoding.toLowerCase(Locale.US).contains("gzip") ? new GZIPInputStream(raw) : raw;
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) >= 0) out.write(buffer, 0, len);
            return out.toByteArray();
        }
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
