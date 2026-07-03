package com.chat.userscript;

import android.text.TextUtils;

import com.chat.userscript.model.UserScript;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UserScriptMatcher {
    private UserScriptMatcher() {
    }

    public static boolean matches(UserScript script, String url) {
        if (script == null || !script.enabled || TextUtils.isEmpty(url)) return false;
        if (!AiWebPolicy.isScriptHostAllowed(url)) return false;
        if (matchesAny(script.excludes, url)) return false;
        if (matchesAny(script.matches, url)) return true;
        return matchesAny(script.includes, url);
    }

    private static boolean matchesAny(List<String> patterns, String url) {
        if (patterns == null || patterns.isEmpty()) return false;
        for (String pattern : patterns) {
            if (wildcardMatch(pattern, url)) return true;
        }
        return false;
    }

    private static boolean wildcardMatch(String pattern, String url) {
        if (TextUtils.isEmpty(pattern) || TextUtils.isEmpty(url)) return false;
        String p = pattern.trim();
        if ("<all_urls>".equals(p)) return AiWebPolicy.isScriptHostAllowed(url);
        String lower = p.toLowerCase(Locale.US);
        if (lower.startsWith("http://")) return false;
        try {
            return Pattern.compile(wildcardToRegex(p), Pattern.CASE_INSENSITIVE).matcher(url).matches();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String wildcardToRegex(String wildcard) {
        StringBuilder out = new StringBuilder("^");
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            if (c == '*') {
                out.append(".*");
            } else if (".[]{}()+-^$?|\\".indexOf(c) >= 0) {
                out.append('\\').append(c);
            } else {
                out.append(c);
            }
        }
        out.append('$');
        return out.toString();
    }
}
