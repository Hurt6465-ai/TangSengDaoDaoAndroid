package com.chat.partner.profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PartnerProfileActivity extends WKBaseActivity<ActPartnerProfileBinding> {
    private String uid;
    private boolean isSelf;
    private boolean isSayHiLoading;
    private boolean introExpanded;
    private boolean introCanExpand;
    private boolean hasAnimatedEntrance;
    private PartnerProfileEntity profile;

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
        wkVBinding.avatarView.setSize(90);
        wkVBinding.toolbarAvatarView.setSize(26);
        wkVBinding.backBtn.setVisibility(View.GONE);
        wkVBinding.editBtn.setVisibility(isSelf ? View.VISIBLE : View.GONE);
        wkVBinding.helloBar.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.bottomActionSpace.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        setupScrollLinkedHeader();
        setupEntranceAnimation();
    }

    @Override
    protected void initListener() {
        wkVBinding.editBtn.setOnClickListener(v -> pressAndRun(v, () -> startActivity(new Intent(this, PartnerProfileEditActivity.class))));
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

    private void setupImmersiveStatusBar() {
        Window window = getWindow();
        if (window == null) return;
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        window.setStatusBarColor(Color.TRANSPARENT);
    }

    private void setupScrollLinkedHeader() {
        wkVBinding.appBarLayout.addOnOffsetChangedListener((AppBarLayout appBarLayout, int verticalOffset) -> {
            int range = appBarLayout.getTotalScrollRange();
            if (range <= 0) return;
            float percent = Math.min(1f, Math.max(0f, Math.abs(verticalOffset) * 1f / range));

            float titleAlpha = (percent - 0.58f) / 0.42f;
            wkVBinding.toolbarTitleLayout.setAlpha(Math.max(0f, Math.min(1f, titleAlpha)));

            float scale = 1f + (0.035f * (1f - percent));
            wkVBinding.coverIv.setScaleX(scale);
            wkVBinding.coverIv.setScaleY(scale);

            int scrimAlpha = (int) (Math.max(0f, (percent - 0.72f) / 0.28f) * 210);
            wkVBinding.toolbar.setBackgroundColor(Color.argb(scrimAlpha, 108, 77, 255));
        });
    }

    private void setupEntranceAnimation() {
        wkVBinding.contentSheetLayout.setAlpha(0f);
        wkVBinding.contentSheetLayout.setTranslationY(dp(32));
    }

    private void playEntranceAnimation() {
        if (hasAnimatedEntrance) return;
        hasAnimatedEntrance = true;
        wkVBinding.contentSheetLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();

        wkVBinding.avatarGlowLayout.setScaleX(0.75f);
        wkVBinding.avatarGlowLayout.setScaleY(0.75f);
        wkVBinding.avatarGlowLayout.setAlpha(0f);
        wkVBinding.avatarGlowLayout.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(420)
                .setStartDelay(120)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .start();
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

    private void bindProfile(PartnerProfileEntity data) {
        String showName = firstNotEmpty(data.name, data.username, data.uid);
        wkVBinding.nameTv.setText(showName);
        wkVBinding.toolbarTitleTv.setText(showName);
        wkVBinding.avatarView.showAvatar(uid, WKChannelType.PERSONAL, data.avatar_cache_key);
        wkVBinding.toolbarAvatarView.showAvatar(uid, WKChannelType.PERSONAL, data.avatar_cache_key);
        showCountryFlagIfSupported(data.country_code);
        tuneProfileAvatarBadges();
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
            wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        }
    }

    private void showCountryFlagIfSupported(String countryCode) {
        if (TextUtils.isEmpty(countryCode)) return;
        try {
            wkVBinding.avatarView.getClass().getMethod("showFlag", String.class).invoke(wkVBinding.avatarView, normalizeCountryCode(countryCode));
        } catch (Exception ignored) {
        }
    }

    private void tuneProfileAvatarBadges() {
        try {
            Field field = wkVBinding.avatarView.getClass().getDeclaredField("flagIv");
            field.setAccessible(true);
            Object obj = field.get(wkVBinding.avatarView);
            if (obj instanceof ImageView) {
                ImageView flagIv = (ImageView) obj;
                FrameLayout.LayoutParams flagLp = new FrameLayout.LayoutParams(dp(17), dp(12), Gravity.BOTTOM | Gravity.START);
                flagLp.leftMargin = dp(7);
                flagLp.bottomMargin = dp(8);
                flagIv.setLayoutParams(flagLp);
                flagIv.setScaleType(ImageView.ScaleType.FIT_XY);
                flagIv.bringToFront();
            }
        } catch (Exception ignored) {
        }
    }

    private void bindSexAge(PartnerProfileEntity data) {
        int age = data.age > 0 ? data.age : ageFromBirthday(data.birthday);
        String text = "";
        if (data.sex == 1 && age > 0) text = "男 " + age;
        else if (data.sex == 0 && age > 0) text = "女 " + age;
        else if (data.sex == 1) text = "男";
        else if (data.sex == 0) text = "女";
        else if (age > 0) text = String.valueOf(age);

        wkVBinding.sexAgeTv.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        wkVBinding.sexAgeTv.setText(text);
        if (data.sex == 1) {
            wkVBinding.sexAgeTv.setTextColor(0xFF4A8FD9);
            tintBackground(wkVBinding.sexAgeTv, 0xFFEEF5FF);
        } else {
            wkVBinding.sexAgeTv.setTextColor(0xFFE05C9E);
            tintBackground(wkVBinding.sexAgeTv, 0xFFFFF1F8);
        }
    }

    private void tintBackground(View view, int color) {
        if (view.getBackground() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.getBackground().setTint(color);
        }
    }

    private void bindLanguages(PartnerProfileEntity data) {
        String nativeText = formatLanguageLabels(data.getNativeLanguagesSafe());
        String learningText = formatLanguageLabels(data.getLearningLanguagesSafe());
        boolean show = !TextUtils.isEmpty(nativeText) || !TextUtils.isEmpty(learningText);
        wkVBinding.langLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        wkVBinding.nativeLangTv.setText(nativeText);
        wkVBinding.learningLangTv.setText(learningText);
    }

    private void bindIntro(PartnerProfileEntity data) {
        introExpanded = false;
        introCanExpand = false;
        String intro = TextUtils.isEmpty(data.intro) ? getString(R.string.partner_profile_intro_empty) : data.intro;
        wkVBinding.introTv.setText(intro);
        wkVBinding.introTv.setMaxLines(3);
        wkVBinding.introTv.setEllipsize(TextUtils.TruncateAt.END);
        wkVBinding.introMoreTv.setVisibility(View.GONE);
        wkVBinding.introMoreTv.setText("展开全部");
        View.OnClickListener toggle = v -> toggleIntroExpand();
        wkVBinding.introTv.setOnClickListener(toggle);
        wkVBinding.introMoreTv.setOnClickListener(toggle);
        wkVBinding.introTv.post(() -> {
            android.text.Layout layout = wkVBinding.introTv.getLayout();
            introCanExpand = layout != null && layout.getLineCount() >= 3 && layout.getEllipsisCount(2) > 0;
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
            wkVBinding.introMoreTv.setText("展开全部");
        }
    }

    private void bindTags(PartnerProfileEntity data) {
        wkVBinding.tagLayout.removeAllViews();
        // 主页先不展示标签，标签仍然在编辑页选择和保存。
        wkVBinding.tagSection.setVisibility(View.GONE);
    }

    private void addChip(String text, boolean isPlaceholder) {
        if (TextUtils.isEmpty(text)) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(isPlaceholder ? 0xFFAAAAAA : 0xFF6C4DFF);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setBackgroundResource(isPlaceholder ? R.drawable.bg_partner_tag_unselected : R.drawable.bg_partner_tag_chip);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
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
            iv.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14)); }
            });
            iv.setClipToOutline(true);
            iv.setBackgroundResource(R.drawable.bg_partner_photo_placeholder);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(112));
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
        int targetWidth = dp(54);
        wkVBinding.helloBtnText.animate().alpha(0f).setDuration(120).start();
        wkVBinding.helloBtnProgress.setVisibility(View.VISIBLE);
        wkVBinding.helloBtnProgress.animate().alpha(1f).setDuration(180).setStartDelay(80).start();
        ValueAnimator anim = ValueAnimator.ofInt(initialWidth, targetWidth);
        anim.addUpdateListener(va -> setHelloButtonWidth((Integer) va.getAnimatedValue()));
        anim.setDuration(320);
        anim.setInterpolator(new OvershootInterpolator(0.9f));
        anim.start();
    }

    private void animateProgressToButton(boolean success) {
        int initialWidth = wkVBinding.helloBtnLayout.getWidth();
        if (initialWidth <= 0) initialWidth = dp(54);
        int targetWidth = getAvailableButtonWidth();
        wkVBinding.helloBtnProgress.animate().alpha(0f).setDuration(130).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                wkVBinding.helloBtnProgress.setVisibility(View.GONE);
                wkVBinding.helloBtnProgress.animate().setListener(null);
                wkVBinding.helloBtnText.setText(success ? R.string.partner_hello_sent : R.string.partner_say_hello);
                wkVBinding.helloBtnText.animate().alpha(1f).setDuration(180).start();
            }
        }).start();
        ValueAnimator anim = ValueAnimator.ofInt(initialWidth, targetWidth);
        anim.addUpdateListener(va -> setHelloButtonWidth((Integer) va.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
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

    private String normalizeCountryCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim().toUpperCase(Locale.US);
        if ("MY".equals(v)) return "MY";
        return countryCodeFromText(v);
    }

    private String normalizeLangCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim();
        String lower = v.toLowerCase(Locale.US);
        switch (lower) {
            case "zh": case "cn": case "中文": case "chinese": return "ZH";
            case "en": case "英语": case "english": return "EN";
            case "my": case "mm": case "burmese": case "myanmar": case "缅甸语": return "MY";
            case "th": case "thai": case "泰语": return "TH";
            case "ja": case "jp": case "japanese": case "日语": return "JP";
            case "ko": case "kr": case "korean": case "韩语": return "KR";
            case "vi": case "vn": case "vietnamese": case "越南语": return "VN";
            case "id": case "indonesian": case "印尼语": return "ID";
            case "ms": case "malay": case "马来语": return "MS";
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
        return "";
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

    private void pressAndRun(View view, Runnable action) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction(() -> {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction(action).start();
        }).start();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
