package com.chat.partner.profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.WKDialogUtils;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileBinding;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.setting.SettingActivity;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Date;
import java.util.Locale;

public class PartnerProfileActivity extends WKBaseActivity<ActPartnerProfileBinding> {
    private static final int REQ_CHANGE_COVER = 7201;
    private String uid;
    private boolean isSelf;
    private boolean isSayHiLoading;
    private boolean coverUploading;
    private boolean introExpanded;
    private boolean introCanExpand;
    private boolean hasAnimatedEntrance;
    private boolean introBaseVisible;
    private boolean tagBaseVisible;
    private boolean langBaseVisible;
    private boolean profileLastOnlineBaseVisible;
    private int topBarHeight;
    private float currentCollapsePercent;
    private boolean fixedTopBarSolid;
    private PartnerProfileEntity profile;
    private boolean feedWorksAttached;
    private Fragment feedWorksFragment;

    // These views were moved out of the old Toolbar/AppBar collapsing area. Use findViewById
    // instead of ViewBinding fields, so the Java file does not depend on generated binding fields
    // that may be absent until the updated XML is compiled.
    private View fixedProfileTopBar;
    private View fixedWorksHeader;
    private TextView toolbarTitleTv;
    private View toolbarMetaLayout;
    private View toolbarCountryGroup;
    private ImageView toolbarCountryIcon;
    private TextView toolbarCountryTv;
    private View toolbarLastOnlineGroup;
    private ImageView toolbarTimeIcon;
    private TextView toolbarLastOnlineTv;
    private View feedWorksSection;
    private TextView feedWorksTitleTv;
    private ViewGroup feedWorksContainer;

    @Override
    protected ActPartnerProfileBinding getViewBinding() {
        return ActPartnerProfileBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        uid = getIntent().getStringExtra(PartnerProfileRoute.EXTRA_UID);
        if (TextUtils.isEmpty(uid)) uid = WKConfig.getInstance().getUid();
        isSelf = TextUtils.equals(uid, WKConfig.getInstance().getUid());
    }

    @Override
    protected boolean supportSlideBack() {
        return true;
    }

    @Override
    protected void toggleStatusBarMode() {
        super.toggleStatusBarMode();
        setupImmersiveStatusBar();
    }

    @Override
    protected void initView() {
        ensureFixedTopBarViews();
        setupImmersiveStatusBar();
        wkVBinding.avatarView.setSize(96);
        wkVBinding.editBtn.setVisibility(View.VISIBLE);
        wkVBinding.editBtn.setImageResource(R.drawable.ic_partner_more_horizontal);
        wkVBinding.helloBar.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.bottomActionSpace.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        // 固定顶栏样式只设置一次。它永远是白底深色图标，不再随折叠百分比变化。
        applyFixedTopBarStyle();
        setupCoverParallax();
        setupWorksScrollBridge();
        setupEntranceAnimation();
        resetInitialScrollState();
    }

    @Override
    protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> pressAndRun(v, this::finish));
        wkVBinding.editBtn.setOnClickListener(v -> pressAndRun(v, () -> {
            if (isSelf) showSelfSystemMenu();
            else showProfileMoreMenu();
        }));
        wkVBinding.profileSelfEditChip.setOnClickListener(v -> pressAndRun(v, () -> startActivity(new Intent(this, PartnerProfileEditActivity.class))));
        wkVBinding.helloBtnLayout.setOnClickListener(v -> onMainActionClick());
        wkVBinding.tagSection.setOnClickListener(v -> {
            if (isSelf) startActivity(new Intent(this, PartnerProfileEditActivity.class));
        });
        wkVBinding.coverIv.setOnClickListener(v -> {
            if (isSelf) pickCoverImage();
        });
    }

    @Override
    protected void initData() {
        loadProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (profile != null) loadProfile();
    }

    private void resetInitialScrollState() {
        wkVBinding.appBarLayout.post(() -> {
            wkVBinding.appBarLayout.setExpanded(true, false);
            wkVBinding.nestedScrollView.scrollTo(0, 0);
            applyProfileContentVisibility();
            updateWorksHeaderPin();
        });
    }

    private void ensureFixedTopBarViews() {
        if (fixedProfileTopBar == null) fixedProfileTopBar = findViewById(R.id.fixedProfileTopBar);
        if (fixedWorksHeader == null) fixedWorksHeader = findViewById(R.id.fixedWorksHeader);
        if (toolbarTitleTv == null) toolbarTitleTv = findViewById(R.id.toolbarTitleTv);
        if (toolbarMetaLayout == null) toolbarMetaLayout = findViewById(R.id.toolbarMetaLayout);
        if (toolbarCountryGroup == null) toolbarCountryGroup = findViewById(R.id.toolbarCountryGroup);
        if (toolbarCountryIcon == null) toolbarCountryIcon = findViewById(R.id.toolbarCountryIcon);
        if (toolbarCountryTv == null) toolbarCountryTv = findViewById(R.id.toolbarCountryTv);
        if (toolbarLastOnlineGroup == null) toolbarLastOnlineGroup = findViewById(R.id.toolbarLastOnlineGroup);
        if (toolbarTimeIcon == null) toolbarTimeIcon = findViewById(R.id.toolbarTimeIcon);
        if (toolbarLastOnlineTv == null) toolbarLastOnlineTv = findViewById(R.id.toolbarLastOnlineTv);
        if (feedWorksSection == null) feedWorksSection = findViewById(R.id.feedWorksSection);
        if (feedWorksTitleTv == null) feedWorksTitleTv = findViewById(R.id.feedWorksTitleTv);
        if (feedWorksContainer == null) feedWorksContainer = findViewById(R.id.feedWorksContainer);
    }

    private void setupImmersiveStatusBar() {
        ensureFixedTopBarViews();
        Window window = getWindow();
        if (window == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
        // 顶部默认是透明覆盖在背景墙上，状态栏先用浅色图标；折叠后再切深色。
        setStatusBarIconDark(false);

        // 固定顶栏：高度 = 状态栏 + 56dp。内容用 paddingTop 推到状态栏图标下方。
        if (fixedProfileTopBar == null) return;
        int statusBarHeight = getStatusBarHeight();
        topBarHeight = statusBarHeight + dp(56);
        fixedProfileTopBar.setPadding(0, statusBarHeight, 0, 0);
        ViewGroup.LayoutParams topLp = fixedProfileTopBar.getLayoutParams();
        if (topLp != null) {
            topLp.height = topBarHeight;
            fixedProfileTopBar.setLayoutParams(topLp);
        }
        clearOverlayShadows();
        positionWorksHeader();
    }

    private void clearOverlayShadows() {
        ensureFixedTopBarViews();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        wkVBinding.appBarLayout.setElevation(0f);
        wkVBinding.appBarLayout.setTranslationZ(0f);
        wkVBinding.appBarLayout.setStateListAnimator(null);
        if (fixedProfileTopBar != null) {
            fixedProfileTopBar.setElevation(0f);
            fixedProfileTopBar.setTranslationZ(0f);
            fixedProfileTopBar.setStateListAnimator(null);
        }
        if (fixedWorksHeader != null) {
            fixedWorksHeader.setElevation(0f);
            fixedWorksHeader.setTranslationZ(0f);
            fixedWorksHeader.setStateListAnimator(null);
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        return result > 0 ? result : dp(24);
    }

    // 只保留 cover 的视差/缩放，绝不再用折叠百分比驱动顶栏。
    private void setupCoverParallax() {
        wkVBinding.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int range = appBarLayout.getTotalScrollRange();
            if (range <= 0) return;
            float percent = Math.min(1f, Math.max(0f, Math.abs(verticalOffset) * 1f / range));
            currentCollapsePercent = percent;
            float scale = 1f + (0.04f * (1f - percent));
            wkVBinding.coverIv.setScaleX(scale);
            wkVBinding.coverIv.setScaleY(scale);
            applyProfileContentVisibility();
            updateWorksHeaderPin();
        });
    }

    private void setupWorksScrollBridge() {
        wkVBinding.nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    updateWorksHeaderPin();
                    if (scrollY > oldScrollY) maybeLoadMoreWorks();
                });
    }

    private void maybeLoadMoreWorks() {
        ensureFixedTopBarViews();
        if (feedWorksFragment == null || feedWorksSection == null || feedWorksSection.getVisibility() != View.VISIBLE) return;
        View child = wkVBinding.nestedScrollView.getChildAt(0);
        if (child == null) return;
        int distanceToBottom = child.getBottom() - (wkVBinding.nestedScrollView.getScrollY() + wkVBinding.nestedScrollView.getHeight());
        if (distanceToBottom > dp(900)) return;
        try {
            feedWorksFragment.getClass().getMethod("loadMoreIfNeeded").invoke(feedWorksFragment);
        } catch (Throwable ignored) {
        }
    }

    // 只隐藏大资料区（大头像、背景墙资料、简介、标签、语言）。
    // 绝不触碰 fixedProfileTopBar —— 它永远显示。
    private void applyProfileContentVisibility() {
        wkVBinding.profileHeaderLayout.setVisibility(View.VISIBLE);
        wkVBinding.langLayout.setVisibility(langBaseVisible ? View.VISIBLE : View.GONE);
        wkVBinding.introSection.setVisibility(introBaseVisible ? View.VISIBLE : View.GONE);
        wkVBinding.tagSection.setVisibility(tagBaseVisible ? View.VISIBLE : View.GONE);
        if (introCanExpand && introBaseVisible) {
            wkVBinding.introMoreTv.setVisibility(View.VISIBLE);
        }
        updateFixedTopBarAppearance(isWorksPinned());
    }

    // ============ 作品栏吸顶核心逻辑 ============
    // 判断依据：内容里的“作品”标题 feedWorksTitleTv 相对 root 的 top
    // 是否已经到达 fixedProfileTopBar 的 bottom。不使用 scrollY。
    private void updateWorksHeaderPin() {
        ensureFixedTopBarViews();
        if (wkVBinding == null || fixedWorksHeader == null || feedWorksSection == null) return;
        positionWorksHeader();
        boolean pinned = isWorksPinned();
        fixedWorksHeader.setVisibility(pinned ? View.VISIBLE : View.GONE);
        updateFixedTopBarAppearance(pinned);
        if (pinned) {
            fixedProfileTopBar.bringToFront();
            fixedWorksHeader.bringToFront();
            wkVBinding.helloBar.bringToFront();
        }
        if (feedWorksTitleTv != null) {
            // 隐藏内容流里的标题但保留占位，避免跳动。
            feedWorksTitleTv.setVisibility(pinned ? View.INVISIBLE : View.VISIBLE);
        }
    }

    // 把固定作品栏定位到 fixedProfileTopBar 的正下方。
    private void positionWorksHeader() {
        ensureFixedTopBarViews();
        if (wkVBinding == null || fixedWorksHeader == null || fixedProfileTopBar == null) return;
        int barBottom = topBarHeight > 0 ? topBarHeight : fixedProfileTopBar.getHeight();
        fixedWorksHeader.setY(barBottom);
    }

    private boolean isWorksPinned() {
        if (wkVBinding == null || feedWorksSection == null || feedWorksTitleTv == null) return false;
        if (feedWorksSection.getVisibility() != View.VISIBLE) return false;
        if (feedWorksTitleTv.getHeight() <= 0) return false;

        // feedWorksTitleTv 相对 root 的 top vs fixedProfileTopBar 相对 root 的 bottom。
        int titleTop = topInRoot(feedWorksTitleTv);
        int barBottom = topInRoot(fixedProfileTopBar) + fixedProfileTopBar.getHeight();
        if (barBottom <= 0) barBottom = topBarHeight;
        return titleTop <= barBottom;
    }

    private int topInRoot(View view) {
        if (view == null) return Integer.MAX_VALUE;
        int[] rootLoc = new int[2];
        int[] viewLoc = new int[2];
        wkVBinding.getRoot().getLocationInWindow(rootLoc);
        view.getLocationInWindow(viewLoc);
        return viewLoc[1] - rootLoc[1];
    }

    // 固定顶栏分两态：
    // 1. 背景墙展开时：透明覆盖在封面上，只保留白色返回/更多，隐藏用户名，背景墙不被白面板截短。
    // 2. 折叠/作品吸顶时：白底深色，显示用户名和国家/在线。
    private void applyFixedTopBarStyle() {
        updateFixedTopBarAppearance(false);
    }

    private void updateFixedTopBarAppearance(boolean forceSolid) {
        ensureFixedTopBarViews();
        if (fixedProfileTopBar == null) return;
        boolean solid = forceSolid || currentCollapsePercent >= 0.72f;
        if (fixedTopBarSolid == solid && toolbarTitleTv != null) return;
        fixedTopBarSolid = solid;

        fixedProfileTopBar.setBackgroundColor(solid ? 0xFFFFFFFF : Color.TRANSPARENT);
        setStatusBarIconDark(solid);

        int iconColor = solid ? 0xFF202033 : 0xFFFFFFFF;
        wkVBinding.backBtn.setColorFilter(iconColor);
        wkVBinding.editBtn.setColorFilter(iconColor);

        if (toolbarTitleTv != null) {
            toolbarTitleTv.setAlpha(solid ? 1f : 0f);
            toolbarTitleTv.setTextColor(0xFF202033);
        }
        if (toolbarMetaLayout != null) toolbarMetaLayout.setAlpha(solid ? 1f : 0f);
        if (toolbarCountryTv != null) toolbarCountryTv.setTextColor(0xFF8C8C99);
        if (toolbarLastOnlineTv != null) toolbarLastOnlineTv.setTextColor(0xFF8C8C99);
        setImageTint(toolbarCountryIcon, 0xFF9A9AA6);
        setImageTint(toolbarTimeIcon, 0xFF9A9AA6);
    }

    private void setStatusBarIconDark(boolean dark) {
        Window window = getWindow();
        if (window == null) return;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void setImageTint(ImageView imageView, int color) {
        if (imageView != null) imageView.setColorFilter(color);
    }

    private void setupEntranceAnimation() {
        wkVBinding.contentSheetLayout.setAlpha(0f);
        wkVBinding.contentSheetLayout.setTranslationY(dp(40));
    }

    private void playEntranceAnimation() {
        if (hasAnimatedEntrance) return;
        hasAnimatedEntrance = true;
        wkVBinding.contentSheetLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

        wkVBinding.avatarGlowLayout.setScaleX(0.5f);
        wkVBinding.avatarGlowLayout.setScaleY(0.5f);
        wkVBinding.avatarGlowLayout.setAlpha(0f);
        wkVBinding.avatarGlowLayout.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(120)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        if (wkVBinding.helloBar.getVisibility() == View.VISIBLE) {
            wkVBinding.helloBar.setTranslationY(dp(80));
            wkVBinding.helloBar.animate()
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void loadProfile() {
        PartnerProfileModel.getInstance().getUserProfile(uid, (code, msg, data) -> {
            if (code == HttpResponseCode.success && data != null) {
                profile = data;
                bindProfile(data);
                playEntranceAnimation();
            } else {
                wkVBinding.contentSheetLayout.setAlpha(1f);
                wkVBinding.contentSheetLayout.setTranslationY(0f);
                if (!TextUtils.isEmpty(msg)) showToast(msg);
            }
        });
    }

    private void pickCoverImage() {
        if (coverUploading) {
            showToast(getString(R.string.partner_wait_upload_finish));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_CHANGE_COVER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CHANGE_COVER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            prepareAndUploadCover(data.getData());
        }
    }

    private void prepareAndUploadCover(Uri uri) {
        coverUploading = true;
        showToast(getString(R.string.partner_uploading));
        new Thread(() -> {
            try {
                File source = copyUriToCache(uri, "partner_cover_src");
                File webp = PartnerImageCompressor.compressToWebp150KB(source, getCacheDir(), "partner_cover_" + System.currentTimeMillis() + ".webp");
                runOnUiThread(() -> {
                    wkVBinding.coverIv.setImageURI(Uri.fromFile(webp));
                    uploadCoverFile(webp);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    coverUploading = false;
                    showToast(getString(R.string.partner_upload_failed));
                });
            }
        }).start();
    }

    private void uploadCoverFile(File file) {
        PartnerProfileModel.getInstance().getProfileUploadFileUrl(WKConfig.getInstance().getUid(), file.getAbsolutePath(), true, (code, msg, uploadUrl) -> {
            if (uploadUrl == null || TextUtils.isEmpty(uploadUrl.url)) {
                coverUploading = false;
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.partner_upload_failed) : msg);
                return;
            }
            WKUploader.getInstance().upload(uploadUrl.url, file.getAbsolutePath(), "partner_cover_" + System.currentTimeMillis(), new WKUploader.IUploadBack() {
                @Override
                public void onSuccess(String uploadedPath) {
                    String finalPath = normalizeUploadedCoverPath(TextUtils.isEmpty(uploadedPath) ? uploadUrl.path : uploadedPath);
                    runOnUiThread(() -> saveCoverPath(finalPath));
                }

                @Override
                public void onError() {
                    runOnUiThread(() -> {
                        coverUploading = false;
                        showToast(getString(R.string.partner_upload_failed));
                    });
                }
            });
        });
    }

    private void saveCoverPath(String path) {
        if (TextUtils.isEmpty(path)) {
            coverUploading = false;
            showToast(getString(R.string.partner_upload_failed));
            return;
        }
        JSONObject body = new JSONObject();
        body.put("profile_cover", path);
        PartnerProfileModel.getInstance().updateCurrentProfile(body, (code, msg, data) -> {
            coverUploading = false;
            if (code == HttpResponseCode.success || code == 200 || code == 0) {
                if (profile != null) profile.profile_cover = path;
                showToast(getString(R.string.partner_upload_success));
            } else {
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.partner_save_media_failed) : msg);
            }
        });
    }

    private String normalizeUploadedCoverPath(String path) {
        if (TextUtils.isEmpty(path)) return "";
        String v = path.trim();
        if (v.startsWith(WKApiConfig.baseUrl)) v = v.substring(WKApiConfig.baseUrl.length());
        if (v.startsWith("/")) v = v.substring(1);
        if (v.startsWith("common/")) return "file/preview/" + v;
        if (v.startsWith("profile/")) return "file/preview/common/" + v;
        return v;
    }

    private File copyUriToCache(Uri uri, String prefix) throws Exception {
        File out = new File(getCacheDir(), prefix + "_" + System.currentTimeMillis() + ".jpg");
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IllegalStateException("empty uri");
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = input.read(buffer)) > 0) fos.write(buffer, 0, len);
        fos.flush();
        fos.close();
        input.close();
        return out;
    }

    private void bindProfile(PartnerProfileEntity data) {
        String showName = firstNotEmpty(data.name, data.username, data.uid);
        wkVBinding.nameTv.setText(showName);
        ensureFixedTopBarViews();
        if (toolbarTitleTv != null) toolbarTitleTv.setText(showName);
        bindToolbarMeta(data);
        wkVBinding.avatarView.showAvatar(uid, WKChannelType.PERSONAL, data.avatar_cache_key);
        showCountryFlagIfSupported(firstNotEmpty(data.country_code, data.country));
        bindCover(data.profile_cover);
        bindSexAge(data);
        bindLanguages(data);
        bindIntro(data);
        bindTags(data);
        bindPhotos(data);
        bindFeedWorks();
        bindActionButton(data);
        wkVBinding.onlineIndicator.setVisibility(data.status == 1 ? View.VISIBLE : View.GONE);
    }

    private void bindFeedWorks() {
        ensureFixedTopBarViews();
        if (TextUtils.isEmpty(uid) || feedWorksSection == null || feedWorksContainer == null) {
            hideFeedWorks();
            return;
        }
        try {
            Class<?> routeClass = Class.forName("com.chat.feed.FeedRoute");
            Object fragmentObj = routeClass.getMethod("newUserWaterfallFragment", String.class).invoke(null, uid);
            if (!(fragmentObj instanceof Fragment)) {
                hideFeedWorks();
                return;
            }
            feedWorksSection.setVisibility(View.VISIBLE);
            updateWorksHeaderPin();
            feedWorksFragment = (Fragment) fragmentObj;
            if (!feedWorksAttached) {
                feedWorksAttached = true;
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.feedWorksContainer, feedWorksFragment)
                        .commitAllowingStateLoss();
                // 内嵌作品瀑布流自带 RecyclerView。关闭它的嵌套滚动，
                // 由外层 NestedScrollView 统一滚动，避免父子互抢。
                feedWorksContainer.postDelayed(this::disableNestedFeedScrolling, 160);
                feedWorksContainer.postDelayed(this::disableNestedFeedScrolling, 480);
                feedWorksContainer.postDelayed(this::maybeLoadMoreWorks, 650);
            } else {
                feedWorksContainer.post(this::disableNestedFeedScrolling);
                feedWorksContainer.post(this::maybeLoadMoreWorks);
            }
        } catch (Throwable ignored) {
            // wkfeed 是可选模块。没有安装时不要影响个人主页。
            hideFeedWorks();
        }
    }

    private void hideFeedWorks() {
        if (wkVBinding == null) return;
        if (feedWorksSection != null) feedWorksSection.setVisibility(View.GONE);
        feedWorksFragment = null;
        updateWorksHeaderPin();
    }

    private void disableNestedFeedScrolling() {
        ensureFixedTopBarViews();
        if (wkVBinding == null || feedWorksContainer == null) return;
        disableNestedFeedScrolling(feedWorksContainer);
    }

    private void disableNestedFeedScrolling(View view) {
        if (view == null) return;
        if (view instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) view;
            recyclerView.setNestedScrollingEnabled(false);
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            recyclerView.setHasFixedSize(false);
            recyclerView.requestLayout();
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disableNestedFeedScrolling(group.getChildAt(i));
            }
        }
    }

    private void bindCover(String cover) {
        if (!TextUtils.isEmpty(cover)) {
            GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(cover), wkVBinding.coverIv);
        } else {
            bindRandomDefaultCover();
        }
    }

    private void bindRandomDefaultCover() {
        String seed = TextUtils.isEmpty(uid) ? "partner" : uid;
        int index = (int) (Math.abs((long) seed.hashCode()) % 20L) + 1;
        String name = String.format(Locale.US, "bj%02d", index);
        int resId = getResources().getIdentifier(name, "drawable", getPackageName());
        if (resId != 0) {
            wkVBinding.coverIv.setImageResource(resId);
        } else {
            wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        }
    }

    private void bindToolbarMeta(PartnerProfileEntity data) {
        String country = firstNotEmpty(data.country, countryNameFromCode(data.country_code));
        String lastOnline = formatLastOnline(data);
        boolean hasCountry = !TextUtils.isEmpty(country);
        boolean hasLastOnline = !TextUtils.isEmpty(lastOnline);

        if (isSelf) {
            if (toolbarMetaLayout != null) toolbarMetaLayout.setVisibility(hasCountry || hasLastOnline ? View.VISIBLE : View.GONE);
            if (toolbarCountryGroup != null) toolbarCountryGroup.setVisibility(hasCountry ? View.VISIBLE : View.GONE);
            if (toolbarLastOnlineGroup != null) toolbarLastOnlineGroup.setVisibility(hasLastOnline ? View.VISIBLE : View.GONE);
            if (toolbarCountryTv != null) toolbarCountryTv.setText(country);
            if (toolbarLastOnlineTv != null) toolbarLastOnlineTv.setText(lastOnline);

            profileLastOnlineBaseVisible = hasLastOnline;
            wkVBinding.profileLastOnlineGroup.setVisibility(hasLastOnline ? View.VISIBLE : View.GONE);
            wkVBinding.profileLastOnlineTv.setText(lastOnline);
            wkVBinding.profileSelfEditChip.setVisibility(View.VISIBLE);
        } else {
            if (toolbarMetaLayout != null) toolbarMetaLayout.setVisibility(hasCountry || hasLastOnline ? View.VISIBLE : View.GONE);
            if (toolbarCountryGroup != null) toolbarCountryGroup.setVisibility(hasCountry ? View.VISIBLE : View.GONE);
            if (toolbarLastOnlineGroup != null) toolbarLastOnlineGroup.setVisibility(hasLastOnline ? View.VISIBLE : View.GONE);
            if (toolbarCountryTv != null) toolbarCountryTv.setText(country);
            if (toolbarLastOnlineTv != null) toolbarLastOnlineTv.setText(lastOnline);

            profileLastOnlineBaseVisible = hasLastOnline;
            wkVBinding.profileLastOnlineGroup.setVisibility(hasLastOnline ? View.VISIBLE : View.GONE);
            wkVBinding.profileLastOnlineTv.setText(lastOnline);
            wkVBinding.profileSelfEditChip.setVisibility(View.GONE);
        }
        applyProfileContentVisibility();
    }

    private String formatLastOnline(PartnerProfileEntity data) {
        if (data == null) return "";
        if (data.status == 1) return getString(R.string.partner_online);
        String raw = firstNotEmpty(data.last_online, data.last_online_time, data.last_seen, data.last_seen_at,
                data.last_active_at, data.last_active_time, data.last_login_at, data.last_login_time);
        if (TextUtils.isEmpty(raw)) return "";
        long millis = parseTimeMillis(raw);
        if (millis > 0) {
            CharSequence relative = DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
            return getString(R.string.partner_last_online_format, relative);
        }
        return getString(R.string.partner_last_online_format, raw.trim());
    }

    private long parseTimeMillis(String raw) {
        if (TextUtils.isEmpty(raw)) return 0L;
        String v = raw.trim();
        if (v.matches("^\\d{10,13}$")) {
            try {
                long number = Long.parseLong(v);
                return v.length() == 10 ? number * 1000L : number;
            } catch (Exception ignored) {
                return 0L;
            }
        }
        String clean = v.replace("T", " ").replace("Z", "");
        String[] patterns = new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                Date date = sdf.parse(clean);
                if (date != null) return date.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private void showCountryFlagIfSupported(String countryCode) {
        if (TextUtils.isEmpty(countryCode)) return;
        try {
            wkVBinding.avatarView.getClass().getMethod("showFlag", String.class).invoke(wkVBinding.avatarView, normalizeCountryCode(countryCode));
        } catch (Exception ignored) {
        }
    }

    private void bindSexAge(PartnerProfileEntity data) {
        int age = data.age > 0 ? data.age : ageFromBirthday(data.birthday);
        String gender;
        if (data.sex == 1) gender = "♂";
        else if (data.sex == 0) gender = "♀";
        else gender = "";

        String text = "";
        if (!TextUtils.isEmpty(gender) && age > 0) text = age + " " + gender;
        else if (age > 0) text = String.valueOf(age);
        else if (!TextUtils.isEmpty(gender)) text = gender;

        wkVBinding.sexAgeTv.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        wkVBinding.sexAgeTv.setText(text);
        wkVBinding.sexAgeTv.setTypeface(Typeface.DEFAULT_BOLD);
        wkVBinding.sexAgeTv.getPaint().setFakeBoldText(true);
        if (data.sex == 1) {
            wkVBinding.sexAgeTv.setTextColor(0xFF347BCD);
            if (wkVBinding.sexAgeTv.getBackground() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                wkVBinding.sexAgeTv.getBackground().setTint(0xFFDDEEFF);
            }
        } else if (data.sex == 0) {
            wkVBinding.sexAgeTv.setTextColor(0xFFD92D93);
            if (wkVBinding.sexAgeTv.getBackground() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                wkVBinding.sexAgeTv.getBackground().setTint(0xFFFFE1F1);
            }
        } else {
            wkVBinding.sexAgeTv.setTextColor(0xFF6E6E7E);
            if (wkVBinding.sexAgeTv.getBackground() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                wkVBinding.sexAgeTv.getBackground().setTint(0xFFEDEDF5);
            }
        }
    }

    private void bindLanguages(PartnerProfileEntity data) {
        String nativeText = formatLanguageLetters(data.getNativeLanguagesSafe());
        String learningText = formatLanguageLetters(data.getLearningLanguagesSafe());
        boolean showNative = !TextUtils.isEmpty(nativeText);
        boolean showLearning = !TextUtils.isEmpty(learningText);
        langBaseVisible = showNative || showLearning;
        wkVBinding.langLayout.setVisibility(langBaseVisible ? View.VISIBLE : View.GONE);
        wkVBinding.nativeLangTv.setVisibility(showNative ? View.VISIBLE : View.GONE);
        wkVBinding.learningLangTv.setVisibility(showLearning ? View.VISIBLE : View.GONE);
        wkVBinding.langToTv.setVisibility(showNative && showLearning ? View.VISIBLE : View.GONE);
        wkVBinding.nativeLangTv.setText(nativeText);
        wkVBinding.learningLangTv.setText(learningText);
        applyProfileContentVisibility();
    }

    private void bindIntro(PartnerProfileEntity data) {
        introExpanded = false;
        introCanExpand = false;
        String intro = data == null ? "" : data.intro;
        if (intro != null) intro = intro.trim();
        if (TextUtils.isEmpty(intro) || "null".equalsIgnoreCase(intro)) {
            introBaseVisible = false;
            wkVBinding.introSection.setVisibility(View.GONE);
            wkVBinding.introMoreTv.setVisibility(View.GONE);
            return;
        }
        introBaseVisible = true;
        wkVBinding.introSection.setVisibility(View.VISIBLE);
        wkVBinding.introTv.setText(intro);
        wkVBinding.introTv.setMaxLines(2);
        wkVBinding.introTv.setEllipsize(TextUtils.TruncateAt.END);
        wkVBinding.introMoreTv.setText(R.string.partner_expand_all);
        wkVBinding.introMoreTv.setVisibility(View.GONE);

        View.OnClickListener toggle = v -> toggleIntroExpand();
        wkVBinding.introTv.setOnClickListener(toggle);
        wkVBinding.introMoreTv.setOnClickListener(toggle);

        wkVBinding.introTv.post(() -> {
            android.text.Layout layout = wkVBinding.introTv.getLayout();
            introCanExpand = layout != null && layout.getLineCount() >= 2 && layout.getEllipsisCount(1) > 0;
            wkVBinding.introMoreTv.setVisibility(introCanExpand ? View.VISIBLE : View.GONE);
            applyProfileContentVisibility();
        });
    }

    private void toggleIntroExpand() {
        if (!introCanExpand) return;
        introExpanded = !introExpanded;
        if (introExpanded) {
            wkVBinding.introTv.setMaxLines(Integer.MAX_VALUE);
            wkVBinding.introTv.setEllipsize(null);
            wkVBinding.introMoreTv.setText(R.string.partner_collapse);
        } else {
            wkVBinding.introTv.setMaxLines(2);
            wkVBinding.introTv.setEllipsize(TextUtils.TruncateAt.END);
            wkVBinding.introMoreTv.setText(R.string.partner_expand_all);
        }
    }

    private void bindTags(PartnerProfileEntity data) {
        wkVBinding.tagLayout.removeAllViews();
        List<String> tags = data.getTagsSafe();
        if (tags.isEmpty()) {
            tagBaseVisible = false;
            wkVBinding.tagSection.setVisibility(View.GONE);
            return;
        }
        tagBaseVisible = true;
        wkVBinding.tagSection.setVisibility(View.VISIBLE);
        int max = Math.min(tags.size(), 20);
        for (int i = 0; i < max; i++) addChip(PartnerTagLocalizer.label(this, tags.get(i)), false);
        applyProfileContentVisibility();
    }

    private void addChip(String text, boolean isPlaceholder) {
        if (TextUtils.isEmpty(text)) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(isPlaceholder ? 0xFF8A8A96 : 0xFF626270);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setBackgroundResource(isPlaceholder ? R.drawable.bg_partner_tag_unselected : R.drawable.bg_partner_tag_chip);
        tv.setPadding(dp(14), dp(7), dp(14), dp(7));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) tv.setForeground(getSelectableItemBackground());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        lp.rightMargin = dp(8);
        wkVBinding.tagLayout.addView(tv, lp);
    }

    private android.graphics.drawable.Drawable getSelectableItemBackground() {
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        return getResources().getDrawable(outValue.resourceId);
    }

    private void bindPhotos(PartnerProfileEntity data) {
        // 主页先不展示“语伴图片”。这些图片只作为资料审核/以后动态素材，
        // 避免主页变成空白占位。后续接发现动态时再单独做动态列表。
        wkVBinding.photoLayout.removeAllViews();
        wkVBinding.photoCard.setVisibility(View.GONE);
    }

    private void bindActionButton(PartnerProfileEntity data) {
        if (isSelf) {
            wkVBinding.helloBar.setVisibility(View.GONE);
            wkVBinding.bottomActionSpace.setVisibility(View.GONE);
            return;
        }
        boolean blocked = isBlacklisted(data);
        boolean isFriend = isFriend(data);
        wkVBinding.helloBar.setVisibility(View.VISIBLE);
        wkVBinding.bottomActionSpace.setVisibility(View.VISIBLE);
        wkVBinding.helloBtnLayout.setEnabled(true);
        wkVBinding.helloBtnLayout.setAlpha(1f);
        if (blocked) {
            wkVBinding.helloBtnText.setText(R.string.partner_remove_blacklist);
        } else {
            wkVBinding.helloBtnText.setText(isFriend ? R.string.partner_send_message : R.string.partner_say_hello);
        }
        wkVBinding.helloBtnText.setAlpha(1f);
        wkVBinding.helloBtnProgress.setAlpha(0f);
        wkVBinding.helloBtnProgress.setVisibility(View.GONE);
        setHelloButtonWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        isSayHiLoading = false;
    }

    private void onMainActionClick() {
        if (isSayHiLoading) return;
        pressAndRun(wkVBinding.helloBtnLayout, () -> {
            if (isBlacklisted(profile)) {
                removeBlackList();
                return;
            }
            if (isFriend(profile)) {
                WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, uid, WKChannelType.PERSONAL, 0, false));
                return;
            }
            WKDialogUtils.getInstance().showInputDialog(this, getString(R.string.partner_say_hello), getString(R.string.partner_hello_hint), defaultGreeting(), defaultGreeting(), 40, text -> {
                String remark = TextUtils.isEmpty(text) ? defaultGreeting() : text;
                String vercode = profile == null ? "" : profile.vercode;
                animateButtonToProgress();
                FriendModel.getInstance().applyAddFriend(uid, vercode, remark, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        animateProgressToButton(true);
                    } else {
                        animateProgressToButton(false);
                        if (!TextUtils.isEmpty(msg)) showToast(msg);
                    }
                });
            });
        });
    }

    private boolean isFriend(PartnerProfileEntity data) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        return (data != null && data.follow == 1) || (channel != null && channel.follow == 1);
    }

    private boolean isBlacklisted(PartnerProfileEntity data) {
        return data != null && data.status == 2;
    }


    private void showSelfSystemMenu() {
        startActivity(new Intent(this, SettingActivity.class));
    }

    private void showProfileMoreMenu() {
        ArrayList<String> items = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();
        if (isFriend(profile)) {
            items.add(getString(R.string.partner_delete_friend));
            actions.add(1);
        }
        if (isBlacklisted(profile)) {
            items.add(getString(R.string.partner_remove_blacklist));
            actions.add(2);
        } else {
            items.add(getString(R.string.partner_add_blacklist));
            actions.add(3);
        }
        items.add(getString(R.string.partner_report));
        actions.add(4);
        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (dialog, which) -> handleMoreAction(actions.get(which)))
                .show();
    }

    private void handleMoreAction(int action) {
        if (action == 1) {
            confirmDeleteFriend();
        } else if (action == 2) {
            removeBlackList();
        } else if (action == 3) {
            addBlackList();
        } else {
            showToast(getString(R.string.partner_report_coming));
        }
    }

    private void confirmDeleteFriend() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.partner_delete_friend_confirm)
                .setNegativeButton(R.string.partner_cancel, null)
                .setPositiveButton(R.string.partner_confirm, (dialog, which) -> UserModel.getInstance().deleteUser(uid, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        showToast(getString(R.string.partner_delete_friend_success));
                        loadProfile();
                    } else if (!TextUtils.isEmpty(msg)) showToast(msg);
                }))
                .show();
    }

    private void addBlackList() {
        UserModel.getInstance().addBlackList(uid, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                showToast(getString(R.string.partner_blacklist_added));
                loadProfile();
            } else if (!TextUtils.isEmpty(msg)) showToast(msg);
        });
    }

    private void removeBlackList() {
        UserModel.getInstance().removeBlackList(uid, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                showToast(getString(R.string.partner_blacklist_removed));
                loadProfile();
            } else if (!TextUtils.isEmpty(msg)) showToast(msg);
        });
    }

    private void animateButtonToProgress() {
        isSayHiLoading = true;
        wkVBinding.helloBtnLayout.setEnabled(false);
        int initialWidth = wkVBinding.helloBtnLayout.getWidth();
        if (initialWidth <= 0) initialWidth = getAvailableButtonWidth();
        int targetWidth = dp(56);
        wkVBinding.helloBtnText.animate().alpha(0f).setDuration(120).start();
        wkVBinding.helloBtnProgress.setVisibility(View.VISIBLE);
        wkVBinding.helloBtnProgress.animate().alpha(1f).setDuration(180).setStartDelay(80).start();
        ValueAnimator anim = ValueAnimator.ofInt(initialWidth, targetWidth);
        anim.addUpdateListener(valueAnimator -> setHelloButtonWidth((Integer) valueAnimator.getAnimatedValue()));
        anim.setDuration(320);
        anim.setInterpolator(new OvershootInterpolator(0.9f));
        anim.start();
    }

    private void animateProgressToButton(boolean success) {
        int initialWidth = wkVBinding.helloBtnLayout.getWidth();
        if (initialWidth <= 0) initialWidth = dp(56);
        int targetWidth = getAvailableButtonWidth();
        wkVBinding.helloBtnProgress.animate().alpha(0f).setDuration(130).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                wkVBinding.helloBtnProgress.setVisibility(View.GONE);
                wkVBinding.helloBtnProgress.animate().setListener(null);
                wkVBinding.helloBtnText.setText(success ? R.string.partner_hello_sent : R.string.partner_say_hello);
                wkVBinding.helloBtnText.animate().alpha(1f).setDuration(180).start();
            }
        }).start();
        ValueAnimator anim = ValueAnimator.ofInt(initialWidth, targetWidth);
        anim.addUpdateListener(valueAnimator -> setHelloButtonWidth((Integer) valueAnimator.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setHelloButtonWidth(ViewGroup.LayoutParams.MATCH_PARENT);
                isSayHiLoading = false;
                wkVBinding.helloBtnLayout.setEnabled(!success);
                wkVBinding.helloBtnLayout.setAlpha(success ? 0.55f : 1f);
            }
        });
        anim.setDuration(300);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
    }

    private int getAvailableButtonWidth() {
        View parent = (View) wkVBinding.helloBtnLayout.getParent();
        int width = parent == null ? 0 : parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels - dp(48);
        return width;
    }

    private void setHelloButtonWidth(int width) {
        ViewGroup.LayoutParams lp = wkVBinding.helloBtnLayout.getLayoutParams();
        lp.width = width;
        wkVBinding.helloBtnLayout.setLayoutParams(lp);
    }

    private void pressAndRun(View view, Runnable runnable) {
        if (view == null || runnable == null) return;
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction(() -> {
            view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
            runnable.run();
        }).start();
    }

    private String defaultGreeting() {
        String learning = profile == null ? "" : formatLanguageLabels(profile.getLearningLanguagesSafe());
        if (TextUtils.isEmpty(learning)) return getString(R.string.partner_default_hello_plain);
        return String.format(getString(R.string.partner_default_hello_with_lang), learning);
    }

    private String formatLanguageLabels(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> labels = new ArrayList<>();
        for (String item : list) {
            String code = normalizeLangCode(item);
            if (!TextUtils.isEmpty(code) && !labels.contains(code)) labels.add(code);
        }
        return join(labels, " ");
    }

    private String formatLanguageLetters(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> labels = new ArrayList<>();
        for (String item : list) {
            String code = normalizeLangLetter(item);
            if (!TextUtils.isEmpty(code) && !labels.contains(code)) labels.add(code);
        }
        return join(labels, " ");
    }

    private String normalizeLangLetter(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim();
        String lower = v.toLowerCase(Locale.US);
        switch (lower) {
            case "zh": case "cn": case "中文": case "chinese": return "ZH";
            case "en": case "英语": case "english": return "EN";
            case "my": case "mm": case "burmese": case "myanmar": case "缅甸语": case "မြန်မာ": return "MY";
            case "th": case "thai": case "泰语": case "ไทย": return "TH";
            case "ja": case "jp": case "japanese": case "日语": case "日本語": return "JA";
            case "ko": case "kr": case "korean": case "韩语": case "한국어": return "KO";
            case "vi": case "vn": case "vietnamese": case "越南语": return "VI";
            case "id": case "indonesian": case "印尼语": return "ID";
            case "ms": case "malay": case "马来语": return "MS";
            default:
                String only = v.replaceAll("[^A-Za-z]", "");
                if (only.length() >= 2) return only.substring(0, Math.min(3, only.length())).toUpperCase(Locale.US);
                return v.toUpperCase(Locale.US);
        }
    }

    private String countryNameFromCode(String value) {
        String code = normalizeCountryCode(value);
        if (TextUtils.isEmpty(code)) return "";
        switch (code) {
            case "MM": return "缅甸";
            case "CN": return "中国";
            case "TH": return "泰国";
            case "JP": return "日本";
            case "KR": return "韩国";
            case "VN": return "越南";
            case "LA": return "老挝";
            case "KH": return "柬埔寨";
            case "MY": return "马来西亚";
            case "SG": return "新加坡";
            case "US": return "美国";
            default: return code;
        }
    }

    private String normalizeCountryCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim().toUpperCase(Locale.US);
        return countryCodeFromText(v);
    }

    private String normalizeLangCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim();
        String lower = v.toLowerCase(Locale.US);
        switch (lower) {
            case "zh": case "cn": case "中文": case "chinese": return "中文";
            case "en": case "英语": case "english": return "English";
            case "my": case "mm": case "burmese": case "myanmar": case "缅甸语": return "မြန်မာ";
            case "th": case "thai": case "泰语": return "ไทย";
            case "ja": case "jp": case "japanese": case "日语": return "日本語";
            case "ko": case "kr": case "korean": case "韩语": return "한국어";
            case "vi": case "vn": case "vietnamese": case "越南语": return "Tiếng Việt";
            case "id": case "indonesian": case "印尼语": return "Indonesia";
            case "ms": case "malay": case "马来语": return "Malay";
            default:
                String only = v.replaceAll("[^A-Za-z]", "");
                if (only.length() >= 2) return only.substring(0, Math.min(3, only.length())).toUpperCase(Locale.US);
                return v.toUpperCase(Locale.US);
        }
    }

    private String countryCodeFromText(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.toUpperCase(Locale.US);
        if (v.contains("MM") || v.contains("MYANMAR") || v.contains("缅甸") || v.contains("မြန်မာ")) return "MM";
        if (v.contains("CN") || v.contains("CHINA") || v.contains("中国")) return "CN";
        if (v.contains("TH") || v.contains("THAI") || v.contains("泰国")) return "TH";
        if (v.contains("JP") || v.contains("JAPAN") || v.contains("日本")) return "JP";
        if (v.contains("KR") || v.contains("KOREA") || v.contains("韩国")) return "KR";
        if (v.contains("VN") || v.contains("VIETNAM") || v.contains("越南")) return "VN";
        if (v.contains("LA") || v.contains("LAOS") || v.contains("老挝")) return "LA";
        if (v.contains("KH") || v.contains("CAMBODIA") || v.contains("柬埔寨")) return "KH";
        if (v.contains("MY") || v.contains("MALAYSIA") || v.contains("马来西亚")) return "MY";
        if (v.contains("SG") || v.contains("SINGAPORE") || v.contains("新加坡")) return "SG";
        if (v.contains("US") || v.contains("UNITED STATES") || v.contains("美国")) return "US";
        return v.length() == 2 ? v : "";
    }

    private int ageFromBirthday(String birthday) {
        if (TextUtils.isEmpty(birthday) || birthday.length() < 4) return 0;
        try {
            int year = Integer.parseInt(birthday.substring(0, 4));
            int current = Calendar.getInstance().get(Calendar.YEAR);
            int age = current - year;
            return age > 0 && age < 120 ? age : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private String join(List<String> values, String separator) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (TextUtils.isEmpty(v)) continue;
            if (sb.length() > 0) sb.append(separator);
            sb.append(v);
        }
        return sb.toString();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
