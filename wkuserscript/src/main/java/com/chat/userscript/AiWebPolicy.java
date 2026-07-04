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
            "www.deepseek.com",
            "chat.qwen.ai",
            "qwen.ai",
            "www.qwen.ai",
            "qianwen.com",
            "www.qianwen.com",
            "tongyi.aliyun.com",
            "886.best"
    ));

    private static final Set<String> NAV_HOST_SUFFIXES = new HashSet<>(Arrays.asList(
            "chat.deepseek.com",
            "deepseek.com",
            "www.deepseek.com",
            "chat.qwen.ai",
            "qwen.ai",
            "www.qwen.ai",
            "qianwen.com",
            "www.qianwen.com",
            "tongyi.aliyun.com",
            "aliyun.com",
            "alibaba.com",
            "alicdn.com",
            "aliyuncs.com",
            "886.best"
    ));

    private static final Set<String> NETWORK_HOST_SUFFIXES = new HashSet<>(Arrays.asList(
            "chat.deepseek.com",
            "deepseek.com",
            "www.deepseek.com",
            "chat.qwen.ai",
            "qwen.ai",
            "www.qwen.ai",
            "qianwen.com",
            "www.qianwen.com",
            "tongyi.aliyun.com",
            "api.886.best",
            "886.best"
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
        if (!isHttpsUrl(url)) return false;
        return hostMatchesAny(url, NETWORK_HOST_SUFFIXES) && !isLocalOrPrivateHost(url);
    }

    public static boolean isConnectAllowedByMeta(String url, java.util.List<String> connects) {
        if (!isNetworkRequestAllowed(url)) return false;
        if (connects == null || connects.isEmpty()) return true;
        String host = hostOf(url);
        for (String connect : connects) {
            if (TextUtils.isEmpty(connect)) continue;
            String c = connect.trim().toLowerCase(Locale.US);
            if ("*".equals(c)) return true;
            if (host.equals(c) || host.endsWith("." + c)) return true;
        }
        return false;
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

    private static boolean isLocalOrPrivateHost(String url) {
        String host = hostOf(url);
        if (TextUtils.isEmpty(host)) return true;
        return "localhost".equals(host)
                || host.startsWith("127.")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")
                || host.startsWith("0.");
    }
}
