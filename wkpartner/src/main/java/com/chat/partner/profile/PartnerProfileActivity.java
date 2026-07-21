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

import com.alibaba.fastjson.JSONObject;
import com.chat.base.act.WKWebViewActivity;
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
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.setting.SettingActivity;
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
    private static final String TAG_FEED_WORKS = "partner_profile_feed_works";
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
    private String sourceVercode;
    private Fragment feedWorksFragment;
    private int lastFeedTopInset = -1;
    private int lastFeedBottomInset = -1;

    // These views were moved out of the old Toolbar/AppBar collapsing area. Use findViewById
    // instead of ViewBinding fields, so the Java file does not depend on generated binding fields
    // that may be absent until the updated XML is compiled.
    private View fixedProfileTopBar;
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
        sourceVercode = getIntent().getStringExtra(PartnerProfileRoute.EXTRA_VERCODE);
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
        wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        // 固定顶栏样式只设置一次。它永远是白底深色图标，不再随折叠百分比变化。
        applyFixedTopBarStyle();
        setupCoverParallax();
        setupEntranceAnimation();
        resetInitialScrollState();
    }

    @Override
    protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> pressAndRun(v, this::finish));
        wkVBinding.editBtn.setOnClickListener(v -> pressAndRun(v, () -> {
            if (isSelf) {
                startActivity(new Intent(this, SettingActivity.class));
            } else {
                showProfileMoreMenu();
            }
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
            applyProfileContentVisibility();
            updateWorksHeaderPin();
            invokeFeedMethod("scrollToTop", new Class<?>[0]);
        });
    }

    private void ensureFixedTopBarViews() {
        if (fixedProfileTopBar == null) fixedProfileTopBar = findViewById(R.id.fixedProfileTopBar);
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
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        return result > 0 ? result : dp(24);
    }

    // 封面折叠百分比只按封面本身计算。资料区现在也属于 AppBar，不能再用总滚动范围，
    // 否则封面已经消失后顶栏仍可能保持白色图标。
    private void setupCoverParallax() {
        wkVBinding.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int coverRange = Math.max(1, wkVBinding.collapsingToolbar.getHeight());
            float percent = Math.min(1f,
                    Math.max(0f, Math.abs(verticalOffset) * 1f / coverRange));
            currentCollapsePercent = percent;
            float scale = 1f + (0.04f * (1f - percent));
            wkVBinding.coverIv.setScaleX(scale);
            wkVBinding.coverIv.setScaleY(scale);
            applyProfileContentVisibility();
            updateWorksHeaderPin();
        });
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

    // ============ 作品区滚动状态 ============
    // 个人主页滚到作品区后，只保留最顶部的用户名栏。
    // “作品”标题属于 AppBar 内容，必须和头像、简介、语言、标签一起自然滚出屏幕，
    // 不能再创建第二条固定吸顶栏，否则顶部会显得拥挤并额外遮挡第一排作品。
    private void updateWorksHeaderPin() {
        ensureFixedTopBarViews();
        if (wkVBinding == null || feedWorksSection == null) return;

        boolean worksReachedTop = isWorksPinned();
        if (feedWorksTitleTv != null) {
            feedWorksTitleTv.setVisibility(View.VISIBLE);
        }

        // 作品区到达顶部后，把唯一保留的用户名栏切成白底深色模式。
        updateFixedTopBarAppearance(worksReachedTop);
        if (fixedProfileTopBar != null) fixedProfileTopBar.bringToFront();
        if (wkVBinding.helloBar.getVisibility() == View.VISIBLE) {
            wkVBinding.helloBar.bringToFront();
        }
        applyFeedHostInsets();
    }

    // 旧版会在这里定位第二条固定“作品”栏；新结构不再创建该覆盖层。
    private void positionWorksHeader() {
        // No-op by design.
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

    /**
     * Keep the first/last work card clear of the fixed top bars and the bottom action button.
     * The top inset is calculated from real screen positions, so it grows only when the
     * RecyclerView actually moves underneath an overlay; there is no blank gap while expanded.
     */
    private void applyFeedHostInsets() {
        ensureFixedTopBarViews();
        if (feedWorksContainer == null || feedWorksContainer.getVisibility() != View.VISIBLE) return;

        // 作品滚动时顶部只存在用户名栏，不再为“作品”标题预留第二层 inset。
        int overlayBottom = topBarHeight > 0 ? topBarHeight : dp(80);
        int containerTop = topInRoot(feedWorksContainer);
        int topInset = containerTop == Integer.MAX_VALUE
                ? 0 : Math.max(0, overlayBottom - containerTop);
        int bottomInset = isSelf ? dp(24) : dp(116);
        if (lastFeedBottomInset == bottomInset
                && lastFeedTopInset >= 0
                && Math.abs(lastFeedTopInset - topInset) < dp(2)) return;
        lastFeedTopInset = topInset;
        lastFeedBottomInset = bottomInset;
        invokeFeedMethod("setHostInsets",
                new Class<?>[]{int.class, int.class}, topInset, bottomInset);
    }

    private void invokeFeedMethod(String name, Class<?>[] parameterTypes, Object... args) {
        Fragment current = resolveFeedWorksFragment();
        if (current == null) return;
        try {
            current.getClass().getMethod(name, parameterTypes).invoke(current, args);
        } catch (Throwable ignored) {
        }
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
                if (TextUtils.isEmpty(data.vercode) && !TextUtils.isEmpty(sourceVercode)) {
                    data.vercode = sourceVercode;
                }
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
        bindProfileAvatar(data, showName);
        showCountryFlagIfSupported(firstNotEmpty(data.country_code, data.country));
        bindCover(data.profile_cover);
        bindSexAge(data);
        bindLanguages(data);
        bindIntro(data);
        bindTags(data);
        bindPhotos(data);
        bindFeedWorks();
        bindActionButton(data);
        wkVBinding.onlineIndicator.setVisibility(!isSelf && data.online == 1 ? View.VISIBLE : View.GONE);
    }

    private void bindProfileAvatar(PartnerProfileEntity data, String showName) {
        String targetUid = firstNotEmpty(data == null ? "" : data.uid, uid);
        String avatarPath = data == null ? "" : data.avatar;
        if (TextUtils.isEmpty(avatarPath) && !TextUtils.isEmpty(targetUid)) {
            // 陌生人通常还没有本地 WKChannel，个人主页必须直接请求用户头像接口，
            // 不能依赖 IM 频道资料同步后才显示。
            avatarPath = "users/" + targetUid + "/avatar";
        }
        wkVBinding.avatarView.showAvatarUrl(avatarPath,
                data == null ? "" : data.avatar_cache_key,
                firstNotEmpty(showName, targetUid),
                targetUid);
    }

    private void bindFeedWorks() {
        ensureFixedTopBarViews();
        if (TextUtils.isEmpty(uid) || feedWorksSection == null || feedWorksContainer == null) {
            hideFeedWorks();
            return;
        }

        try {
            Fragment current = resolveFeedWorksFragment();
            if (current == null || !fragmentMatchesProfileUid(current, uid)) {
                Class<?> routeClass = Class.forName("com.chat.feed.FeedRoute");
                Object fragmentObj = routeClass
                        .getMethod("newUserWaterfallFragment", String.class)
                        .invoke(null, uid);
                if (!(fragmentObj instanceof Fragment)) {
                    hideFeedWorks();
                    return;
                }
                current = (Fragment) fragmentObj;
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.feedWorksContainer, current, TAG_FEED_WORKS)
                        .commitAllowingStateLoss();
            }

            // Reuse the Fragment that is actually attached to the container. Profile refreshes
            // must not replace the visible list with a detached Fragment or reset its pagination.
            feedWorksFragment = current;
            lastFeedTopInset = -1;
            lastFeedBottomInset = -1;
            feedWorksSection.setVisibility(View.VISIBLE);
            feedWorksContainer.setVisibility(View.VISIBLE);
            feedWorksContainer.post(this::updateWorksHeaderPin);
            feedWorksContainer.postDelayed(this::updateWorksHeaderPin, 120);
        } catch (Throwable ignored) {
            // wkfeed is optional. Its absence must not break the profile page.
            hideFeedWorks();
        }
    }

    private Fragment resolveFeedWorksFragment() {
        Fragment current = getSupportFragmentManager().findFragmentByTag(TAG_FEED_WORKS);
        if (current == null) {
            current = getSupportFragmentManager().findFragmentById(R.id.feedWorksContainer);
        }
        if (current != null) feedWorksFragment = current;
        return current != null ? current : feedWorksFragment;
    }

    private boolean fragmentMatchesProfileUid(Fragment fragment, String expectedUid) {
        if (fragment == null || TextUtils.isEmpty(expectedUid) || fragment.getArguments() == null) {
            return false;
        }
        String fragmentUid = fragment.getArguments().getString("uid", "");
        return TextUtils.equals(fragmentUid, expectedUid);
    }

    private void hideFeedWorks() {
        if (wkVBinding == null) return;
        if (feedWorksSection != null) feedWorksSection.setVisibility(View.GONE);
        if (feedWorksContainer != null) feedWorksContainer.setVisibility(View.GONE);
        if (feedWorksTitleTv != null) feedWorksTitleTv.setVisibility(View.VISIBLE);
        feedWorksFragment = null;
        lastFeedTopInset = -1;
        lastFeedBottomInset = -1;
        updateFixedTopBarAppearance(false);
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
            if (toolbarMetaLayout != null) toolbarMetaLayout.setVisibility(hasCountry ? View.VISIBLE : View.GONE);
            if (toolbarCountryGroup != null) toolbarCountryGroup.setVisibility(hasCountry ? View.VISIBLE : View.GONE);
            if (toolbarLastOnlineGroup != null) toolbarLastOnlineGroup.setVisibility(View.GONE);
            if (toolbarCountryTv != null) toolbarCountryTv.setText(country);
            if (toolbarLastOnlineTv != null) toolbarLastOnlineTv.setText("");

            profileLastOnlineBaseVisible = false;
            wkVBinding.profileLastOnlineGroup.setVisibility(View.GONE);
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
        if (data.online == 1) return getString(R.string.partner_online);

        // /v1/users/{uid} 返回的是 last_offline（秒级时间戳）。旧代码误读 status，
        // 导致所有正常账号（status=1）都显示在线，同时离线时间也永远取不到。
        long lastOfflineMillis = normalizeEpochMillis(data.last_offline);
        if (lastOfflineMillis > 0) {
            CharSequence relative = DateUtils.getRelativeTimeSpanString(lastOfflineMillis, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
            return getString(R.string.partner_last_online_format, relative);
        }

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

    private long normalizeEpochMillis(long value) {
        if (value <= 0) return 0L;
        return value < 100000000000L ? value * 1000L : value;
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
        List<String> tags = PartnerTagLocalizer.toDisplayList(this, data.getTagsSafe());
        if (tags.isEmpty()) {
            tagBaseVisible = false;
            wkVBinding.tagSection.setVisibility(View.GONE);
            return;
        }
        tagBaseVisible = true;
        wkVBinding.tagSection.setVisibility(View.VISIBLE);
        int max = Math.min(tags.size(), 20);
        for (int i = 0; i < max; i++) addChip(tags.get(i), false);
        applyProfileContentVisibility();
    }

    private void addChip(String text, boolean isPlaceholder) {
        if (TextUtils.isEmpty(text)) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tv.setEllipsize(null);
        tv.setMaxWidth(dp(180));
        tv.setIncludeFontPadding(true);

        if (isPlaceholder) {
            tv.setTextColor(0xFF9999A8);
            tv.setBackgroundResource(R.drawable.bg_partner_tag_unselected);
        } else {
            // Stable color by tag text: scrolling/rebinding never changes the color, while
            // adjacent tags are visually separated by several soft pill backgrounds.
            int style = (text.hashCode() & Integer.MAX_VALUE) % 5;
            int background;
            int textColor;
            switch (style) {
                case 1:
                    background = R.drawable.bg_partner_tag_chip_mint;
                    textColor = 0xFF347D67;
                    break;
                case 2:
                    background = R.drawable.bg_partner_tag_chip_blue;
                    textColor = 0xFF3974A4;
                    break;
                case 3:
                    background = R.drawable.bg_partner_tag_chip_peach;
                    textColor = 0xFFAA653E;
                    break;
                case 4:
                    background = R.drawable.bg_partner_tag_chip_pink;
                    textColor = 0xFFA34E77;
                    break;
                default:
                    background = R.drawable.bg_partner_profile_tag_chip_lavender;
                    textColor = 0xFF6550B8;
                    break;
            }
            tv.setTextColor(textColor);
            tv.setBackgroundResource(background);
        }

        tv.setPadding(dp(12), dp(5), dp(12), dp(5));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tv.setForeground(getSelectableItemBackground());
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(7);
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
            if (feedWorksContainer != null) feedWorksContainer.post(this::applyFeedHostInsets);
            return;
        }
        boolean blocked = isBlacklisted(data);
        boolean isFriend = isFriend(data);
        wkVBinding.helloBar.setVisibility(View.VISIBLE);
        wkVBinding.helloBtnLayout.setEnabled(true);
        wkVBinding.helloBtnLayout.setAlpha(1f);
        if (blocked) {
            wkVBinding.helloBtnText.setText(R.string.partner_remove_blacklist);
        } else {
            wkVBinding.helloBtnText.setText(isFriend ? R.string.partner_send_message : R.string.partner_add_friend);
        }
        wkVBinding.helloBtnText.setAlpha(1f);
        wkVBinding.helloBtnProgress.setAlpha(0f);
        wkVBinding.helloBtnProgress.setVisibility(View.GONE);
        setHelloButtonWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        isSayHiLoading = false;
        if (feedWorksContainer != null) feedWorksContainer.post(this::applyFeedHostInsets);
    }

    private void onMainActionClick() {
        // This button sends a real friend application. The entered text is the verification
        // message attached to that application; it is not the partner greeting/private-message API.
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
            WKDialogUtils.getInstance().showInputDialog(this, getString(R.string.partner_add_friend), getString(R.string.partner_add_friend_hint), defaultGreeting(), defaultGreeting(), 40, text -> {
                String remark = TextUtils.isEmpty(text) ? defaultGreeting() : text;
                String vercode = profile == null ? "" : profile.vercode;
                if (TextUtils.isEmpty(vercode)) vercode = sourceVercode;
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
            openReportPage();
        }
    }

    private void openReportPage() {
        if (TextUtils.isEmpty(uid)) {
            showToast(getString(R.string.partner_report_open_failed));
            return;
        }
        try {
            Intent intent = new Intent(this, WKWebViewActivity.class);
            intent.putExtra("channelType", WKChannelType.PERSONAL);
            intent.putExtra("channelID", uid);
            intent.putExtra("url", WKApiConfig.baseWebUrl + "report.html");
            startActivity(intent);
        } catch (Exception ignored) {
            showToast(getString(R.string.partner_report_open_failed));
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
                wkVBinding.helloBtnText.setText(success ? R.string.partner_friend_apply_sent : R.string.partner_add_friend);
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
