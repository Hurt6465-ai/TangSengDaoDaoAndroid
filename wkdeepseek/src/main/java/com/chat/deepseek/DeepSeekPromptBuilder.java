package com.chat.deepseek;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DeepSeekPromptBuilder {
    private DeepSeekPromptBuilder() {}

    static String build(Context context, DeepSeekRequest request, DeepSeekMessageLoader.Result result) throws Exception {
        String base = read(context, "prompts/base_prompt.txt");
        String task;
        if (request.action == DeepSeekRequest.ACTION_TRANSLATE) {
            task = read(context, "prompts/translate_prompt.txt");
        } else if (request.action == DeepSeekRequest.ACTION_POLISH) {
            task = read(context, "prompts/polish_prompt.txt");
        } else {
            task = read(context, "prompts/reply_prompt.txt");
        }
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        String prompt = task
                .replace("{MY_NATIVE}", request.safeMyNative())
                .replace("{PEER_NATIVE}", request.safePeerNative())
                .replace("{MY_LEARNING}", safe(request.myLearningLanguages, "未提供"))
                .replace("{PEER_LEARNING}", safe(request.peerLearningLanguages, "未提供"))
                .replace("{CURRENT_TIME}", currentTime)
                .replace("{BACKGROUND}", safe(request.background, "未提供，由聊天记录判断"))
                .replace("{PURPOSE}", safe(request.purpose, "自然继续聊天"))
                .replace("{MESSAGES}", safe(result.formattedMessages, "无"))
                .replace("{TARGET_MESSAGE}", safe(result.targetMessage, "无"))
                .replace("{DRAFT}", safe(request.draft, "无"));
        return base + "\n\n" + prompt;
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static String read(Context context, String path) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
            }
        }
        return out.toString();
    }
}
