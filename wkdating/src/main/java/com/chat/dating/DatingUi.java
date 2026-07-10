package com.chat.dating;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;

import com.chat.dating.model.DatingProfile;
import com.chat.uikit.chat.ChatActivity;

import java.util.Locale;

public final class DatingUi {
    private DatingUi() {}

    public static void applyDarkSystemBars(Activity activity, int backgroundColor) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(backgroundColor);
            window.setNavigationBarColor(backgroundColor);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (Color.red(backgroundColor) + Color.green(backgroundColor) + Color.blue(backgroundColor) > 500) {
                flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    public static void applyFullscreen(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.BLACK);
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public static void openChat(Activity activity, String uid) {
        if (activity == null || TextUtils.isEmpty(uid)) return;
        Intent intent = new Intent(activity, ChatActivity.class);
        intent.putExtra("channelId", uid);
        intent.putExtra("channelType", (byte) 1); // WKChannelType.PERSONAL
        activity.startActivity(intent);
    }

    public static String flagEmoji(String countryCode) {
        if (TextUtils.isEmpty(countryCode) || countryCode.trim().length() < 2) return "";
        String code = countryCode.trim().toUpperCase(Locale.US);
        int first = Character.codePointAt(code, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(code, 1) - 'A' + 0x1F1E6;
        if (first < 0x1F1E6 || first > 0x1F1FF || second < 0x1F1E6 || second > 0x1F1FF) return "";
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    public static String nameAgeFlag(DatingProfile profile) {
        if (profile == null) return "";
        StringBuilder line = new StringBuilder(profile.safeName());
        if (profile.age > 0) line.append(" ").append(profile.age);
        String flag = flagEmoji(profile.safeCountryCode());
        if (!TextUtils.isEmpty(flag)) line.append(" ").append(flag);
        return line.toString();
    }

    public static String loveExpectation(DatingProfile profile) {
        if (profile == null) return "";
        StringBuilder out = new StringBuilder();
        if (!TextUtils.isEmpty(profile.safeRelationshipGoal())) out.append(profile.safeRelationshipGoal());
        if (!TextUtils.isEmpty(profile.safeCrossBorderPreference())) {
            if (out.length() > 0) out.append(" · ");
            out.append(profile.safeCrossBorderPreference());
        }
        if (!TextUtils.isEmpty(profile.relationship_status)) {
            if (out.length() > 0) out.append(" · ");
            out.append(profile.relationship_status);
        }
        return out.toString();
    }

    public static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
