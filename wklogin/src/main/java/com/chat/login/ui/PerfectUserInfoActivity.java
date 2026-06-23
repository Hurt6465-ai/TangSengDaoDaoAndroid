package com.chat.login.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 2020-08-28 13:43
 * 完善个人资料
 *
 * 稳定版：头像和昵称仍走原来的服务端逻辑；
 * 新增介绍、国籍、母语、学习语言、性别、出生日期先保存在本地，
 * 语伴后端插件做好后再统一迁移到服务端字段。
 */
public class PerfectUserInfoActivity extends WKBaseActivity<ActPerfectUserInfoLayoutBinding> {

    private static final int MAX_LANGUAGE_SELECT_COUNT = 5;

    String path;
    private String selectedCountry = "";
    private final List<String> selectedNativeLanguages = new ArrayList<>();
    private final List<String> selectedLearningLanguages = new ArrayList<>();
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
        wkVBinding.avatarView.setSize(120);
        wkVBinding.avatarView.setStrokeWidth(0);
        wkVBinding.avatarView.imageView.setImageResource(R.mipmap.icon_default_header);
        // 资料补充页不显示头像右下角“最后在线时间”，避免和相机按钮、后续国旗角标冲突。
        wkVBinding.avatarView.onlineTv.setText("");
        wkVBinding.avatarView.onlineTv.setVisibility(View.GONE);
        wkVBinding.avatarView.spotView.setVisibility(View.GONE);
    }

    @Override
    protected void initListener() {
        wkVBinding.sureBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.avatarView.setOnClickListener(v -> chooseIMG());
        wkVBinding.countryTv.setOnClickListener(v -> chooseOption(R.string.profile_country_region, R.array.profile_country_options, value -> {
            selectedCountry = value;
            wkVBinding.countryTv.setText(value);
        }));
        wkVBinding.nativeLanguageTv.setOnClickListener(v -> chooseMultiOption(
                R.string.profile_native_language,
                R.array.profile_language_options,
                selectedNativeLanguages,
                wkVBinding.nativeLanguageTv
        ));
        wkVBinding.learningLanguageTv.setOnClickListener(v -> chooseMultiOption(
                R.string.profile_learning_language,
                R.array.profile_language_options,
                selectedLearningLanguages,
                wkVBinding.learningLanguageTv
        ));
        wkVBinding.genderTv.setOnClickListener(v -> chooseOption(R.string.profile_gender, R.array.profile_gender_options, value -> {
            selectedGender = value;
            wkVBinding.genderTv.setText(value);
        }));
        wkVBinding.birthdayTv.setOnClickListener(v -> showBirthdayPicker());
        wkVBinding.sureBtn.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        if (TextUtils.isEmpty(path)) {
            showToast(R.string.wklogin_must_upload_header);
            return;
        }
        if (checkEditInputIsEmpty(wkVBinding.nameEt, R.string.nickname_not_null)) {
            return;
        }
        if (isEmptyText(wkVBinding.introEt.getText())) {
            showToast(R.string.profile_intro_required);
            return;
        }
        if (TextUtils.isEmpty(selectedCountry)) {
            showToast(R.string.profile_country_required);
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
        if (TextUtils.isEmpty(selectedGender)) {
            showToast(R.string.profile_gender_required);
            return;
        }
        if (TextUtils.isEmpty(selectedBirthday)) {
            showToast(R.string.profile_birthday_required);
            return;
        }

        String name = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString().trim();
        loadingPopup.show();
        LoginModel.getInstance().updateUserInfo("name", name, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                saveLocalUserName(name);
                saveLocalExtraProfile();
                uploadAvatarAfterNameSaved();
            } else {
                loadingPopup.dismiss();
            }
        });
    }

    private boolean isEmptyText(CharSequence text) {
        return text == null || TextUtils.isEmpty(text.toString().trim());
    }

    private void chooseIMG() {
        GlideUtils.getInstance().chooseIMG(this, 1, true, ChooseMimeType.img, false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (WKReader.isNotEmpty(paths)) {
                    path = paths.get(0).path;
                    showLocalAvatarPreview(path);
                }
            }

            @Override
            public void onCancel() {

            }
        });
    }

    private void showLocalAvatarPreview(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) return;
        try {
            wkVBinding.avatarView.imageView.setImageURI(Uri.fromFile(new File(imagePath)));
            wkVBinding.coverIv.setVisibility(View.GONE);
        } catch (Exception ignored) {
        }
    }

    private void chooseOption(int titleRes, int arrayRes, OptionCallback callback) {
        String[] options = getResources().getStringArray(arrayRes);
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setItems(options, (dialog, which) -> callback.onSelected(options[which]))
                .show();
    }

    private void chooseMultiOption(int titleRes, int arrayRes, List<String> selectedValues, TextView targetView) {
        String[] options = getResources().getStringArray(arrayRes);
        boolean[] checkedItems = new boolean[options.length];
        List<String> tempValues = new ArrayList<>(selectedValues);
        for (int i = 0; i < options.length; i++) {
            checkedItems[i] = tempValues.contains(options[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_multi_select_title, getString(titleRes)))
                .setMultiChoiceItems(options, checkedItems, (dialog, which, isChecked) -> {
                    String value = options[which];
                    if (isChecked) {
                        if (!tempValues.contains(value)) {
                            if (tempValues.size() >= MAX_LANGUAGE_SELECT_COUNT) {
                                ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                                showToast(R.string.profile_multi_select_limit);
                                return;
                            }
                            tempValues.add(value);
                        }
                    } else {
                        tempValues.remove(value);
                    }
                })
                .setPositiveButton(R.string.profile_select_done, (dialog, which) -> {
                    selectedValues.clear();
                    selectedValues.addAll(tempValues);
                    updateMultiSelectText(targetView, selectedValues);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateMultiSelectText(TextView targetView, List<String> selectedValues) {
        targetView.setText(joinSelections(selectedValues));
    }

    private String joinSelections(List<String> selectedValues) {
        if (selectedValues == null || selectedValues.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String value : selectedValues) {
            if (TextUtils.isEmpty(value)) continue;
            if (builder.length() > 0) builder.append("、");
            builder.append(value);
        }
        return builder.toString();
    }

    private void showBirthdayPicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedBirthday = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            wkVBinding.birthdayTv.setText(selectedBirthday);
        }, calendar.get(Calendar.YEAR) - 20, calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void saveLocalUserName(String name) {
        UserInfoEntity userInfoEntity = WKConfig.getInstance().getUserInfo();
        if (userInfoEntity != null) {
            userInfoEntity.name = name;
            WKConfig.getInstance().saveUserInfo(userInfoEntity);
        }
        WKConfig.getInstance().setUserName(name);
    }

    private void saveLocalExtraProfile() {
        String uid = WKConfig.getInstance().getUid();
        String prefix = TextUtils.isEmpty(uid) ? "current" : uid;
        String nativeLanguages = joinSelections(selectedNativeLanguages);
        String learningLanguages = joinSelections(selectedLearningLanguages);
        SharedPreferences.Editor editor = getSharedPreferences("front_profile_extra", MODE_PRIVATE).edit();
        editor.putString(prefix + "_intro", wkVBinding.introEt.getText() == null ? "" : wkVBinding.introEt.getText().toString().trim());
        editor.putString(prefix + "_country", selectedCountry);
        editor.putString(prefix + "_native_language", nativeLanguages);
        editor.putString(prefix + "_native_languages", nativeLanguages);
        editor.putString(prefix + "_learning_language", learningLanguages);
        editor.putString(prefix + "_learning_languages", learningLanguages);
        editor.putString(prefix + "_gender", selectedGender);
        editor.putString(prefix + "_birthday", selectedBirthday);
        editor.apply();
    }

    private void uploadAvatarAfterNameSaved() {
        if (TextUtils.isEmpty(path)) {
            finishPerfectUserInfo();
            return;
        }
        LoginModel.getInstance().uploadAvatar(path, code -> finishPerfectUserInfo());
    }

    private void finishPerfectUserInfo() {
        List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
        if (WKReader.isNotEmpty(list)) {
            for (LoginMenu menu : list) {
                if (menu.iMenuClick != null)
                    menu.iMenuClick.onClick();
            }
        }
        loadingPopup.dismiss();
        setResult(RESULT_OK);
        finish();
    }

    private interface OptionCallback {
        void onSelected(String value);
    }
}
