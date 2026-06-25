package com.chat.partner.profile;

import android.app.AlertDialog;
import android.text.TextUtils;
import android.widget.TextView;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileEditBinding;

import java.util.Locale;

/**
 * 复用注册完善资料的同一套用户字段，不再做 front_profile_extra 本地副本。
 * 保存字段统一走 /v1/user/current：name、sex、birthday、country_code、country、native_languages、learning_languages、intro。
 */
public class PartnerProfileEditActivity extends WKBaseActivity<ActPartnerProfileEditBinding> {
    private static final String[][] COUNTRIES = new String[][]{
            {"MM", "缅甸"}, {"CN", "中国"}, {"TH", "泰国"}, {"JP", "日本"},
            {"KR", "韩国"}, {"VN", "越南"}, {"LA", "老挝"}, {"KH", "柬埔寨"},
            {"MY", "马来西亚"}, {"SG", "新加坡"}, {"US", "美国"}
    };

    private String countryCode = "";
    private String countryName = "";
    /** 1 男，0 女，2 保密。 */
    private int sexValue = 2;

    @Override
    protected ActPartnerProfileEditBinding getViewBinding() {
        return ActPartnerProfileEditBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.partner_edit_profile);
    }

    @Override
    protected void initView() {
        wkVBinding.countryValueTv.setText(R.string.partner_choose_country);
        wkVBinding.sexValueTv.setText(R.string.partner_sex_private);
    }

    @Override
    protected void initListener() {
        wkVBinding.countryRow.setOnClickListener(v -> showCountryDialog());
        wkVBinding.sexRow.setOnClickListener(v -> showSexDialog());
        wkVBinding.saveBtn.setOnClickListener(v -> saveProfile());
    }

    @Override
    protected void initData() {
        PartnerProfileModel.getInstance().getUserProfile(WKConfig.getInstance().getUid(), (code, msg, data) -> {
            if (code == HttpResponseCode.success && data != null) bindProfile(data);
            else {
                bindProfile(new PartnerProfileEntity());
                if (!TextUtils.isEmpty(msg)) showToast(msg);
            }
        });
    }

    private void bindProfile(PartnerProfileEntity data) {
        wkVBinding.nameEt.setText(firstNotEmpty(data.name, WKConfig.getInstance().getUserName()));
        countryCode = safe(data.country_code).toUpperCase(Locale.US);
        countryName = firstNotEmpty(data.country, countryNameByCode(countryCode));
        updateCountryText();

        sexValue = (data.sex == 0 || data.sex == 1) ? data.sex : 2;
        updateSexText();

        wkVBinding.birthdayEt.setText(safe(data.birthday));
        wkVBinding.nativeLangEt.setText(joinCodes(data.getNativeLanguagesSafe()));
        wkVBinding.learningLangEt.setText(joinCodes(data.getLearningLanguagesSafe()));
        wkVBinding.introEt.setText(safe(data.intro));
    }

    private void saveProfile() {
        String name = valueOf(wkVBinding.nameEt);
        if (TextUtils.isEmpty(name)) {
            showToast(getString(R.string.partner_name_required));
            return;
        }
        if (TextUtils.isEmpty(countryCode)) {
            showToast(getString(R.string.partner_country_required));
            return;
        }
        if (TextUtils.isEmpty(valueOf(wkVBinding.nativeLangEt))) {
            showToast(getString(R.string.partner_native_language_required));
            return;
        }
        if (TextUtils.isEmpty(valueOf(wkVBinding.learningLangEt))) {
            showToast(getString(R.string.partner_learning_language_required));
            return;
        }

        JSONObject body = new JSONObject();
        body.put("name", name);
        body.put("sex", sexValue);
        body.put("birthday", valueOf(wkVBinding.birthdayEt));
        body.put("country_code", countryCode);
        body.put("country", countryName);
        body.put("native_languages", normalizeCodes(valueOf(wkVBinding.nativeLangEt)));
        body.put("learning_languages", normalizeCodes(valueOf(wkVBinding.learningLangEt)));
        body.put("intro", valueOf(wkVBinding.introEt));

        wkVBinding.saveBtn.setEnabled(false);
        PartnerProfileModel.getInstance().updateCurrentProfile(body, (code, msg, data) -> {
            wkVBinding.saveBtn.setEnabled(true);
            if (code == HttpResponseCode.success) {
                showToast(getString(R.string.partner_save_success));
                finish();
            } else {
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.partner_save_failed) : msg);
            }
        });
    }

    private void showCountryDialog() {
        String[] items = new String[COUNTRIES.length];
        for (int i = 0; i < COUNTRIES.length; i++) items[i] = COUNTRIES[i][1] + "  " + COUNTRIES[i][0];
        new AlertDialog.Builder(this)
                .setTitle(R.string.partner_country)
                .setItems(items, (dialog, which) -> {
                    countryCode = COUNTRIES[which][0];
                    countryName = COUNTRIES[which][1];
                    updateCountryText();
                })
                .show();
    }

    private void showSexDialog() {
        String[] items = new String[]{getString(R.string.partner_sex_male), getString(R.string.partner_sex_female), getString(R.string.partner_sex_private)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.partner_sex)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) sexValue = 1;
                    else if (which == 1) sexValue = 0;
                    else sexValue = 2;
                    updateSexText();
                })
                .show();
    }

    private void updateCountryText() {
        if (TextUtils.isEmpty(countryCode) && TextUtils.isEmpty(countryName)) wkVBinding.countryValueTv.setText(R.string.partner_choose_country);
        else wkVBinding.countryValueTv.setText(firstNotEmpty(countryName, countryNameByCode(countryCode)) + "  " + countryCode);
    }

    private void updateSexText() {
        if (sexValue == 1) wkVBinding.sexValueTv.setText(R.string.partner_sex_male);
        else if (sexValue == 0) wkVBinding.sexValueTv.setText(R.string.partner_sex_female);
        else wkVBinding.sexValueTv.setText(R.string.partner_sex_private);
    }

    private String valueOf(TextView textView) {
        if (textView == null || textView.getText() == null) return "";
        return textView.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String joinCodes(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(value.trim());
        }
        return sb.toString();
    }

    private String normalizeCodes(String input) {
        if (TextUtils.isEmpty(input)) return "";
        return input.replace('，', ' ').replace(',', ' ').replace('/', ' ').trim().replaceAll("\\s+", " ").toUpperCase(Locale.US);
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private String countryNameByCode(String code) {
        if (TextUtils.isEmpty(code)) return "";
        for (String[] item : COUNTRIES) if (item[0].equalsIgnoreCase(code)) return item[1];
        return "";
    }
}
