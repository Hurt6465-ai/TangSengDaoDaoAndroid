package com.chat.uikit;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.LayoutHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mikepenz.iconics.IconicsDrawable;

/** 给独立沉浸 Activity 复用主页底部导航，交友仍归属于“语伴”一级入口。 */
public final class GlobalBottomNavigationController {
    private static final int SELECTED = 0xFF1877F2;
    private static final int NORMAL = 0xFF65676B;

    private GlobalBottomNavigationController() {}

    public static void attach(Activity activity, BottomNavigationView navigation, int selectedMenuId) {
        if (activity == null || navigation == null) return;
        navigation.setItemIconTintList(null);
        navigation.setItemRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        try { navigation.setItemBackgroundResource(0); } catch (Throwable ignored) {}

        addItem(activity, navigation, R.id.i_study, "faw-graduation-cap", R.string.tab_text_study, selectedMenuId == R.id.i_study);
        addItem(activity, navigation, R.id.i_partner, "faw-user-friends", R.string.tab_text_partner, selectedMenuId == R.id.i_partner);
        addItem(activity, navigation, R.id.i_chat, "faw-comments", R.string.tab_text_chat, selectedMenuId == R.id.i_chat);
        addItem(activity, navigation, R.id.i_discover, "faw-compass", R.string.tab_text_discover, selectedMenuId == R.id.i_discover);
        addItem(activity, navigation, R.id.i_community, "faw-heart", R.string.tab_text_community, selectedMenuId == R.id.i_community);

        navigation.setSelectedItemId(selectedMenuId);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedMenuId) {
                if (id == R.id.i_partner && activity.getClass().getName().contains("Dating")) {
                    try {
                        Object handled = EndpointManager.getInstance().invoke("peipe_open_partner_list", activity);
                        if (handled instanceof Boolean && (Boolean) handled) {
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                    TabActivity.openFromChild(activity, R.id.i_partner);
                }
                return true;
            }
            navigation.setEnabled(false);
            boolean opened = TabActivity.openFromChild(activity, id);
            if (!opened) navigation.setEnabled(true);
            return opened;
        });
    }

    private static void addItem(Activity activity, BottomNavigationView navigation, int menuId,
                                String iconName, int textRes, boolean selected) {
        FrameLayout root = navigation.findViewById(menuId);
        if (root == null || root.getTag(R.id.i_chat) != null) return;
        root.setTag(R.id.i_chat, Boolean.TRUE);
        root.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) root.setForeground(null);

        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        IconicsDrawable drawable = new IconicsDrawable(activity, iconName);
        drawable.setColorList(ColorStateList.valueOf(selected ? SELECTED : NORMAL));
        drawable.setSizeXPx(dp(activity, 24));
        drawable.setSizeYPx(dp(activity, 24));
        drawable.setPaddingPx(dp(activity, 2));
        icon.setImageDrawable(drawable);

        TextView text = new TextView(activity);
        try {
            text.setTypeface(Typeface.createFromAsset(activity.getAssets(), "fonts/mw_bold.ttf"));
        } catch (Throwable ignored) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        text.setText(textRes);
        text.setTextColor(selected ? SELECTED : NORMAL);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);

        root.addView(icon, LayoutHelper.createFrame(34, 34, Gravity.CENTER | Gravity.TOP, 0, 5, 0, 0));
        root.addView(text, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 15, 0, 0));
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
