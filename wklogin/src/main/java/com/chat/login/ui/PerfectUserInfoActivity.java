package com.chat.login.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKReader;
import com.chat.login.R;
import com.chat.login.databinding.ActPerfectUserInfoLayoutBinding;
import com.chat.login.service.LoginModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 语伴资料完善页。
 * 注册阶段只把服务端已经支持的 name、sex、头像提交到服务端；
 * 其余资料先压缩图片并保存到本地，等后端字段和图片接口接好后再统一上传。
 */
public class PerfectUserInfoActivity extends WKBaseActivity<ActPerfectUserInfoLayoutBinding> {

    private static final int MAX_PARTNER_PHOTOS = 5;
    private static final int MAX_IMAGE_EDGE = 720;
    private static final int TARGET_IMAGE_BYTES = 150 * 1024;
    private static final int MIN_WEBP_QUALITY = 50;

    private String avatarPath;
    private final ArrayList<String> partnerPhotoPaths = new ArrayList<>();
    private final LinkedHashSet<String> selectedTags = new LinkedHashSet<>();

    private String selectedCountry = "";
    private String selectedGender = "";
    private String selectedNativeLanguage = "";
    private String selectedLearningLanguage = "";
    private String selectedBirthday = "";
    private String selectedEducation = "";

    @Override
    protected ActPerfectUserInfoLayoutBinding getViewBinding() {
        return ActPerfectUserInfoLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.wklogin_perfect_userinfo);
    }

    @Override
    protected void initView() {
        wkVBinding.avatarView.setSize(76);
        wkVBinding.avatarView.setStrokeWidth(0);
        wkVBinding.avatarView.imageView.setImageResource(R.mipmap.icon_default_header);
        wkVBinding.sureBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.countryFlagTv.setVisibility(View.GONE);
        refreshPartnerPhotos();
    }

    @Override
    protected void initListener() {
        wkVBinding.avatarView.setOnClickListener(v -> chooseAvatar());
        wkVBinding.uploadPartnerPhotoBtn.setOnClickListener(v -> choosePartnerPhotos());
        wkVBinding.countryTv.setOnClickListener(v -> selectSingle(wkVBinding.countryTv, R.string.profile_country_region, R.array.profile_country_options));
        wkVBinding.genderTv.setOnClickListener(v -> selectSingle(wkVBinding.genderTv, R.string.profile_gender, R.array.profile_gender_options));
        wkVBinding.nativeLanguageTv.setOnClickListener(v -> selectSingle(wkVBinding.nativeLanguageTv, R.string.profile_native_language, R.array.profile_language_options));
        wkVBinding.learningLanguageTv.setOnClickListener(v -> selectSingle(wkVBinding.learningLanguageTv, R.string.profile_learning_language, R.array.profile_language_options));
        wkVBinding.birthdayTv.setOnClickListener(v -> showBirthdayPicker());
        wkVBinding.educationTv.setOnClickListener(v -> selectSingle(wkVBinding.educationTv, R.string.profile_education_optional, R.array.profile_education_options));
        wkVBinding.tagBtn.setOnClickListener(v -> showTagCategoryDialog());
        wkVBinding.sureBtn.setOnClickListener(v -> saveProfile());
    }

    private void chooseAvatar() {
        GlideUtils.getInstance().chooseIMG(this, 1, true, ChooseMimeType.img, false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (WKReader.isNotEmpty(paths)) {
                    ChooseResult result = paths.get(0);
                    if (result != null && !TextUtils.isEmpty(result.path)) {
                        showToast(R.string.profile_image_compressing);
                        compressImageAsync(result.path, true, compressedPath -> {
                            if (TextUtils.isEmpty(compressedPath)) {
                                showToast(R.string.profile_image_compress_failed);
                                return;
                            }
                            avatarPath = compressedPath;
                            GlideUtils.getInstance().showImg(PerfectUserInfoActivity.this, avatarPath, wkVBinding.avatarView.imageView);
                            wkVBinding.coverIv.setVisibility(View.GONE);
                        });
                    }
                }
            }

            @Override
            public void onCancel() {
            }
        });
    }

    private void choosePartnerPhotos() {
        int leftCount = MAX_PARTNER_PHOTOS - partnerPhotoPaths.size();
        if (leftCount <= 0) {
            showToast(R.string.profile_partner_photo_limit);
            return;
        }
        GlideUtils.getInstance().chooseIMG(this, leftCount, true, ChooseMimeType.img, false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (WKReader.isNotEmpty(paths)) {
                    showToast(R.string.profile_image_compressing);
                    for (ChooseResult result : paths) {
                        if (result != null && !TextUtils.isEmpty(result.path) && partnerPhotoPaths.size() < MAX_PARTNER_PHOTOS) {
                            compressImageAsync(result.path, false, compressedPath -> {
                                if (!TextUtils.isEmpty(compressedPath) && partnerPhotoPaths.size() < MAX_PARTNER_PHOTOS) {
                                    partnerPhotoPaths.add(compressedPath);
                                    refreshPartnerPhotos();
                                }
                            });
                        }
                    }
                }
            }

            @Override
            public void onCancel() {
            }
        });
    }

    private void refreshPartnerPhotos() {
        wkVBinding.partnerPhotosLayout.removeAllViews();
        for (int i = 0; i < partnerPhotoPaths.size(); i++) {
            String photoPath = partnerPhotoPaths.get(i);
            wkVBinding.partnerPhotosLayout.addView(createPhotoItem(photoPath, i));
        }
    }

    private View createPhotoItem(String photoPath, int index) {
        FrameLayout frameLayout = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp(82), dp(82));
        frameParams.setMarginEnd(dp(10));
        frameLayout.setLayoutParams(frameParams);

        ImageView imageView = new ImageView(this);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(78), dp(78));
        imageParams.gravity = Gravity.BOTTOM | Gravity.START;
        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundResource(R.drawable.profile_photo_bg);
        GlideUtils.getInstance().showImg(this, photoPath, imageView);
        imageView.setOnClickListener(v -> showImagePreview(photoPath, index));
        frameLayout.addView(imageView);

        TextView deleteTv = new TextView(this);
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(dp(26), dp(26));
        deleteParams.gravity = Gravity.TOP | Gravity.END;
        deleteTv.setLayoutParams(deleteParams);
        deleteTv.setGravity(Gravity.CENTER);
        deleteTv.setText("×");
        deleteTv.setTextColor(0xFFFFFFFF);
        deleteTv.setTextSize(18);
        deleteTv.setBackgroundResource(R.drawable.profile_photo_delete_bg);
        deleteTv.setOnClickListener(v -> {
            if (index >= 0 && index < partnerPhotoPaths.size()) {
                partnerPhotoPaths.remove(index);
                refreshPartnerPhotos();
            }
        });
        frameLayout.addView(deleteTv);
        return frameLayout;
    }

    private void showImagePreview(String photoPath, int index) {
        ImageView imageView = new ImageView(this);
        int size = getResources().getDisplayMetrics().widthPixels - dp(64);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GlideUtils.getInstance().showImg(this, photoPath, imageView);
        new AlertDialog.Builder(this)
                .setTitle(R.string.profile_preview)
                .setView(imageView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_delete_photo, (dialog, which) -> {
                    if (index >= 0 && index < partnerPhotoPaths.size()) {
                        partnerPhotoPaths.remove(index);
                        refreshPartnerPhotos();
                    }
                })
                .show();
    }

    private void selectSingle(TextView target, int titleRes, int arrayRes) {
        String[] options = getResources().getStringArray(arrayRes);
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setItems(options, (dialog, which) -> {
                    String value = options[which];
                    target.setText(value);
                    target.setTextColor(getColorCompat(com.chat.base.R.color.colorDark));
                    saveSelectedValue(target.getId(), value);
                })
                .show();
    }

    private void saveSelectedValue(int viewId, String value) {
        if (viewId == R.id.countryTv) {
            selectedCountry = value;
            String flag = extractFlag(value);
            wkVBinding.countryFlagTv.setText(flag);
            wkVBinding.countryFlagTv.setVisibility(TextUtils.isEmpty(flag) ? View.GONE : View.VISIBLE);
        } else if (viewId == R.id.genderTv) {
            selectedGender = value;
        } else if (viewId == R.id.nativeLanguageTv) {
            selectedNativeLanguage = value;
        } else if (viewId == R.id.learningLanguageTv) {
            selectedLearningLanguage = value;
        } else if (viewId == R.id.educationTv) {
            selectedEducation = value;
        }
    }

    private String extractFlag(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String trimmed = value.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace > 0 && firstSpace <= 4) {
            return trimmed.substring(0, firstSpace);
        }
        return "";
    }

    private void showBirthdayPicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedBirthday = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            wkVBinding.birthdayTv.setText(selectedBirthday);
            wkVBinding.birthdayTv.setTextColor(getColorCompat(com.chat.base.R.color.colorDark));
        }, calendar.get(Calendar.YEAR) - 20, calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void showTagCategoryDialog() {
        String[] categories = getResources().getStringArray(R.array.profile_tag_category_options);
        new AlertDialog.Builder(this)
                .setTitle(R.string.profile_tag_category_title)
                .setItems(categories, (dialog, which) -> showTagDialog(getTagArrayRes(which)))
                .show();
    }

    private int getTagArrayRes(int index) {
        if (index == 0) return R.array.profile_tag_learning_options;
        if (index == 1) return R.array.profile_tag_social_options;
        if (index == 2) return R.array.profile_tag_personality_options;
        if (index == 3) return R.array.profile_tag_interest_options;
        if (index == 4) return R.array.profile_tag_job_options;
        return R.array.profile_tag_relationship_options;
    }

    private void showTagDialog(int arrayRes) {
        String[] tags = getResources().getStringArray(arrayRes);
        boolean[] checkedItems = new boolean[tags.length];
        for (int i = 0; i < tags.length; i++) {
            checkedItems[i] = selectedTags.contains(tags[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.profile_choose_tags)
                .setMultiChoiceItems(tags, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selectedTags.add(tags[which]);
                    } else {
                        selectedTags.remove(tags[which]);
                    }
                })
                .setPositiveButton(R.string.wklogin_sure, (dialog, which) -> refreshTags())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshTags() {
        wkVBinding.tagResultTv.setText(selectedTags.isEmpty() ? getString(R.string.profile_choose_tags_hint) : TextUtils.join("  ", selectedTags));
        wkVBinding.tagResultTv.setTextColor(getColorCompat(selectedTags.isEmpty() ? com.chat.base.R.color.color999 : com.chat.base.R.color.colorDark));
    }

    private void saveProfile() {
        if (TextUtils.isEmpty(avatarPath)) {
            showToast(R.string.profile_avatar_required);
            return;
        }
        if (checkEditInputIsEmpty(wkVBinding.nameEt, R.string.nickname_not_null)) return;
        if (TextUtils.isEmpty(getEditTextString(wkVBinding.introEt))) {
            showToast(R.string.profile_intro_required);
            return;
        }
        if (TextUtils.isEmpty(selectedCountry)) {
            showToast(R.string.profile_country_required);
            return;
        }
        if (TextUtils.isEmpty(selectedGender)) {
            showToast(R.string.profile_gender_required);
            return;
        }
        if (TextUtils.isEmpty(selectedNativeLanguage)) {
            showToast(R.string.profile_native_language_required);
            return;
        }
        if (TextUtils.isEmpty(selectedLearningLanguage)) {
            showToast(R.string.profile_learning_language_required);
            return;
        }
        if (TextUtils.isEmpty(selectedBirthday)) {
            showToast(R.string.profile_birthday_required);
            return;
        }
        if (selectedTags.isEmpty()) {
            showToast(R.string.profile_tags_required);
            return;
        }

        loadingPopup.show();
        String name = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString().trim();
        LoginModel.getInstance().updateUserInfo("name", name, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                saveLocalUserName(name);
                saveLocalProfile();
                updateSexIfNeeded(() -> uploadAvatarIfNeeded(this::finishPerfectUserInfo));
            } else {
                loadingPopup.dismiss();
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.profile_save_failed) : msg);
            }
        });
    }

    private void updateSexIfNeeded(Runnable next) {
        String sexValue = "";
        if (selectedGender.contains(getString(R.string.profile_gender_male))) {
            sexValue = "1";
        } else if (selectedGender.contains(getString(R.string.profile_gender_female))) {
            sexValue = "0";
        }
        if (TextUtils.isEmpty(sexValue)) {
            next.run();
            return;
        }
        LoginModel.getInstance().updateUserInfo("sex", sexValue, (code, msg) -> next.run());
    }

    private void uploadAvatarIfNeeded(Runnable next) {
        if (TextUtils.isEmpty(avatarPath)) {
            next.run();
            return;
        }
        LoginModel.getInstance().uploadAvatar(avatarPath, code -> {
            if (code == HttpResponseCode.success) {
                next.run();
            } else {
                loadingPopup.dismiss();
                showToast(R.string.profile_avatar_upload_failed);
            }
        });
    }

    private void saveLocalUserName(String name) {
        UserInfoEntity userInfoEntity = WKConfig.getInstance().getUserInfo();
        if (userInfoEntity != null) {
            userInfoEntity.name = name;
            WKConfig.getInstance().saveUserInfo(userInfoEntity);
        }
        WKConfig.getInstance().setUserName(name);
    }

    private void saveLocalProfile() {
        SharedPreferences.Editor editor = getSharedPreferences("partner_profile", MODE_PRIVATE).edit();
        String uid = WKConfig.getInstance().getUid();
        String prefix = TextUtils.isEmpty(uid) ? "current" : uid;
        editor.putString(prefix + "_avatar", avatarPath == null ? "" : avatarPath);
        editor.putString(prefix + "_photos", TextUtils.join("|", partnerPhotoPaths));
        editor.putString(prefix + "_intro", getEditTextString(wkVBinding.introEt));
        editor.putString(prefix + "_country", selectedCountry);
        editor.putString(prefix + "_gender", selectedGender);
        editor.putString(prefix + "_native_language", selectedNativeLanguage);
        editor.putString(prefix + "_learning_language", selectedLearningLanguage);
        editor.putString(prefix + "_birthday", selectedBirthday);
        editor.putString(prefix + "_height", getEditTextString(wkVBinding.heightEt));
        editor.putString(prefix + "_weight", getEditTextString(wkVBinding.weightEt));
        editor.putString(prefix + "_education", selectedEducation);
        editor.putString(prefix + "_tags", TextUtils.join(",", selectedTags));
        editor.apply();
    }

    private String getEditTextString(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void finishPerfectUserInfo() {
        List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
        if (WKReader.isNotEmpty(list)) {
            for (LoginMenu menu : list) {
                if (menu.iMenuClick != null) {
                    menu.iMenuClick.onClick();
                }
            }
        }
        loadingPopup.dismiss();
        setResult(RESULT_OK);
        finish();
    }

    private void compressImageAsync(String sourcePath, boolean avatar, ImageCompressCallback callback) {
        new Thread(() -> {
            String result = null;
            try {
                result = compressImageToWebp(sourcePath, avatar);
            } catch (Exception ignored) {
            }
            String finalResult = result;
            runOnUiThread(() -> callback.onResult(finalResult));
        }).start();
    }

    private String compressImageToWebp(String sourcePath, boolean avatar) throws IOException {
        if (TextUtils.isEmpty(sourcePath)) return "";
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) return "";

        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourcePath, boundsOptions);
        int sourceWidth = boundsOptions.outWidth;
        int sourceHeight = boundsOptions.outHeight;
        if (sourceWidth <= 0 || sourceHeight <= 0) return "";

        int inSampleSize = 1;
        int maxSourceEdge = Math.max(sourceWidth, sourceHeight);
        while (maxSourceEdge / inSampleSize > MAX_IMAGE_EDGE * 2) {
            inSampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = inSampleSize;
        Bitmap decoded = BitmapFactory.decodeFile(sourcePath, decodeOptions);
        if (decoded == null) return "";

        Bitmap outputBitmap = decoded;
        int decodedWidth = decoded.getWidth();
        int decodedHeight = decoded.getHeight();
        int decodedMaxEdge = Math.max(decodedWidth, decodedHeight);
        if (decodedMaxEdge > MAX_IMAGE_EDGE) {
            float scale = MAX_IMAGE_EDGE * 1f / decodedMaxEdge;
            int targetWidth = Math.max(1, Math.round(decodedWidth * scale));
            int targetHeight = Math.max(1, Math.round(decodedHeight * scale));
            outputBitmap = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true);
            if (outputBitmap != decoded) {
                decoded.recycle();
            }
        }

        File outDir = new File(getCacheDir(), "profile_webp");
        if (!outDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
        }
        String fileName = (avatar ? "avatar_" : "partner_") + System.currentTimeMillis() + "_" + Math.abs(sourcePath.hashCode()) + ".webp";
        File outFile = new File(outDir, fileName);

        int quality = 86;
        while (quality >= MIN_WEBP_QUALITY) {
            writeWebp(outputBitmap, outFile, quality);
            if (outFile.length() <= TARGET_IMAGE_BYTES || quality == MIN_WEBP_QUALITY) {
                break;
            }
            quality -= 6;
        }

        if (!outputBitmap.isRecycled()) {
            outputBitmap.recycle();
        }
        return outFile.getAbsolutePath();
    }

    private void writeWebp(Bitmap bitmap, File outFile, int quality) throws IOException {
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(outFile, false);
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream);
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getColorCompat(int colorRes) {
        return getResources().getColor(colorRes);
    }

    private interface ImageCompressCallback {
        void onResult(String compressedPath);
    }
}
