package com.chat.partner.profile;

import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.systembar.WKStatusBarUtils;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileBinding;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.service.FriendModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PartnerProfileActivity extends WKBaseActivity<ActPartnerProfileBinding> {
    private String uid;
    private boolean isSelf;
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
        Window window = getWindow();
        if (window != null) WKStatusBarUtils.setLightMode(window);
    }

    @Override
    protected void initView() {
        wkVBinding.avatarView.setSize(78);
        wkVBinding.helloBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.editBtn.setVisibility(isSelf ? View.VISIBLE : View.GONE);
        wkVBinding.helloBar.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.bottomActionSpace.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        applyStatusBarSafeTop();
    }

    @Override
    protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> finish());
        wkVBinding.editBtn.setOnClickListener(v -> startActivity(new Intent(this, PartnerProfileEditActivity.class)));
        wkVBinding.helloBtn.setOnClickListener(v -> onMainActionClick());
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

    private void applyStatusBarSafeTop() {
        int statusBar = WKStatusBarUtils.getStatusBarHeight(this);
        setTopMargin(wkVBinding.backBtn, statusBar + dp(12));
        setTopMargin(wkVBinding.editBtn, statusBar + dp(14));
    }

    private void setTopMargin(View view, int topMargin) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) lp;
            marginLp.topMargin = topMargin;
            view.setLayoutParams(marginLp);
        }
    }

    private void bindProfile(PartnerProfileEntity data) {
        String showName = firstNotEmpty(data.name, data.username, data.uid);
        wkVBinding.nameTv.setText(showName);
        wkVBinding.avatarView.showAvatar(uid, WKChannelType.PERSONAL, data.avatar_cache_key);
        showCountryFlagIfSupported(data.country_code);
        if (!TextUtils.isEmpty(data.profile_cover)) {
            GlideUtils.getInstance().showImg(this, data.profile_cover, wkVBinding.coverIv);
        } else {
            wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
        }
        bindRole(data);
        bindSexAge(data);
        bindCountry(data);
        bindLanguages(data);
        bindIntro(data);
        bindTags(data);
        bindPhotos(data);
        bindActionButton(data);
    }

    private void showCountryFlagIfSupported(String countryCode) {
        if (TextUtils.isEmpty(countryCode)) return;
        try {
            wkVBinding.avatarView.getClass().getMethod("showFlag", String.class).invoke(wkVBinding.avatarView, countryCode);
        } catch (Exception ignored) {
            // 兼容还没有接国旗版 AvatarView 的工程。
        }
    }

    private void bindRole(PartnerProfileEntity data) {
        String roleText = getRoleText(data);
        wkVBinding.roleTv.setVisibility(TextUtils.isEmpty(roleText) ? View.GONE : View.VISIBLE);
        wkVBinding.roleTv.setText(roleText);
    }

    private void bindSexAge(PartnerProfileEntity data) {
        List<String> parts = new ArrayList<>();
        if (data.sex == 1) parts.add("♂");
        else if (data.sex == 0) parts.add("♀");
        int age = data.age > 0 ? data.age : ageFromBirthday(data.birthday);
        if (age > 0) parts.add(String.valueOf(age));
        String text = join(parts, "  ");
        wkVBinding.sexAgeTv.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        wkVBinding.sexAgeTv.setText(text);
    }

    private void bindCountry(PartnerProfileEntity data) {
        String text = formatCountry(data.country_code, data.country);
        wkVBinding.countryTv.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        wkVBinding.countryTv.setText(text);
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
        wkVBinding.introTv.setText(TextUtils.isEmpty(data.intro) ? getString(R.string.partner_profile_intro_empty) : data.intro);
    }

    private void bindTags(PartnerProfileEntity data) {
        wkVBinding.tagLayout.removeAllViews();
        List<String> tags = data.getTagsSafe();
        if (tags.isEmpty()) {
            wkVBinding.tagCard.setVisibility(View.GONE);
            return;
        }
        wkVBinding.tagCard.setVisibility(View.VISIBLE);
        for (String tag : tags) addChip(tag);
    }

    private void addChip(String text) {
        if (TextUtils.isEmpty(text)) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(0xFFFFFFFF);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setBackgroundResource(R.drawable.bg_partner_glass_chip);
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));
        wkVBinding.tagLayout.addView(tv);
    }

    private void bindPhotos(PartnerProfileEntity data) {
        wkVBinding.photoLayout.removeAllViews();
        List<String> photos = data.getProfileImagesSafe();
        if (photos.isEmpty()) {
            wkVBinding.photoCard.setVisibility(View.GONE);
            return;
        }
        wkVBinding.photoCard.setVisibility(View.VISIBLE);
        int max = Math.min(photos.size(), 9);
        for (int i = 0; i < max; i++) {
            String url = photos.get(i);
            if (TextUtils.isEmpty(url)) continue;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundResource(R.drawable.bg_partner_photo_placeholder);
            LinearLayoutCompat.addViewCompat(wkVBinding.photoLayout, iv, dp(92), dp(92), i == 0 ? 0 : dp(8));
            GlideUtils.getInstance().showImg(this, url, iv);
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
        wkVBinding.helloBtn.setEnabled(true);
        wkVBinding.helloBtn.setAlpha(1f);
        wkVBinding.helloBtn.setText(isFriend ? R.string.partner_send_message : R.string.partner_say_hello);
    }

    private void onMainActionClick() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        boolean isFriend = (profile != null && profile.follow == 1) || (channel != null && channel.follow == 1);
        if (isFriend) {
            WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, uid, WKChannelType.PERSONAL, 0, true));
            return;
        }
        showGreetingDialog();
    }

    private void showGreetingDialog() {
        WKDialogUtils.getInstance().showInputDialog(this, getString(R.string.partner_say_hello), getString(R.string.partner_hello_hint), defaultGreeting(), getString(R.string.partner_hello_hint), 40, text -> {
            String remark = TextUtils.isEmpty(text) ? defaultGreeting() : text;
            String vercode = profile == null ? "" : profile.vercode;
            FriendModel.getInstance().applyAddFriend(uid, vercode, remark, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    wkVBinding.helloBtn.setText(R.string.partner_hello_sent);
                    wkVBinding.helloBtn.setEnabled(false);
                    wkVBinding.helloBtn.setAlpha(0.45f);
                } else if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
            });
        });
    }

    private String defaultGreeting() {
        String learning = profile == null ? "" : formatLanguageLabels(profile.getLearningLanguagesSafe());
        if (TextUtils.isEmpty(learning)) return getString(R.string.partner_default_hello_plain);
        return String.format(getString(R.string.partner_default_hello_with_lang), learning);
    }

    private String getRoleText(PartnerProfileEntity data) {
        String raw = firstNotEmpty(data.role, data.category);
        if (TextUtils.isEmpty(raw)) return "";
        String role = raw.toLowerCase(Locale.US);
        if (role.contains("teacher")) return getString(R.string.partner_teacher);
        if (role.contains("admin")) return getString(R.string.partner_admin);
        if (role.contains("official") || role.contains("system")) return getString(R.string.partner_official);
        if (role.contains("service") || role.contains("customer")) return getString(R.string.partner_service);
        return "";
    }

    private String formatCountry(String code, String name) {
        String safeCode = normalizeCountryCode(code);
        String label = firstNotEmpty(name, countryNameByCode(safeCode));
        if (TextUtils.isEmpty(safeCode) && TextUtils.isEmpty(label)) return "";
        return countryFlag(safeCode) + " " + firstNotEmpty(label, safeCode);
    }

    private String formatLanguageLabels(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> labels = new ArrayList<>();
        for (String item : list) {
            String code = normalizeLangCode(item);
            if (!TextUtils.isEmpty(code)) labels.add(languageFlag(code) + " " + code);
        }
        return join(labels, "  ");
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
            case "ja": case "jp": case "japanese": case "日语": return "JA";
            case "ko": case "kr": case "korean": case "韩语": return "KO";
            case "vi": case "vn": case "vietnamese": case "越南语": return "VI";
            case "id": case "indonesian": case "印尼语": return "ID";
            case "ms": case "malay": case "马来语": return "MS";
            default:
                String only = v.replaceAll("[^A-Za-z]", "");
                if (only.length() >= 2) return only.substring(0, Math.min(3, only.length())).toUpperCase(Locale.US);
                return v.toUpperCase(Locale.US);
        }
    }

    private String languageFlag(String code) {
        switch (code) {
            case "ZH": return "🇨🇳";
            case "EN": return "🇺🇸";
            case "MY": return "🇲🇲";
            case "TH": return "🇹🇭";
            case "JA": return "🇯🇵";
            case "KO": return "🇰🇷";
            case "VI": return "🇻🇳";
            case "ID": return "🇮🇩";
            case "MS": return "🇲🇾";
            default: return "🏳️";
        }
    }

    private String countryFlag(String code) {
        switch (code) {
            case "MM": return "🇲🇲";
            case "CN": return "🇨🇳";
            case "TH": return "🇹🇭";
            case "JP": return "🇯🇵";
            case "KR": return "🇰🇷";
            case "VN": return "🇻🇳";
            case "LA": return "🇱🇦";
            case "KH": return "🇰🇭";
            case "MY": return "🇲🇾";
            case "SG": return "🇸🇬";
            case "US": return "🇺🇸";
            default: return "🌐";
        }
    }

    private String countryNameByCode(String code) {
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
            default: return "";
        }
    }

    private int ageFromBirthday(String birthday) {
        if (TextUtils.isEmpty(birthday) || birthday.length() < 4) return 0;
        try {
            int year = Integer.parseInt(birthday.substring(0, 4));
            int current = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            int age = current - year;
            return age > 0 && age < 120 ? age : 0;
        } catch (Exception ignored) {
            return 0;
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

    private static class LinearLayoutCompat {
        static void addViewCompat(android.widget.LinearLayout parent, View child, int width, int height, int marginStart) {
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(width, height);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.leftMargin = marginStart;
            parent.addView(child, lp);
        }
    }
}
