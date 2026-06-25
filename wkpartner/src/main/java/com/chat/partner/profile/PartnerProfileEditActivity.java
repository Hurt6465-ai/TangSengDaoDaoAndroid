package com.chat.partner.profile;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ud.WKUploader;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileEditBinding;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PartnerProfileEditActivity extends WKBaseActivity<ActPartnerProfileEditBinding> {
    private static final int REQ_COVER = 501;
    private static final int REQ_PHOTO = 502;
    private static final int REQ_TAGS = 503;

    private static final String[][] COUNTRIES = new String[][]{
            {"MM", "🇲🇲 缅甸", "缅甸"}, {"CN", "🇨🇳 中国", "中国"}, {"TH", "🇹🇭 泰国", "泰国"},
            {"JP", "🇯🇵 日本", "日本"}, {"KR", "🇰🇷 韩国", "韩国"}, {"VN", "🇻🇳 越南", "越南"},
            {"LA", "🇱🇦 老挝", "老挝"}, {"KH", "🇰🇭 柬埔寨", "柬埔寨"}, {"MY", "🇲🇾 马来西亚", "马来西亚"},
            {"SG", "🇸🇬 新加坡", "新加坡"}, {"US", "🇺🇸 美国", "美国"}
    };

    private static final String[][] LANGS = new String[][]{
            {"MY", "🇲🇲 缅甸语  MY"}, {"ZH", "🇨🇳 中文  ZH"}, {"EN", "🇺🇸 英语  EN"},
            {"TH", "🇹🇭 泰语  TH"}, {"JA", "🇯🇵 日语  JA"}, {"KO", "🇰🇷 韩语  KO"},
            {"VI", "🇻🇳 越南语  VI"}, {"ID", "🇮🇩 印尼语  ID"}, {"MS", "🇲🇾 马来语  MS"}
    };

    private String countryCode = "";
    private String countryName = "";
    private int sexValue = 2; // 1 男，0 女，2 保密
    private String birthday = "";
    private String profileCover = "";
    private String localCoverPreview = "";
    private final ArrayList<String> nativeCodes = new ArrayList<>();
    private final ArrayList<String> learningCodes = new ArrayList<>();
    private final ArrayList<String> tags = new ArrayList<>();
    private final ArrayList<String> profileImages = new ArrayList<>();
    private final ArrayList<String> localPhotoPreviews = new ArrayList<>();

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
        updateTagsText();
        updateCoverPreview();
        updatePhotoPreview();
    }

    @Override
    protected void initListener() {
        wkVBinding.countryRow.setOnClickListener(v -> showCountryDialog());
        wkVBinding.sexRow.setOnClickListener(v -> showSexDialog());
        wkVBinding.birthdayRow.setOnClickListener(v -> showBirthdayPicker());
        wkVBinding.nativeLangRow.setOnClickListener(v -> showLanguageDialog(true));
        wkVBinding.learningLangRow.setOnClickListener(v -> showLanguageDialog(false));
        wkVBinding.tagsRow.setOnClickListener(v -> openTagSelector());
        wkVBinding.coverUploadBtn.setOnClickListener(v -> pickImage(REQ_COVER));
        wkVBinding.addPhotoBtn.setOnClickListener(v -> pickImage(REQ_PHOTO));
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
        profileCover = safe(data.profile_cover);

        nativeCodes.clear();
        nativeCodes.addAll(normalizeCodeList(data.getNativeLanguagesSafe()));
        learningCodes.clear();
        learningCodes.addAll(normalizeCodeList(data.getLearningLanguagesSafe()));
        tags.clear();
        tags.addAll(cleanList(data.getTagsSafe()));
        profileImages.clear();
        profileImages.addAll(cleanList(data.getProfileImagesSafe()));

        wkVBinding.introEt.setText(safe(data.intro));
        updateCountryText();
        updateSexText();
        updateBirthdayText();
        updateLanguageText();
        updateTagsText();
        updateCoverPreview();
        updatePhotoPreview();
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
        body.put("tags", joinPlain(tags));
        body.put("profile_cover", profileCover);
        body.put("profile_images", joinPlain(profileImages));
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
                    sexValue = which == 0 ? 1 : (which == 1 ? 0 : 2);
                    updateSexText();
                })
                .show();
    }

    private void showBirthdayPicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR) - 20;
        int month = 0;
        int day = 1;
        if (!TextUtils.isEmpty(birthday) && birthday.length() >= 10) {
            try {
                year = Integer.parseInt(birthday.substring(0, 4));
                month = Integer.parseInt(birthday.substring(5, 7)) - 1;
                day = Integer.parseInt(birthday.substring(8, 10));
            } catch (Exception ignored) {
            }
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
                    } else target.remove(code);
                })
                .setPositiveButton(R.string.partner_confirm, (dialog, which) -> updateLanguageText())
                .setNegativeButton(R.string.partner_cancel, null)
                .show();
    }

    private void openTagSelector() {
        Intent intent = new Intent(this, PartnerTagSelectorActivity.class);
        intent.putExtra(PartnerTagSelectorActivity.EXTRA_TAGS, joinPlain(tags));
        startActivityForResult(intent, REQ_TAGS);
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_TAGS) {
            tags.clear();
            tags.addAll(splitText(data.getStringExtra(PartnerTagSelectorActivity.EXTRA_TAGS)));
            updateTagsText();
            return;
        }
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQ_COVER) {
            localCoverPreview = uri.toString();
        } else {
            String local = uri.toString();
            if (!localPhotoPreviews.contains(local)) localPhotoPreviews.add(local);
        }
        updateCoverPreview();
        updatePhotoPreview();
        uploadPickedImage(uri, requestCode == REQ_COVER);
    }

    private void uploadPickedImage(Uri uri, boolean cover) {
        final String localPreview = uri.toString();
        showToast(getString(R.string.partner_uploading));
        new Thread(() -> {
            try {
                File source = copyUriToCache(uri, cover ? "partner_cover_src" : "partner_photo_src");
                File webp = PartnerImageCompressor.compressToWebp150KB(source, getCacheDir(), cover ? "partner_cover.webp" : ("partner_photo_" + System.currentTimeMillis() + ".webp"));
                runOnUiThread(() -> uploadCompressedFile(webp, cover, localPreview));
            } catch (Exception e) {
                runOnUiThread(() -> showToast(getString(R.string.partner_upload_failed)));
            }
        }).start();
    }

    private void uploadCompressedFile(File file, boolean cover, String localPreview) {
        WKUploader.getInstance().getUploadFileUrl(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL, file.getAbsolutePath(), (url, fileUrl) -> {
            if (TextUtils.isEmpty(url)) {
                showToast(getString(R.string.partner_upload_failed));
                return;
            }
            WKUploader.getInstance().upload(url, file.getAbsolutePath(), new WKUploader.IUploadBack() {
                @Override
                public void onSuccess(String ignore) {
                    if (cover) {
                        profileCover = fileUrl;
                        if (TextUtils.equals(localCoverPreview, localPreview)) localCoverPreview = "";
                        updateCoverPreview();
                    } else {
                        localPhotoPreviews.remove(localPreview);
                        if (!profileImages.contains(fileUrl)) profileImages.add(fileUrl);
                        updatePhotoPreview();
                    }
                    showToast(getString(R.string.partner_upload_success));
                }

                @Override
                public void onError() {
                    showToast(getString(R.string.partner_upload_failed));
                }
            });
        });
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
        BitmapFactory.decodeFile(out.getAbsolutePath());
        return out;
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
        wkVBinding.birthdayValueTv.setText(TextUtils.isEmpty(birthday) ? getString(R.string.partner_choose_birthday) : birthday);
    }

    private void updateLanguageText() {
        wkVBinding.nativeLangValueTv.setText(nativeCodes.isEmpty() ? getString(R.string.partner_choose_native_language) : joinLanguageWithFlag(nativeCodes));
        wkVBinding.learningLangValueTv.setText(learningCodes.isEmpty() ? getString(R.string.partner_choose_learning_language) : joinLanguageWithFlag(learningCodes));
    }

    private void updateTagsText() {
        wkVBinding.tagsValueTv.setText(tags.isEmpty() ? getString(R.string.partner_choose_tags) : joinPlain(tags));
    }

    private void updateCoverPreview() {
        if (!TextUtils.isEmpty(localCoverPreview)) {
            wkVBinding.coverPreviewIv.setImageURI(Uri.parse(localCoverPreview));
        } else if (!TextUtils.isEmpty(profileCover)) {
            GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(profileCover), wkVBinding.coverPreviewIv);
        } else {
            wkVBinding.coverPreviewIv.setImageResource(R.drawable.bg_partner_cover_default);
        }
    }

    private void updatePhotoPreview() {
        wkVBinding.imagePreviewLayout.removeAllViews();
        ArrayList<String> all = new ArrayList<>();
        all.addAll(localPhotoPreviews);
        all.addAll(profileImages);
        int count = Math.min(all.size(), 6);
        for (int i = 0; i < count; i++) {
            String path = all.get(i);
            ImageView imageView = new ImageView(this);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackgroundResource(R.drawable.bg_partner_photo_placeholder);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(dp(64), dp(64));
            if (i > 0) lp.leftMargin = dp(8);
            wkVBinding.imagePreviewLayout.addView(imageView, lp);
            if (!TextUtils.isEmpty(path) && (path.startsWith("content:") || path.startsWith("file:"))) {
                imageView.setImageURI(Uri.parse(path));
            } else {
                GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(path), imageView);
            }
        }
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

    private ArrayList<String> cleanList(List<String> list) {
        ArrayList<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String item : list) if (!TextUtils.isEmpty(item) && !out.contains(item.trim())) out.add(item.trim());
        return out;
    }

    private ArrayList<String> splitText(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        String[] parts = text.replace('，', ' ').replace(',', ' ').replace('/', ' ').trim().split("\\s+");
        for (String p : parts) if (!TextUtils.isEmpty(p) && !out.contains(p.trim())) out.add(p.trim());
        return out;
    }

    private String valueOf(TextView textView) {
        if (textView == null || textView.getText() == null) return "";
        return textView.getText().toString().trim();
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }

    private String joinLanguageWithFlag(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            String code = normalizeLangCode(value);
            if (TextUtils.isEmpty(code)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(languageFlag(code)).append(' ').append(code);
        }
        return sb.toString();
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
            default: return "🌐";
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

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
