package com.chat.partner.profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKDialogUtils;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileBinding;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.service.FriendModel;
import com.google.android.material.appbar.AppBarLayout;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Date;
import java.util.Locale;

public class PartnerProfileActivity extends WKBaseActivity<ActPartnerProfileBinding> {
    private String uid;
    private String groupId;
    private boolean isSelf;
    private boolean isSayHiLoading;
    private boolean introExpanded;
    private boolean introCanExpand;
    private boolean hasAnimatedEntrance;
    private boolean introBaseVisible;
    private boolean tagBaseVisible;
    private boolean langBaseVisible;
    private boolean profileLastOnlineBaseVisible;
    private float currentCollapsePercent;
    private PartnerProfileEntity profile;

    @Override
    protected ActPartnerProfileBinding getViewBinding() {
        return ActPartnerProfileBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        uid = getIntent().getStringExtra(PartnerProfileRoute.EXTRA_UID);
        groupId = getIntent().getStringExtra(PartnerProfileRoute.EXTRA_GROUP_ID);
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
        setupImmersiveStatusBar();
        wkVBinding.avatarView.setSize(96);
        wkVBinding.editBtn.setVisibility(View.VISIBLE);
        wkVBinding.helloBar.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.bottomActionSpace.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        applyToolbarStyle(0f);
        setupScrollLinkedHeader();
        setupEntranceAnimation();
        resetInitialScrollState();
    }

    @Override
    protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> pressAndRun(v, this::finish));
        wkVBinding.editBtn.setOnClickListener(v -> pressAndRun(v, () -> {
            if (isSelf) {
                startActivity(new Intent(this, PartnerProfileEditActivity.class));
            } else {
                showToast("更多功能后续添加");
            }
        }));
        wkVBinding.helloBtnLayout.setOnClickListener(v -> onMainActionClick());
        wkVBinding.tagSection.setOnClickListener(v -> {
            if (isSelf) startActivity(new Intent(this, PartnerProfileEditActivity.class));
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
            applyToolbarStyle(0f);
            applyProfileContentVisibility(0f);
        });
    }

    private void setupImmersiveStatusBar() {
        Window window = getWindow();
        if (window == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        // Let the cover image draw behind the phone status bar, then push only
        // the toolbar content below the status-bar icons. This avoids the blue
        // status-bar strip that looked different from the cover image.
        int statusBarHeight = getStatusBarHeight();
        ViewGroup.LayoutParams toolbarLp = wkVBinding.toolbar.getLayoutParams();
        if (toolbarLp != null) {
            toolbarLp.height = statusBarHeight + dp(56);
            wkVBinding.toolbar.setLayoutParams(toolbarLp);
        }
        wkVBinding.toolbar.setPadding(0, statusBarHeight, 0, 0);
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        return result > 0 ? result : dp(24);
    }

    private void setupScrollLinkedHeader() {
        wkVBinding.appBarLayout.addOnOffsetChangedListener((AppBarLayout appBarLayout, int verticalOffset) -> {
            int range = appBarLayout.getTotalScrollRange();
            if (range <= 0) return;
            float percent = Math.min(1f, Math.max(0f, Math.abs(verticalOffset) * 1f / range));

            float scale = 1f + (0.04f * (1f - percent));
            wkVBinding.coverIv.setScaleX(scale);
            wkVBinding.coverIv.setScaleY(scale);

            currentCollapsePercent = percent;
            applyToolbarStyle(percent);
            applyProfileContentVisibility(percent);
        });
    }

    private void applyProfileContentVisibility(float percent) {
        // When the cover is pushed up, the white toolbar should be the only
        // thing left above the feed area. The profile header, intro and tags
        // fade out and stop drawing before the toolbar becomes fully white.
        float contentAlpha = clamp01((0.92f - percent) / 0.20f);
        boolean fullyCollapsed = percent >= 0.985f;
        applyFoldedVisibility(wkVBinding.profileHeaderLayout, true, contentAlpha, fullyCollapsed);
        applyFoldedVisibility(wkVBinding.langLayout, langBaseVisible, contentAlpha, fullyCollapsed);
        applyFoldedVisibility(wkVBinding.introSection, introBaseVisible, contentAlpha, fullyCollapsed);
        applyFoldedVisibility(wkVBinding.tagSection, tagBaseVisible, contentAlpha, fullyCollapsed);
        float lift = -dp(12) * percent;
        wkVBinding.profileHeaderLayout.setTranslationY(lift);
        wkVBinding.introSection.setTranslationY(lift);
        wkVBinding.tagSection.setTranslationY(lift);
        if (introCanExpand && introBaseVisible) {
            wkVBinding.introMoreTv.setVisibility(fullyCollapsed ? View.GONE : View.VISIBLE);
        }
    }

    private void applyFoldedVisibility(View view, boolean baseVisible, float alpha, boolean fullyCollapsed) {
        if (view == null) return;
        if (!baseVisible || fullyCollapsed) {
            view.setVisibility(View.GONE);
            view.setAlpha(1f);
            return;
        }
        view.setAlpha(alpha);
        view.setVisibility(View.VISIBLE);
    }

    private void applyToolbarStyle(float percent) {
        float titleAlpha = clamp01((percent - 0.58f) / 0.28f);
        wkVBinding.toolbarTitleLayout.setAlpha(titleAlpha);

        int bgAlpha = (int) (clamp01((percent - 0.48f) / 0.42f) * 255);
        wkVBinding.toolbar.setBackgroundColor(Color.argb(bgAlpha, 255, 255, 255));

        float iconT = clamp01((percent - 0.55f) / 0.35f);
        int iconColor = blendColor(0xFFFFFFFF, 0xFF202033, iconT);
        wkVBinding.backBtn.setColorFilter(iconColor);
        wkVBinding.editBtn.setColorFilter(iconColor);

        wkVBinding.toolbarTitleTv.setTextColor(0xFF202033);
        wkVBinding.toolbarCountryTv.setTextColor(0xFF8C8C99);
        wkVBinding.toolbarLastOnlineTv.setTextColor(0xFF8C8C99);
        setImageTint(wkVBinding.toolbarCountryIcon, 0xFF9A9AA6);
        setImageTint(wkVBinding.toolbarTimeIcon, 0xFF9A9AA6);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (percent > 0.72f) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void setImageTint(ImageView imageView, int color) {
        if (imageView != null) imageView.setColorFilter(color);
    }

    private float clamp01(float value) {
        return Math.min(1f, Math.max(0f, value));
    }

    private int blendColor(int from, int to, float ratio) {
        float t = clamp01(ratio);
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
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
        PartnerProfileModel.getInstance().getUserProfile(uid, groupId, (code, msg, data) -> {
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

    private void bindProfile(PartnerProfileEntity data) {
        String showName = firstNotEmpty(data.name, data.username, data.uid);
        wkVBinding.nameTv.setText(showName);
        wkVBinding.toolbarTitleTv.setText(showName);
        bindToolbarMeta(data);
        wkVBinding.avatarView.showAvatar(uid, WKChannelType.PERSONAL, data.avatar_cache_key);
        showCountryFlagIfSupported(firstNotEmpty(data.country_code, data.country));
        bindCover(data.profile_cover);
        bindSexAge(data);
        bindLanguages(data);
        bindIntro(data);
        bindTags(data);
        bindPhotos(data);
        bindActionButton(data);
        wkVBinding.onlineIndicator.setVisibility(data.status == 1 ? View.VISIBLE : View.GONE);
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

        wkVBinding.toolbarMetaLayout.setVisibility(hasCountry || hasLastOnline ? View.VISIBLE : View.GONE);
        wkVBinding.toolbarCountryGroup.setVisibility(hasCountry ? View.VISIBLE : View.GONE);
        wkVBinding.toolbarLastOnlineGroup.setVisibility(hasLastOnline ? View.VISIBLE : View.GONE);
        wkVBinding.toolbarCountryTv.setText(country);
        wkVBinding.toolbarLastOnlineTv.setText(lastOnline);

        profileLastOnlineBaseVisible = hasLastOnline;
        wkVBinding.profileLastOnlineGroup.setVisibility(hasLastOnline ? View.VISIBLE : View.GONE);
        wkVBinding.profileLastOnlineTv.setText(lastOnline);
        applyProfileContentVisibility(currentCollapsePercent);
    }

    private String formatLastOnline(PartnerProfileEntity data) {
        if (data == null) return "";
        if (data.status == 1) return "在线";
        String raw = firstNotEmpty(data.last_online, data.last_online_time, data.last_seen, data.last_seen_at,
                data.last_active_at, data.last_active_time, data.last_login_at, data.last_login_time);
        if (TextUtils.isEmpty(raw)) return "";
        long millis = parseTimeMillis(raw);
        if (millis > 0) {
            CharSequence relative = DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
            return "最后在线 " + relative;
        }
        return "最后在线 " + raw.trim();
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
        applyProfileContentVisibility(currentCollapsePercent);
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
        wkVBinding.introMoreTv.setText("展开全部");
        wkVBinding.introMoreTv.setVisibility(View.GONE);

        View.OnClickListener toggle = v -> toggleIntroExpand();
        wkVBinding.introTv.setOnClickListener(toggle);
        wkVBinding.introMoreTv.setOnClickListener(toggle);

        wkVBinding.introTv.post(() -> {
            android.text.Layout layout = wkVBinding.introTv.getLayout();
            introCanExpand = layout != null && layout.getLineCount() >= 2 && layout.getEllipsisCount(1) > 0;
            wkVBinding.introMoreTv.setVisibility(introCanExpand && currentCollapsePercent < 0.985f ? View.VISIBLE : View.GONE);
            applyProfileContentVisibility(currentCollapsePercent);
        });
    }

    private void toggleIntroExpand() {
        if (!introCanExpand) return;
        introExpanded = !introExpanded;
        if (introExpanded) {
            wkVBinding.introTv.setMaxLines(Integer.MAX_VALUE);
            wkVBinding.introTv.setEllipsize(null);
            wkVBinding.introMoreTv.setText("收起");
        } else {
            wkVBinding.introTv.setMaxLines(2);
            wkVBinding.introTv.setEllipsize(TextUtils.TruncateAt.END);
            wkVBinding.introMoreTv.setText("展开全部");
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
        for (int i = 0; i < max; i++) addChip(tags.get(i), false);
        applyProfileContentVisibility(currentCollapsePercent);
    }

    private void addChip(String text, boolean isPlaceholder) {
        if (TextUtils.isEmpty(text)) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(isPlaceholder ? 0xFF9999A8 : 0xFF5B3FE6);
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
        wkVBinding.photoLayout.removeAllViews();
        List<String> photos = data.getProfileImagesSafe();
        if (photos.isEmpty()) {
            wkVBinding.photoCard.setVisibility(View.GONE);
            return;
        }
        wkVBinding.photoCard.setVisibility(View.VISIBLE);
        int max = Math.min(photos.size(), 5);
        for (int i = 0; i < max; i++) {
            String url = photos.get(i);
            if (TextUtils.isEmpty(url)) continue;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundResource(R.drawable.bg_partner_photo_placeholder);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                iv.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                    }
                });
                iv.setClipToOutline(true);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(120));
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.leftMargin = i == 0 ? 0 : dp(10);
            wkVBinding.photoLayout.addView(iv, lp);
            GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(url), iv);
        }
    }

    private void bindActionButton(PartnerProfileEntity data) {
        if (isSelf) {
            wkVBinding.helloBar.setVisibility(View.GONE);
            wkVBinding.bottomActionSpace.setVisibility(View.GONE);
            return;
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        boolean isFriend = data.follow == 1 || (channel != null && channel.follow == 1);
        wkVBinding.helloBar.setVisibility(View.VISIBLE);
        wkVBinding.bottomActionSpace.setVisibility(View.VISIBLE);
        wkVBinding.helloBtnLayout.setEnabled(true);
        wkVBinding.helloBtnLayout.setAlpha(1f);
        wkVBinding.helloBtnText.setText(isFriend ? R.string.partner_send_message : R.string.partner_say_hello);
        wkVBinding.helloBtnText.setAlpha(1f);
        wkVBinding.helloBtnProgress.setAlpha(0f);
        wkVBinding.helloBtnProgress.setVisibility(View.GONE);
        setHelloButtonWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        isSayHiLoading = false;
    }

    private void onMainActionClick() {
        if (isSayHiLoading) return;
        pressAndRun(wkVBinding.helloBtnLayout, () -> {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
            boolean isFriend = (profile != null && profile.follow == 1) || (channel != null && channel.follow == 1);
            if (isFriend) {
                WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, uid, WKChannelType.PERSONAL, 0, false));
                return;
            }
            WKDialogUtils.getInstance().showInputDialog(this, getString(R.string.partner_hello_hint), "", defaultGreeting(), defaultGreeting(), 40, text -> {
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
        return join(labels, " / ");
    }

    private String formatLanguageLetters(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> labels = new ArrayList<>();
        for (String item : list) {
            String code = normalizeLangLetter(item);
            if (!TextUtils.isEmpty(code) && !labels.contains(code)) labels.add(code);
        }
        return join(labels, " / ");
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
