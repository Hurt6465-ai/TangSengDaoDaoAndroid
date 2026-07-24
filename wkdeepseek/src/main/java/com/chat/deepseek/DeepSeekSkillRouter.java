package com.chat.deepseek;

import android.content.Context;
import android.text.TextUtils;

final class DeepSeekSkillRouter {
    private DeepSeekSkillRouter() {}

    static String loadReferences(Context context, DeepSeekRequest request) {
        StringBuilder out = new StringBuilder();
        if (request.action == DeepSeekRequest.ACTION_TRANSLATE) {
            add(out, DeepSeekSkillLoader.read(context, "social_skill/cross_culture.md"));
            return out.toString();
        }
        if (request.action == DeepSeekRequest.ACTION_POLISH) {
            if (request.flirtLevel > 0) add(out, DeepSeekSkillLoader.read(context, "social_skill/boundaries.md"));
            return out.toString();
        }

        add(out, DeepSeekSkillLoader.read(context, "social_skill/chat_analysis.md"));
        add(out, DeepSeekSkillLoader.read(context, "social_skill/boundaries.md"));
        add(out, DeepSeekSkillLoader.read(context, "social_skill/profile_update.md"));

        if (isRelationshipAnalysisUseful(request.relationshipStage)) {
            add(out, DeepSeekSkillLoader.read(context, "social_skill/relationship_stage.md"));
            add(out, DeepSeekSkillLoader.read(context, "social_skill/signal_trends.md"));
        }
        if (isCrossLanguage(request)) {
            add(out, DeepSeekSkillLoader.read(context, "social_skill/cross_culture.md"));
        }
        return out.toString();
    }

    private static boolean isRelationshipAnalysisUseful(String stage) {
        return "auto".equals(stage)
                || "dating".equals(stage)
                || "ambiguous".equals(stage)
                || "relationship".equals(stage);
    }

    private static boolean isCrossLanguage(DeepSeekRequest request) {
        String mine = request.safeMyNative().trim();
        String peer = request.safePeerNative().trim();
        if (mine.startsWith("自动") || peer.startsWith("自动")) return true;
        return !TextUtils.equals(mine, peer);
    }

    private static void add(StringBuilder out, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (out.length() > 0) out.append("\n\n");
        out.append(value.trim());
    }
}
