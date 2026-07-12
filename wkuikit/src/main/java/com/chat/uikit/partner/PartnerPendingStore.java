package com.chat.uikit.partner;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;

import org.json.JSONObject;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 语伴陌生会话的客户端状态镜像。
 *
 * 后端数据库始终是最终权威；本地状态只用于选择正确发送通道和及时更新输入区。
 * 数据按登录 UID 隔离，避免切换账号后串用 Pending 状态。
 */
public final class PartnerPendingStore {
    public interface Listener {
        void onPartnerPendingChanged(String peerUid);
    }

    public static final class Entry {
        public final boolean pending;
        public final boolean requester;
        public final int messageCount;
        public final int maxMessageCount;

        Entry(boolean pending, boolean requester, int messageCount, int maxMessageCount) {
            this.pending = pending;
            this.requester = requester;
            this.messageCount = Math.max(0, messageCount);
            this.maxMessageCount = Math.max(1, maxMessageCount);
        }

        public int remaining() {
            return Math.max(0, maxMessageCount - messageCount);
        }
    }

    private static final String PREF_PREFIX = "partner_pending_state_v2_";
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PartnerPendingStore() {}

    public static Entry get(String peerUid) {
        if (TextUtils.isEmpty(peerUid)) return null;
        Context context = appContext();
        String loginUid = WKConfig.getInstance().getUid();
        if (context == null || TextUtils.isEmpty(loginUid)) return null;
        try {
            String raw = prefs(context, loginUid).getString(peerUid, "");
            if (TextUtils.isEmpty(raw)) return null;
            JSONObject json = new JSONObject(raw);
            return new Entry(
                    json.optBoolean("pending", false),
                    json.optBoolean("requester", false),
                    json.optInt("message_count", 0),
                    json.optInt("max_message_count", 3)
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void markRequester(String peerUid, int messageCount, int maxMessageCount) {
        save(peerUid, true, true, messageCount, maxMessageCount);
    }

    public static void markReceiver(String peerUid, int messageCount, int maxMessageCount) {
        save(peerUid, true, false, messageCount, maxMessageCount);
    }

    public static void updateRequesterCount(String peerUid, int messageCount, int maxMessageCount) {
        Entry old = get(peerUid);
        if (old == null || !old.pending || !old.requester) {
            markRequester(peerUid, messageCount, maxMessageCount);
            return;
        }
        save(peerUid, true, true, messageCount, maxMessageCount);
    }

    public static void markActive(String peerUid) {
        save(peerUid, false, false, 0, 3);
    }

    public static void clearCurrentAccount() {
        Context context = appContext();
        String loginUid = WKConfig.getInstance().getUid();
        if (context == null || TextUtils.isEmpty(loginUid)) return;
        prefs(context, loginUid).edit().clear().apply();
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    private static void save(String peerUid, boolean pending, boolean requester, int messageCount, int maxMessageCount) {
        if (TextUtils.isEmpty(peerUid)) return;
        Context context = appContext();
        String loginUid = WKConfig.getInstance().getUid();
        if (context == null || TextUtils.isEmpty(loginUid)) return;
        try {
            JSONObject json = new JSONObject();
            json.put("pending", pending);
            json.put("requester", requester);
            json.put("message_count", Math.max(0, messageCount));
            json.put("max_message_count", Math.max(1, maxMessageCount));
            prefs(context, loginUid).edit().putString(peerUid, json.toString()).apply();
            notifyChanged(peerUid);
        } catch (Throwable ignored) {
        }
    }

    private static SharedPreferences prefs(Context context, String loginUid) {
        return context.getSharedPreferences(PREF_PREFIX + loginUid, Context.MODE_PRIVATE);
    }

    private static Context appContext() {
        try {
            return WKBaseApplication.getInstance().getContext();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void notifyChanged(String peerUid) {
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) {
                try {
                    listener.onPartnerPendingChanged(peerUid);
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
