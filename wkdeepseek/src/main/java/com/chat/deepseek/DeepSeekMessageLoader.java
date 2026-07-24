package com.chat.deepseek;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.interfaces.IGetOrSyncHistoryMsgBack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    private DeepSeekMessageLoader() {}

    static void load(DeepSeekRequest request, Callback callback) {
        if (request == null || callback == null) return;
        if (!request.contextEnabled && request.action == DeepSeekRequest.ACTION_POLISH) {
            callback.onResult(new Result("", "", "", 0));
            return;
        }
        if (!request.contextEnabled && !TextUtils.isEmpty(request.targetMessageText)) {
            callback.onResult(new Result("", sanitize(request.targetMessageText), safeId(request), 1));
            return;
        }
        int limit;
        if (!request.contextEnabled) {
            limit = 20;
        } else if (request.action == DeepSeekRequest.ACTION_REPLY) {
            limit = normalizeLimit(request.contextLimit);
        } else if (request.action == DeepSeekRequest.ACTION_TRANSLATE) {
            limit = Math.min(50, normalizeLimit(request.contextLimit));
        } else {
            limit = Math.min(50, normalizeLimit(request.contextLimit));
        }
        WKIM.getInstance().getMsgManager().getOrSyncHistoryMessages(
                request.channelId,
                request.channelType,
                0,
                false,
                1,
                limit,
                0,
                new IGetOrSyncHistoryMsgBack() {
                    @Override
                    public void onSyncing() {
                    }

                    @Override
                    public void onResult(List<WKMsg> list) {
                        callback.onResult(format(request, list));
                    }
                }
        );
    }

    private static Result format(DeepSeekRequest request, List<WKMsg> source) {
        List<WKMsg> list = source == null ? new ArrayList<>() : new ArrayList<>(source);
        list.removeIf(msg -> msg == null || msg.isDeleted == 1 || isRevoked(msg));
        Collections.sort(list, Comparator.comparingLong(DeepSeekMessageLoader::orderValue));

        int maxChars = request.action == DeepSeekRequest.ACTION_REPLY
                ? MAX_CHARS_REPLY
                : (request.action == DeepSeekRequest.ACTION_TRANSLATE ? MAX_CHARS_TRANSLATE : MAX_CHARS_POLISH);
        ArrayList<String> lines = new ArrayList<>();
        String target = sanitize(request.targetMessageText);
        String targetId = safeId(request);
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        for (WKMsg msg : list) {
            String content = content(msg);
            if (TextUtils.isEmpty(content)) continue;
            String sanitized = sanitize(content);
            String who = TextUtils.equals(request.selfUid, msg.fromUID) ? "我" : "对方";
            long timestamp = msg.timestamp;
            if (timestamp > 0 && timestamp < 100000000000L) timestamp *= 1000L;
            String time = timestamp > 0 ? format.format(new Date(timestamp)) : "时间未知";
            String line = "[" + time + "] " + who + "：" + sanitized;
            lines.add(line);

            String id = messageId(msg);
            if (!TextUtils.isEmpty(request.targetMessageId) && TextUtils.equals(request.targetMessageId, id)) {
                target = sanitized;
                targetId = id;
            } else if (TextUtils.isEmpty(request.targetMessageText) && "对方".equals(who)) {
                target = sanitized;
                targetId = id;
            }
        }

        StringBuilder out = new StringBuilder();
        if (request.contextEnabled) {
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (out.length() + line.length() + 1 > maxChars && out.length() > 0) break;
                if (out.length() == 0) out.insert(0, line);
                else out.insert(0, line + "\n");
            }
        } else if (request.action == DeepSeekRequest.ACTION_REPLY && !TextUtils.isEmpty(target)) {
            out.append("[当前消息] 对方：").append(target);
        }
        int kept = out.length() == 0 ? 0 : out.toString().split("\n", -1).length;
        if (!TextUtils.isEmpty(target) && kept == 0) kept = 1;
        return new Result(out.toString(), target, targetId, kept);
    }

    private static boolean isRevoked(WKMsg msg) {
        try {
            return msg.remoteExtra != null && (msg.remoteExtra.revoke == 1 || msg.remoteExtra.isMutualDeleted == 1);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long orderValue(WKMsg msg) {
        try {
            if (msg.orderSeq > 0) return msg.orderSeq;
        } catch (Exception ignored) {
        }
        try {
            if (msg.messageSeq > 0) return msg.messageSeq;
        } catch (Exception ignored) {
        }
        return msg.timestamp;
    }

    private static String content(WKMsg msg) {
        String display = "";
        try {
            if (msg.baseContentMsgModel != null) display = msg.baseContentMsgModel.getDisplayContent();
        } catch (Exception ignored) {
        }
        if (TextUtils.isEmpty(display)) display = msg.content;
        if (TextUtils.isEmpty(display)) return "";
        String value = display.trim();
        if ((value.startsWith("{") && value.endsWith("}")) || value.length() > 4000) return "";
        if (value.startsWith("__cp_harmony_rtc__:")) return "";
        return value;
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value
                .replace('\u0000', ' ')
                .replace("```", "` ` `")
                .trim();
        return clean.length() <= MAX_SINGLE_MESSAGE_CHARS
                ? clean
                : clean.substring(0, MAX_SINGLE_MESSAGE_CHARS) + "…";
    }

    private static String safeId(DeepSeekRequest request) {
        return request == null || request.targetMessageId == null ? "" : request.targetMessageId.trim();
    }

    private static String messageId(WKMsg msg) {
        if (msg == null) return "";
        if (!TextUtils.isEmpty(msg.messageID) && !"0".equals(msg.messageID)) return msg.messageID;
        if (!TextUtils.isEmpty(msg.clientMsgNO)) return msg.clientMsgNO;
        if (msg.messageSeq > 0) return String.valueOf(msg.messageSeq);
        if (msg.orderSeq > 0) return String.valueOf(msg.orderSeq);
        return "";
    }

    private static int normalizeLimit(int value) {
        if (value <= 0) return 100;
        return Math.max(20, Math.min(120, value));
    }
}
