package com.chat.feedlist.model;

import android.text.TextUtils;

import com.chat.base.config.WKApiConfig;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FeedListMedia implements Serializable {
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_TIKTOK = "tiktok";

    private static final Pattern TIKTOK_VIDEO_ID_PATTERN = Pattern.compile(
            "(?:/video/|/v/)([0-9]{8,32})",
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
    public String external_provider;
    public String external_id;
    public String external_url;
    public String external_title;
    public String external_author;

    public boolean isTikTok() {
        return TYPE_TIKTOK.equalsIgnoreCase(type) || "tiktok".equalsIgnoreCase(external_provider);
    }

    public boolean isVideo() {
        return TYPE_VIDEO.equalsIgnoreCase(type)
                || !TextUtils.isEmpty(play_url_540p)
                || !TextUtils.isEmpty(play_url_480p)
                || !TextUtils.isEmpty(play_url_720p)
                || !TextUtils.isEmpty(play_url);
    }

    /**
     * Returns the cover with compatibility fallbacks for older feed rows.
     * Older TikTok posts may have stored the thumbnail in thumb/display/origin.
     */
    public String tiktokCoverUrl() {
        String raw = firstNonEmpty(cover_url, thumb_url, display_url, origin_url);
        return showUrl(raw);
    }

    /**
     * Returns the persisted TikTok ID, or derives it from a canonical TikTok URL.
     * This keeps old posts playable even when external_id was not stored yet.
     */
    public String tiktokVideoId() {
        String saved = trim(external_id);
        if (saved.matches("[0-9]{8,32}")) return saved;

        String[] candidates = {external_url, play_url, play_url_540p, play_url_480p, play_url_720p};
        for (String candidate : candidates) {
            if (TextUtils.isEmpty(candidate)) continue;
            Matcher matcher = TIKTOK_VIDEO_ID_PATTERN.matcher(candidate);
            if (matcher.find()) return matcher.group(1);
        }
        return "";
    }

    public String displayUrl() {
        String raw;
        if (isTikTok()) raw = firstNonEmpty(cover_url, thumb_url, display_url, origin_url);
        else if (isVideo()) raw = !TextUtils.isEmpty(cover_url) ? cover_url : thumb_url;
        else raw = !TextUtils.isEmpty(display_url) ? display_url : (!TextUtils.isEmpty(thumb_url) ? thumb_url : origin_url);
        return showUrl(raw);
    }

    public String thumbUrl() {
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

    private String showUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        return WKApiConfig.getShowUrl(value);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public float ratio() {
        if (width <= 0 || height <= 0) return 1.25f;
        return height * 1f / Math.max(1, width);
    }
}
