package com.chat.base.config;

import android.text.TextUtils;

import com.xinbida.wukongim.entity.WKChannelType;

import java.net.URLEncoder;

/**
 * 2019-11-20 10:11
 * api地址
 */
public class WKApiConfig {
    public static String baseUrl = "";
    public static String baseWebUrl = "";

    public static final String NODEBB_BASE_URL = "https://bbs.886.best";
    public static final String NODEBB_HOME_URL = NODEBB_BASE_URL + "/";
    public static final String NODEBB_PARTNERS_SWIPE_URL = NODEBB_BASE_URL + "/partners/swipe";
    public static final String NODEBB_VIDEO_URL = NODEBB_BASE_URL + "/video";
    public static final String NODEBB_SSO_API_BASE_URL = "https://api.886.best/v1/";

    // Independent bbs-go service. Keep the trailing slash for Retrofit.
    private static String forumBaseUrl = "http://107.172.79.50:3001/";

    public static void initBaseURL(String apiURL) {
        baseUrl = apiURL + "/v1/";
        baseWebUrl = apiURL + "/web/";
    }

    public static void initBaseURLIncludeIP(String apiURL) {
        baseUrl = apiURL + "/v1/";
        baseWebUrl = apiURL + "/web/";
    }

    public static void initForumBaseURL(String url) {
        if (TextUtils.isEmpty(url)) return;
        String normalized = url.trim();
        if (!normalized.endsWith("/")) normalized += "/";
        forumBaseUrl = normalized;
    }

    public static String getForumBaseUrl() {
        return forumBaseUrl;
    }

    public static String getNodeBBSSOUrl(String redirectUrl) {
        if (TextUtils.isEmpty(redirectUrl)) {
            redirectUrl = NODEBB_HOME_URL;
        }
        try {
            return NODEBB_SSO_API_BASE_URL + "community/nodebb-login?redirect=" + URLEncoder.encode(redirectUrl, "UTF-8");
        } catch (Exception e) {
            return NODEBB_SSO_API_BASE_URL + "community/nodebb-login?redirect=" + redirectUrl;
        }
    }

    public static String getAvatarUrl(String uid) {
        return baseUrl + "users/" + uid + "/avatar";
    }

    public static String getGroupUrl(String groupId) {
        return baseUrl + "groups/" + groupId + "/avatar";
    }

    public static String getShowAvatar(String channelID, byte channelType) {
        return channelType == WKChannelType.PERSONAL ? getAvatarUrl(channelID) : getGroupUrl(channelID);
    }

    public static String getShowUrl(String url) {
        if (TextUtils.isEmpty(url) || url.startsWith("http") || url.startsWith("HTTP")) {
            return url;
        } else {
            return baseUrl + url;
        }
    }

}
