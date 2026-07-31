package com.chat.dating;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingSharedProfileBinding;
import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 交友模块内的共享资料编辑页。
 * 默认读取语伴/账号的共享字段，保存到 user/current，语伴和交友同步生效。
 */
public class DatingSharedProfileActivity extends Activity {
    public static final String EXTRA_PROFILE = "dating_shared_profile";
    public static final String EXTRA_RESULT_PROFILE = "dating_shared_profile_result";

    private static final int MAX_LANGUAGE_COUNT = 5;
    private static final int MAX_MULTI_TAG_COUNT = 5;

    private static final String[] COUNTRY_CODES = {
            "MM", "CN", "TH", "JP", "KR", "VN", "LA", "KH", "MY", "SG", "US"
    };
    private static final String[] LANGUAGE_CODES = {
            "MY", "ZH", "EN", "TH", "JA", "KO", "VI", "ID", "MS"
    };
    private static final String[] RELATIONSHIP_CODES = {
            "relationship_private", "relationship_single", "relationship_dating",
            "relationship_married", "relationship_divorced"
    };
    private static final String[] PERSONALITY_CODES = {
            "personality_patient", "personality_outgoing", "personality_quiet", "personality_introvert",
            "personality_funny", "personality_gentle", "personality_serious", "personality_slow_warm",
            "personality_eq", "personality_easygoing"
    };
    private static final String[] PET_CODES = {
            "pet_dog", "pet_cat", "pet_rabbit", "pet_bird", "pet_fish", "pet_hamster",
            "pet_reptile", "pet_love_animals"
    };
    private static final String[] SPORT_CODES = {
            "sport_running", "sport_basketball", "sport_football", "sport_badminton", "sport_fitness",
            "sport_yoga", "sport_swimming", "sport_cycling", "sport_hiking", "sport_skateboard"
    };
    private static final String[] MOVIE_CODES = {
            "movie_film", "movie_comedy", "movie_romance", "movie_action", "movie_mystery",
            "movie_documentary", "movie_tv", "movie_anime", "movie_variety", "movie_short_video"
    };
    private static final String[] JOB_CODES = {
            "job_private", "job_student", "job_worker", "job_waiter", "job_teacher", "job_police",
            "job_driver", "job_sales", "job_boss", "job_freelance", "job_unemployed", "job_other"
    };
    private static final String[] EDUCATION_CODES = {
            "education_private", "education_middle", "education_high", "education_bachelor",
            "education_master", "education_other"
    };

    private ActivityWkDatingSharedProfileBinding binding;
    private DatingProfile profile;
    private String birthday = "";
    private int sex = -1;
    private String countryCode = "";
    private String countryName = "";
    private String relationshipCode = "";
    private String jobCode = "";
    private String educationCode = "";
    private final ArrayList<String> nativeLanguages = new ArrayList<>();
    private final ArrayList<String> learningLanguages = new ArrayList<>();
    private final ArrayList<String> personalityCodes = new ArrayList<>();
    private final ArrayList<String> petCodes = new ArrayList<>();
    private final ArrayList<String> sportCodes = new ArrayList<>();
    private final ArrayList<String> movieCodes = new ArrayList<>();
    private boolean saving;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(247, 247, 249));
        binding = ActivityWkDatingSharedProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        DatingUi.applyPageInsets(this, binding.getRoot());
        Object value = getIntent().getSerializableExtra(EXTRA_PROFILE);
        profile = value instanceof DatingProfile ? (DatingProfile) value : new DatingProfile();
        initValues();
        initListeners();
        bindValues();
    }

    private void initValues() {
        birthday = safe(profile.birthday);
        sex = profile.normalizedSex();
        countryCode = normalizeCode(profile.safeCountryCode());
        countryName = safe(profile.country);
        relationshipCode = firstCategory(profile.relationship_status, profile.safeTags(), "relationship_");
        jobCode = firstCategory(!TextUtils.isEmpty(profile.job_status) ? profile.job_status : profile.job,
                profile.safeTags(), "job_");
        educationCode = firstCategory(profile.education, profile.safeTags(), "education_");
        nativeLanguages.addAll(normalizeLanguageCodes(profile.safeNativeLanguages()));
        learningLanguages.addAll(normalizeLanguageCodes(profile.safeLearningLanguages()));
        copyCategory(profile.safePersonalityTags(), profile.safeTags(), "personality_", personalityCodes);
        copyCategory(profile.safePetTags(), profile.safeTags(), "pet_", petCodes);
        copyCategory(profile.safeSportTags(), profile.safeTags(), "sport_", sportCodes);
        copyCategory(profile.safeMovieTags(), profile.safeTags(), "movie_", movieCodes);
    }

    private void initListeners() {
        binding.backBtn.setOnClickListener(v -> finish());
        binding.birthdayRow.setOnClickListener(v -> showBirthdayPicker());
        binding.genderRow.setOnClickListener(v -> pickSingle(
                getString(R.string.dating_shared_gender),
                getResources().getStringArray(R.array.dating_shared_gender_options),
                sex == 1 ? 0 : (sex == 0 ? 1 : 2),
                index -> {
                    sex = index == 0 ? 1 : (index == 1 ? 0 : 2);
                    updateCoreValues();
                }));
        binding.countryRow.setOnClickListener(v -> pickCountry());
        binding.nativeLanguagesRow.setOnClickListener(v -> pickLanguages(true));
        binding.learningLanguagesRow.setOnClickListener(v -> pickLanguages(false));
        binding.relationshipRow.setOnClickListener(v -> pickCodeSingle(
                getString(R.string.dating_shared_relationship_label), RELATIONSHIP_CODES,
                R.array.dating_shared_relationship_values, relationshipCode,
                value -> { relationshipCode = value; updatePartnerValues(); }));
        binding.personalityRow.setOnClickListener(v -> pickCodeMulti(
                getString(R.string.dating_shared_personality_label), PERSONALITY_CODES,
                R.array.dating_shared_personality_values, personalityCodes,
                () -> updatePartnerValues()));
        binding.petsRow.setOnClickListener(v -> pickCodeMulti(
                getString(R.string.dating_shared_pets), PET_CODES,
                R.array.dating_shared_pet_values, petCodes,
                () -> updatePartnerValues()));
        binding.sportsRow.setOnClickListener(v -> pickCodeMulti(
                getString(R.string.dating_shared_sports), SPORT_CODES,
                R.array.dating_shared_sport_values, sportCodes,
                () -> updatePartnerValues()));
        binding.moviesRow.setOnClickListener(v -> pickCodeMulti(
                getString(R.string.dating_shared_movies), MOVIE_CODES,
                R.array.dating_shared_movie_values, movieCodes,
                () -> updatePartnerValues()));
        binding.jobRow.setOnClickListener(v -> pickCodeSingle(
                getString(R.string.dating_shared_job), JOB_CODES,
                R.array.dating_shared_job_values, jobCode,
                value -> { jobCode = value; updatePartnerValues(); }));
        binding.educationRow.setOnClickListener(v -> pickCodeSingle(
                getString(R.string.dating_shared_education), EDUCATION_CODES,
                R.array.dating_shared_education_values, educationCode,
                value -> { educationCode = value; updatePartnerValues(); }));
        binding.saveBtn.setOnClickListener(v -> saveSharedProfile());
    }

    private void bindValues() {
        binding.nameEt.setText(!TextUtils.isEmpty(profile.name)
                ? profile.name : WKConfig.getInstance().getUserName());
        updateCoreValues();
        updatePartnerValues();
    }

    private void updateCoreValues() {
        binding.birthdayValue.setText(orSelect(birthday));
        String[] genders = getResources().getStringArray(R.array.dating_shared_gender_options);
        int genderIndex = sex == 1 ? 0 : (sex == 0 ? 1 : 2);
        binding.genderValue.setText(genderIndex < genders.length ? genders[genderIndex] : getString(R.string.dating_select));
        String countryDisplay = countryDisplay(countryCode);
        binding.countryValue.setText(orSelect(TextUtils.isEmpty(countryDisplay) ? countryName : countryDisplay));
        binding.nativeLanguagesValue.setText(orSelect(languageDisplay(nativeLanguages)));
        binding.learningLanguagesValue.setText(orSelect(languageDisplay(learningLanguages)));
    }

    private void updatePartnerValues() {
        binding.relationshipValue.setText(orSelect(codeLabel(RELATIONSHIP_CODES,
                R.array.dating_shared_relationship_values, relationshipCode)));
        binding.personalityValue.setText(orSelect(codeLabels(PERSONALITY_CODES,
                R.array.dating_shared_personality_values, personalityCodes)));
        binding.petsValue.setText(orSelect(codeLabels(PET_CODES,
                R.array.dating_shared_pet_values, petCodes)));
        binding.sportsValue.setText(orSelect(codeLabels(SPORT_CODES,
                R.array.dating_shared_sport_values, sportCodes)));
        binding.moviesValue.setText(orSelect(codeLabels(MOVIE_CODES,
                R.array.dating_shared_movie_values, movieCodes)));
        binding.jobValue.setText(orSelect(codeLabel(JOB_CODES,
                R.array.dating_shared_job_values, jobCode)));
        binding.educationValue.setText(orSelect(codeLabel(EDUCATION_CODES,
                R.array.dating_shared_education_values, educationCode)));
    }

    private void showBirthdayPicker() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR) - 24;
        int month = now.get(Calendar.MONTH);
        int day = now.get(Calendar.DAY_OF_MONTH);
        if (birthday.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                year = Integer.parseInt(birthday.substring(0, 4));
                month = Integer.parseInt(birthday.substring(5, 7)) - 1;
                day = Integer.parseInt(birthday.substring(8, 10));
            } catch (Throwable ignored) {
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            birthday = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            updateCoreValues();
        }, year, month, day);
        Calendar adultLimit = Calendar.getInstance();
        adultLimit.add(Calendar.YEAR, -18);
        dialog.getDatePicker().setMaxDate(adultLimit.getTimeInMillis());
        dialog.show();
    }

    private void pickCountry() {
        String[] labels = getResources().getStringArray(R.array.dating_shared_country_options);
        int selected = indexOf(COUNTRY_CODES, countryCode);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_shared_country)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    if (which >= 0 && which < COUNTRY_CODES.length) {
                        countryCode = COUNTRY_CODES[which];
                        countryName = countryNameFromLabel(labels[which]);
                        updateCoreValues();
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void pickLanguages(boolean nativeSide) {
        String[] labels = getResources().getStringArray(R.array.dating_shared_language_options);
        ArrayList<String> target = nativeSide ? nativeLanguages : learningLanguages;
        boolean[] checked = new boolean[LANGUAGE_CODES.length];
        for (int i = 0; i < LANGUAGE_CODES.length; i++) checked[i] = target.contains(LANGUAGE_CODES[i]);
        ArrayList<String> draft = new ArrayList<>(target);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(nativeSide ? R.string.dating_shared_native_languages : R.string.dating_shared_learning_languages)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    if (which < 0 || which >= LANGUAGE_CODES.length) return;
                    String code = LANGUAGE_CODES[which];
                    if (isChecked) {
                        if (draft.size() >= MAX_LANGUAGE_COUNT && !draft.contains(code)) {
                            ((AlertDialog) d).getListView().setItemChecked(which, false);
                            toast(getString(R.string.dating_shared_language_limit, MAX_LANGUAGE_COUNT));
                            return;
                        }
                        if (!draft.contains(code)) draft.add(code);
                    } else {
                        draft.remove(code);
                    }
                })
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(R.string.dating_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            target.clear();
            target.addAll(draft);
            updateCoreValues();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void pickSingle(String title, String[] labels, int selected, IndexConsumer consumer) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    consumer.accept(which);
                    dialog.dismiss();
                })
                .show();
    }

    private void pickCodeSingle(String title, String[] codes, int labelsRes, String current,
                                StringConsumer consumer) {
        String[] labels = getResources().getStringArray(labelsRes);
        int selected = indexOf(codes, current);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    if (which >= 0 && which < codes.length) consumer.accept(codes[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void pickCodeMulti(String title, String[] codes, int labelsRes,
                               ArrayList<String> target, Runnable onChanged) {
        String[] labels = getResources().getStringArray(labelsRes);
        boolean[] checked = new boolean[codes.length];
        for (int i = 0; i < codes.length; i++) checked[i] = target.contains(codes[i]);
        ArrayList<String> draft = new ArrayList<>(target);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    if (which < 0 || which >= codes.length) return;
                    String code = codes[which];
                    if (isChecked) {
                        if (draft.size() >= MAX_MULTI_TAG_COUNT && !draft.contains(code)) {
                            ((AlertDialog) d).getListView().setItemChecked(which, false);
                            toast(getString(R.string.dating_shared_tag_limit, MAX_MULTI_TAG_COUNT));
                            return;
                        }
                        if (!draft.contains(code)) draft.add(code);
                    } else {
                        draft.remove(code);
                    }
                })
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(R.string.dating_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            target.clear();
            target.addAll(draft);
            onChanged.run();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void saveSharedProfile() {
        if (saving) return;
        String name = safe(binding.nameEt.getText() == null ? "" : binding.nameEt.getText().toString());
        if (TextUtils.isEmpty(name)) {
            toast(getString(R.string.dating_shared_name_required));
            return;
        }
        if (TextUtils.isEmpty(birthday)) {
            toast(getString(R.string.dating_shared_birthday_required));
            return;
        }
        if (TextUtils.isEmpty(countryCode)) {
            toast(getString(R.string.dating_shared_country_required));
            return;
        }
        if (nativeLanguages.isEmpty() || learningLanguages.isEmpty()) {
            toast(getString(R.string.dating_shared_languages_required));
            return;
        }

        JSONObject body = new JSONObject();
        body.put("name", name);
        body.put("sex", sex < 0 ? 2 : sex);
        body.put("birthday", birthday);
        body.put("country_code", countryCode);
        body.put("country", countryName);
        body.put("native_languages", new ArrayList<>(nativeLanguages));
        body.put("learning_languages", new ArrayList<>(learningLanguages));
        body.put("tags", buildSharedTags());

        setSaving(true);
        DatingModel.getInstance().updateSharedUser(body, (code, msg, data) -> {
            if (isFinishing() || binding == null) return;
            if (code != HttpResponseCode.success) {
                setSaving(false);
                toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_shared_save_failed) : msg);
                return;
            }
            updateLocalUser(name, sex);
            DatingModel.getInstance().getMyDatingProfile((profileCode, profileMsg, updated) -> {
                if (isFinishing() || binding == null) return;
                setSaving(false);
                if (profileCode == HttpResponseCode.success && updated != null) {
                    Intent result = new Intent();
                    result.putExtra(EXTRA_RESULT_PROFILE, updated);
                    setResult(RESULT_OK, result);
                } else {
                    setResult(RESULT_OK);
                }
                toast(getString(R.string.dating_shared_saved));
                finish();
            });
        });
    }

    private ArrayList<String> buildSharedTags() {
        ArrayList<String> tags = new ArrayList<>();
        for (String raw : profile.safeTags()) {
            if (TextUtils.isEmpty(raw)) continue;
            String lower = raw.trim().toLowerCase(Locale.US);
            if (lower.startsWith("relationship_") || lower.startsWith("personality_")
                    || lower.startsWith("pet_") || lower.startsWith("sport_")
                    || lower.startsWith("movie_") || lower.startsWith("job_")
                    || lower.startsWith("education_")) continue;
            addUnique(tags, raw.trim());
        }
        addUnique(tags, relationshipCode);
        addAllUnique(tags, personalityCodes);
        addAllUnique(tags, petCodes);
        addAllUnique(tags, sportCodes);
        addAllUnique(tags, movieCodes);
        addUnique(tags, jobCode);
        addUnique(tags, educationCode);
        return tags;
    }

    private void updateLocalUser(String name, int sexValue) {
        WKConfig.getInstance().setUserName(name);
        UserInfoEntity info = WKConfig.getInstance().getUserInfo();
        if (info == null) return;
        info.name = name;
        if (sexValue >= 0) info.sex = sexValue;
        WKConfig.getInstance().saveUserInfo(info);
    }

    private void setSaving(boolean value) {
        saving = value;
        binding.saveBtn.setEnabled(!value);
        binding.saveBtn.setText(value ? R.string.dating_saving : R.string.dating_save_shared_profile);
    }

    private String countryDisplay(String code) {
        String[] labels = getResources().getStringArray(R.array.dating_shared_country_options);
        int index = indexOf(COUNTRY_CODES, code);
        return index >= 0 && index < labels.length ? labels[index] : "";
    }

    private String languageDisplay(List<String> codes) {
        String[] labels = getResources().getStringArray(R.array.dating_shared_language_options);
        ArrayList<String> out = new ArrayList<>();
        for (String code : codes) {
            int index = indexOf(LANGUAGE_CODES, code);
            if (index >= 0 && index < labels.length) addUnique(out, labels[index]);
            else addUnique(out, code);
        }
        return TextUtils.join(getString(R.string.dating_list_separator), out);
    }

    private String codeLabel(String[] codes, int labelsRes, String selected) {
        int index = indexOf(codes, selected);
        String[] labels = getResources().getStringArray(labelsRes);
        return index >= 0 && index < labels.length ? labels[index] : "";
    }

    private String codeLabels(String[] codes, int labelsRes, List<String> selected) {
        String[] labels = getResources().getStringArray(labelsRes);
        ArrayList<String> out = new ArrayList<>();
        for (String code : selected) {
            int index = indexOf(codes, code);
            if (index >= 0 && index < labels.length) addUnique(out, labels[index]);
        }
        return TextUtils.join(getString(R.string.dating_list_separator), out);
    }

    private ArrayList<String> normalizeLanguageCodes(List<String> values) {
        ArrayList<String> out = new ArrayList<>();
        if (values == null) return out;
        for (String value : values) {
            String code = normalizeLanguageCode(value);
            if (!TextUtils.isEmpty(code)) addUnique(out, code);
        }
        return out;
    }

    private String normalizeLanguageCode(String value) {
        String clean = normalizeCode(value);
        for (String code : LANGUAGE_CODES) {
            if (code.equals(clean) || clean.endsWith(" " + code) || clean.endsWith(code)) return code;
        }
        if (clean.contains("中文") || clean.contains("CHINESE")) return "ZH";
        if (clean.contains("缅甸") || clean.contains("MYANMAR") || clean.contains("BURMESE")) return "MY";
        if (clean.contains("英语") || clean.contains("ENGLISH")) return "EN";
        if (clean.contains("泰语") || clean.contains("THAI")) return "TH";
        if (clean.contains("日语") || clean.contains("JAPANESE")) return "JA";
        if (clean.contains("韩语") || clean.contains("KOREAN")) return "KO";
        if (clean.contains("越南") || clean.contains("VIETNAMESE")) return "VI";
        if (clean.contains("印尼") || clean.contains("INDONESIAN")) return "ID";
        if (clean.contains("马来") || clean.contains("MALAY")) return "MS";
        return "";
    }

    private void copyCategory(List<String> direct, List<String> all, String prefix, ArrayList<String> out) {
        out.clear();
        if (direct != null) {
            for (String value : direct) {
                String code = DatingSharedProfileFormatter.canonicalCode(value);
                if (!TextUtils.isEmpty(code) && code.toLowerCase(Locale.US).startsWith(prefix)) addUnique(out, code);
            }
        }
        if (!out.isEmpty() || all == null) return;
        for (String value : all) {
            String code = DatingSharedProfileFormatter.canonicalCode(value);
            if (!TextUtils.isEmpty(code) && code.toLowerCase(Locale.US).startsWith(prefix)) addUnique(out, code);
        }
    }

    private String firstCategory(String direct, List<String> tags, String prefix) {
        String directCode = DatingSharedProfileFormatter.canonicalCode(direct);
        if (!TextUtils.isEmpty(directCode) && directCode.toLowerCase(Locale.US).startsWith(prefix)) return directCode;
        if (tags != null) {
            for (String value : tags) {
                String code = DatingSharedProfileFormatter.canonicalCode(value);
                if (!TextUtils.isEmpty(code) && code.toLowerCase(Locale.US).startsWith(prefix)) return code;
            }
        }
        return "";
    }

    private String countryNameFromLabel(String label) {
        if (TextUtils.isEmpty(label)) return "";
        String value = label.trim();
        int firstSpace = value.indexOf(' ');
        return firstSpace >= 0 && firstSpace < value.length() - 1 ? value.substring(firstSpace + 1).trim() : value;
    }

    private String orSelect(String value) {
        return TextUtils.isEmpty(value) ? getString(R.string.dating_select) : value;
    }

    private String normalizeCode(String value) {
        return TextUtils.isEmpty(value) ? "" : value.trim().toUpperCase(Locale.US);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int indexOf(String[] values, String target) {
        if (values == null || TextUtils.isEmpty(target)) return -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(target)) return i;
        }
        return -1;
    }

    private void addUnique(ArrayList<String> out, String value) {
        if (!TextUtils.isEmpty(value) && !out.contains(value)) out.add(value);
    }

    private void addAllUnique(ArrayList<String> out, List<String> values) {
        if (values == null) return;
        for (String value : values) addUnique(out, value);
    }

    private void toast(String text) {
        if (!TextUtils.isEmpty(text)) Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private interface IndexConsumer { void accept(int index); }
    private interface StringConsumer { void accept(String value); }
}
