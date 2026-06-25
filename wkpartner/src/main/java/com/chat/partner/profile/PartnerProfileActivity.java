package com.chat.partner.profile;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKDialogUtils;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileBinding;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.user.MyInfoActivity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 独立语伴个人主页插件。
 * 不改唐僧叨叨原 UserDetailActivity，后续语伴页、发现页、聊天头像入口都可以跳这里。
 */
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
    protected void initView() {
        wkVBinding.avatarView.setSize(92);
        wkVBinding.helloBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.editBtn.setVisibility(isSelf ? View.VISIBLE : View.GONE);
        wkVBinding.helloBtn.setVisibility(isSelf ? View.GONE : View.VISIBLE);
        wkVBinding.coverIv.setImageResource(R.drawable.bg_partner_cover_default);
    }

    @Override
    protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> finish());
        wkVBinding.editBtn.setOnClickListener(v -> startActivity(new Intent(this, MyInfoActivity.class)));
        wkVBinding.helloBtn.setOnClickListener(v -> onMainActionClick());
    }

    @Override
    protected void initData() {
        loadProfile();
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

    private void bindProfile(PartnerProfileEntity data) {
        String showName = firstNotEmpty(data.name, data.username, data.uid);
        wkVBinding.nameTv.setText(showName);
        wkVBinding.avatarView.showAvatar(uid, WKChannelType.PERSONAL, data.avatar_cache_key);
        if (!TextUtils.isEmpty(data.country_code)) {
            wkVBinding.avatarView.showFlag(data.country_code);
        }

        bindRole(data);
        bindSexAge(data);
        bindLanguages(data);
        bindIntro(data);
        bindTags(data);
        bindActionButton(data);
    }

    private void bindRole(PartnerProfileEntity data) {
        String roleText = getRoleText(data);
        if (TextUtils.isEmpty(roleText)) {
            wkVBinding.roleTv.setVisibility(View.GONE);
        } else {
            wkVBinding.roleTv.setText(roleText);
            wkVBinding.roleTv.setVisibility(View.VISIBLE);
        }
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

    private void bindLanguages(PartnerProfileEntity data) {
        String nativeText = formatLanguageCodes(data.getNativeLanguagesSafe());
        String learningText = formatLanguageCodes(data.getLearningLanguagesSafe());
        boolean show = !TextUtils.isEmpty(nativeText) || !TextUtils.isEmpty(learningText);
        wkVBinding.langLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        wkVBinding.nativeLangTv.setText(nativeText);
        wkVBinding.learningLangTv.setText(learningText);
    }

    private void bindIntro(PartnerProfileEntity data) {
        if (TextUtils.isEmpty(data.intro)) {
            wkVBinding.introTv.setText(R.string.partner_profile_intro_empty);
        } else {
            wkVBinding.introTv.setText(data.intro);
        }
    }

    private void bindTags(PartnerProfileEntity data) {
        wkVBinding.tagLayout.removeAllViews();
        List<String> tags = data.getTagsSafe();
        if (tags.isEmpty()) {
            wkVBinding.tagTitleTv.setVisibility(View.GONE);
            wkVBinding.tagLayout.setVisibility(View.GONE);
            return;
        }
        wkVBinding.tagTitleTv.setVisibility(View.VISIBLE);
        wkVBinding.tagLayout.setVisibility(View.VISIBLE);
        for (String tag : tags) {
            if (TextUtils.isEmpty(tag)) continue;
            TextView tv = new TextView(this);
            tv.setText(tag);
            tv.setTextSize(12);
            tv.setTextColor(0xFF333333);
            tv.setSingleLine(true);
            tv.setBackgroundResource(R.drawable.bg_partner_tag);
            int hPad = dp(10);
            int vPad = dp(5);
            tv.setPadding(hPad, vPad, hPad, vPad);
            wkVBinding.tagLayout.addView(tv);
        }
    }

    private void bindActionButton(PartnerProfileEntity data) {
        if (isSelf) {
            wkVBinding.helloBtn.setVisibility(View.GONE);
            return;
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        boolean isFriend = (data.follow == 1) || (channel != null && channel.follow == 1);
        wkVBinding.helloBtn.setVisibility(View.VISIBLE);
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
        String title = getString(R.string.partner_say_hello);
        String hint = getString(R.string.partner_hello_hint);
        WKDialogUtils.getInstance().showInputDialog(this, title, hint, defaultGreeting(), hint, 40, text -> {
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
        String learning = profile == null ? "" : formatLanguageCodes(profile.getLearningLanguagesSafe());
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

    private String formatLanguageCodes(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> codes = new ArrayList<>();
        for (String item : list) {
            String code = normalizeLangCode(item);
            if (!TextUtils.isEmpty(code)) codes.add(code);
        }
        return join(codes, " ");
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

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) return value;
        }
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
