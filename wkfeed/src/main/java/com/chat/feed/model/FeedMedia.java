package com.chat.feed.model;

import android.text.TextUtils;

import com.chat.base.config.WKApiConfig;

import java.io.Serializable;

public class FeedMedia implements Serializable {
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";

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

    public boolean isVideo() {
        return TYPE_VIDEO.equalsIgnoreCase(type)
                || !TextUtils.isEmpty(play_url_540p)
                || !TextUtils.isEmpty(play_url_480p)
                || !TextUtils.isEmpty(play_url_720p)
                || !TextUtils.isEmpty(play_url);
    }

    public String displayUrl() {
        String raw;
        if (isVideo()) raw = !TextUtils.isEmpty(cover_url) ? cover_url : thumb_url;
        else raw = !TextUtils.isEmpty(display_url) ? display_url : (!TextUtils.isEmpty(thumb_url) ? thumb_url : origin_url);
        return showUrl(raw);
    }

    public String thumbUrl() {
        String raw = !TextUtils.isEmpty(thumb_url) ? thumb_url : display_url;
        if (TextUtils.isEmpty(raw)) raw = cover_url;
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

    public float ratio() {
        if (width <= 0 || height <= 0) return 1.25f;
        return height * 1f / Math.max(1, width);
    }
}
