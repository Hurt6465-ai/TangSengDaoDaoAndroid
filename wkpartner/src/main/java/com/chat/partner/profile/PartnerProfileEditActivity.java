package com.chat.partner.profile;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ud.WKProgressManager;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.WKReader;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileEditBinding;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.File;
import java.io.FileInputStream;
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
    public static final String EXTRA_FROM_REGISTER = "from_register";
    public static final String EXTRA_FORCE_COMPLETE = "force_complete";
    public static final String EXTRA_HIDE_BACK = "hide_back";
    public static final String EXTRA_HIDE_SKIP = "hide_skip";
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
    private boolean fromRegister = false;
    private boolean forceComplete = false;
    private boolean hideBack = false;
    private boolean hideSkip = false;
    private long avatarUploadSeq = 0L;
    private final ArrayList<String> nativeCodes = new ArrayList<>();
    private final ArrayList<String> learningCodes = new ArrayList<>();
    private final ArrayList<String> tags = new ArrayList<>();
    private final ArrayList<String> profileImages = new ArrayList<>();
    private final ArrayList<String> localPhotoPreviews = new ArrayList<>();
    private final HashMap<String, String> uploadedPhotoLocalPreviewMap = new HashMap<>();
    private final HashSet<String> deletedLocalPhotoPreviews = new HashSet<>();

    private boolean isRegisterCompleteMode() {
        Intent intent = getIntent();
        return intent != null && (intent.getBooleanExtra(EXTRA_FROM_REGISTER, false)
                || intent.getBooleanExtra(EXTRA_FORCE_COMPLETE, false));
    }

    @Override
    public boolean supportSlideBack() {
        return !isRegisterCompleteMode();
    }

    @Override
    protected boolean isHiddenBackLayout() {
        Intent intent = getIntent();
        return intent != null && (intent.getBooleanExtra(EXTRA_HIDE_BACK, false) || isRegisterCompleteMode());
    }

    @Override
    protected void backListener(int type) {
        if (fromRegister || forceComplete || isRegisterCompleteMode()) {
            showToast(getString(R.string.partner_complete_profile_first));
            return;
        }
        super.backListener(type);
    }

    @Override
    public void onBackPressed() {
        if (fromRegister || forceComplete || isRegisterCompleteMode()) {
            showToast(getString(R.string.partner_complete_profile_first));
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected ActPartnerProfileEditBinding getViewBinding() {
        return ActPartnerProfileEditBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(fromRegister || forceComplete || isRegisterCompleteMode()
                ? R.string.partner_complete_profile
                : R.string.partner_edit_profile);
    }

    @Override
    protected void initView() {
        Intent intent = getIntent();
        fromRegister = intent != null && intent.getBooleanExtra(EXTRA_FROM_REGISTER, false);
        forceComplete = intent != null && intent.getBooleanExtra(EXTRA_FORCE_COMPLETE, false);
        hideBack = intent != null && intent.getBooleanExtra(EXTRA_HIDE_BACK, false);
        hideSkip = intent != null && intent.getBooleanExtra(EXTRA_HIDE_SKIP, false);
        requireProfileImage = intent != null && (intent.getBooleanExtra(EXTRA_REQUIRE_PROFILE_IMAGE, false) || forceComplete || fromRegister);
        wkVBinding.avatarView.setSize(88);
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
        wkVBinding.avatarPickerLayout.setOnClickListener(v -> {
            hideKeyboard();
            pickImage(REQ_AVATAR);
        });
        wkVBinding.countryRow.setOnClickListener(v -> {
            hideKeyboard();
            showCountryDialog();
        });
        wkVBinding.sexRow.setOnClickListener(v -> {
            hideKeyboard();
            showSexDialog();
        });
        wkVBinding.birthdayRow.setOnClickListener(v -> {
            hideKeyboard();
            showBirthdayPicker();
        });
        wkVBinding.nativeLangRow.setOnClickListener(v -> {
            hideKeyboard();
            showLanguageDialog(true);
        });
        wkVBinding.learningLangRow.setOnClickListener(v -> {
            hideKeyboard();
            showLanguageDialog(false);
        });
        wkVBinding.tagsRow.setOnClickListener(v -> {
            hideKeyboard();
            openTagSelector();
        });
        wkVBinding.addPhotoBtn.setOnClickListener(v -> {
            hideKeyboard();
            pickImage(REQ_PHOTO);
        });
        wkVBinding.saveBtn.setOnClickListener(v -> {
            hideKeyboard();
            saveProfile();
        });
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
        tags.addAll(PartnerTagLocalizer.toKeyList(data.getTagsSafe()));
        profileImages.clear();
        profileImages.addAll(limitList(cleanList(data.getProfileImagesSafe()), MAX_PROFILE_IMAGES));

        wkVBinding.introEt.setText(safe(data.intro));
        wkVBinding.avatarView.setSize(88);
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
                    finishAfterProfileSaved();
                } else {
                    showToast(TextUtils.isEmpty(mediaMsg) ? getString(R.string.partner_save_media_failed) : mediaMsg);
                }
            });
        });
    }

    private void finishAfterProfileSaved() {
        if (fromRegister || forceComplete) {
            List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
            if (WKReader.isNotEmpty(list)) {
                for (LoginMenu menu : list) {
                    if (menu != null && menu.iMenuClick != null) menu.iMenuClick.onClick();
                }
            } else {
                EndpointManager.getInstance().invoke("show_tab_main", null);
            }
            setResult(RESULT_OK);
        }
        finish();
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
        if (requestCode == REQ_PHOTO && currentProfileImageSlotCount() >= MAX_PROFILE_IMAGES) {
            showToast(getString(R.string.partner_image_max_tip));
            return;
        }
        try {
            Intent intent;
            if (requestCode == REQ_PHOTO) {
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.partner_add_photo)), requestCode);
                return;
            }
            intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("image/*");
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                if (requestCode == REQ_PHOTO) fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(fallback, requestCode);
            } catch (Exception ignored) {
                showToast(getString(R.string.partner_upload_failed));
            }
        }
    }

    private int currentProfileImageSlotCount() {
        int pendingWithoutPreview = Math.max(0, uploadingPhotoCount - localPhotoPreviews.size());
        return profileImages.size() + localPhotoPreviews.size() + pendingWithoutPreview;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_TAGS) {
            tags.clear();
            tags.addAll(PartnerTagLocalizer.toKeyList(splitText(data.getStringExtra(PartnerTagSelectorActivity.EXTRA_TAGS))));
            updateTagsText();
            return;
        }
        if (requestCode == REQ_PHOTO) {
            handlePickedProfileImages(data);
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

    private void handlePickedProfileImages(Intent data) {
        int remain = MAX_PROFILE_IMAGES - currentProfileImageSlotCount();
        if (remain <= 0) {
            showToast(getString(R.string.partner_image_max_tip));
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        HashSet<String> uriKeys = new HashSet<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri == null) continue;
                String key = uri.toString();
                if (!uriKeys.contains(key)) {
                    uriKeys.add(key);
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        int added = 0;
        for (Uri uri : uris) {
            if (added >= remain) break;
            prepareAndUploadPickedImage(uri, false);
            added++;
        }
        if (uris.size() > remain) {
            showToast(getString(R.string.partner_image_max_tip));
        }
    }

    private void prepareAndUploadAvatar(Uri uri) {
        if (uri == null) return;
        final long requestSeq = ++avatarUploadSeq;
        showToast(getString(R.string.partner_uploading));
        new Thread(() -> {
            try {
                File source = copyUriToCache(uri, "partner_avatar_src");
                File avatarFile = makeSquareAvatarWebp(source);
                File fallbackJpg = null;
                if (avatarFile == null || !avatarFile.exists() || avatarFile.length() <= 0) {
                    avatarFile = makeSquareAvatarJpg(source);
                } else {
                    // 少数旧后端或对象存储如果不接受 WebP 头像，失败时自动用 JPG 兜底。
                    fallbackJpg = makeSquareAvatarJpg(source);
                }
                File finalAvatarFile = avatarFile;
                File finalFallbackJpg = fallbackJpg;
                runOnUiThread(() -> {
                    if (requestSeq != avatarUploadSeq) return;
                    showAvatarLocalPreview(finalAvatarFile.getAbsolutePath());
                    uploadPreparedAvatar(finalAvatarFile, finalFallbackJpg, requestSeq);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestSeq == avatarUploadSeq) showToast(getString(R.string.partner_avatar_upload_failed));
                });
            }
        }).start();
    }

    private void uploadPreparedAvatar(File avatarFile, File fallbackJpg, long requestSeq) {
        if (avatarFile == null || !avatarFile.exists() || avatarFile.length() <= 0) {
            showToast(getString(R.string.partner_avatar_upload_failed));
            return;
        }
        try {
            UserModel.getInstance().uploadAvatar(avatarFile.getAbsolutePath(), code -> runOnUiThread(() -> {
                if (requestSeq != avatarUploadSeq) return;
                if (code == HttpResponseCode.success) {
                    finishAvatarUploadSuccess(avatarFile);
                } else if (fallbackJpg != null && fallbackJpg.exists() && fallbackJpg.length() > 0) {
                    uploadFallbackAvatarJpg(fallbackJpg, requestSeq);
                } else {
                    showToast(getString(R.string.partner_avatar_upload_failed));
                }
            }));
        } catch (Exception e) {
            if (fallbackJpg != null && fallbackJpg.exists() && fallbackJpg.length() > 0) {
                uploadFallbackAvatarJpg(fallbackJpg, requestSeq);
            } else {
                showToast(getString(R.string.partner_avatar_upload_failed));
            }
        }
    }

    private void uploadFallbackAvatarJpg(File fallbackJpg, long requestSeq) {
        try {
            UserModel.getInstance().uploadAvatar(fallbackJpg.getAbsolutePath(), code -> runOnUiThread(() -> {
                if (requestSeq != avatarUploadSeq) return;
                if (code == HttpResponseCode.success) {
                    finishAvatarUploadSuccess(fallbackJpg);
                } else {
                    showToast(getString(R.string.partner_avatar_upload_failed));
                }
            }));
        } catch (Exception e) {
            showToast(getString(R.string.partner_avatar_upload_failed));
        }
    }

    private void finishAvatarUploadSuccess(File avatarFile) {
        String cacheKey = "local_" + UUID.randomUUID().toString().replace("-", "");
        persistLocalAvatarCache(avatarFile, cacheKey);
        updateCurrentChannelAvatarCacheKey(cacheKey);
        showAvatarLocalPreview(avatarFile.getAbsolutePath());
        showToast(getString(R.string.partner_avatar_upload_success));
    }

    /**
     * 头像也统一走压缩 WebP：512x512，质量 82。
     * 语伴图片已经是 WebP；头像之前为了兼容用 JPG，这里改成优先 WebP，上传失败再兜底 JPG。
     */
    private File makeSquareAvatarWebp(File source) throws Exception {
        Bitmap src = decodeBitmapFileForAvatar(source, 1600);
        if (src == null) throw new IllegalStateException("decode avatar failed");
        int side = Math.min(src.getWidth(), src.getHeight());
        int left = Math.max(0, (src.getWidth() - side) / 2);
        int top = Math.max(0, (src.getHeight() - side) / 2);
        Bitmap crop = Bitmap.createBitmap(src, left, top, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, 512, 512, true);
        File out = new File(getCacheDir(), "partner_avatar_" + System.currentTimeMillis() + "_" + Math.abs(System.nanoTime()) + ".webp");
        FileOutputStream fos = new FileOutputStream(out);
        try {
            boolean ok = scaled.compress(getWebpCompressFormat(), 82, fos);
            fos.flush();
            if (!ok || out.length() <= 0) {
                throw new IllegalStateException("compress avatar webp failed");
            }
        } finally {
            try { fos.close(); } catch (Exception ignored) { }
            if (scaled != crop) scaled.recycle();
            if (crop != src) crop.recycle();
            src.recycle();
        }
        return out;
    }

    private Bitmap.CompressFormat getWebpCompressFormat() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return Bitmap.CompressFormat.valueOf("WEBP_LOSSY");
            } catch (Exception ignored) {
            }
        }
        //noinspection deprecation
        return Bitmap.CompressFormat.WEBP;
    }

    private File makeSquareAvatarJpg(File source) throws Exception {
        Bitmap src = decodeBitmapFileForAvatar(source, 1600);
        if (src == null) throw new IllegalStateException("decode avatar failed");
        int side = Math.min(src.getWidth(), src.getHeight());
        int left = Math.max(0, (src.getWidth() - side) / 2);
        int top = Math.max(0, (src.getHeight() - side) / 2);
        Bitmap crop = Bitmap.createBitmap(src, left, top, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, 512, 512, true);
        File out = new File(getCacheDir(), "partner_avatar_" + System.currentTimeMillis() + "_" + Math.abs(System.nanoTime()) + ".jpg");
        FileOutputStream fos = new FileOutputStream(out);
        try {
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, fos);
            fos.flush();
        } finally {
            try { fos.close(); } catch (Exception ignored) { }
        }
        if (scaled != crop) scaled.recycle();
        if (crop != src) crop.recycle();
        src.recycle();
        return out;
    }

    private Bitmap decodeBitmapFileForAvatar(File source, int maxSide) {
        if (source == null || !source.exists()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showAvatarLocalPreview(String localPath) {
        if (TextUtils.isEmpty(localPath) || wkVBinding == null || wkVBinding.avatarView == null) return;
        try {
            Bitmap bitmap = decodeLocalBitmap(localPath, 256, 256);
            if (bitmap != null) {
                wkVBinding.avatarView.imageView.setVisibility(View.VISIBLE);
                wkVBinding.avatarView.defaultAvatarTv.setVisibility(View.GONE);
                wkVBinding.avatarView.imageView.setImageBitmap(bitmap);
            } else {
                wkVBinding.avatarView.imageView.setVisibility(View.VISIBLE);
                wkVBinding.avatarView.defaultAvatarTv.setVisibility(View.GONE);
                wkVBinding.avatarView.imageView.setImageURI(Uri.fromFile(new File(localPath)));
            }
        } catch (Exception ignored) {
        }
    }

    private void persistLocalAvatarCache(File avatarFile, String cacheKey) {
        if (avatarFile == null || TextUtils.isEmpty(cacheKey)) return;
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) return;
        File target = new File(WKConstants.avatarCacheDir + WKChannelType.PERSONAL + "_" + uid);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        File tmp = new File(target.getAbsolutePath() + ".tmp");
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(avatarFile);
            output = new FileOutputStream(tmp);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) > 0) output.write(buffer, 0, len);
            output.flush();
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(target);
            getSharedPreferences("avatar_cache_meta", MODE_PRIVATE)
                    .edit()
                    .putString("avatar_key_" + WKChannelType.PERSONAL + "_" + uid, cacheKey)
                    .apply();
        } catch (Exception ignored) {
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            try { if (output != null) output.close(); } catch (Exception ignored) { }
            if (tmp.exists() && tmp.length() <= 0) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private void updateCurrentChannelAvatarCacheKey(String cacheKey) {
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(cacheKey)) return;
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
            if (channel == null || TextUtils.isEmpty(channel.channelID)) {
                channel = new WKChannel(uid, WKChannelType.PERSONAL);
                WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
            }
            channel.avatarCacheKey = cacheKey;
            WKIM.getInstance().getChannelManager().updateAvatarCacheKey(uid, WKChannelType.PERSONAL, cacheKey);
        } catch (Exception ignored) {
        }
    }

    private void prepareAndUploadPickedImage(Uri uri, boolean cover) {
        if (uri == null) return;
        uploadingCount++;
        if (!cover) uploadingPhotoCount++;
        updateProgress(0, true);
        showToast(getString(R.string.partner_uploading));
        new Thread(() -> {
            try {
                File source = copyUriToCache(uri, cover ? "partner_cover_src" : "partner_photo_src");
                String outName = (cover ? "partner_cover_" : "partner_photo_")
                        + System.currentTimeMillis() + "_" + Math.abs(System.nanoTime()) + ".webp";
                File webp = PartnerImageCompressor.compressToWebp150KB(source, getCacheDir(), outName);
                if (webp == null || !webp.exists() || webp.length() <= 0) {
                    throw new IllegalStateException("compress image failed");
                }
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
                    updatePhotoPreview();
                    showToast(getString(R.string.partner_upload_failed));
                });
            }
        }).start();
    }

    private void uploadCompressedFile(File file, boolean cover, String localPreview) {
        if (file == null || !file.exists() || file.length() <= 0) {
            finishOneUpload(cover, localPreview, false, "");
            return;
        }
        String tag = "partner_profile_" + System.currentTimeMillis() + "_" + Math.abs(file.getAbsolutePath().hashCode()) + "_" + Math.abs(System.nanoTime());
        try {
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
                try {
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
                } catch (Exception e) {
                    WKProgressManager.Companion.getInstance().unregisterProgress(tag);
                    finishOneUpload(cover, localPreview, false, "");
                }
            });
        } catch (Exception e) {
            try {
                WKProgressManager.Companion.getInstance().unregisterProgress(tag);
            } catch (Exception ignored) {
            }
            finishOneUpload(cover, localPreview, false, "");
        }
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
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread(() -> finishOneUpload(cover, localPreview, success, fileUrl));
            return;
        }
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
        if (uri == null) throw new IllegalStateException("empty uri");
        String safePrefix = TextUtils.isEmpty(prefix) ? "partner_img" : prefix.replaceAll("[^a-zA-Z0-9_\-]", "_");
        File out = new File(getCacheDir(), safePrefix + "_" + System.currentTimeMillis() + "_" + Math.abs(System.nanoTime()) + ".jpg");
        InputStream input = null;
        FileOutputStream fos = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input == null) throw new IllegalStateException("empty uri stream");
            fos = new FileOutputStream(out);
            byte[] buffer = new byte[8192];
            int len;
            long total = 0;
            while ((len = input.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
                total += len;
            }
            fos.flush();
            if (total <= 0 || !out.exists() || out.length() <= 0) {
                throw new IllegalStateException("empty copied image");
            }
            return out;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
            if (input != null) {
                try { input.close(); } catch (Exception ignored) {}
            }
        }
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
            lp.height = dp(40);
            lp.setMargins(0, 0, dp(8), dp(8));
            chip.setLayoutParams(lp);

            TextView text = new TextView(this);
            text.setText(PartnerTagLocalizer.tagText(this, tag));
            text.setTextSize(12);
            text.setTextColor(0xFF6A4DDF);
            text.setGravity(android.view.Gravity.CENTER);
            text.setSingleLine(false);
            text.setMaxLines(2);
            text.setMaxWidth(dp(160));
            text.setEllipsize(null);
            text.setBackgroundResource(R.drawable.bg_partner_tag_chip);
            text.setPadding(dp(12), 0, dp(24), 0);
            chip.addView(text, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(34), android.view.Gravity.BOTTOM | android.view.Gravity.START));

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
        int slotCount = 0;
        for (String remotePath : new ArrayList<>(profileImages)) {
            if (slotCount >= MAX_PROFILE_IMAGES) break;
            String previewPath = uploadedPhotoLocalPreviewMap.containsKey(remotePath) ? uploadedPhotoLocalPreviewMap.get(remotePath) : remotePath;
            addPhotoPreviewSlot(previewPath, false, () -> {
                profileImages.remove(remotePath);
                uploadedPhotoLocalPreviewMap.remove(remotePath);
                updatePhotoPreview();
            });
            slotCount++;
        }
        for (String localPath : new ArrayList<>(localPhotoPreviews)) {
            if (slotCount >= MAX_PROFILE_IMAGES) break;
            addPhotoPreviewSlot(localPath, false, () -> {
                deletedLocalPhotoPreviews.add(localPath);
                localPhotoPreviews.remove(localPath);
                updatePhotoPreview();
            });
            slotCount++;
        }
        while (slotCount < MAX_PROFILE_IMAGES) {
            addPhotoPreviewSlot("", true, () -> pickImage(REQ_PHOTO));
            slotCount++;
        }
    }

    private int getPhotoSlotSize() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int horizontalPadding = dp(16 * 2);
        int gap = dp(8 * (MAX_PROFILE_IMAGES - 1));
        int slot = (screenWidth - horizontalPadding - gap) / MAX_PROFILE_IMAGES;
        return Math.max(dp(56), Math.min(dp(72), slot));
    }

    private void addPhotoPreviewSlot(String path, boolean placeholder, Runnable action) {
        int slot = getPhotoSlotSize();
        FrameLayout item = new FrameLayout(this);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(slot, slot + dp(8));
        if (wkVBinding.imagePreviewLayout.getChildCount() > 0) lp.leftMargin = dp(8);
        wkVBinding.imagePreviewLayout.addView(item, lp);

        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundResource(R.drawable.bg_partner_photo_placeholder);
        item.addView(imageView, new FrameLayout.LayoutParams(slot, slot, android.view.Gravity.BOTTOM | android.view.Gravity.START));

        if (placeholder) {
            imageView.setImageDrawable(null);
            TextView plus = new TextView(this);
            plus.setText("+");
            plus.setTextSize(28);
            plus.setTextColor(0xFF7A6CFF);
            plus.setGravity(android.view.Gravity.CENTER);
            plus.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            item.addView(plus, new FrameLayout.LayoutParams(slot, slot, android.view.Gravity.BOTTOM | android.view.Gravity.START));
            item.setOnClickListener(v -> {
                hideKeyboard();
                if (action != null) action.run();
            });
            imageView.setOnClickListener(v -> {
                hideKeyboard();
                if (action != null) action.run();
            });
            return;
        }

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
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(26), dp(26), android.view.Gravity.TOP | android.view.Gravity.END);
        item.addView(deleteTv, dlp);
        deleteTv.setOnClickListener(v -> {
            hideKeyboard();
            if (action != null) action.run();
        });
    }

    private Bitmap decodeLocalBitmap(String path, int reqW, int reqH) {
        try {
            if (TextUtils.isEmpty(path)) return null;
            String realPath = path.startsWith("file://") ? path.substring(7) : path;
            File file = new File(realPath);
            if (!file.exists() || !file.isFile() || file.length() <= 0) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(realPath, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            return BitmapFactory.decodeFile(realPath, options);
        } catch (Throwable ignored) {
            return null;
        }
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

    private void hideKeyboard() {
        try {
            View view = getCurrentFocus();
            if (view == null) view = wkVBinding.getRoot();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                view.clearFocus();
            }
        } catch (Exception ignored) {
        }
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
