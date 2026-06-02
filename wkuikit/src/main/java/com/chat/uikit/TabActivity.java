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

    // 参考 Messenger 的底部导航颜色：选中蓝色，未选中淡黑灰色。
    private static final int COLOR_TAB_SELECTED = 0xFF1877F2;
    private static final int COLOR_TAB_NORMAL = 0xFF65676B;

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

        // 只去掉 Material 默认的选中背景/水波纹/indicator，不改底部面板背景。
        // 面板保持 XML/主题里的原始样式，避免半透明、矩形黑线等问题。
        disableBottomNavigationSelectedBackground();

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
        textView.setTextColor(COLOR_TAB_NORMAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.CENTER);
        return textView;
    }

    private void addTabView(int menuId, ImageView iconView, TextView textView, boolean withMsgCounter) {
        FrameLayout tabRoot = wkVBinding.bottomNavigation.findViewById(menuId);
        if (tabRoot == null) return;

        clearItemViewBackground(tabRoot);

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
        disableBottomNavigationSelectedBackground();

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
        disableBottomNavigationSelectedBackground();

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

        disableBottomNavigationSelectedBackground();

        setTabIcon(chatIV, ICON_CHAT, index == TAB_CHAT);
        setTabIcon(partnerIV, ICON_PARTNER, index == TAB_PARTNER);
        setTabIcon(discoverIV, ICON_DISCOVER, index == TAB_DISCOVER);
        setTabIcon(communityIV, ICON_COMMUNITY, index == TAB_COMMUNITY);
        setTabIcon(studyIV, ICON_STUDY, index == TAB_STUDY);

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

        IconicsDrawable drawable = new IconicsDrawable(this, iconName);
        drawable.setColorList(ColorStateList.valueOf(selected ? COLOR_TAB_SELECTED : COLOR_TAB_NORMAL));
        drawable.setSizeXPx(dp(24));
        drawable.setSizeYPx(dp(24));
        drawable.setPaddingPx(dp(2));

        // 不再给选中图标加任何背景，保持图二那种纯蓝色图标 + 文字效果。
        imageView.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            imageView.setForeground(null);
        }
        imageView.setImageDrawable(drawable);
    }

    private void setTabTextSelected(TextView textView, boolean selected) {
        if (textView == null) return;
        textView.setTextColor(selected ? COLOR_TAB_SELECTED : COLOR_TAB_NORMAL);
    }

    /**
     * 关闭 BottomNavigationView/NavigationBarView 自带的选中背景、indicator 和水波纹。
     * 不设置 bottomNavigation 的 background，底部面板保持布局 XML/主题原来的背景。
     */
    private void disableBottomNavigationSelectedBackground() {
        if (wkVBinding == null || wkVBinding.bottomNavigation == null) {
            return;
        }

        wkVBinding.bottomNavigation.setAlpha(1f);
        wkVBinding.bottomNavigation.setItemIconTintList(null);

        try {
            wkVBinding.bottomNavigation.setItemRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        } catch (Exception ignored) {
        }

        try {
            wkVBinding.bottomNavigation.setItemBackgroundResource(0);
        } catch (Exception ignored) {
        }

        // Material 1.4+/1.5+ 可能有 active indicator。用反射避免低版本编译/运行问题。
        invokeBottomNavigationMethod("setItemActiveIndicatorEnabled", new Class[]{boolean.class}, false);
        invokeBottomNavigationMethod("setItemActiveIndicatorColor", new Class[]{ColorStateList.class}, ColorStateList.valueOf(Color.TRANSPARENT));
        invokeBottomNavigationMethod("setItemActiveIndicatorWidth", new Class[]{int.class}, 0);
        invokeBottomNavigationMethod("setItemActiveIndicatorHeight", new Class[]{int.class}, 0);
        invokeBottomNavigationMethod("setItemActiveIndicatorMarginHorizontal", new Class[]{int.class}, 0);

        clearItemViewBackground(wkVBinding.bottomNavigation.findViewById(R.id.i_chat));
        clearItemViewBackground(wkVBinding.bottomNavigation.findViewById(R.id.i_partner));
        clearItemViewBackground(wkVBinding.bottomNavigation.findViewById(R.id.i_discover));
        clearItemViewBackground(wkVBinding.bottomNavigation.findViewById(R.id.i_community));
        clearItemViewBackground(wkVBinding.bottomNavigation.findViewById(R.id.i_study));
    }

    private void clearItemViewBackground(View view) {
        if (view == null) return;
        view.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setForeground(null);
        }
    }

    private void invokeBottomNavigationMethod(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = wkVBinding.bottomNavigation.getClass().getMethod(methodName, parameterTypes);
            method.invoke(wkVBinding.bottomNavigation, args);
        } catch (Exception ignored) {
        }
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
