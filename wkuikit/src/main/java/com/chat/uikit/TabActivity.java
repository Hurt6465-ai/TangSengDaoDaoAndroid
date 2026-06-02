package com.chat.uikit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.adapter.WKFragmentStateAdapter;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.CounterView;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.language.WKMultiLanguageUtil;
import com.chat.base.utils.rxpermissions.RxPermissions;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ActTabMainBinding;
import com.chat.uikit.fragment.ChatFragment;
import com.chat.uikit.fragment.PlaceholderTabFragment;
import com.chat.uikit.fragment.WebTabFragment;
import com.chat.uikit.user.service.UserModel;
import com.mikepenz.iconics.IconicsDrawable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * tab导航栏
 */
public class TabActivity extends WKBaseActivity<ActTabMainBinding> {
    private static final int TAB_CHAT = 0;
    private static final int TAB_PARTNER = 1;
    private static final int TAB_DISCOVER = 2;
    private static final int TAB_COMMUNITY = 3;
    private static final int TAB_STUDY = 4;

    private static final String ICON_CHAT = "faw-comments";
    private static final String ICON_PARTNER = "faw-user-friends";
    private static final String ICON_DISCOVER = "faw-compass";
    private static final String ICON_COMMUNITY = "faw-users";
    private static final String ICON_STUDY = "faw-graduation-cap";

    /** 选中后的图标、文字蓝色。 */
    private static final int COLOR_TAB_SELECTED = 0xFF2F6BFF;
    /** 未选中的图标、文字颜色。 */
    private static final int COLOR_TAB_NORMAL_RES = R.color.tab_text_normal;
    /** 被选中按钮的浅黑色背景。 */
    private static final int COLOR_SELECTED_BUTTON_BG = 0x14000000;
    /** 底部面板半透明白色玻璃背景。 */
    private static final int COLOR_GLASS_PANEL_TOP = 0xE6FFFFFF;
    private static final int COLOR_GLASS_PANEL_BOTTOM = 0xBFFFFFFF;
    private static final int COLOR_GLASS_PANEL_STROKE = 0x55FFFFFF;

    private CounterView msgCounterView;
    private ImageView chatIV, partnerIV, discoverIV, communityIV, studyIV;
    private TextView chatTV, partnerTV, discoverTV, communityTV, studyTV;
    private long lastClickChatTabTime = 0L;
    private final boolean isShowTabText = true;
    private final List<Fragment> fragments = new ArrayList<>(5);

    @Override
    protected ActTabMainBinding getViewBinding() {
        return ActTabMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        ActManagerUtils.getInstance().clearAllActivity();
    }

    @Override
    public boolean supportSlideBack() {
        return false;
    }

    @SuppressLint("CheckResult")
    @Override
    protected void initView() {
        UserModel.getInstance().device();
        requestNotificationPermissionIfNeeded();

        initTabViews();
        initFragments();
        initBadgesAndCounters();
        applyBottomNavigationGlassStyle();

        WKCommonModel.getInstance().getAppNewVersion(false, version -> {
            String v = WKDeviceUtils.getInstance().getVersionName(TabActivity.this);
            if (version != null && !TextUtils.isEmpty(version.download_url) && !version.app_version.equals(v)) {
                WKDialogUtils.getInstance().showNewVersionDialog(TabActivity.this, version);
            }
        });

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
        WKCommonModel.getInstance().getAppConfig(null);

        playAnimation(TAB_CHAT);
        setBottomNavigationVisible(true);
    }

    @SuppressLint("CheckResult")
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String desc = String.format(getString(R.string.notification_permissions_desc), getString(R.string.app_name));
            RxPermissions rxPermissions = new RxPermissions(this);
            rxPermissions.request(Manifest.permission.POST_NOTIFICATIONS).subscribe(aBoolean -> {
                if (!aBoolean) {
                    WKDialogUtils.getInstance().showDialog(this, getString(com.chat.base.R.string.authorization_request), desc, true, getString(R.string.cancel), getString(R.string.to_set), 0, Theme.colorAccount, index -> {
                        if (index == 1) {
                            EndpointManager.getInstance().invoke("show_open_notification_dialog", this);
                        }
                    });
                }
            });
        } else {
            boolean isEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
            if (!isEnabled) {
                EndpointManager.getInstance().invoke("show_open_notification_dialog", this);
            }
        }
    }

    private void initTabViews() {
        chatIV = new ImageView(this);
        partnerIV = new ImageView(this);
        discoverIV = new ImageView(this);
        communityIV = new ImageView(this);
        studyIV = new ImageView(this);

        chatTV = createTabTextView(R.string.tab_text_chat);
        partnerTV = createTabTextView(R.string.tab_text_partner);
        discoverTV = createTabTextView(R.string.tab_text_discover);
        communityTV = createTabTextView(R.string.tab_text_community);
        studyTV = createTabTextView(R.string.tab_text_study);

        addTabView(R.id.i_chat, chatIV, chatTV, true);
        addTabView(R.id.i_partner, partnerIV, partnerTV, false);
        addTabView(R.id.i_discover, discoverIV, discoverTV, false);
        addTabView(R.id.i_community, communityIV, communityTV, false);
        addTabView(R.id.i_study, studyIV, studyTV, false);
    }

    private TextView createTabTextView(int textResId) {
        TextView textView = new TextView(this);
        try {
            Typeface face = Typeface.createFromAsset(getResources().getAssets(), "fonts/mw_bold.ttf");
            textView.setTypeface(face);
        } catch (Exception ignored) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        textView.setText(textResId);
        textView.setTextColor(ContextCompat.getColor(this, COLOR_TAB_NORMAL_RES));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.CENTER);
        return textView;
    }

    private void addTabView(int menuId, ImageView iconView, TextView textView, boolean withMsgCounter) {
        FrameLayout tabRoot = wkVBinding.bottomNavigation.findViewById(menuId);
        if (tabRoot == null) return;

        tabRoot.setClipToOutline(false);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        if (isShowTabText) {
            tabRoot.addView(iconView, LayoutHelper.createFrame(34, 34, Gravity.CENTER | Gravity.TOP, 0, 5, 0, 0));
            tabRoot.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 15, 0, 0));
        } else {
            tabRoot.addView(iconView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        if (withMsgCounter) {
            msgCounterView = new CounterView(this);
            msgCounterView.setColors(R.color.white, R.color.reminderColor);
            msgCounterView.setVisibility(View.GONE);
            tabRoot.addView(msgCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 5, 0, 15));
        }
    }

    private void initFragments() {
        fragments.clear();
        fragments.add(new ChatFragment());
        fragments.add(PlaceholderTabFragment.newInstance(getString(R.string.tab_text_partner), getString(R.string.tab_placeholder_partner_desc)));
        fragments.add(PlaceholderTabFragment.newInstance(getString(R.string.tab_text_discover), getString(R.string.tab_placeholder_discover_desc)));
        fragments.add(WebTabFragment.newInstance("https://bbs.886.best"));
        fragments.add(WebTabFragment.newInstance("https://886.best"));
        wkVBinding.vp.setAdapter(new WKFragmentStateAdapter(this, fragments));
        // 底部是一级导航，只允许点击切换；横滑手势留给聊天页内部二级导航使用。
        wkVBinding.vp.setUserInputEnabled(false);
    }

    private void initBadgesAndCounters() {
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_chat).setVisible(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_partner).setVisible(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_discover).setVisible(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_community).setVisible(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_study).setVisible(false);
    }

    /**
     * 底部导航面板玻璃效果：半透明白色渐变 + 白色描边 + 轻微阴影。
     * 真正的实时背景模糊需要父布局或第三方 BlurView 支持，这里不改 XML，使用轻量方案模拟磨砂玻璃质感。
     */
    private void applyBottomNavigationGlassStyle() {
        if (wkVBinding == null || wkVBinding.bottomNavigation == null) return;

        GradientDrawable glassBackground = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_GLASS_PANEL_TOP, COLOR_GLASS_PANEL_BOTTOM}
        );
        glassBackground.setShape(GradientDrawable.RECTANGLE);
        glassBackground.setCornerRadii(new float[]{
                dp(24), dp(24),
                dp(24), dp(24),
                0, 0,
                0, 0
        });
        glassBackground.setStroke(dp(1), COLOR_GLASS_PANEL_STROKE);

        wkVBinding.bottomNavigation.setBackground(glassBackground);
        wkVBinding.bottomNavigation.setElevation(dp(14));
        wkVBinding.bottomNavigation.setTranslationZ(dp(14));

        // 去掉 Material BottomNavigationView / NavigationBarView 自带选中指示器和 item 背景，避免和自定义浅黑按钮冲突。
        disableNavigationBarDefaultActiveIndicator();
    }

    private void disableNavigationBarDefaultActiveIndicator() {
        if (wkVBinding == null || wkVBinding.bottomNavigation == null) return;

        try {
            Method method = wkVBinding.bottomNavigation.getClass().getMethod("setItemActiveIndicatorEnabled", boolean.class);
            method.invoke(wkVBinding.bottomNavigation, false);
        } catch (Exception ignored) {
            // Material 版本较旧时没有这个方法，忽略即可。
        }

        try {
            Method method = wkVBinding.bottomNavigation.getClass().getMethod("setItemBackgroundResource", int.class);
            method.invoke(wkVBinding.bottomNavigation, android.R.color.transparent);
        } catch (Exception ignored) {
            // Material 版本不支持时忽略。
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.vp.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                playAnimation(position);
                wkVBinding.bottomNavigation.setSelectedItemId(getMenuIdByIndex(position));
                syncBottomNavigationForCurrentTab();
            }
        });

        wkVBinding.bottomNavigation.setItemIconTintList(null);
        wkVBinding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.i_chat) {
                long nowTime = WKTimeUtils.getInstance().getCurrentMills();
                if (wkVBinding.vp.getCurrentItem() == TAB_CHAT) {
                    if (nowTime - lastClickChatTabTime <= 300) {
                        EndpointManager.getInstance().invoke("scroll_to_unread_channel", null);
                    }
                    lastClickChatTabTime = nowTime;
                    return true;
                }
                switchToTab(TAB_CHAT);
            } else if (itemId == R.id.i_partner) {
                switchToTab(TAB_PARTNER);
            } else if (itemId == R.id.i_discover) {
                switchToTab(TAB_DISCOVER);
            } else if (itemId == R.id.i_community) {
                switchToTab(TAB_COMMUNITY);
            } else if (itemId == R.id.i_study) {
                switchToTab(TAB_STUDY);
            }
            return true;
        });

        EndpointManager.getInstance().setMethod("tab_activity", EndpointCategory.wkRefreshMailList, object -> null);
    }

    private void switchToTab(int index) {
        wkVBinding.vp.setCurrentItem(index, false);
        playAnimation(index);
        syncBottomNavigationForCurrentTab();
    }

    private void syncBottomNavigationForCurrentTab() {
        Fragment fragment = getCurrentFragment();
        if (fragment instanceof WebTabFragment) {
            ((WebTabFragment) fragment).syncBottomNavigationWithCurrentUrl();
        } else {
            setBottomNavigationVisible(true);
        }
    }

    public void setBottomNavigationVisible(boolean visible) {
        if (wkVBinding == null || wkVBinding.bottomNavigation == null) {
            return;
        }
        int targetVisibility = visible ? View.VISIBLE : View.GONE;
        if (wkVBinding.bottomNavigation.getVisibility() != targetVisibility) {
            wkVBinding.bottomNavigation.setVisibility(targetVisibility);
        }
    }

    private Fragment getCurrentFragment() {
        if (wkVBinding == null || wkVBinding.vp == null) {
            return null;
        }
        int index = wkVBinding.vp.getCurrentItem();
        if (index < 0 || index >= fragments.size()) {
            return null;
        }
        return fragments.get(index);
    }

    private int getMenuIdByIndex(int index) {
        if (index == TAB_PARTNER) return R.id.i_partner;
        if (index == TAB_DISCOVER) return R.id.i_discover;
        if (index == TAB_COMMUNITY) return R.id.i_community;
        if (index == TAB_STUDY) return R.id.i_study;
        return R.id.i_chat;
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncBottomNavigationForCurrentTab();
        FriendModel.getInstance().syncFriends((code, msg) -> {
            if (code != HttpResponseCode.success && !TextUtils.isEmpty(msg)) {
                showToast(msg);
            }
        });
    }

    public void setMsgCount(int number) {
        WKUIKitApplication.getInstance().totalMsgCount = number;
        if (msgCounterView == null) return;
        if (number > 0) {
            msgCounterView.setCount(number, true);
            msgCounterView.setVisibility(View.VISIBLE);
        } else {
            msgCounterView.setCount(0, true);
            msgCounterView.setVisibility(View.GONE);
        }
    }

    public void setContactCount(int number, boolean showDot) {
        // 联系人入口已从底部导航移除；保留空实现，避免旧逻辑调用时报错。
    }

    @Override
    public Resources getResources() {
        float fontScale = WKConstants.getFontScale();
        Resources res = super.getResources();
        Configuration config = res.getConfiguration();
        config.fontScale = fontScale;
        res.updateConfiguration(config, res.getDisplayMetrics());
        return res;
    }

    @Override
    public void onBackPressed() {
        if (handleBackPress()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return handleBackPress();
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleBackPress() {
        Fragment fragment = getCurrentFragment();
        if (fragment instanceof WebTabFragment) {
            WebTabFragment webTabFragment = (WebTabFragment) fragment;
            if (webTabFragment.canGoBack()) {
                webTabFragment.goBack();
                return true;
            }
        }

        // 只有 WebView 已退回一级页，或者当前不是 WebView 二级页时，才退到后台。
        setBottomNavigationVisible(true);
        moveTaskToBack(true);
        return true;
    }

    private void playAnimation(int index) {
        lastClickChatTabTime = index == TAB_CHAT ? lastClickChatTabTime : 0L;

        setTabIcon(chatIV, ICON_CHAT, index == TAB_CHAT);
        setTabIcon(partnerIV, ICON_PARTNER, index == TAB_PARTNER);
        setTabIcon(discoverIV, ICON_DISCOVER, index == TAB_DISCOVER);
        setTabIcon(communityIV, ICON_COMMUNITY, index == TAB_COMMUNITY);
        setTabIcon(studyIV, ICON_STUDY, index == TAB_STUDY);

        setTabButtonBackground(R.id.i_chat, index == TAB_CHAT);
        setTabButtonBackground(R.id.i_partner, index == TAB_PARTNER);
        setTabButtonBackground(R.id.i_discover, index == TAB_DISCOVER);
        setTabButtonBackground(R.id.i_community, index == TAB_COMMUNITY);
        setTabButtonBackground(R.id.i_study, index == TAB_STUDY);

        if (isShowTabText) {
            setTabTextSelected(chatTV, index == TAB_CHAT);
            setTabTextSelected(partnerTV, index == TAB_PARTNER);
            setTabTextSelected(discoverTV, index == TAB_DISCOVER);
            setTabTextSelected(communityTV, index == TAB_COMMUNITY);
            setTabTextSelected(studyTV, index == TAB_STUDY);
        }
    }

    private void setTabIcon(ImageView imageView, String iconName, boolean selected) {
        if (imageView == null) return;

        // 选中图标改为蓝色，不再给图标本身添加蓝色圆角背景。
        int iconColor = selected ? COLOR_TAB_SELECTED : ContextCompat.getColor(this, COLOR_TAB_NORMAL_RES);
        IconicsDrawable drawable = new IconicsDrawable(this, iconName);
        drawable.setColorList(ColorStateList.valueOf(iconColor));
        drawable.setSizeXPx(dp(24));
        drawable.setSizeYPx(dp(24));
        drawable.setPaddingPx(dp(2));

        imageView.setImageDrawable(drawable);
    }

    private void setTabButtonBackground(int menuId, boolean selected) {
        FrameLayout tabRoot = wkVBinding.bottomNavigation.findViewById(menuId);
        if (tabRoot == null) return;

        if (!selected) {
            tabRoot.setBackgroundColor(Color.TRANSPARENT);
            return;
        }

        GradientDrawable selectedBackground = new GradientDrawable();
        selectedBackground.setShape(GradientDrawable.RECTANGLE);
        selectedBackground.setColor(COLOR_SELECTED_BUTTON_BG);
        selectedBackground.setCornerRadius(dp(16));
        tabRoot.setBackground(selectedBackground);
    }

    private void setTabTextSelected(TextView textView, boolean selected) {
        if (textView == null) return;
        int color = selected ? COLOR_TAB_SELECTED : ContextCompat.getColor(this, COLOR_TAB_NORMAL_RES);
        textView.setTextColor(color);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        WKMultiLanguageUtil.getInstance().setConfiguration();
        Theme.applyTheme();
    }

    @Override
    public void finish() {
        super.finish();
        EndpointManager.getInstance().remove("tab_activity");
    }
}
