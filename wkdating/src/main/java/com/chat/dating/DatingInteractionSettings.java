package com.chat.dating;

import android.content.Context;
import android.content.SharedPreferences;

/** 用户可关闭滑卡短音效或触感反馈。 */
public final class DatingInteractionSettings {
    private static final String SP = "wkdating_interaction_settings";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_HAPTIC = "haptic_enabled";

    private DatingInteractionSettings() {}

    public static boolean soundEnabled(Context context) {
        return preferences(context).getBoolean(KEY_SOUND, true);
    }

    public static boolean hapticEnabled(Context context) {
        return preferences(context).getBoolean(KEY_HAPTIC, true);
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    public static void setHapticEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_HAPTIC, enabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
    }
}
