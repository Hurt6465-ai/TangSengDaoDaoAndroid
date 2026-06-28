package com.chat.partner.profile;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ud.WKProgressManager;
import com.chat.base.net.ud.WKUploader;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileEditBinding;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PartnerProfileEditActivity extends WKBaseActivity<ActPartnerProfileEditBinding> {
    public static final String EXTRA_REQUIRE_PROFILE_IMAGE = "require_profile_image";
    private static final int REQ_AVATAR = 500;
    private static final int REQ_COVER = 501;
    private static final int REQ_PHOTO = 502;
    private static final int REQ_TAGS = 503;
    private static final int MAX_PROFILE_IMAGES = 5;
    private static final int MAX_INTRO_LENGTH = 200;

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
    private int sexValue = 2;
    private String birthday = "";
    private String profileCover = "";
    private String localCoverPreview = "";
    private int uploadingCount = 0;
    private int uploadingPhotoCount = 0;
    private boolean requireProfileImage = false;
    private final ArrayList<String> nativeCodes = new ArrayList<>();
    private final ArrayList<String> learningCodes = new ArrayList<>();
    private final ArrayList<String> tags = new ArrayList<>();
    private final ArrayList<String> profileImages = new ArrayList<>();
    private final ArrayList<String> localPhotoPreviews = new ArrayList<>();
    private final HashMap<String, String> uploadedPhotoLocalPreviewMap = new HashMap<>();
    private final HashSet<String> deletedLocalPhotoPreviews = new HashSet<>();

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
        requireProfileImage = getIntent() != null && getIntent().getBooleanExtra(EXTRA_REQUIRE_PROFILE_IMAGE, false);
        wkVBinding.avatarView.setSize(72);
        wkVBinding.avatarView.showAvatar(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
        updateCountryText();
        updateSexText();
        updateBirthdayText();
        updateLanguageText();
        updateTagsText();
        updateCoverPreview();
        updatePhotoPreview();
        updateProgress(0, false);
        setupIntroCounter();
    }

    @Override
    protected void initListener() {
        wkVBinding.avatarPickerLayout.setOnClickListener(v -> pickImage(REQ_AVATAR));
        wkVBinding.countryRow.setOnClickListener(v -> showCountryDialog());
        wkVBinding.sexRow.setOnClickListener(v -> showSexDialog());
        wkVBinding.birthdayRow.setOnClickListener(v -> showBirthdayPicker());
        wkVBinding.nativeLangRow.setOnClickListener(v -> showLanguageDialog(true));
        wkVBinding.learningLangRow.setOnClickListener(v -> showLanguageDialog(false));
        wkVBinding.tagsRow.setOnClickListener(v -> openTagSelector());
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
        sexValue = (data.sex == 0 || data.sex == 1 || data.sex == 2) ? data.sex : 2;
        birthday = safe(data.birthday);
        profileCover = safe(data.profile_cover);

        nativeCodes.clear();
        nativeCodes.addAll(normalizeCodeList(data.getNativeLanguagesSafe()));
        learningCodes.clear();
        learningCodes.addAll(normalizeCodeList(data.getLearningLanguagesSafe()));
        tags.clear();
        tags.addAll(cleanList(data.getTagsSafe()));
        profileImages.clear();
        profileImages.addAll(limitList(cleanList(data.getProfileImagesSafe()), MAX_PROFILE_IMAGES));

        wkVBinding.introEt.setText(safe(data.intro));
        wkVBinding.avatarView.setSize(72);
        wkVBinding.avatarView.showAvatar(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
        updateCountryText();
        updateSexText();
        updateBirthdayText();
        updateLanguageText();
        updateTagsText();
        updateCoverPreview();
        updatePhotoPreview();
    }

    private void saveProfile() {
        if (uploadingCount > 0) {
            showToast(getString(R.string.partner_wait_upload_finish));
            return;
        }
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
        if (requireProfileImage && profileImages.isEmpty()) {
            showToast(getString(R.string.partner_profile_image_required));
            return;
        }

        JSONObject coreBody = new JSONObject();
        coreBody.put("name", name);
        coreBody.put("sex", sexValue);
        coreBody.put("birthday", birthday);
        coreBody.put("country_code", countryCode);
        coreBody.put("country", countryName);
        coreBody.put("native_languages", new ArrayList<>(nativeCodes));
        coreBody.put("learning_languages", new ArrayList<>(learningCodes));
        coreBody.put("intro", limitIntro(valueOf(wkVBinding.introEt)));

        JSONObject mediaBody = new JSONObject();
        mediaBody.put("tags", new ArrayList<>(tags));
        mediaBody.put("profile_cover", profileCover);
        mediaBody.put("profile_images", new ArrayList<>(limitList(profileImages, MAX_PROFILE_IMAGES)));

        wkVBinding.saveBtn.setEnabled(false);
        PartnerProfileModel.getInstance().updateCurrentProfile(coreBody, (code, msg, data) -> {
            if (!(code == HttpResponseCode.success || code == 200 || code == 0)) {
                wkVBinding.saveBtn.setEnabled(true);
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.partner_save_failed) : msg);
                return;
            }
            PartnerProfileModel.getInstance().updateCurrentProfile(mediaBody, (mediaCode, mediaMsg, mediaData) -> {
                wkVBinding.saveBtn.setEnabled(true);
                if (mediaCode == HttpResponseCode.success || mediaCode == 200 || mediaCode == 0) {
                    showToast(getString(R.string.partner_save_success));
                    finish();
                } else {
                    showToast(TextUtils.isEmpty(mediaMsg) ? getString(R.string.partner_save_media_failed) : mediaMsg);
                }
            });
        });
    }

    private void showCountryDialog() {
        String[] items = new String[COUNTRIES.length];
        for (int i = 0; i < COUNTRIES.length; i++) items[i] = COUNTRIES[i][1];
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
                        if (target.size() >= 5 && !target.contains(code)) {
                            showToast(getString(R.string.partner_language_max_tip));
                            ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                            return;
                        }
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
        if (requestCode == REQ_PHOTO && profileImages.size() + uploadingPhotoCount >= MAX_PROFILE_IMAGES) {
            showToast(getString(R.string.partner_image_max_tip));
            return;
        }
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
        if (requestCode == REQ_AVATAR) {
            prepareAndUploadAvatar(uri);
            return;
        }
        prepareAndUploadPickedImage(uri, requestCode == REQ_COVER);
    }

    private void prepareAndUploadAvatar(Uri uri) {
        showToast(getString(R.string.partner_uploading));
        new Thread(() -> {
            try {
                File source = copyUriToCache(uri, "partner_avatar_src");
                File avatarPng = makeSquareAvatarPng(source);
                runOnUiThread(() -> UserModel.getInstance().uploadAvatar(avatarPng.getAbsolutePath(), code -> {
                    if (code == HttpResponseCode.success) {
                        String cacheKey = UUID.randomUUID().toString().replace("-", "");
                        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
                        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
                            channel = new WKChannel(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
                            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                        }
                        channel.avatarCacheKey = cacheKey;
                        WKIM.getInstance().getChannelManager().updateAvatarCacheKey(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL, cacheKey);
                        wkVBinding.avatarView.showAvatar(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL, cacheKey);
                        showToast(getString(R.string.partner_avatar_upload_success));
                    } else {
                        showToast(getString(R.string.partner_avatar_upload_failed));
                    }
                }));
            } catch (Exception e) {
                runOnUiThread(() -> showToast(getString(R.string.partner_avatar_upload_failed)));
            }
        }).start();
    }

    private File makeSquareAvatarPng(File source) throws Exception {
        Bitmap src = BitmapFactory.decodeFile(source.getAbsolutePath());
        if (src == null) throw new IllegalStateException("decode avatar failed");
        int side = Math.min(src.getWidth(), src.getHeight());
        int left = (src.getWidth() - side) / 2;
        int top = (src.getHeight() - side) / 2;
        Bitmap crop = Bitmap.createBitmap(src, left, top, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, 512, 512, true);
        File out = new File(getCacheDir(), "partner_avatar_" + System.currentTimeMillis() + ".png");
        FileOutputStream fos = new FileOutputStream(out);
        scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
        fos.close();
        if (scaled != crop) scaled.recycle();
        crop.recycle();
        src.recycle();
        return out;
    }

    private void prepareAndUploadPickedImage(Uri uri, boolean cover) {
        uploadingCount++;
        if (!cover) uploadingPhotoCount++;
        updateProgress(0, true);
        showToast(getString(R.string.partner_uploading));
        new Thread(() -> {
            try {
                File source = copyUriToCache(uri, cover ? "partner_cover_src" : "partner_photo_src");
                File webp = PartnerImageCompressor.compressToWebp150KB(source, getCacheDir(), cover ? "partner_cover.webp" : ("partner_photo_" + System.currentTimeMillis() + ".webp"));
                String localPath = webp.getAbsolutePath();
                runOnUiThread(() -> {
                    if (cover) localCoverPreview = localPath;
                    else if (!localPhotoPreviews.contains(localPath)) localPhotoPreviews.add(localPath);
                    updateCoverPreview();
                    updatePhotoPreview();
                    uploadCompressedFile(webp, cover, localPath);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!cover) uploadingPhotoCount = Math.max(0, uploadingPhotoCount - 1);
                    uploadingCount = Math.max(0, uploadingCount - 1);
                    updateProgress(0, uploadingCount > 0);
                    showToast(getString(R.string.partner_upload_failed));
                });
            }
        }).start();
    }

    private void uploadCompressedFile(File file, boolean cover, String localPreview) {
        String tag = "partner_profile_" + System.currentTimeMillis() + "_" + Math.abs(file.getAbsolutePath().hashCode());
        WKProgressManager.Companion.getInstance().registerProgress(tag, new WKProgressManager.IProgress() {
            @Override
            public void onProgress(Object progressTag, int progress) {
                runOnUiThread(() -> updateProgress(progress, true));
            }

            @Override
            public void onSuccess(Object progressTag, String path) {
            }

            @Override
            public void onFail(Object progressTag, String msg) {
            }
        });
        PartnerProfileModel.getInstance().getProfileUploadFileUrl(WKConfig.getInstance().getUid(), file.getAbsolutePath(), cover, (code, msg, uploadUrl) -> {
            if (uploadUrl == null || TextUtils.isEmpty(uploadUrl.url)) {
                WKProgressManager.Companion.getInstance().unregisterProgress(tag);
                finishOneUpload(cover, localPreview, false, "");
                return;
            }
            WKUploader.getInstance().upload(uploadUrl.url, file.getAbsolutePath(), tag, new WKUploader.IUploadBack() {
                @Override
                public void onSuccess(String uploadedPath) {
                    WKProgressManager.Companion.getInstance().unregisterProgress(tag);
                    String finalPath = TextUtils.isEmpty(uploadedPath) ? uploadUrl.path : uploadedPath;
                    finishOneUpload(cover, localPreview, true, normalizeUploadedPath(finalPath));
                }

                @Override
                public void onError() {
                    WKProgressManager.Companion.getInstance().unregisterProgress(tag);
                    finishOneUpload(cover, localPreview, false, "");
                }
            });
        });
    }

    private String normalizeUploadedPath(String path) {
        if (TextUtils.isEmpty(path)) return "";
        String v = path.trim();
        if (v.startsWith(WKApiConfig.baseUrl)) v = v.substring(WKApiConfig.baseUrl.length());
        if (v.startsWith("/")) v = v.substring(1);
        if (v.startsWith("common/")) return "file/preview/" + v;
        if (v.startsWith("profile/")) return "file/preview/common/" + v;
        return v;
    }

    private void finishOneUpload(boolean cover, String localPreview, boolean success, String fileUrl) {
        if (cover) {
            if (success) {
                profileCover = safe(fileUrl);
                if (TextUtils.equals(localCoverPreview, localPreview)) localCoverPreview = "";
            }
        } else {
            uploadingPhotoCount = Math.max(0, uploadingPhotoCount - 1);
            boolean deletedBeforeFinish = deletedLocalPhotoPreviews.remove(localPreview);
            if (success && !deletedBeforeFinish && !TextUtils.isEmpty(fileUrl) && !profileImages.contains(fileUrl) && profileImages.size() < MAX_PROFILE_IMAGES) {
                profileImages.add(fileUrl);
                uploadedPhotoLocalPreviewMap.put(fileUrl, localPreview);
                localPhotoPreviews.remove(localPreview);
            } else if (!success && !deletedBeforeFinish) {
                // 上传失败时保留本地预览，让用户能看到是哪张图失败，并可点 X 删除后重选。
                if (!localPhotoPreviews.contains(localPreview)) localPhotoPreviews.add(localPreview);
            } else {
                localPhotoPreviews.remove(localPreview);
            }
        }
        uploadingCount = Math.max(0, uploadingCount - 1);
        updateProgress(success ? 100 : 0, uploadingCount > 0);
        updateCoverPreview();
        updatePhotoPreview();
        showToast(getString(success ? R.string.partner_upload_success : R.string.partner_upload_failed));
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

    private void updateCountryText() {
        if (TextUtils.isEmpty(countryCode)) wkVBinding.countryValueTv.setText(R.string.partner_choose_country);
        else wkVBinding.countryValueTv.setText(countryFlag(countryCode) + " " + firstNotEmpty(countryName, countryNameByCode(countryCode)));
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
        wkVBinding.tagsValueTv.setText(tags.isEmpty() ? getString(R.string.partner_choose_tags) : getString(R.string.partner_selected_count, tags.size()));
        renderTagChips();
    }

    private void renderTagChips() {
        wkVBinding.tagChipContainer.removeAllViews();
        if (tags.isEmpty()) {
            wkVBinding.tagChipContainer.setVisibility(View.GONE);
            return;
        }
        wkVBinding.tagChipContainer.setVisibility(View.VISIBLE);
        for (String tag : new ArrayList<>(tags)) {
            if (TextUtils.isEmpty(tag)) continue;
            FrameLayout chip = new FrameLayout(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.height = dp(34);
            lp.setMargins(0, 0, dp(8), dp(8));
            chip.setLayoutParams(lp);

            TextView text = new TextView(this);
            text.setText(tag);
            text.setTextSize(12);
            text.setTextColor(0xFF6A4DDF);
            text.setGravity(android.view.Gravity.CENTER);
            text.setSingleLine(true);
            text.setMaxWidth(dp(110));
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setBackgroundResource(R.drawable.bg_partner_tag_chip);
            text.setPadding(dp(12), 0, dp(24), 0);
            chip.addView(text, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(28), android.view.Gravity.BOTTOM | android.view.Gravity.START));

            TextView close = new TextView(this);
            close.setText("×");
            close.setTextColor(0xFFFFFFFF);
            close.setTextSize(12);
            close.setGravity(android.view.Gravity.CENTER);
            close.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            close.setBackgroundResource(R.drawable.bg_partner_delete_photo);
            FrameLayout.LayoutParams xlp = new FrameLayout.LayoutParams(dp(18), dp(18), android.view.Gravity.TOP | android.view.Gravity.END);
            chip.addView(close, xlp);
            close.setOnClickListener(v -> {
                tags.remove(tag);
                updateTagsText();
            });
            wkVBinding.tagChipContainer.addView(chip);
        }
    }

    private void setupIntroCounter() {
        updateIntroCount();
        wkVBinding.introEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateIntroCount();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void updateIntroCount() {
        String intro = valueOf(wkVBinding.introEt);
        int len = intro.length();
        wkVBinding.introCountTv.setText(len + "/" + MAX_INTRO_LENGTH);
        wkVBinding.introCountTv.setTextColor(len >= MAX_INTRO_LENGTH ? 0xFFFF5A7A : 0xFF999999);
    }

    private String limitIntro(String value) {
        if (value == null) return "";
        return value.length() > MAX_INTRO_LENGTH ? value.substring(0, MAX_INTRO_LENGTH) : value;
    }

    private void updateProgress(int progress, boolean show) {
        wkVBinding.uploadProgressLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        int p = Math.max(0, Math.min(100, progress));
        wkVBinding.uploadProgressBar.setProgress(p);
        wkVBinding.uploadProgressTv.setText(getString(R.string.partner_uploading_percent, p));
    }

    private void updateCoverPreview() {
        if (!TextUtils.isEmpty(localCoverPreview)) {
            Bitmap bitmap = decodeLocalBitmap(localCoverPreview, 900, 360);
            if (bitmap != null) wkVBinding.coverPreviewIv.setImageBitmap(bitmap);
            else wkVBinding.coverPreviewIv.setImageURI(Uri.parse(localCoverPreview));
        } else if (!TextUtils.isEmpty(profileCover)) {
            GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(profileCover), wkVBinding.coverPreviewIv);
        } else {
            wkVBinding.coverPreviewIv.setImageResource(R.drawable.bj01);
        }
    }

    private void updatePhotoPreview() {
        wkVBinding.imagePreviewLayout.removeAllViews();
        for (String remotePath : new ArrayList<>(profileImages)) {
            String previewPath = uploadedPhotoLocalPreviewMap.containsKey(remotePath) ? uploadedPhotoLocalPreviewMap.get(remotePath) : remotePath;
            addPhotoPreviewItem(previewPath, () -> {
                profileImages.remove(remotePath);
                uploadedPhotoLocalPreviewMap.remove(remotePath);
                updatePhotoPreview();
            });
        }
        for (String localPath : new ArrayList<>(localPhotoPreviews)) {
            addPhotoPreviewItem(localPath, () -> {
                deletedLocalPhotoPreviews.add(localPath);
                localPhotoPreviews.remove(localPath);
                updatePhotoPreview();
            });
        }
    }

    private void addPhotoPreviewItem(String path, Runnable deleteAction) {
        FrameLayout item = new FrameLayout(this);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(dp(78), dp(78));
        if (wkVBinding.imagePreviewLayout.getChildCount() > 0) lp.leftMargin = dp(8);
        wkVBinding.imagePreviewLayout.addView(item, lp);

        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundResource(R.drawable.bg_partner_photo_placeholder);
        item.addView(imageView, new FrameLayout.LayoutParams(dp(70), dp(70), android.view.Gravity.BOTTOM | android.view.Gravity.START));

        Bitmap bitmap = decodeLocalBitmap(path, 240, 240);
        if (bitmap != null) imageView.setImageBitmap(bitmap);
        else GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(path), imageView);

        TextView deleteTv = new TextView(this);
        deleteTv.setText("×");
        deleteTv.setTextColor(0xFFFFFFFF);
        deleteTv.setTextSize(18);
        deleteTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        deleteTv.setGravity(android.view.Gravity.CENTER);
        deleteTv.setBackgroundResource(R.drawable.bg_partner_delete_photo);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(28), dp(28), android.view.Gravity.TOP | android.view.Gravity.END);
        item.addView(deleteTv, dlp);
        deleteTv.setOnClickListener(v -> {
            if (deleteAction != null) deleteAction.run();
        });
    }

    private Bitmap decodeLocalBitmap(String path, int reqW, int reqH) {
        if (TextUtils.isEmpty(path)) return null;
        String realPath = path.startsWith("file://") ? path.substring(7) : path;
        File file = new File(realPath);
        if (!file.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(realPath, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        return BitmapFactory.decodeFile(realPath, options);
    }

    private ArrayList<String> normalizeCodeList(List<String> list) {
        ArrayList<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String item : list) {
            String code = normalizeLangCode(item);
            if (!TextUtils.isEmpty(code) && !out.contains(code)) out.add(code);
        }
        return limitList(out, 5);
    }

    private ArrayList<String> cleanList(List<String> list) {
        ArrayList<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String item : list) if (!TextUtils.isEmpty(item) && !out.contains(item.trim())) out.add(item.trim());
        return out;
    }

    private ArrayList<String> limitList(List<String> input, int max) {
        ArrayList<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String item : input) {
            if (!TextUtils.isEmpty(item) && !out.contains(item)) out.add(item);
            if (out.size() >= max) break;
        }
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
