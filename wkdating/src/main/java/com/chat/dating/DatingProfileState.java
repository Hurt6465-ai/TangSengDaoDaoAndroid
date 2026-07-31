package com.chat.dating;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;

/** 仅保存客户端交互状态；后端仍是交友展示状态的最终权威。 */
public final class DatingProfileState {
    private static final String SP = "wkdating_profile_state";
    private static final String KEY_USER_PAUSED_PREFIX = "user_paused_";

    private DatingProfileState() {}

    public static boolean isUserPaused(Context context) {
        if (context == null) return false;
        return preferences(context).getBoolean(KEY_USER_PAUSED_PREFIX + safeUid(), false);
    }

    public static void setUserPaused(Context context, boolean paused) {
        if (context == null) return;
        preferences(context).edit().putBoolean(KEY_USER_PAUSED_PREFIX + safeUid(), paused).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    private static String safeUid() {
        String uid = WKConfig.getInstance().getUid();
        return TextUtils.isEmpty(uid) ? "anonymous" : uid;
    }
}
