package com.chat.feedlist;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.chat.feedlist.model.FeedListTikTokPreview;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side fallback for the app server's TikTok preview endpoint.
 * TikTok's public oEmbed response contains a fresh thumbnail_url and embed metadata.
 */
public final class TikTokMetadataResolver {
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(FeedListTikTokPreview preview);
        void onFail(String message);
    }

    private TikTokMetadataResolver() {
    }

    public static void resolve(String sourceUrl, Callback callback) {
        String safeSource = safeTikTokUrl(sourceUrl);
        if (TextUtils.isEmpty(safeSource)) {
            deliverFail(callback, "TikTok link is invalid");
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                String canonicalUrl = resolveCanonicalUrl(safeSource);
                FeedListTikTokPreview preview = requestOEmbed(canonicalUrl);
                if (TextUtils.isEmpty(preview.url)) preview.url = canonicalUrl;
                if (TextUtils.isEmpty(preview.video_id)) preview.video_id = preview.bestVideoId();
                if (!preview.hasPlayableVideo() && TextUtils.isEmpty(preview.bestCoverUrl())) {
                    throw new IllegalStateException("TikTok metadata is incomplete");
                }
                MAIN.post(() -> {
                    if (callback != null) callback.onSuccess(preview);
                });
            } catch (Throwable error) {
                String message = error.getMessage();
                deliverFail(callback, TextUtils.isEmpty(message) ? "TikTok metadata failed" : message);
            }
        });
    }

    private static String resolveCanonicalUrl(String sourceUrl) {
        if (!TextUtils.isEmpty(extractVideoId(sourceUrl))) return sourceUrl;
        HttpURLConnection connection = null;
        try {
            connection = open(sourceUrl);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Range", "bytes=0-0");
            int code = connection.getResponseCode();
            String finalUrl = connection.getURL() == null ? "" : connection.getURL().toString();
            if (code >= 200 && code < 400 && !TextUtils.isEmpty(safeTikTokUrl(finalUrl))) {
                return finalUrl;
            }
        } catch (Throwable ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return sourceUrl;
    }

    private static FeedListTikTokPreview requestOEmbed(String canonicalUrl) throws Exception {
        String endpoint = "https://www.tiktok.com/oembed?url="
                + URLEncoder.encode(canonicalUrl, StandardCharsets.UTF_8.name());
        HttpURLConnection connection = null;
        try {
            connection = open(endpoint);
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("TikTok oEmbed HTTP " + code);
            }
            String json = readText(connection.getInputStream());
            JSONObject object = new JSONObject(json);
            FeedListTikTokPreview result = new FeedListTikTokPreview();
            result.provider = object.optString("provider_name", "TikTok");
            result.provider_name = object.optString("provider_name", "TikTok");
            result.title = object.optString("title", "");
            result.author_name = object.optString("author_name", "");
            result.author_url = object.optString("author_url", "");
            result.thumbnail_url = normalizeHttps(object.optString("thumbnail_url", ""));
            result.cover_url = result.thumbnail_url;
            result.html = object.optString("html", "");
            result.url = canonicalUrl;
            result.video_id = firstNonEmpty(extractVideoId(canonicalUrl), result.bestVideoId());
            return result;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection open(String value) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag());
        return connection;
    }

    private static String readText(InputStream source) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("TikTok response is too large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String extractVideoId(String value) {
        if (TextUtils.isEmpty(value)) return "";
        FeedListTikTokPreview preview = new FeedListTikTokPreview();
        preview.url = value;
        return preview.bestVideoId();
    }

    private static String safeTikTokUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = normalizeHttps(value);
        try {
            Uri uri = Uri.parse(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return "";
            String host = uri.getHost();
            if (TextUtils.isEmpty(host)) return "";
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("tiktok.com") || host.endsWith(".tiktok.com") ? uri.toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String normalizeHttps(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = value.trim();
        if (normalized.startsWith("//")) normalized = "https:" + normalized;
        if (normalized.startsWith("http://")) normalized = "https://" + normalized.substring(7);
        return normalized;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static void deliverFail(Callback callback, String message) {
        MAIN.post(() -> {
            if (callback != null) callback.onFail(message);
        });
    }
}
