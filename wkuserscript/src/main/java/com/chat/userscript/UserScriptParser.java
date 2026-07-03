package com.chat.userscript;

import android.text.TextUtils;

import com.chat.userscript.model.UserScript;

import java.util.Locale;

public final class UserScriptParser {
    private UserScriptParser() {
    }

    public static UserScript parse(String rawCode) {
        UserScript script = new UserScript();
        script.code = rawCode == null ? "" : rawCode;
        String code = script.code;
        int start = code.indexOf("==UserScript==");
        int end = code.indexOf("==/UserScript==");
        if (start < 0 || end <= start) {
            fillDefaultScope(script);
            return script;
        }
        String meta = code.substring(start, end);
        String[] lines = meta.split("\\r?\\n");
        for (String line : lines) {
            parseMetaLine(script, line);
        }
        if (TextUtils.isEmpty(script.name)) script.name = "未命名脚本";
        if (TextUtils.isEmpty(script.runAt)) script.runAt = "document-end";
        normalizeRunAt(script);
        if (script.matches.isEmpty() && script.includes.isEmpty()) fillDefaultScope(script);
        if (script.grants.isEmpty()) script.grants.add("none");
        return script;
    }

    private static void parseMetaLine(UserScript script, String line) {
        if (line == null) return;
        String clean = line.trim();
        while (clean.startsWith("/")) clean = clean.substring(1).trim();
        if (!clean.startsWith("@")) return;
        int space = clean.indexOf(' ');
        int tab = clean.indexOf('\t');
        if (space < 0 || (tab >= 0 && tab < space)) space = tab;
        String key;
        String value;
        if (space > 0) {
            key = clean.substring(1, space).trim().toLowerCase(Locale.US);
            value = clean.substring(space + 1).trim();
        } else {
            key = clean.substring(1).trim().toLowerCase(Locale.US);
            value = "";
        }
        if (TextUtils.isEmpty(key)) return;
        switch (key) {
            case "name":
                script.name = value;
                break;
            case "description":
                script.description = value;
                break;
            case "version":
                script.version = value;
                break;
            case "author":
                script.author = value;
                break;
            case "namespace":
                script.namespace = value;
                break;
            case "match":
                add(script.matches, value);
                break;
            case "include":
                add(script.includes, value);
                break;
            case "exclude":
                add(script.excludes, value);
                break;
            case "run-at":
                script.runAt = value;
                break;
            case "grant":
                add(script.grants, value);
                break;
            case "noframes":
                script.noFrames = true;
                break;
            default:
                break;
        }
    }

    private static void normalizeRunAt(UserScript script) {
        String runAt = script.runAt == null ? "" : script.runAt.trim().toLowerCase(Locale.US);
        if ("document-start".equals(runAt) || "document-end".equals(runAt) || "document-idle".equals(runAt)) {
            script.runAt = runAt;
        } else {
            script.runAt = "document-end";
        }
    }

    private static void fillDefaultScope(UserScript script) {
        if (script.matches.isEmpty()) {
            script.matches.add("https://chat.deepseek.com/*");
            script.matches.add("https://chat.qwen.ai/*");
        }
        if (script.grants.isEmpty()) {
            script.grants.add("GM_getValue");
            script.grants.add("GM_setValue");
            script.grants.add("GM_addStyle");
        }
    }

    private static void add(java.util.List<String> list, String value) {
        if (list == null || TextUtils.isEmpty(value)) return;
        value = value.trim();
        if (value.length() > 0 && !list.contains(value)) list.add(value);
    }

    public static String defaultScriptTemplate() {
        return "// ==UserScript==\n" +
                "// @name         AI 网页测试脚本\n" +
                "// @version      1.0.0\n" +
                "// @description  在 DeepSeek / 千问网页右下角显示测试按钮\n" +
                "// @match        https://chat.deepseek.com/*\n" +
                "// @match        https://chat.qwen.ai/*\n" +
                "// @run-at       document-end\n" +
                "// @grant        GM_getValue\n" +
                "// @grant        GM_setValue\n" +
                "// @grant        GM_addStyle\n" +
                "// ==/UserScript==\n\n" +
                "GM_addStyle(`\n" +
                "  #tsdd-ai-script-test-btn {\n" +
                "    position: fixed; right: 16px; bottom: 92px; z-index: 2147483647;\n" +
                "    border: 0; border-radius: 999px; padding: 10px 14px;\n" +
                "    background: #1877F2; color: #fff; font-size: 14px; font-weight: 700;\n" +
                "    box-shadow: 0 8px 24px rgba(24,119,242,.25);\n" +
                "  }\n" +
                "`);\n\n" +
                "if (!document.getElementById('tsdd-ai-script-test-btn')) {\n" +
                "  const btn = document.createElement('button');\n" +
                "  btn.id = 'tsdd-ai-script-test-btn';\n" +
                "  btn.textContent = '唐僧脚本';\n" +
                "  btn.onclick = () => {\n" +
                "    const count = Number(GM_getValue('clickCount', 0)) + 1;\n" +
                "    GM_setValue('clickCount', count);\n" +
                "    alert('脚本正常运行：第 ' + count + ' 次点击');\n" +
                "  };\n" +
                "  document.body.appendChild(btn);\n" +
                "}\n";
    }
}
