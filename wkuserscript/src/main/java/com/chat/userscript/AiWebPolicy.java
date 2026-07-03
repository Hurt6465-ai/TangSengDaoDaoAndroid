package com.chat.userscript;

import android.net.Uri;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AiWebPolicy {
    private static final Set<String> SCRIPT_HOST_SUFFIXES = new HashSet<>(Arrays.asList(
            "chat.deepseek.com",
            "deepseek.com",
            "chat.qwen.ai",
            "qwen.ai",
            "qianwen.com",
            "www.qianwen.com",
            "tongyi.aliyun.com"
    ));

    private static final Set<String> NAV_HOST_SUFFIXES = new HashSet<>(Arrays.asList(
            "chat.deepseek.com",
            "deepseek.com",
            "www.deepseek.com",
            "chat.qwen.ai",
            "qwen.ai",
            "qianwen.com",
            "www.qianwen.com",
            "tongyi.aliyun.com",
            "aliyun.com",
            "alibaba.com",
            "alicdn.com",
            "aliyuncs.com"
    ));

    private AiWebPolicy() {
    }

    public static boolean isHttpsUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            Uri uri = Uri.parse(url);
            return "https".equalsIgnoreCase(uri.getScheme());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isNavigationAllowed(String url) {
        if (!isHttpsUrl(url)) return false;
        return hostMatchesAny(url, NAV_HOST_SUFFIXES);
    }

    public static boolean isScriptHostAllowed(String url) {
        if (!isHttpsUrl(url)) return false;
        return hostMatchesAny(url, SCRIPT_HOST_SUFFIXES);
    }

    public static boolean isNetworkRequestAllowed(String url) {
        // 第三阶段默认不开放跨域联网。后续要做 GM_xmlhttpRequest 时，再加显式授权和域名白名单。
        return isScriptHostAllowed(url);
    }

    private static boolean hostMatchesAny(String url, Set<String> suffixes) {
        String host = hostOf(url);
        if (TextUtils.isEmpty(host)) return false;
        for (String suffix : suffixes) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        }
        return false;
    }

    public static String hostOf(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }
}
