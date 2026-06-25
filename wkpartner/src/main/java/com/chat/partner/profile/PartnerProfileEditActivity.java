package com.chat.partner.profile;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.TextUtils;
import android.widget.TextView;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileEditBinding;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 复用注册完善资料的同一套 user 字段。
 * 国籍/语言/生日使用选择器；背景墙、照片、标签直接保存到后端字段，不做本地副本。
 */
public class PartnerProfileEditActivity extends WKBaseActivity<ActPartnerProfileEditBinding> {
    private static final String[][] COUNTRIES = new String[][]{
            {"MM", "🇲🇲 缅甸", "缅甸"}, {"CN", "🇨🇳 中国", "中国"}, {"TH", "🇹🇭 泰国", "泰国"},
            {"JP", "🇯🇵 日本", "日本"}, {"KR", "🇰🇷 韩国", "韩国"}, {"VN", "🇻🇳 越南", "越南"},
            {"LA", "🇱🇦 老挝", "老挝"}, {"KH", "🇰🇭 柬埔寨", "柬埔寨"}, {"MY", "🇲🇾 马来西亚", "马来西亚"},
            {"SG", "🇸🇬 新加坡", "新加坡"}, {"US", "🇺🇸 美国", "美国"}
    };

    private static final String[][] LANGS = new String[][]{
            {"MY", "🇲🇲 缅甸语 MY"}, {"ZH", "🇨🇳 中文 ZH"}, {"EN", "🇺🇸 英语 EN"},
            {"TH", "🇹🇭 泰语 TH"}, {"JA", "🇯🇵 日语 JA"}, {"KO", "🇰🇷 韩语 KO"},
            {"VI", "🇻🇳 越南语 VI"}, {"ID", "🇮🇩 印尼语 ID"}, {"MS", "🇲🇾 马来语 MS"}
    };

    private String countryCode = "";
    private String countryName = "";
    private int sexValue = 2; // 1 男，0 女，2 保密
    private String birthday = "";
    private final ArrayList<String> nativeCodes = new ArrayList<>();
    private final ArrayList<String> learningCodes = new ArrayList<>();

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
        updateCountryText();
        updateSexText();
        updateBirthdayText();
        updateLanguageText();
    }

    @Override
    protected void initListener() {
        wkVBinding.countryRow.setOnClickListener(v -> showCountryDialog());
        wkVBinding.sexRow.setOnClickListener(v -> showSexDialog());
        wkVBinding.birthdayRow.setOnClickListener(v -> showBirthdayPicker());
        wkVBinding.nativeLangRow.setOnClickListener(v -> showLanguageDialog(true));
        wkVBinding.learningLangRow.setOnClickListener(v -> showLanguageDialog(false));
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
        countryCode = normalizeCountryCode(data.country_code);
        countryName = firstNotEmpty(data.country, countryNameByCode(countryCode));
        sexValue = (data.sex == 0 || data.sex == 1) ? data.sex : 2;
        birthday = safe(data.birthday);

        nativeCodes.clear();
        nativeCodes.addAll(normalizeCodeList(data.getNativeLanguagesSafe()));
        learningCodes.clear();
        learningCodes.addAll(normalizeCodeList(data.getLearningLanguagesSafe()));

        wkVBinding.introEt.setText(safe(data.intro));
        wkVBinding.tagsEt.setText(joinPlain(data.getTagsSafe()));
        wkVBinding.coverEt.setText(safe(data.profile_cover));
        wkVBinding.imagesEt.setText(joinPlain(data.getProfileImagesSafe()));

        updateCountryText();
        updateSexText();
        updateBirthdayText();
        updateLanguageText();
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
        if (nativeCodes.isEmpty()) {
            showToast(getString(R.string.partner_native_language_required));
            return;
        }
        if (learningCodes.isEmpty()) {
            showToast(getString(R.string.partner_learning_language_required));
            return;
        }

        JSONObject body = new JSONObject();
        body.put("name", name);
        body.put("sex", sexValue);
        body.put("birthday", birthday);
        body.put("country_code", countryCode);
        body.put("country", countryName);
        body.put("native_languages", joinPlain(nativeCodes));
        body.put("learning_languages", joinPlain(learningCodes));
        body.put("intro", valueOf(wkVBinding.introEt));
        body.put("tags", normalizeFreeText(valueOf(wkVBinding.tagsEt)));
        body.put("profile_cover", valueOf(wkVBinding.coverEt));
        body.put("profile_images", normalizeFreeText(valueOf(wkVBinding.imagesEt)));

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
                    countryName = COUNTRIES[which][2];
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

    private void showBirthdayPicker() {
        Calendar calendar = Calendar.getInstance();
        int year = 2000;
        int month = 0;
        int day = 1;
        if (!TextUtils.isEmpty(birthday) && birthday.length() >= 10) {
            try {
                year = Integer.parseInt(birthday.substring(0, 4));
                month = Integer.parseInt(birthday.substring(5, 7)) - 1;
                day = Integer.parseInt(birthday.substring(8, 10));
            } catch (Exception ignored) {
            }
        } else {
            year = calendar.get(Calendar.YEAR) - 20;
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            birthday = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            updateBirthdayText();
        }, year, month, day);
        dialog.show();
    }

    private void showLanguageDialog(boolean nativeSide) {
        ArrayList<String> target = nativeSide ? nativeCodes : learningCodes;
        String[] items = new String[LANGS.length];
        boolean[] checked = new boolean[LANGS.length];
        for (int i = 0; i < LANGS.length; i++) {
            items[i] = LANGS[i][1];
            checked[i] = target.contains(LANGS[i][0]);
        }
        new AlertDialog.Builder(this)
                .setTitle(nativeSide ? R.string.partner_native_language : R.string.partner_learning_language)
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    String code = LANGS[which][0];
                    if (isChecked) {
                        if (!target.contains(code)) target.add(code);
                    } else {
                        target.remove(code);
                    }
                })
                .setPositiveButton(R.string.partner_confirm, (dialog, which) -> updateLanguageText())
                .setNegativeButton(R.string.partner_cancel, null)
                .show();
    }

    private void updateCountryText() {
        if (TextUtils.isEmpty(countryCode)) wkVBinding.countryValueTv.setText(R.string.partner_choose_country);
        else wkVBinding.countryValueTv.setText(countryFlag(countryCode) + " " + firstNotEmpty(countryName, countryNameByCode(countryCode)) + "  " + countryCode);
    }

    private void updateSexText() {
        if (sexValue == 1) wkVBinding.sexValueTv.setText(R.string.partner_sex_male);
        else if (sexValue == 0) wkVBinding.sexValueTv.setText(R.string.partner_sex_female);
        else wkVBinding.sexValueTv.setText(R.string.partner_sex_private);
    }

    private void updateBirthdayText() {
        if (TextUtils.isEmpty(birthday)) wkVBinding.birthdayValueTv.setText(R.string.partner_choose_birthday);
        else wkVBinding.birthdayValueTv.setText(birthday);
    }

    private void updateLanguageText() {
        wkVBinding.nativeLangValueTv.setText(nativeCodes.isEmpty() ? getString(R.string.partner_choose_native_language) : formatLangLabels(nativeCodes));
        wkVBinding.learningLangValueTv.setText(learningCodes.isEmpty() ? getString(R.string.partner_choose_learning_language) : formatLangLabels(learningCodes));
    }

    private ArrayList<String> normalizeCodeList(List<String> list) {
        ArrayList<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String item : list) {
            String code = normalizeLangCode(item);
            if (!TextUtils.isEmpty(code) && !out.contains(code)) out.add(code);
        }
        return out;
    }

    private String formatLangLabels(List<String> codes) {
        ArrayList<String> labels = new ArrayList<>();
        for (String code : codes) labels.add(languageFlag(code) + " " + code);
        return joinPlain(labels);
    }

    private String valueOf(TextView textView) {
        if (textView == null || textView.getText() == null) return "";
        return textView.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeFreeText(String input) {
        if (TextUtils.isEmpty(input)) return "";
        return input.replace('，', ' ').replace(',', ' ').replace('/', ' ').trim().replaceAll("\\s+", " ");
    }

    private String joinPlain(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(value.trim());
        }
        return sb.toString();
    }

    private String normalizeLangCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim().toLowerCase(Locale.US);
        if (v.equals("zh") || v.equals("cn") || v.equals("中文") || v.equals("chinese")) return "ZH";
        if (v.equals("en") || v.equals("英语") || v.equals("english")) return "EN";
        if (v.equals("my") || v.equals("mm") || v.equals("burmese") || v.equals("myanmar") || v.equals("缅甸语")) return "MY";
        if (v.equals("th") || v.equals("thai") || v.equals("泰语")) return "TH";
        if (v.equals("ja") || v.equals("jp") || v.equals("japanese") || v.equals("日语")) return "JA";
        if (v.equals("ko") || v.equals("kr") || v.equals("korean") || v.equals("韩语")) return "KO";
        if (v.equals("vi") || v.equals("vn") || v.equals("vietnamese") || v.equals("越南语")) return "VI";
        if (v.equals("id") || v.equals("indonesian") || v.equals("印尼语")) return "ID";
        if (v.equals("ms") || v.equals("malay") || v.equals("马来语")) return "MS";
        String only = value.replaceAll("[^A-Za-z]", "");
        if (only.length() >= 2) return only.substring(0, Math.min(3, only.length())).toUpperCase(Locale.US);
        return value.toUpperCase(Locale.US);
    }

    private String normalizeCountryCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim().toUpperCase(Locale.US);
        for (String[] item : COUNTRIES) if (item[0].equals(v)) return item[0];
        return "";
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
        for (String[] item : COUNTRIES) if (item[0].equalsIgnoreCase(code)) return item[2];
        return "";
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }
}
