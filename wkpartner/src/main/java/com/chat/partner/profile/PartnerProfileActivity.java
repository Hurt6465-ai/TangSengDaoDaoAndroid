package com.chat.partner.profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKDialogUtils;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileBinding;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.service.FriendModel;
import com.google.android.material.appbar.AppBarLayout;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PartnerProfileActivity extends WKBaseActivity<ActPartnerProfileBinding> {

    // ──────────────────────────────────────────
    //  状态字段
    // ──────────────────────────────────────────
    private String uid;
    private boolean isSelf;
    private boolean isSayHiLoading;
    private PartnerProfileEntity profile;
    private boolean introExpanded;
    private boolean introCanExpand;

    /** 记录 AppBar 上一次的折叠百分比，用于过滤无效回调 */
    private float lastCollapsePercent = -1f;

    // ──────────────────────────────────────────
    //  生命周期
    // ──────────────────────────────────────────

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
        setupImmersiveStatusBar();
        wkVBinding.avatarView.setSize(96);

        // 主题色应用到打招呼按钮
        if (wkVBinding.helloBtnLayout.getBackground() != null) {
            wkVBinding.helloBtnLayout.getBackground().setTint(Theme.colorAccount);
        }

        // 自身 / 他人视图差异
        wkVBinding.editBtn.setVisibility(isSelf ? View.VISIBLE : View.GONE);
        wkVBinding.helloBar.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.bottomActionSpace.setVisibility(isSelf ? View.GONE : View.VISIBLE);

        // 默认封面占位
        wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);

        setupScrollLinkedHeader();
        runEntranceAnimation();
    }

    @Override
    protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> finish());
        wkVBinding.editBtn.setOnClickListener(v ->
                startActivity(new Intent(this, PartnerProfileEditActivity.class)));
        wkVBinding.helloBtnLayout.setOnClickListener(v -> onMainActionClick());
        wkVBinding.tagSection.setOnClickListener(v -> {
            if (isSelf) startActivity(new Intent(this, PartnerProfileEditActivity.class));
        });
        wkVBinding.introTv.setOnClickListener(v -> toggleIntroExpand());
        wkVBinding.introMoreTv.setOnClickListener(v -> toggleIntroExpand());
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

    // ──────────────────────────────────────────
    //  沉浸式状态栏
    // ──────────────────────────────────────────

    private void setupImmersiveStatusBar() {
        Window window = getWindow();
        if (window == null) return;
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        window.setStatusBarColor(Color.TRANSPARENT);
    }

    // ──────────────────────────────────────────
    //  入场动画（内容卡片从下微微弹入）
    // ──────────────────────────────────────────

    private void runEntranceAnimation() {
        wkVBinding.contentSheetLayout.setAlpha(0f);
        wkVBinding.contentSheetLayout.setTranslationY(dp(24));
        wkVBinding.contentSheetLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420)
                .setStartDelay(120)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
    }

    // ──────────────────────────────────────────
    //  滚动联动：标题淡入 + 封面视差缩放
    // ──────────────────────────────────────────

    private void setupScrollLinkedHeader() {
        wkVBinding.appBarLayout.addOnOffsetChangedListener(
                (AppBarLayout appBarLayout, int verticalOffset) -> {
                    int range = appBarLayout.getTotalScrollRange();
                    if (range <= 0) return;

                    float percent = Math.min(1f, Math.abs(verticalOffset) / (float) range);
                    // 过滤掉变化量过小的回调，减少不必要的绘制
                    if (Math.abs(percent - lastCollapsePercent) < 0.005f) return;
                    lastCollapsePercent = percent;

                    // 标题：从 80% 折叠开始淡入，至 100% 完全显示
                    float titleAlpha = (percent - 0.80f) / 0.20f;
                    wkVBinding.toolbarTitleTv.setAlpha(Math.max(0f, Math.min(1f, titleAlpha)));

                    // 封面轻微缩放（展开时放大 3%，折叠时还原）
                    float scale = 1f + 0.03f * (1f - percent);
                    wkVBinding.coverIv.setScaleX(scale);
                    wkVBinding.coverIv.setScaleY(scale);

                    // 顶部按钮随折叠透明度微调（折叠后按钮融入 scrim，略微降低对比度）
                    float btnAlpha = 1f - percent * 0.25f;
                    wkVBinding.backBtn.setAlpha(btnAlpha);
                    if (isSelf) wkVBinding.editBtn.setAlpha(btnAlpha);
                });
    }

    // ──────────────────────────────────────────
    //  数据加载与绑定
    // ──────────────────────────────────────────

    private void loadProfile() {
        PartnerProfileModel.getInstance().getUserProfile(uid, (code, msg, data) -> {
            if (code == HttpResponseCode.success && data != null) {
                profile = data;
                bindProfile(data);
            } else if (!TextUtils.isEmpty(msg)) {
                showToast(msg);
            }
        });
    }

    private void bindProfile(PartnerProfileEntity data) {
        String showName = firstNotEmpty(data.name, data.username, data.uid);
        wkVBinding.nameTv.setText(showName);
        wkVBinding.toolbarTitleTv.setText(showName);
        wkVBinding.avatarView.showAvatar(uid, WKChannelType.PERSONAL, data.avatar_cache_key);

        showCountryFlagIfSupported(data.country_code);
        bindCover(data.profile_cover);
        bindSexAge(data);
        bindLanguages(data);
        bindIntro(data);
        bindTags(data);
        bindPhotos(data);
        bindActionButton(data);
    }

    // ──────────────────────────────────────────
    //  各字段绑定
    // ──────────────────────────────────────────

    private void bindCover(String cover) {
        if (!TextUtils.isEmpty(cover)) {
            GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(cover), wkVBinding.coverIv);
        } else {
            wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        }
    }

    private void showCountryFlagIfSupported(String countryCode) {
        if (TextUtils.isEmpty(countryCode)) return;
        try {
            wkVBinding.avatarView.getClass()
                    .getMethod("showFlag", String.class)
                    .invoke(wkVBinding.avatarView, normalizeCountryCode(countryCode));
        } catch (Exception ignored) {
        }
    }

    private void bindSexAge(PartnerProfileEntity data) {
        int age = data.age > 0 ? data.age : ageFromBirthday(data.birthday);
        String gender = data.sex == 1 ? "♂" : (data.sex == 0 ? "♀" : "");
        String text;
        if (!TextUtils.isEmpty(gender) && age > 0)      text = gender + " " + age;
        else if (!TextUtils.isEmpty(gender))             text = gender;
        else if (age > 0)                                text = String.valueOf(age);
        else                                             text = "";
        wkVBinding.sexAgeTv.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        wkVBinding.sexAgeTv.setText(text);
    }

    private void bindLanguages(PartnerProfileEntity data) {
        String nativeText   = formatLanguageLabels(data.getNativeLanguagesSafe());
        String learningText = formatLanguageLabels(data.getLearningLanguagesSafe());
        boolean show = !TextUtils.isEmpty(nativeText) || !TextUtils.isEmpty(learningText);
        wkVBinding.langLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        wkVBinding.nativeLangTv.setText(nativeText);
        wkVBinding.learningLangTv.setText(learningText);

        // 语言行淡入动画（数据加载后）
        if (show) {
            wkVBinding.langLayout.setAlpha(0f);
            wkVBinding.langLayout.animate().alpha(1f).setDuration(300).setStartDelay(180).start();
        }
    }

    private void bindIntro(PartnerProfileEntity data) {
        introExpanded  = false;
        introCanExpand = false;
        String intro = TextUtils.isEmpty(data.intro)
                ? getString(R.string.partner_profile_intro_empty)
                : data.intro;
        wkVBinding.introTv.setText(intro);
        wkVBinding.introTv.setMaxLines(3);
        wkVBinding.introTv.setEllipsize(TextUtils.TruncateAt.END);
        wkVBinding.introMoreTv.setVisibility(View.GONE);
        wkVBinding.introMoreTv.setText("展开");

        wkVBinding.introTv.post(() -> {
            android.text.Layout layout = wkVBinding.introTv.getLayout();
            introCanExpand = layout != null
                    && layout.getLineCount() >= 3
                    && layout.getEllipsisCount(2) > 0;
            wkVBinding.introMoreTv.setVisibility(introCanExpand ? View.VISIBLE : View.GONE);
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
            wkVBinding.introTv.setMaxLines(3);
            wkVBinding.introTv.setEllipsize(TextUtils.TruncateAt.END);
            wkVBinding.introMoreTv.setText("展开");
        }
    }

    private void bindTags(PartnerProfileEntity data) {
        wkVBinding.tagLayout.removeAllViews();
        List<String> tags = data.getTagsSafe();
        if (tags.isEmpty()) {
            if (isSelf) {
                wkVBinding.tagSection.setVisibility(View.VISIBLE);
                addChip(getString(R.string.partner_choose_tags), true);
            } else {
                wkVBinding.tagSection.setVisibility(View.GONE);
            }
            return;
        }
        wkVBinding.tagSection.setVisibility(View.VISIBLE);
        int max = Math.min(tags.size(), 20);
        for (int i = 0; i < max; i++) addChip(tags.get(i), false);
    }

    /**
     * 创建标签 Chip。
     *
     * @param text      显示文字
     * @param isHint    是否为"点击添加"提示样式（文字颜色用主题色）
     */
    private void addChip(String text, boolean isHint) {
        if (TextUtils.isEmpty(text)) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(isHint ? 0xFF3B2BC7 : 0xFF555566);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setBackgroundResource(R.drawable.bg_partner_tag_unselected);
        tv.setPadding(dp(14), dp(6), dp(14), dp(6));
        tv.setForeground(getSelectableItemBackground());

        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        lp.rightMargin = dp(8);
        wkVBinding.tagLayout.addView(tv, lp);
    }

    private void bindPhotos(PartnerProfileEntity data) {
        wkVBinding.photoLayout.removeAllViews();
        List<String> photos = data.getProfileImagesSafe();
        if (photos.isEmpty()) {
            wkVBinding.photoCard.setVisibility(View.GONE);
            return;
        }
        wkVBinding.photoCard.setVisibility(View.VISIBLE);
        int max = Math.min(photos.size(), 6);
        for (int i = 0; i < max; i++) {
            String url = photos.get(i);
            if (TextUtils.isEmpty(url)) continue;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundResource(R.drawable.bg_partner_photo_placeholder);
            // 圆角裁剪（使用 outline provider 或直接依赖 placeholder 的 shape）
            iv.setClipToOutline(true);
            addViewCompat(wkVBinding.photoLayout, iv, dp(100), dp(100), i == 0 ? 0 : dp(8));
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

        // 底部栏入场动画
        wkVBinding.helloBar.setTranslationY(dp(80));
        wkVBinding.helloBar.setAlpha(0f);
        wkVBinding.helloBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(360)
                .setStartDelay(250)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
    }

    // ──────────────────────────────────────────
    //  打招呼按钮点击 & 动画
    // ──────────────────────────────────────────

    private void onMainActionClick() {
        if (isSayHiLoading) return;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        boolean isFriend = (profile != null && profile.follow == 1)
                || (channel != null && channel.follow == 1);
        if (isFriend) {
            WKIMUtils.getInstance().startChatActivity(
                    new ChatViewMenu(this, uid, WKChannelType.PERSONAL, 0, false));
            return;
        }
        WKDialogUtils.getInstance().showInputDialog(
                this,
                getString(R.string.partner_hello_hint),
                "",
                defaultGreeting(),
                defaultGreeting(),
                40,
                text -> {
                    String remark  = TextUtils.isEmpty(text) ? defaultGreeting() : text;
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
    }

    /**
     * 按钮 → 圆形进度条动画（收缩 + 文字淡出 + 进度条淡入）
     */
    private void animateButtonToProgress() {
        isSayHiLoading = true;
        wkVBinding.helloBtnLayout.setEnabled(false);

        int initialWidth = wkVBinding.helloBtnLayout.getWidth();
        if (initialWidth <= 0) initialWidth = getAvailableButtonWidth();
        int targetWidth = dp(52);

        // 文字淡出
        wkVBinding.helloBtnText.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(150)
                .start();

        // 进度条淡入
        wkVBinding.helloBtnProgress.setVisibility(View.VISIBLE);
        wkVBinding.helloBtnProgress.animate()
                .alpha(1f)
                .setDuration(200)
                .setStartDelay(100)
                .start();

        // 宽度收缩
        ValueAnimator anim = ValueAnimator.ofInt(initialWidth, targetWidth);
        anim.addUpdateListener(va -> setHelloButtonWidth((int) va.getAnimatedValue()));
        anim.setDuration(340);
        anim.setInterpolator(new OvershootInterpolator(0.8f));
        anim.start();
    }

    /**
     * 圆形进度条 → 按钮动画（展开 + 进度条淡出 + 文字淡入）
     */
    private void animateProgressToButton(boolean success) {
        int initialWidth = wkVBinding.helloBtnLayout.getWidth();
        if (initialWidth <= 0) initialWidth = dp(52);
        int targetWidth = getAvailableButtonWidth();

        // 进度条淡出
        wkVBinding.helloBtnProgress.animate()
                .alpha(0f)
                .setDuration(150)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        wkVBinding.helloBtnProgress.setVisibility(View.GONE);
                        wkVBinding.helloBtnProgress.animate().setListener(null);
                        wkVBinding.helloBtnText.setText(
                                success ? R.string.partner_hello_sent : R.string.partner_say_hello);
                        wkVBinding.helloBtnText.setScaleX(0.85f);
                        wkVBinding.helloBtnText.setScaleY(0.85f);
                        wkVBinding.helloBtnText.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(220)
                                .setInterpolator(new OvershootInterpolator(1.2f))
                                .start();
                    }
                }).start();

        // 宽度展开
        ValueAnimator anim = ValueAnimator.ofInt(initialWidth, targetWidth);
        anim.addUpdateListener(va -> setHelloButtonWidth((int) va.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setHelloButtonWidth(ViewGroup.LayoutParams.MATCH_PARENT);
                isSayHiLoading = false;
                wkVBinding.helloBtnLayout.setEnabled(!success);
                wkVBinding.helloBtnLayout.setAlpha(success ? 0.52f : 1f);
            }
        });
        anim.setDuration(320);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
    }

    // ──────────────────────────────────────────
    //  工具方法
    // ──────────────────────────────────────────

    private int getAvailableButtonWidth() {
        View parent = (View) wkVBinding.helloBtnLayout.getParent();
        int width = parent == null ? 0
                : parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels - dp(40);
        return width;
    }

    private void setHelloButtonWidth(int width) {
        ViewGroup.LayoutParams lp = wkVBinding.helloBtnLayout.getLayoutParams();
        lp.width = width;
        wkVBinding.helloBtnLayout.setLayoutParams(lp);
    }

    private String defaultGreeting() {
        String learning = profile == null ? ""
                : formatLanguageLabels(profile.getLearningLanguagesSafe());
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
        return join(labels, "/");
    }

    private String normalizeCountryCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim().toUpperCase(Locale.US);
        return countryCodeFromText(v);
    }

    private String normalizeLangCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String lower = value.trim().toLowerCase(Locale.US);
        switch (lower) {
            case "zh": case "cn": case "中文":  case "chinese":  return "ZH";
            case "en": case "英语": case "english":              return "EN";
            case "my": case "mm": case "burmese":
            case "myanmar": case "缅甸语":                       return "MY";
            case "th": case "thai":  case "泰语":                return "TH";
            case "ja": case "jp":   case "japanese": case "日语": return "JA";
            case "ko": case "kr":   case "korean":   case "韩语": return "KO";
            case "vi": case "vn":   case "vietnamese":case "越南语":return "VI";
            case "id": case "indonesian": case "印尼语":         return "ID";
            case "ms": case "malay": case "马来语":              return "MS";
            default:
                String only = value.trim().replaceAll("[^A-Za-z]", "");
                if (only.length() >= 2)
                    return only.substring(0, Math.min(3, only.length())).toUpperCase(Locale.US);
                return value.trim().toUpperCase(Locale.US);
        }
    }

    private String countryCodeFromText(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.toUpperCase(Locale.US);
        if (v.contains("MM") || v.contains("MYANMAR") || v.contains("缅甸") || v.contains("မြန်မာ")) return "MM";
        if (v.contains("CN") || v.contains("CHINA")   || v.contains("中国"))                         return "CN";
        if (v.contains("TH") || v.contains("THAI")    || v.contains("泰国"))                         return "TH";
        if (v.contains("JP") || v.contains("JAPAN")   || v.contains("日本"))                         return "JP";
        if (v.contains("KR") || v.contains("KOREA")   || v.contains("韩国"))                         return "KR";
        if (v.contains("VN") || v.contains("VIETNAM") || v.contains("越南"))                         return "VN";
        if (v.contains("LA") || v.contains("LAOS")    || v.contains("老挝"))                         return "LA";
        if (v.contains("KH") || v.contains("CAMBODIA")|| v.contains("柬埔寨"))                       return "KH";
        if (v.contains("MY") || v.contains("MALAYSIA")|| v.contains("马来西亚"))                     return "MY";
        if (v.contains("SG") || v.contains("SINGAPORE")|| v.contains("新加坡"))                      return "SG";
        if (v.contains("US") || v.contains("UNITED STATES") || v.contains("美国"))                   return "US";
        return "";
    }

    private int ageFromBirthday(String birthday) {
        if (TextUtils.isEmpty(birthday) || birthday.length() < 4) return 0;
        try {
            int year    = Integer.parseInt(birthday.substring(0, 4));
            int current = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            int age     = current - year;
            return age > 0 && age < 120 ? age : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) if (!TextUtils.isEmpty(v)) return v;
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

    private void addViewCompat(android.widget.LinearLayout parent, View child,
                               int width, int height, int marginStart) {
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(width, height);
        lp.gravity    = Gravity.CENTER_VERTICAL;
        lp.leftMargin = marginStart;
        parent.addView(child, lp);
    }

    private android.graphics.drawable.Drawable getSelectableItemBackground() {
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        return getResources().getDrawable(outValue.resourceId);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
