package com.chat.deepseek;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class DeepSeekContactProfile {
    public String relationshipStage = "auto";
    public String interactionState = "uncertain";
    public String trend = "unknown";
    public String confidence = "low";
    public String preferredStyle = "natural";
    public int flirtLevel = 0;
    public String purpose = "自然继续聊天";
    public String background = "";
    public String conversationSummary = "";
    public final List<String> knownFacts = new ArrayList<>();
    public final List<String> sensitiveTopics = new ArrayList<>();
    public final List<DeepSeekKeyEvent> keyEvents = new ArrayList<>();
    public String lastAnalyzedMessageId = "";
    public long lastUpdatedAt = 0L;

    public static DeepSeekContactProfile fromJson(String raw) {
        DeepSeekContactProfile profile = new DeepSeekContactProfile();
        if (TextUtils.isEmpty(raw)) return profile;
        try {
            JSONObject object = new JSONObject(raw);
            profile.relationshipStage = allowed(object.optString("relationship_stage", "auto"),
                    new String[]{"auto", "new_contact", "language_partner", "friend", "dating", "ambiguous", "relationship", "formal"}, "auto");
            profile.interactionState = allowed(object.optString("interaction_state", "uncertain"),
                    new String[]{"warm", "neutral", "cool", "boundary", "uncertain"}, "uncertain");
            profile.trend = allowed(object.optString("trend", "unknown"),
                    new String[]{"up", "stable", "down", "unknown"}, "unknown");
            profile.confidence = allowed(object.optString("confidence", "low"),
                    new String[]{"low", "medium", "high"}, "low");
            profile.preferredStyle = allowed(object.optString("preferred_style", "natural"),
                    new String[]{"natural", "short", "warm", "light", "humorous", "direct", "formal"}, "natural");
            profile.flirtLevel = clamp(object.optInt("flirt_level", 0), 0, 2);
            profile.purpose = limit(object.optString("purpose", "自然继续聊天"), 200);
            profile.background = limit(object.optString("background", ""), 1000);
            profile.conversationSummary = limit(object.optString("conversation_summary", ""), 1200);
            readStrings(object.optJSONArray("known_facts"), profile.knownFacts, 30, 160);
            readStrings(object.optJSONArray("sensitive_topics"), profile.sensitiveTopics, 20, 120);
            JSONArray events = object.optJSONArray("key_events");
            if (events != null) {
                for (int i = 0; i < events.length() && profile.keyEvents.size() < 30; i++) {
                    JSONObject event = events.optJSONObject(i);
                    if (event == null) continue;
                    String text = limit(event.optString("event", ""), 240);
                    if (TextUtils.isEmpty(text)) continue;
                    profile.keyEvents.add(new DeepSeekKeyEvent(event.optLong("time", 0L), text));
                }
            }
            profile.lastAnalyzedMessageId = limit(object.optString("last_analyzed_message_id", ""), 160);
            profile.lastUpdatedAt = object.optLong("last_updated_at", 0L);
        } catch (Exception ignored) {
        }
        return profile;
    }

    public String toJson() {
        try {
            JSONObject object = new JSONObject();
            object.put("relationship_stage", relationshipStage);
            object.put("interaction_state", interactionState);
            object.put("trend", trend);
            object.put("confidence", confidence);
            object.put("preferred_style", preferredStyle);
            object.put("flirt_level", flirtLevel);
            object.put("purpose", limit(purpose, 200));
            object.put("background", limit(background, 1000));
            object.put("conversation_summary", limit(conversationSummary, 1200));
            object.put("known_facts", new JSONArray(knownFacts));
            object.put("sensitive_topics", new JSONArray(sensitiveTopics));
            JSONArray events = new JSONArray();
            int start = Math.max(0, keyEvents.size() - 30);
            for (int i = start; i < keyEvents.size(); i++) {
                DeepSeekKeyEvent item = keyEvents.get(i);
                if (item == null || TextUtils.isEmpty(item.event)) continue;
                JSONObject event = new JSONObject();
                event.put("time", item.time);
                event.put("event", limit(item.event, 240));
                events.put(event);
            }
            object.put("key_events", events);
            object.put("last_analyzed_message_id", limit(lastAnalyzedMessageId, 160));
            object.put("last_updated_at", lastUpdatedAt);
            return object.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    public String knownFactsText() {
        return join(knownFacts, "；", "未记录");
    }

    public String sensitiveTopicsText() {
        return join(sensitiveTopics, "；", "未记录");
    }

    private static void readStrings(JSONArray array, List<String> output, int maxCount, int maxLength) {
        if (array == null) return;
        for (int i = 0; i < array.length() && output.size() < maxCount; i++) {
            String item = limit(array.optString(i, ""), maxLength);
            if (!TextUtils.isEmpty(item) && !output.contains(item)) output.add(item);
        }
    }

    static String allowed(String value, String[] allowed, String fallback) {
        if (value != null) {
            for (String item : allowed) {
                if (item.equals(value)) return item;
            }
        }
        return fallback;
    }

    static String limit(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String join(List<String> values, String separator, String fallback) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (TextUtils.isEmpty(value)) continue;
                if (builder.length() > 0) builder.append(separator);
                builder.append(value.trim());
            }
        }
        return builder.length() == 0 ? fallback : builder.toString();
    }
}
