package com.chat.deepseek;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DeepSeekPromptBuilder {
    private DeepSeekPromptBuilder() {}

    static String build(Context context, DeepSeekRequest request, DeepSeekMessageLoader.Result result) {
        String base = DeepSeekSkillLoader.read(context, "prompts/base_prompt.txt");
        String task;
        if (request.action == DeepSeekRequest.ACTION_TRANSLATE) {
            task = DeepSeekSkillLoader.read(context, "prompts/translate_prompt.txt");
        } else if (request.action == DeepSeekRequest.ACTION_POLISH) {
            task = DeepSeekSkillLoader.read(context, "prompts/polish_prompt.txt");
        } else {
            task = DeepSeekSkillLoader.read(context, "prompts/reply_prompt.txt");
        }
        String references = DeepSeekSkillRouter.loadReferences(context, request);
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        DeepSeekContactProfile profile = request.contactProfile == null
                ? new DeepSeekContactProfile() : request.contactProfile;
        String prompt = task
                .replace("{MY_NATIVE}", request.safeMyNative())
                .replace("{PEER_NATIVE}", request.safePeerNative())
                .replace("{MY_LEARNING}", safe(request.myLearningLanguages, "未提供"))
                .replace("{PEER_LEARNING}", safe(request.peerLearningLanguages, "未提供"))
                .replace("{CURRENT_TIME}", currentTime)
                .replace("{BACKGROUND}", safe(request.background, "未提供，由聊天记录谨慎判断"))
                .replace("{PURPOSE}", safe(request.purpose, "自然继续聊天"))
                .replace("{RELATIONSHIP_STAGE}", relationshipLabel(request.relationshipStage))
                .replace("{PREFERRED_STYLE}", styleLabel(request.preferredStyle))
                .replace("{FLIRT_LEVEL}", flirtLabel(request.flirtLevel))
                .replace("{INTERACTION_STATE}", safe(DeepSeekProfileParser.stateLabel(profile.interactionState), "信息不足"))
                .replace("{TREND}", safe(DeepSeekProfileParser.trendLabel(profile.trend), "未知"))
                .replace("{PROFILE_SUMMARY}", safe(profile.conversationSummary, "未记录"))
                .replace("{KNOWN_FACTS}", profile.knownFactsText())
                .replace("{SENSITIVE_TOPICS}", profile.sensitiveTopicsText())
                .replace("{CONTEXT_STATUS}", request.contextEnabled ? "已开启" : "已关闭，仅使用当前目标消息或草稿")
                .replace("{MESSAGES}", safe(result.formattedMessages, "无"))
                .replace("{TARGET_MESSAGE}", safe(result.targetMessage, "无"))
                .replace("{TARGET_MESSAGE_ID}", safe(result.targetMessageId, ""))
                .replace("{DRAFT}", safe(request.draft, "无"));

        StringBuilder output = new StringBuilder();
        if (!base.isEmpty()) output.append(base.trim());
        if (!references.isEmpty()) output.append("\n\n").append(references.trim());
        if (!task.isEmpty()) output.append("\n\n").append(prompt.trim());
        return output.toString();
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static String relationshipLabel(String value) {
        String label = DeepSeekProfileParser.relationshipLabel(value);
        return label.isEmpty() ? "自动判断" : label;
    }

    private static String styleLabel(String value) {
        if ("short".equals(value)) return "简短";
        if ("warm".equals(value)) return "温暖";
        if ("light".equals(value)) return "轻松";
        if ("humorous".equals(value)) return "幽默";
        if ("direct".equals(value)) return "直接";
        if ("formal".equals(value)) return "正式";
        return "自然";
    }

    private static String flirtLabel(int value) {
        if (value >= 2) return "明显，但必须符合关系阶段并尊重边界";
        if (value == 1) return "轻微，仅在双方已有积极互动时使用";
        return "关闭";
    }
}
