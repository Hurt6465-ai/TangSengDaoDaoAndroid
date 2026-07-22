package com.chat.feed.model;

import android.net.Uri;
import android.text.TextUtils;

import com.chat.base.config.WKApiConfig;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FeedMedia implements Serializable {
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_TIKTOK = "tiktok";

    private static final Pattern TIKTOK_VIDEO_ID_PATTERN = Pattern.compile(
            "(?:/video/|/v/|/player/v1/)([0-9]{8,32})|(?:video_id|item_id)=([0-9]{8,32})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXPIRES_PATTERN = Pattern.compile(
            "(?:[?&]|&amp;)(?:x-)?expires=([0-9]{10,13})",
            Pattern.CASE_INSENSITIVE
    );

    public String type;
    public String thumb_url;
    public String display_url;
    public String origin_url;
    public String cover_url;
    public String play_url;
    public String play_url_480p;
    public String play_url_540p;
    public String play_url_720p;
    public int width;
    public int height;
    public long size;
    public long duration_ms;

    // External media fields returned by the current feed backend.
    public String external_provider;
    public String external_id;
    public String external_url;
    public String external_title;
    public String external_author;

    public boolean isTikTok() {
        return TYPE_TIKTOK.equalsIgnoreCase(type)
                || "tiktok".equalsIgnoreCase(external_provider);
    }

    public boolean isVideo() {
        return TYPE_VIDEO.equalsIgnoreCase(type)
                || !TextUtils.isEmpty(play_url_540p)
                || !TextUtils.isEmpty(play_url_480p)
                || !TextUtils.isEmpty(play_url_720p)
                || !TextUtils.isEmpty(play_url);
    }

    public String displayUrl() {
        if (isTikTok()) return tiktokCoverUrl();
        String raw;
        if (isVideo()) raw = !TextUtils.isEmpty(cover_url) ? cover_url : thumb_url;
        else raw = !TextUtils.isEmpty(display_url) ? display_url
                : (!TextUtils.isEmpty(thumb_url) ? thumb_url : origin_url);
        return showUrl(raw);
    }

    public String thumbUrl() {
        if (isTikTok()) return tiktokCoverUrl();
        String raw = !TextUtils.isEmpty(thumb_url) ? thumb_url
                : (!TextUtils.isEmpty(display_url) ? display_url
                : (!TextUtils.isEmpty(origin_url) ? origin_url : cover_url));
        return showUrl(raw);
    }

    public String playUrl() {
        String raw = !TextUtils.isEmpty(play_url_540p) ? play_url_540p
                : (!TextUtils.isEmpty(play_url_480p) ? play_url_480p
                : (!TextUtils.isEmpty(play_url_720p) ? play_url_720p : play_url));
        return showUrl(raw);
    }

    /** Returns only an image-like URL; a TikTok page/player URL is never sent to Glide. */
    public String tiktokCoverUrl() {
        String[] candidates = {cover_url, thumb_url, display_url, origin_url};
        for (String candidate : candidates) {
            String normalized = showUrl(candidate);
            if (isLikelyImageUrl(normalized)) return normalized;
        }
        return "";
    }

    public boolean isTikTokCoverProbablyExpired(long nowMillis) {
        String value = tiktokCoverUrl();
        if (TextUtils.isEmpty(value)) return true;
        Matcher matcher = EXPIRES_PATTERN.matcher(value);
        if (!matcher.find()) return false;
        try {
            long expires = Long.parseLong(matcher.group(1));
            if (expires < 10_000_000_000L) expires *= 1000L;
            return expires <= nowMillis + 60_000L;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String tiktokSourceUrl() {
        String[] candidates = {external_url, play_url, display_url, origin_url};
        for (String candidate : candidates) {
            String normalized = normalizeAbsolute(candidate);
            if (isTikTokPageUrl(normalized)) return normalized;
        }
        return "";
    }

    public String tiktokVideoId() {
        String saved = trim(external_id);
        if (saved.matches("[0-9]{8,32}")) return saved;
        String[] candidates = {
                external_url, play_url, play_url_540p, play_url_480p, play_url_720p,
                display_url, origin_url
        };
        for (String candidate : candidates) {
            if (TextUtils.isEmpty(candidate)) continue;
            Matcher matcher = TIKTOK_VIDEO_ID_PATTERN.matcher(candidate);
            if (!matcher.find()) continue;
            String value = !TextUtils.isEmpty(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String showUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = normalizeAbsolute(value);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return normalized;
        return WKApiConfig.getShowUrl(normalized);
    }

    private static String normalizeAbsolute(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = value.trim()
                .replace("&amp;", "&")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .replace("\\/", "/");
        if (normalized.startsWith("//")) normalized = "https:" + normalized;
        if (normalized.startsWith("http://") && looksLikeTikTokHost(normalized)) {
            normalized = "https://" + normalized.substring(7);
        }
        return normalized;
    }

    private static boolean isLikelyImageUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || TextUtils.isEmpty(host)) return false;
            // All tiktok.com URLs are pages/redirects. Image CDN hosts use other domains.
            if (isTikTokHost(host)) return false;
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            return !path.endsWith(".mp4") && !path.endsWith(".m3u8");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTikTokPageUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        try {
            Uri uri = Uri.parse(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            return isTikTokHost(uri.getHost());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTikTokHost(String host) {
        if (TextUtils.isEmpty(host)) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("tiktok.com") || lower.endsWith(".tiktok.com");
    }

    private static boolean looksLikeTikTokHost(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("tiktok")
                || lower.contains("muscdn")
                || lower.contains("byteimg")
                || lower.contains("ibytedtos")
                || lower.contains("tiktokcdn");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public float ratio() {
        if (width <= 0 || height <= 0) return 1.25f;
        return height * 1f / Math.max(1, width);
    }
}
