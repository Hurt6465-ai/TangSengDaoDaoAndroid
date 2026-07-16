package com.chat.feedlist.model;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FeedListTikTokPreview implements Serializable {
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:/video/|/v/|/player/v1/|data-video-id=[\\\"'])([0-9]{8,32})|(?:video_id|item_id)=([0-9]{8,32})",
            Pattern.CASE_INSENSITIVE
    );

    public String provider;
    public String provider_name;
    public String video_id;
    public String id;
    public String url;
    public String share_url;
    public String embed_url;
    public String embed_link;
    public String cover_url;
    public String thumbnail_url;
    public String cover_image_url;
    public String title;
    public String author_name;
    public String author_url;
    public String html;

    public String bestVideoId() {
        String direct = firstNonEmpty(video_id, id);
        if (direct.matches("[0-9]{8,32}")) return direct;
        String[] candidates = {url, share_url, embed_url, embed_link, html};
        for (String candidate : candidates) {
            if (TextUtils.isEmpty(candidate)) continue;
            Matcher matcher = VIDEO_ID_PATTERN.matcher(candidate);
            if (matcher.find()) {
                String value = !TextUtils.isEmpty(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
                if (!TextUtils.isEmpty(value)) return value;
            }
        }
        return "";
    }

    public String bestUrl() {
        return normalizeUrl(firstNonEmpty(url, share_url));
    }

    public String bestCoverUrl() {
        return normalizeUrl(firstNonEmpty(cover_url, thumbnail_url, cover_image_url));
    }

    public String bestProvider() {
        return firstNonEmpty(provider, provider_name, "tiktok");
    }

    public boolean hasPlayableVideo() {
        return !TextUtils.isEmpty(bestVideoId());
    }

    private static String normalizeUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = value.trim()
                .replace("&amp;", "&")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .replace("\\/", "/");
        if (normalized.startsWith("//")) normalized = "https:" + normalized;
        if (normalized.startsWith("http://")) normalized = "https://" + normalized.substring(7);
        return normalized;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !TextUtils.isEmpty(value.trim())) return value.trim();
        }
        return "";
    }
}
