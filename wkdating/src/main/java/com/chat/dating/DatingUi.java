package com.chat.dating;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

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
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    public static void applyFullscreen(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        window.getDecorView().setSystemUiVisibility(flags);
    }


    /** 普通页面统一转为 edge-to-edge，再用 WindowInsets 给根布局留出安全区。 */
    public static void applyPageInsets(Activity activity, View root) {
        if (activity == null || root == null) return;
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** 首页是沉浸式布局，顶部和底部区域按真实刘海/导航栏高度扩展，不再依赖固定 42dp。 */
    public static void applyHomeInsets(View root, View topBar, View actionBar) {
        if (root == null || topBar == null || actionBar == null) return;
        final int topBasePadding = topBar.getPaddingTop();
        final int bottomBasePadding = actionBar.getPaddingBottom();
        final int topBaseHeight = layoutHeight(topBar);
        final int bottomBaseHeight = layoutHeight(actionBar);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int topExtra = dp(topBar, 4);
            topBar.setPadding(topBar.getPaddingLeft(), topBasePadding + bars.top + topExtra,
                    topBar.getPaddingRight(), topBar.getPaddingBottom());
            actionBar.setPadding(actionBar.getPaddingLeft(), actionBar.getPaddingTop(),
                    actionBar.getPaddingRight(), bottomBasePadding + bars.bottom);
            setLayoutHeight(topBar, topBaseHeight + bars.top + topExtra);
            setLayoutHeight(actionBar, bottomBaseHeight + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static int layoutHeight(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params == null || params.height < 0 ? 0 : params.height;
    }

    private static void setLayoutHeight(View view, int height) {
        if (height <= 0 || view.getLayoutParams() == null || view.getLayoutParams().height == height) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);
    }

    private static int dp(View view, int value) {
        if (view == null || view.getResources() == null) return value;
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    public static void openChat(Activity activity, String uid) {
        if (activity == null || TextUtils.isEmpty(uid)) return;
        Intent intent = new Intent(activity, ChatActivity.class);
        intent.putExtra("channelId", uid);
        intent.putExtra("channelType", (byte) 1);
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
        return line.toString();
    }

    public static String loveExpectation(Context context, DatingProfile profile) {
        if (profile == null) return "";
        StringBuilder out = new StringBuilder();
        String goal = context == null
                ? profile.safeRelationshipGoal()
                : DatingIntent.displayLabel(context, profile.safeRelationshipGoal());
        if (!TextUtils.isEmpty(goal)) out.append(goal);
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

    /** 兼容旧调用；新界面应传入 Context 以显示本地化意向。 */
    public static String loveExpectation(DatingProfile profile) {
        return loveExpectation(null, profile);
    }

    public static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
