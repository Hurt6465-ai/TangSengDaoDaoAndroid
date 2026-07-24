package com.chat.deepseek;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the prompt context from a read-only snapshot supplied by ChatActivity.
 *
 * Never call WuKongIM getOrSyncHistoryMessages() here. That method is a synchronization
 * operation, not a passive query: it can emit refresh events to the open ChatActivity and
 * cause items to be removed from its adapter while the DeepSeek dialog is visible.
 */
final class DeepSeekMessageLoader {
    interface Callback {
        void onResult(Result result);
    }

    static final class Result {
        final String formattedMessages;
        final String targetMessage;
        final String targetMessageId;
        final int messageCount;

        Result(String formattedMessages, String targetMessage, String targetMessageId, int messageCount) {
            this.formattedMessages = formattedMessages;
            this.targetMessage = targetMessage;
            this.targetMessageId = targetMessageId;
            this.messageCount = messageCount;
        }
    }

    private static final int MAX_CHARS_REPLY = 18000;
    private static final int MAX_CHARS_TRANSLATE = 10000;
    private static final int MAX_CHARS_POLISH = 9000;
    private static final int MAX_SINGLE_MESSAGE_CHARS = 1200;

    private DeepSeekMessageLoader() {
    }

    static void load(DeepSeekRequest request, Callback callback) {
        if (request == null || callback == null) return;

        String target = sanitize(request.targetMessageText);
        String targetId = safe(request.targetMessageId);
        String messages = "";
        int count = 0;

        if (request.contextEnabled && !TextUtils.isEmpty(request.contextSnapshot)) {
            Snapshot snapshot = trimSnapshot(
                    request.contextSnapshot,
                    normalizeLimit(request.contextLimit),
                    maxChars(request.action));
            messages = snapshot.text;
            count = snapshot.count;
        } else if (request.action == DeepSeekRequest.ACTION_REPLY && !TextUtils.isEmpty(target)) {
            messages = "[当前消息] 对方：" + target;
            count = 1;
        }

        // Bubble translation already supplies the exact target message. Returning it directly
        // avoids a second query against the IM database and keeps translation side-effect free.
        if (!TextUtils.isEmpty(target) && count == 0) count = 1;
        callback.onResult(new Result(messages, target, targetId, count));
    }

    private static Snapshot trimSnapshot(String raw, int maxLines, int maxChars) {
        if (TextUtils.isEmpty(raw)) return new Snapshot("", 0);
        String[] source = raw.split("\\n");
        List<String> kept = new ArrayList<>();
        int chars = 0;
        for (int i = source.length - 1; i >= 0 && kept.size() < maxLines; i--) {
            String line = source[i] == null ? "" : source[i].trim();
            if (TextUtils.isEmpty(line)) continue;
            int next = chars + line.length() + (kept.isEmpty() ? 0 : 1);
            if (next > maxChars && !kept.isEmpty()) break;
            if (line.length() > maxChars && kept.isEmpty()) {
                line = line.substring(Math.max(0, line.length() - maxChars));
            }
            kept.add(0, line);
            chars += line.length() + (kept.size() > 1 ? 1 : 0);
        }
        return new Snapshot(TextUtils.join("\n", kept), kept.size());
    }

    private static int maxChars(int action) {
        if (action == DeepSeekRequest.ACTION_TRANSLATE) return MAX_CHARS_TRANSLATE;
        if (action == DeepSeekRequest.ACTION_POLISH) return MAX_CHARS_POLISH;
        return MAX_CHARS_REPLY;
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\u0000', ' ').replace("```", "` ` `").trim();
        return clean.length() <= MAX_SINGLE_MESSAGE_CHARS
                ? clean
                : clean.substring(0, MAX_SINGLE_MESSAGE_CHARS) + "…";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizeLimit(int value) {
        if (value <= 0) return 100;
        return Math.max(20, Math.min(120, value));
    }

    private static final class Snapshot {
        final String text;
        final int count;

        Snapshot(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }
}
