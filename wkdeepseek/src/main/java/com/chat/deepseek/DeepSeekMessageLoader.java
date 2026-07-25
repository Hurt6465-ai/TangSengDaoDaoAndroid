package com.chat.deepseek;

import android.text.TextUtils;

/**
 * Builds a prompt from the read-only snapshot supplied by the chat UI.
 *
 * Normal operation deliberately does not impose a local message-count or character limit. The
 * same DeepSeek web conversation keeps earlier context and DeepSeekConversationStore normally
 * sends only new Talkami lines. A reduced snapshot is used only after the webpage explicitly
 * reports that the input/context is too long.
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

        Result(String formattedMessages, String targetMessage, String targetMessageId,
               int messageCount) {
            this.formattedMessages = formattedMessages == null ? "" : formattedMessages;
            this.targetMessage = targetMessage == null ? "" : targetMessage;
            this.targetMessageId = targetMessageId == null ? "" : targetMessageId;
            this.messageCount = Math.max(0, messageCount);
        }
    }

    private DeepSeekMessageLoader() {}

    static void load(DeepSeekRequest request, Callback callback) {
        if (request == null || callback == null) return;

        String target = sanitize(request.targetMessageText);
        String targetId = safe(request.targetMessageId);
        String messages = "";
        int count = 0;

        if (request.contextEnabled && !TextUtils.isEmpty(request.contextSnapshot)) {
            messages = request.contextSnapshot.trim();
            count = countLines(messages);
        } else if (request.action == DeepSeekRequest.ACTION_REPLY && !TextUtils.isEmpty(target)) {
            messages = "[当前消息] 对方：" + target;
            count = 1;
        }

        if (!TextUtils.isEmpty(target) && count == 0) count = 1;
        callback.onResult(new Result(messages, target, targetId, count));
    }

    private static int countLines(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        int count = 0;
        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            if (!TextUtils.isEmpty(line == null ? "" : line.trim())) count++;
        }
        return count;
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\u0000', ' ').replace("```", "` ` `").trim();
        clean = clean.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[邮箱已隐藏]");
        clean = clean.replaceAll("(?<!\\d)(?:\\+?\\d[\\d\\s-]{6,}\\d)(?!\\d)", "[号码已隐藏]");
        clean = clean.replaceAll("(?<!\\d)\\d{17}[0-9Xx](?!\\d)", "[证件号已隐藏]");
        return clean;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
