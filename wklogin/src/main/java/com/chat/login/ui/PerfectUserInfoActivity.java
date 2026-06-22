package com.chat.login.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private static final int MAX_LANGUAGES = 5;
    private static final int MAX_IMAGE_EDGE = 720;
    private static final int TARGET_IMAGE_BYTES = 150 * 1024;
    private static final int MIN_WEBP_QUALITY = 48;

    private String avatarPath;
    private final ArrayList<String> partnerPhotoPaths = new ArrayList<>();
    private final LinkedHashSet<String> selectedNativeLanguages = new LinkedHashSet<>();
    private final LinkedHashSet<String> selectedLearningLanguages = new LinkedHashSet<>();
    private final LinkedHashSet<String> selectedTags = new LinkedHashSet<>();

    private String selectedCountry = "";
    private String selectedGender = "";
    private String selectedBirthday = "";

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
        wkVBinding.avatarView.setSize(96);
        wkVBinding.avatarView.setStrokeWidth(0);
        wkVBinding.avatarView.imageView.setImageResource(R.mipmap.icon_default_header);
        wkVBinding.sureBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.countryFlagTv.setVisibility(View.GONE);
        refreshPartnerPhotos();
        refreshTags();
    }

    @Override
    protected void initListener() {
        wkVBinding.avatarView.setOnClickListener(v -> chooseAvatar());
        wkVBinding.uploadPartnerPhotoBtn.setOnClickListener(v -> choosePartnerPhotos());
        wkVBinding.countryTv.setOnClickListener(v -> showSingleOptionDialog(
                R.string.profile_country_region,
                R.array.profile_country_options,
                value -> {
                    selectedCountry = value;
                    wkVBinding.countryTv.setText(value);
                    wkVBinding.countryTv.setTextColor(Color.parseColor("#172033"));
                    String flag = extractFlag(value);
                    wkVBinding.countryFlagTv.setText(flag);
                    wkVBinding.countryFlagTv.setVisibility(TextUtils.isEmpty(flag) ? View.GONE : View.VISIBLE);
                }
        ));
        wkVBinding.genderTv.setOnClickListener(v -> showSingleOptionDialog(
                R.string.profile_gender,
                R.array.profile_gender_options,
                value -> {
                    selectedGender = value;
                    wkVBinding.genderTv.setText(value);
                    wkVBinding.genderTv.setTextColor(Color.parseColor("#172033"));
                }
        ));
        wkVBinding.nativeLanguageTv.setOnClickListener(v -> showMultiOptionDialog(
                R.string.profile_native_language,
                R.array.profile_language_options,
                selectedNativeLanguages,
                MAX_LANGUAGES,
                () -> updateMultiText(wkVBinding.nativeLanguageTv, selectedNativeLanguages)
        ));
        wkVBinding.learningLanguageTv.setOnClickListener(v -> showMultiOptionDialog(
                R.string.profile_learning_language,
                R.array.profile_language_options,
                selectedLearningLanguages,
                MAX_LANGUAGES,
                () -> updateMultiText(wkVBinding.learningLanguageTv, selectedLearningLanguages)
        ));
        wkVBinding.birthdayTv.setOnClickListener(v -> showBirthdayPicker());
        wkVBinding.tagBtn.setOnClickListener(v -> showTagFullDialog());
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
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        frameParams.setMarginEnd(dp(10));
        frameLayout.setLayoutParams(frameParams);

        ImageView imageView = new ImageView(this);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(84), dp(84));
        imageParams.gravity = Gravity.BOTTOM | Gravity.START;
        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundResource(R.drawable.profile_photo_bg);
        GlideUtils.getInstance().showImg(this, photoPath, imageView);
        imageView.setOnClickListener(v -> showImagePreview(photoPath, index));
        frameLayout.addView(imageView);

        TextView deleteTv = new TextView(this);
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(dp(24), dp(24));
        deleteParams.gravity = Gravity.TOP | Gravity.END;
        deleteTv.setLayoutParams(deleteParams);
        deleteTv.setGravity(Gravity.CENTER);
        deleteTv.setText("×");
        deleteTv.setTextColor(0xFFFFFFFF);
        deleteTv.setTextSize(17);
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

    private void showSingleOptionDialog(int titleRes, int arrayRes, SingleSelectCallback callback) {
        String[] options = getResources().getStringArray(arrayRes);
        Dialog dialog = createFullScreenDialog();
        LinearLayout root = createDialogRoot();
        root.addView(createDialogHeader(getString(titleRes), dialog));
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(24));
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        addOptionGrid(content, options, null, 1, value -> {
            callback.onSelected(value);
            dialog.dismiss();
        });
        dialog.setContentView(root);
        dialog.show();
    }

    private void showMultiOptionDialog(int titleRes, int arrayRes, LinkedHashSet<String> selectedSet, int maxCount, Runnable onDone) {
        String[] options = getResources().getStringArray(arrayRes);
        Dialog dialog = createFullScreenDialog();
        LinearLayout root = createDialogRoot();
        root.addView(createDialogHeader(getString(titleRes), dialog));
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.profile_language_multi_hint, maxCount));
        hint.setTextColor(Color.parseColor("#667085"));
        hint.setTextSize(14);
        hint.setPadding(dp(20), 0, dp(20), dp(8));
        root.addView(hint);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(16));
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        addOptionGrid(content, options, selectedSet, maxCount, null);
        TextView done = createBottomAction(getString(R.string.wklogin_sure));
        done.setOnClickListener(v -> {
            onDone.run();
            dialog.dismiss();
        });
        root.addView(done);
        dialog.setContentView(root);
        dialog.show();
    }

    private void addOptionGrid(LinearLayout content, String[] options, LinkedHashSet<String> selectedSet, int maxCount, SingleSelectCallback singleCallback) {
        LinearLayout row = null;
        for (int i = 0; i < options.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            String option = options[i];
            TextView item = createOptionItem(option, selectedSet != null && selectedSet.contains(option), false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1);
            params.setMargins(dp(4), dp(6), dp(4), dp(6));
            item.setLayoutParams(params);
            if (selectedSet == null) {
                item.setOnClickListener(v -> {
                    if (singleCallback != null) singleCallback.onSelected(option);
                });
            } else {
                item.setOnClickListener(v -> {
                    if (selectedSet.contains(option)) {
                        selectedSet.remove(option);
                    } else {
                        if (selectedSet.size() >= maxCount) {
                            showToast(getString(R.string.profile_select_max_count, maxCount));
                            return;
                        }
                        selectedSet.add(option);
                    }
                    item.setBackground(makeRoundBg(selectedSet.contains(option), false, tagColorFor(option)));
                    item.setTextColor(selectedSet.contains(option) ? Color.parseColor("#3327A8") : Color.parseColor("#172033"));
                });
            }
            row.addView(item);
            if (i == options.length - 1 && i % 2 == 0) {
                SpaceView space = new SpaceView(this);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(58), 1);
                sp.setMargins(dp(4), dp(6), dp(4), dp(6));
                row.addView(space, sp);
            }
        }
    }

    private TextView createOptionItem(String text, boolean selected, boolean small) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(14), 0, dp(14), 0);
        tv.setSingleLine(false);
        tv.setMaxLines(2);
        tv.setTextSize(small ? 14 : 15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(selected ? Color.parseColor("#3327A8") : Color.parseColor("#172033"));
        tv.setBackground(makeRoundBg(selected, false, tagColorFor(text)));
        return tv;
    }

    private void updateMultiText(TextView target, LinkedHashSet<String> selectedSet) {
        if (selectedSet.isEmpty()) {
            target.setText(R.string.profile_select);
            target.setTextColor(Color.parseColor("#667085"));
        } else {
            target.setText(TextUtils.join("、", selectedSet));
            target.setTextColor(Color.parseColor("#172033"));
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
            wkVBinding.birthdayTv.setTextColor(Color.parseColor("#172033"));
        }, calendar.get(Calendar.YEAR) - 20, calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void showTagFullDialog() {
        String[] categories = getResources().getStringArray(R.array.profile_tag_category_options);
        Dialog dialog = createFullScreenDialog();
        LinearLayout root = createDialogRoot();
        root.addView(createDialogHeader(getString(R.string.profile_choose_tags), dialog));

        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryBar = new LinearLayout(this);
        categoryBar.setOrientation(LinearLayout.HORIZONTAL);
        categoryBar.setPadding(dp(14), 0, dp(14), dp(10));
        categoryScroll.addView(categoryBar);
        root.addView(categoryScroll);

        ScrollView tagScroll = new ScrollView(this);
        LinearLayout tagContainer = new LinearLayout(this);
        tagContainer.setOrientation(LinearLayout.VERTICAL);
        tagContainer.setPadding(dp(16), dp(4), dp(16), dp(16));
        tagScroll.addView(tagContainer);
        root.addView(tagScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        final int[] activeIndex = {0};
        final float[] downX = {0};
        Runnable render = () -> renderTagPage(categories, activeIndex[0], categoryBar, tagContainer);
        tagScroll.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX[0] = event.getX();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX[0];
                if (Math.abs(dx) > dp(70)) {
                    if (dx < 0 && activeIndex[0] < categories.length - 1) {
                        activeIndex[0]++;
                        render.run();
                        return true;
                    } else if (dx > 0 && activeIndex[0] > 0) {
                        activeIndex[0]--;
                        render.run();
                        return true;
                    }
                }
            }
            return false;
        });
        render.run();

        TextView done = createBottomAction(getString(R.string.wklogin_sure));
        done.setOnClickListener(v -> {
            refreshTags();
            dialog.dismiss();
        });
        root.addView(done);
        dialog.setContentView(root);
        dialog.show();
    }

    private void renderTagPage(String[] categories, int activeIndex, LinearLayout categoryBar, LinearLayout tagContainer) {
        categoryBar.removeAllViews();
        for (int i = 0; i < categories.length; i++) {
            int index = i;
            TextView tab = new TextView(this);
            tab.setText(categories[i]);
            tab.setTextSize(14);
            tab.setTypeface(null, android.graphics.Typeface.BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setTextColor(i == activeIndex ? Color.WHITE : Color.parseColor("#1B2640"));
            tab.setBackground(makeCategoryTabBg(i == activeIndex, i));
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-2, dp(42));
            tabParams.setMargins(dp(4), 0, dp(4), 0);
            tab.setPadding(dp(16), 0, dp(16), 0);
            categoryBar.addView(tab, tabParams);
            tab.setOnClickListener(v -> renderTagPage(categories, index, categoryBar, tagContainer));
        }

        tagContainer.removeAllViews();
        int arrayRes = getTagArrayRes(activeIndex);
        String[] tags = getResources().getStringArray(arrayRes);
        TextView section = new TextView(this);
        section.setText(categories[activeIndex]);
        section.setTextSize(20);
        section.setTypeface(null, android.graphics.Typeface.BOLD);
        section.setTextColor(Color.parseColor("#111827"));
        section.setPadding(dp(4), dp(8), dp(4), dp(8));
        tagContainer.addView(section);

        LinearLayout row = null;
        for (int i = 0; i < tags.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                tagContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            String tag = tags[i];
            boolean selected = selectedTags.contains(tag);
            TextView item = createOptionItem(tag, selected, true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
            params.setMargins(dp(4), dp(6), dp(4), dp(6));
            row.addView(item, params);
            item.setOnClickListener(v -> {
                if (selectedTags.contains(tag)) {
                    selectedTags.remove(tag);
                } else {
                    selectedTags.add(tag);
                }
                renderTagPage(categories, activeIndex, categoryBar, tagContainer);
            });
            if (i == tags.length - 1 && i % 2 == 0) {
                SpaceView space = new SpaceView(this);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(50), 1);
                sp.setMargins(dp(4), dp(6), dp(4), dp(6));
                row.addView(space, sp);
            }
        }
    }

    private int getTagArrayRes(int index) {
        if (index == 0) return R.array.profile_tag_language_skill_options;
        if (index == 1) return R.array.profile_tag_learning_goal_options;
        if (index == 2) return R.array.profile_tag_interaction_options;
        if (index == 3) return R.array.profile_tag_safety_options;
        if (index == 4) return R.array.profile_tag_relationship_intent_options;
        if (index == 5) return R.array.profile_tag_personality_options;
        if (index == 6) return R.array.profile_tag_pet_options;
        if (index == 7) return R.array.profile_tag_sports_options;
        if (index == 8) return R.array.profile_tag_movie_options;
        if (index == 9) return R.array.profile_tag_job_options;
        return R.array.profile_tag_education_options;
    }

    private void refreshTags() {
        wkVBinding.selectedTagsLayout.removeAllViews();
        if (selectedTags.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText(R.string.profile_choose_tags_hint);
            hint.setTextColor(Color.parseColor("#667085"));
            hint.setTextSize(14);
            hint.setTypeface(null, android.graphics.Typeface.BOLD);
            wkVBinding.selectedTagsLayout.addView(hint);
            return;
        }
        ArrayList<String> tags = new ArrayList<>(selectedTags);
        LinearLayout row = null;
        for (int i = 0; i < tags.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                wkVBinding.selectedTagsLayout.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            String tag = tags.get(i);
            TextView chip = new TextView(this);
            chip.setText(tag);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.setTextColor(Color.parseColor("#1B2640"));
            chip.setSingleLine(true);
            chip.setBackground(makeRoundBg(true, true, tagColorFor(tag)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), 1);
            params.setMargins(dp(3), dp(4), dp(3), dp(4));
            row.addView(chip, params);
        }
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
        if (selectedNativeLanguages.isEmpty()) {
            showToast(R.string.profile_native_language_required);
            return;
        }
        if (selectedLearningLanguages.isEmpty()) {
            showToast(R.string.profile_learning_language_required);
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
                // 头像接口异常不能再把注册流程卡死；资料已保存，头像可进入 App 后重试。
                showToast(R.string.profile_avatar_upload_failed_non_blocking);
                next.run();
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
        editor.putString(prefix + "_native_language", TextUtils.join(",", selectedNativeLanguages));
        editor.putString(prefix + "_learning_language", TextUtils.join(",", selectedLearningLanguages));
        editor.putString(prefix + "_birthday", selectedBirthday);
        editor.putString(prefix + "_height", getEditTextString(wkVBinding.heightEt));
        editor.putString(prefix + "_weight", getEditTextString(wkVBinding.weightEt));
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

        int quality = 88;
        while (quality >= MIN_WEBP_QUALITY) {
            writeWebp(outputBitmap, outFile, quality);
            if (outFile.length() <= TARGET_IMAGE_BYTES || quality == MIN_WEBP_QUALITY) {
                break;
            }
            quality -= 5;
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

    private Dialog createFullScreenDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnShowListener(d -> {
            Window showWindow = dialog.getWindow();
            if (showWindow != null) {
                showWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                showWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        return dialog;
    }

    private LinearLayout createDialogRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.profile_page_bg);
        root.setPadding(0, dp(18), 0, dp(16));
        return root;
    }

    private View createDialogHeader(String title, Dialog dialog) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(20), 0, dp(16), dp(12));
        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.parseColor("#111827"));
        titleTv.setTextSize(22);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(titleTv, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(28);
        close.setTextColor(Color.parseColor("#344054"));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private TextView createBottomAction(String text) {
        TextView done = new TextView(this);
        done.setText(text);
        done.setGravity(Gravity.CENTER);
        done.setTextColor(Color.WHITE);
        done.setTextSize(17);
        done.setTypeface(null, android.graphics.Typeface.BOLD);
        done.setBackground(makeGradientButtonBg());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
        params.setMargins(dp(22), dp(12), dp(22), dp(8));
        done.setLayoutParams(params);
        return done;
    }

    private GradientDrawable makeRoundBg(boolean selected, boolean soft, int accentColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(18));
        if (selected) {
            drawable.setColor(soft ? lighten(accentColor, 0.86f) : lighten(accentColor, 0.90f));
            drawable.setStroke(dp(1), lighten(accentColor, 0.35f));
        } else {
            drawable.setColor(Color.parseColor("#FBFCFF"));
            drawable.setStroke(dp(1), Color.parseColor("#FFFFFF"));
        }
        return drawable;
    }

    private GradientDrawable makeCategoryTabBg(boolean selected, int index) {
        int color = tagPalette()[index % tagPalette().length];
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(21));
        drawable.setColor(selected ? color : Color.parseColor("#F9FAFF"));
        drawable.setStroke(dp(1), selected ? color : Color.WHITE);
        return drawable;
    }

    private GradientDrawable makeGradientButtonBg() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#8A3FFC"), Color.parseColor("#4057F6")});
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private int[] tagPalette() {
        return new int[]{
                Color.parseColor("#7C3AED"),
                Color.parseColor("#2563EB"),
                Color.parseColor("#0891B2"),
                Color.parseColor("#059669"),
                Color.parseColor("#EA580C"),
                Color.parseColor("#DB2777"),
                Color.parseColor("#9333EA"),
                Color.parseColor("#0EA5E9"),
                Color.parseColor("#16A34A"),
                Color.parseColor("#F59E0B"),
                Color.parseColor("#EF4444")
        };
    }

    private int tagColorFor(String value) {
        int[] colors = tagPalette();
        return colors[Math.abs(value.hashCode()) % colors.length];
    }

    private int lighten(int color, float factor) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = (int) (r + (255 - r) * factor);
        g = (int) (g + (255 - g) * factor);
        b = (int) (b + (255 - b) * factor);
        return Color.rgb(r, g, b);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface ImageCompressCallback {
        void onResult(String compressedPath);
    }

    private interface SingleSelectCallback {
        void onSelected(String value);
    }

    private static class SpaceView extends View {
        public SpaceView(android.content.Context context) {
            super(context);
        }
    }
}
