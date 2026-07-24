package com.chat.deepseek;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class DeepSeekProfileParser {
    static final class Update {
        String interactionState = "";
        String trend = "";
        String confidence = "";
        String relationshipStage = "";
        String summaryUpdate = "";
        String keyEvent = "";
        String lastAnalyzedMessageId = "";
        final List<String> knownFactsAdd = new ArrayList<>();
        final List<String> sensitiveTopicsAdd = new ArrayList<>();

        boolean isEmpty() {
            return TextUtils.isEmpty(interactionState)
                    && TextUtils.isEmpty(trend)
                    && TextUtils.isEmpty(confidence)
                    && TextUtils.isEmpty(relationshipStage)
                    && TextUtils.isEmpty(summaryUpdate)
                    && TextUtils.isEmpty(keyEvent)
                    && knownFactsAdd.isEmpty()
                    && sensitiveTopicsAdd.isEmpty();
        }

        void applyTo(DeepSeekContactProfile profile) {
            if (!TextUtils.isEmpty(interactionState)) profile.interactionState = interactionState;
            if (!TextUtils.isEmpty(trend)) profile.trend = trend;
            if (!TextUtils.isEmpty(confidence)) profile.confidence = confidence;
            if (!TextUtils.isEmpty(relationshipStage) && "auto".equals(profile.relationshipStage)) {
                profile.relationshipStage = relationshipStage;
            }
            if (!TextUtils.isEmpty(summaryUpdate)) profile.conversationSummary = summaryUpdate;
            appendUnique(profile.knownFacts, knownFactsAdd, 30);
            appendUnique(profile.sensitiveTopics, sensitiveTopicsAdd, 20);
            if (!TextUtils.isEmpty(keyEvent)) {
                profile.keyEvents.add(new DeepSeekKeyEvent(System.currentTimeMillis(), keyEvent));
                while (profile.keyEvents.size() > 30) profile.keyEvents.remove(0);
            }
            if (!TextUtils.isEmpty(lastAnalyzedMessageId)) {
                profile.lastAnalyzedMessageId = lastAnalyzedMessageId;
            }
            profile.lastUpdatedAt = System.currentTimeMillis();
        }
    }

    private DeepSeekProfileParser() {}

    static Update parse(String raw) {
        if (TextUtils.isEmpty(raw) || raw.length() > 12000) return null;
        try {
            JSONObject object = new JSONObject(raw.trim());
            Update update = new Update();
            update.interactionState = allowedOrEmpty(object.optString("interaction_state", ""),
                    new String[]{"warm", "neutral", "cool", "boundary", "uncertain"});
            update.trend = allowedOrEmpty(object.optString("trend", ""),
                    new String[]{"up", "stable", "down", "unknown"});
            update.confidence = allowedOrEmpty(object.optString("confidence", ""),
                    new String[]{"low", "medium", "high"});
            update.relationshipStage = allowedOrEmpty(object.optString("relationship_stage", ""),
                    new String[]{"auto", "new_contact", "language_partner", "friend", "dating", "ambiguous", "relationship", "formal"});
            update.summaryUpdate = DeepSeekContactProfile.limit(object.optString("summary_update", ""), 1200);
            update.keyEvent = DeepSeekContactProfile.limit(object.optString("key_event", ""), 240);
            update.lastAnalyzedMessageId = DeepSeekContactProfile.limit(object.optString("last_analyzed_message_id", ""), 160);
            readArray(object.optJSONArray("known_facts_add"), update.knownFactsAdd, 10, 160);
            readArray(object.optJSONArray("sensitive_topics_add"), update.sensitiveTopicsAdd, 8, 120);
            return update.isEmpty() ? null : update;
        } catch (Exception ignored) {
            return null;
        }
    }

    static String describe(Update update) {
        if (update == null) return "";
        StringBuilder out = new StringBuilder();
        add(out, "互动状态", stateLabel(update.interactionState));
        add(out, "近期趋势", trendLabel(update.trend));
        add(out, "判断可信度", confidenceLabel(update.confidence));
        add(out, "关系阶段", relationshipLabel(update.relationshipStage));
        add(out, "摘要", update.summaryUpdate);
        if (!update.knownFactsAdd.isEmpty()) add(out, "新增信息", String.join("；", update.knownFactsAdd));
        if (!update.sensitiveTopicsAdd.isEmpty()) add(out, "谨慎话题", String.join("；", update.sensitiveTopicsAdd));
        add(out, "重要事件", update.keyEvent);
        return out.toString();
    }

    static String stateLabel(String value) {
        if ("warm".equals(value)) return "热络";
        if ("neutral".equals(value)) return "普通";
        if ("cool".equals(value)) return "降温";
        if ("boundary".equals(value)) return "明确边界";
        if ("uncertain".equals(value)) return "信息不足";
        return "";
    }

    static String trendLabel(String value) {
        if ("up".equals(value)) return "升温";
        if ("stable".equals(value)) return "稳定";
        if ("down".equals(value)) return "降温";
        if ("unknown".equals(value)) return "未知";
        return "";
    }

    static String confidenceLabel(String value) {
        if ("low".equals(value)) return "低";
        if ("medium".equals(value)) return "中";
        if ("high".equals(value)) return "高";
        return "";
    }

    static String relationshipLabel(String value) {
        if ("auto".equals(value)) return "自动判断";
        if ("new_contact".equals(value)) return "刚认识";
        if ("language_partner".equals(value)) return "语伴";
        if ("friend".equals(value)) return "普通朋友";
        if ("dating".equals(value)) return "相亲或约会";
        if ("ambiguous".equals(value)) return "暧昧中";
        if ("relationship".equals(value)) return "恋人";
        if ("formal".equals(value)) return "同事或正式关系";
        return "";
    }

    private static void readArray(JSONArray array, List<String> output, int maxCount, int maxLength) {
        if (array == null) return;
        for (int i = 0; i < array.length() && output.size() < maxCount; i++) {
            String value = DeepSeekContactProfile.limit(array.optString(i, ""), maxLength);
            if (!TextUtils.isEmpty(value) && !output.contains(value)) output.add(value);
        }
    }

    private static String allowedOrEmpty(String value, String[] allowed) {
        if (TextUtils.isEmpty(value)) return "";
        for (String item : allowed) if (item.equals(value)) return item;
        return "";
    }

    private static void appendUnique(List<String> target, List<String> values, int maxCount) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !target.contains(value)) target.add(value);
        }
        while (target.size() > maxCount) target.remove(0);
    }

    private static void add(StringBuilder builder, String title, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (builder.length() > 0) builder.append('\n');
        builder.append(title).append("：").append(value);
    }
}
