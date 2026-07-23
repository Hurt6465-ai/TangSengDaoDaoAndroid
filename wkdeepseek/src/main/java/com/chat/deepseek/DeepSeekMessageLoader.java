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
        final int messageCount;

        Result(String formattedMessages, String targetMessage, int messageCount) {
            this.formattedMessages = formattedMessages;
            this.targetMessage = targetMessage;
            this.messageCount = messageCount;
        }
    }

    private static final int MAX_CHARS_REPLY = 14000;
    private static final int MAX_CHARS_TRANSLATE = 9000;

    private DeepSeekMessageLoader() {}

    static void load(DeepSeekRequest request, Callback callback) {
        int limit = request.action == DeepSeekRequest.ACTION_REPLY ? 100 : 50;
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
                        // The dialog already shows a loading state.
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

        int maxChars = request.action == DeepSeekRequest.ACTION_REPLY ? MAX_CHARS_REPLY : MAX_CHARS_TRANSLATE;
        ArrayList<String> lines = new ArrayList<>();
        String target = "";
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        for (WKMsg msg : list) {
            String content = content(msg);
            if (TextUtils.isEmpty(content)) continue;
            String who = TextUtils.equals(request.selfUid, msg.fromUID) ? "我" : "对方";
            long timestamp = msg.timestamp;
            if (timestamp > 0 && timestamp < 100000000000L) timestamp *= 1000L;
            String time = timestamp > 0 ? format.format(new Date(timestamp)) : "时间未知";
            String line = "[" + time + "] " + who + "：" + sanitize(content);
            lines.add(line);
            if ("对方".equals(who)) target = sanitize(content);
        }

        // Keep the newest messages when text exceeds the WebView-friendly budget.
        StringBuilder out = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (out.length() + line.length() + 1 > maxChars && out.length() > 0) break;
            if (out.length() == 0) out.insert(0, line);
            else out.insert(0, line + "\n");
        }
        int kept = out.length() == 0 ? 0 : out.toString().split("\n", -1).length;
        return new Result(out.toString(), target, kept);
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
        // Do not upload internal payload JSON as conversational context.
        if ((value.startsWith("{") && value.endsWith("}")) || value.length() > 4000) return "";
        return value;
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value
                .replace('\u0000', ' ')
                .replace("```", "` ` `")
                .trim();
    }
}
