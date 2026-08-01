package com.chat.dating;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingEditProfileBinding;
import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 编辑自己的交友资料。共享字段来自统一账号资料，可从本页入口直接编辑。 */
public class DatingEditProfileActivity extends Activity {
    public static final String EXTRA_PROFILE = "dating_edit_profile";
    private static final int REQ_PICK_IMAGES = 701;
    private static final int REQ_SHARED_PROFILE = 702;

    private String[] genderOptions;
    private String[] ageOptions;
    private String[] sexualOrientationOptions;
    private String[] drinkingOptions;
    private String[] smokingOptions;
    private String[] dealbreakerOptions;

    private ActivityWkDatingEditProfileBinding binding;
    private DatingProfile profile;
    private DatingPhotoGridAdapter photoAdapter;
    private ItemTouchHelper touchHelper;
    private DatingPhotoUploadManager uploadManager;
    private boolean uploading;
    private boolean initiallyEnabled;

    private String intentValue = DatingIntent.LONG_TERM;
    private int genderPreference = -1;
    private int ageMin = 18;
    private int ageMax = 28;
    private String sexualOrientationValue = "";
    private String drinkingValue = "";
    private String smokingValue = "";
    private final ArrayList<String> dealbreakers = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(247, 247, 249));
        binding = ActivityWkDatingEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        DatingUi.applyPageInsets(this, binding.getRoot());
        Object value = getIntent().getSerializableExtra(EXTRA_PROFILE);
        if (value instanceof DatingProfile) profile = (DatingProfile) value;
        if (profile == null) profile = new DatingProfile();
        genderOptions = getResources().getStringArray(R.array.dating_gender_options);
        ageOptions = getResources().getStringArray(R.array.dating_age_options);
        sexualOrientationOptions = getResources().getStringArray(R.array.dating_sexual_orientation_options);
        drinkingOptions = getResources().getStringArray(R.array.dating_drinking_options);
        smokingOptions = getResources().getStringArray(R.array.dating_smoking_options);
        dealbreakerOptions = getResources().getStringArray(R.array.dating_dealbreaker_options);
        sexualOrientationValue = DatingValueFormatter.orientationCode(this, sexualOrientationOptions[0]);
        drinkingValue = DatingValueFormatter.drinkingCode(this, drinkingOptions[Math.min(1, drinkingOptions.length - 1)]);
        smokingValue = DatingValueFormatter.smokingCode(this, smokingOptions[0]);
        uploadManager = new DatingPhotoUploadManager(this);
        initPhotoGrid();
        initListeners();
        bindProfile();
        syncSharedProfile();
    }

    private void initPhotoGrid() {
        photoAdapter = new DatingPhotoGridAdapter();
        binding.photoRecycler.setLayoutManager(new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL));
        binding.photoRecycler.setAdapter(photoAdapter);
        binding.photoRecycler.setNestedScrollingEnabled(false);
        touchHelper = new ItemTouchHelper(DatingPhotoGridAdapter.touchCallback(photoAdapter));
        touchHelper.attachToRecyclerView(binding.photoRecycler);
        photoAdapter.setListener(new DatingPhotoGridAdapter.Listener() {
            @Override public void onAddPhoto() { openPicker(); }
            @Override public void onDeletePhoto(int position, String url) { confirmDelete(position); }
            @Override public void onPreviewPhoto(int position, String url) { showPhotoOptions(position); }
            @Override public void onStartDrag(androidx.recyclerview.widget.RecyclerView.ViewHolder holder) { touchHelper.startDrag(holder); }
        });
    }

    private void initListeners() {
        binding.backBtn.setOnClickListener(v -> {
            if (uploading) toast(getString(R.string.dating_uploading_wait));
            else finish();
        });
        binding.intentRow.setOnClickListener(v -> {
            String[] options = DatingIntent.profileLabels(this);
            pickSingle(getString(R.string.dating_edit_intent), options,
                    DatingIntent.displayLabel(this, intentValue), value -> {
                        intentValue = DatingIntent.codeForDisplayLabel(this, value);
                        binding.intentValue.setText(DatingIntent.displayLabel(this, intentValue));
                        updateProfileScore();
                    });
        });
        binding.genderRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_gender), genderOptions, genderText(), value -> {
            genderPreference = genderOptions[1].equals(value) ? 0 : (genderOptions[2].equals(value) ? 1 : -1);
            binding.genderValue.setText(value);
            updateProfileScore();
        }));
        binding.ageRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_age), ageOptions, getString(R.string.dating_age_range_plain, ageMin, ageMax), value -> {
            String[] pair = value.replaceAll("[^0-9-]", "").split("-");
            if (pair.length == 2) {
                ageMin = parseInt(pair[0]);
                ageMax = parseInt(pair[1]);
                binding.ageValue.setText(getString(R.string.dating_age_range_plain, ageMin, ageMax));
                updateProfileScore();
            }
        }));
        binding.sexualOrientationRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_orientation), sexualOrientationOptions,
                DatingValueFormatter.orientation(this, sexualOrientationValue), value -> {
                    sexualOrientationValue = DatingValueFormatter.orientationCode(this, value);
                    binding.sexualOrientationValue.setText(DatingValueFormatter.orientation(this, sexualOrientationValue));
                    updateProfileScore();
                }));
        binding.drinkingRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_drinking), drinkingOptions,
                DatingValueFormatter.drinking(this, drinkingValue), value -> {
                    drinkingValue = DatingValueFormatter.drinkingCode(this, value);
                    binding.drinkingValue.setText(DatingValueFormatter.drinking(this, drinkingValue));
                    updateProfileScore();
                }));
        binding.smokingRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_smoking), smokingOptions,
                DatingValueFormatter.smoking(this, smokingValue), value -> {
                    smokingValue = DatingValueFormatter.smokingCode(this, value);
                    binding.smokingValue.setText(DatingValueFormatter.smoking(this, smokingValue));
                    updateProfileScore();
                }));
        binding.dealbreakersRow.setOnClickListener(v -> pickDealbreakers());
        binding.sharedEditBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, DatingSharedProfileActivity.class);
            intent.putExtra(DatingSharedProfileActivity.EXTRA_PROFILE, profile);
            startActivityForResult(intent, REQ_SHARED_PROFILE);
        });
        binding.previewTab.setOnClickListener(v -> openPreview());
        binding.saveBtn.setOnClickListener(v -> saveProfile());
        TextWatcher scoreWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateProfileScore(); }
        };
        binding.cityEt.addTextChangedListener(scoreWatcher);
        binding.heightEt.addTextChangedListener(scoreWatcher);
        binding.weightEt.addTextChangedListener(scoreWatcher);
        binding.introEt.addTextChangedListener(scoreWatcher);
        binding.idealPartnerEt.addTextChangedListener(scoreWatcher);
        binding.tagsEt.addTextChangedListener(scoreWatcher);
    }

    private void bindProfile() {
        photoAdapter.setPhotos(profile.safeDatingPhotos());
        intentValue = normalizeIntent(profile.safeRelationshipGoal());
        genderPreference = profile.gender_preference;
        ageMin = profile.min_age > 0 ? profile.min_age : 18;
        ageMax = profile.max_age > ageMin ? profile.max_age : 28;
        sexualOrientationValue = DatingValueFormatter.orientationCode(this,
                TextUtils.isEmpty(profile.sexual_orientation) ? sexualOrientationOptions[0] : profile.sexual_orientation);
        drinkingValue = DatingValueFormatter.drinkingCode(this,
                TextUtils.isEmpty(profile.drinking) ? drinkingOptions[Math.min(1, drinkingOptions.length - 1)] : profile.drinking);
        smokingValue = DatingValueFormatter.smokingCode(this,
                TextUtils.isEmpty(profile.smoking) ? smokingOptions[0] : profile.smoking);
        dealbreakers.clear();
        dealbreakers.addAll(DatingValueFormatter.dealbreakerCodes(this, profile.safeDealbreakers()));
        initiallyEnabled = profile.enabled == 1;

        binding.intentValue.setText(DatingIntent.displayLabel(this, intentValue));
        binding.genderValue.setText(genderText());
        binding.ageValue.setText(getString(R.string.dating_age_range_plain, ageMin, ageMax));
        binding.sexualOrientationValue.setText(DatingValueFormatter.orientation(this, sexualOrientationValue));
        binding.drinkingValue.setText(DatingValueFormatter.drinking(this, drinkingValue));
        binding.smokingValue.setText(DatingValueFormatter.smoking(this, smokingValue));
        updateDealbreakersText();
        binding.cityEt.setText(profile.city);
        if (profile.height_cm > 0) binding.heightEt.setText(String.valueOf(profile.height_cm));
        if (profile.weight_kg > 0) binding.weightEt.setText(String.valueOf(profile.weight_kg));
        binding.introEt.setText(profile.safeIntro());
        binding.idealPartnerEt.setText(profile.ideal_partner);
        binding.tagsEt.setText(TextUtils.join(getString(R.string.dating_list_separator), profile.tags == null ? new ArrayList<>() : profile.tags));
        bindSharedFields();
        updatePhotoTip();
        updateProfileScore();
    }

    private void syncSharedProfile() {
        DatingModel.getInstance().copyPartnerProfile((code, msg, data) -> {
            if (code == HttpResponseCode.success && data != null) {
                DatingSharedProfileFormatter.mergeSharedFields(profile, data);
                bindSharedFields();
            }
        });
    }

    private void bindSharedFields() {
        ArrayList<String> lines = new ArrayList<>();
        addSharedLine(lines, DatingSharedProfileFormatter.basicLine(this, profile));
        addSharedLine(lines, DatingSharedProfileFormatter.relationshipLine(this, profile));
        addSharedLine(lines, DatingSharedProfileFormatter.personalityLine(this, profile));
        addSharedLine(lines, DatingSharedProfileFormatter.interestsLine(this, profile));
        addSharedLine(lines, DatingSharedProfileFormatter.careerLine(this, profile));
        binding.sharedSummaryTv.setText(lines.isEmpty()
                ? getString(R.string.dating_not_filled)
                : TextUtils.join("\n", lines));
        updateProfileScore();
    }

    private void addSharedLine(List<String> lines, String value) {
        if (!TextUtils.isEmpty(value) && !lines.contains(value)) lines.add(value);
    }

    private void openPicker() {
        if (uploading) return;
        int remain = DatingPhotoPolicy.MAX_PHOTO_COUNT - photoAdapter.photoCount();
        if (remain <= 0) {
            toast(getString(R.string.dating_max_photos));
            return;
        }
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            if (remain > 1) {
                intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, remain);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SHARED_PROFILE) {
            if (resultCode == RESULT_OK && data != null) {
                Object updated = data.getSerializableExtra(DatingSharedProfileActivity.EXTRA_RESULT_PROFILE);
                if (updated instanceof DatingProfile) {
                    DatingSharedProfileFormatter.mergeSharedFields(profile, (DatingProfile) updated);
                    bindSharedFields();
                } else {
                    syncSharedProfile();
                }
            }
            return;
        }
        if (requestCode != REQ_PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        int remain = DatingPhotoPolicy.MAX_PHOTO_COUNT - photoAdapter.photoCount();
        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount() && uris.size() < remain; i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null && !uris.contains(uri)) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (!uris.isEmpty()) uploadSelected(uris);
    }

    private void uploadSelected(List<Uri> uris) {
        uploading = true;
        setUploading(true, 0, getString(R.string.dating_upload_prepare));
        uploadManager.upload(uris, new DatingPhotoUploadManager.Callback() {
            @Override public void onProgress(int progress, String message) { setUploading(true, progress, message); }
            @Override public void onSuccess(List<String> photoUrls) {
                uploading = false;
                setUploading(false, 100, "");
                photoAdapter.appendPhotos(photoUrls);
                updatePhotoTip();
                updateProfileScore();
                toast(getString(R.string.dating_photo_upload_done));
            }
            @Override public void onError(String message) {
                uploading = false;
                setUploading(false, 0, "");
                toast(message);
            }
        });
    }

    private void showPhotoOptions(int position) {
        ArrayList<Integer> actionIds = new ArrayList<>();
        if (position != 0) actionIds.add(R.string.dating_set_primary);
        actionIds.add(R.string.dating_delete_photo_action);
        String[] actions = new String[actionIds.size()];
        for (int i = 0; i < actionIds.size(); i++) actions[i] = getString(actionIds.get(i));
        new AlertDialog.Builder(this)
                .setTitle(position == 0
                        ? getString(R.string.dating_primary_photo)
                        : getString(R.string.dating_photo_number, position + 1))
                .setItems(actions, (dialog, which) -> {
                    if (actionIds.get(which) == R.string.dating_set_primary) {
                        photoAdapter.movePhoto(position, 0);
                        photoAdapter.notifyDataSetChanged();
                    } else {
                        confirmDelete(position);
                    }
                }).show();
    }

    private void confirmDelete(int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_delete_photo_title)
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(R.string.dating_delete, (dialog, which) -> {
                    photoAdapter.removePhoto(position);
                    updatePhotoTip();
                    updateProfileScore();
                }).show();
    }

    private void openPreview() {
        if (uploading) {
            toast(getString(R.string.dating_uploading_wait));
            return;
        }
        if (photoAdapter.getPhotos().isEmpty()) {
            toast(getString(R.string.dating_preview_need_photo));
            return;
        }
        applyFormToProfile();
        Intent intent = new Intent(this, DatingProfileDetailActivity.class);
        intent.putExtra(DatingProfileDetailActivity.EXTRA_PROFILE, profile);
        intent.putExtra(DatingProfileDetailActivity.EXTRA_PHOTO_INDEX, 0);
        intent.putExtra(DatingProfileDetailActivity.EXTRA_PREVIEW_ONLY, true);
        startActivity(intent);
    }

    private void applyFormToProfile() {
        ArrayList<String> photos = photoAdapter.getPhotos();
        profile.photos = new ArrayList<>(photos);
        profile.card_photos = new ArrayList<>(photos);
        profile.profile_images = new ArrayList<>(photos);
        profile.intent = intentValue;
        profile.relationship_goal = intentValue;
        profile.gender_preference = genderPreference;
        profile.min_age = ageMin;
        profile.max_age = ageMax;
        profile.city = text(binding.cityEt);
        profile.sexual_orientation = sexualOrientationValue;
        profile.drinking = drinkingValue;
        profile.smoking = smokingValue;
        profile.height_cm = parseInt(text(binding.heightEt));
        profile.weight_kg = parseInt(text(binding.weightEt));
        profile.bio = limit(text(binding.introEt), 500);
        profile.intro = profile.bio;
        profile.ideal_partner = limit(text(binding.idealPartnerEt), 200);
        profile.dealbreakers = new ArrayList<>(dealbreakers);
        profile.tags = parseTags(text(binding.tagsEt));
    }

    private void updateProfileScore() {
        if (binding == null || photoAdapter == null) return;
        int score = 0;
        if (photoAdapter.photoCount() > 0) score += 35;
        if (photoAdapter.photoCount() >= 3) score += 10;
        if (profile != null && profile.age >= 18) score += 10;
        if (!TextUtils.isEmpty(intentValue)) score += 12;
        if (!TextUtils.isEmpty(text(binding.introEt))) score += 12;
        if (profile != null && !TextUtils.isEmpty(profile.country_code)) score += 5;
        boolean hasTags = !parseTags(text(binding.tagsEt)).isEmpty();
        if (profile != null) {
            hasTags = hasTags || !profile.safePersonalityTags().isEmpty()
                    || !profile.safePetTags().isEmpty()
                    || !profile.safeSportTags().isEmpty()
                    || !profile.safeMovieTags().isEmpty();
        }
        if (hasTags) score += 8;
        if (parseInt(text(binding.heightEt)) > 0 || parseInt(text(binding.weightEt)) > 0) score += 4;
        if (profile != null && (!TextUtils.isEmpty(profile.job_status) || !TextUtils.isEmpty(profile.education))) score += 4;
        if ((profile != null && !TextUtils.isEmpty(profile.relationship_status)) || !TextUtils.isEmpty(sexualOrientationValue)) score += 5;
        if (!TextUtils.isEmpty(text(binding.idealPartnerEt)) || !dealbreakers.isEmpty()) score += 5;
        binding.profileScoreTv.setText(getString(R.string.dating_profile_completion, Math.max(0, Math.min(100, score))));
    }

    private void saveProfile() {
        if (uploading) {
            toast(getString(R.string.dating_uploading_wait));
            return;
        }
        ArrayList<String> photos = photoAdapter.getPhotos();
        Map<String, Object> body = new HashMap<>();
        // 展示状态由后端 user_paused 权威决定：首次完整自动开启，主动暂停后编辑不重开。
        // 不再把本机缓存推回服务器，避免换设备或清数据后状态错乱。
        body.put("intent", intentValue);
        body.put("relationship_goal", intentValue);
        body.put("gender_preference", genderPreference);
        body.put("min_age", ageMin);
        body.put("max_age", ageMax);
        body.put("city", text(binding.cityEt));
        body.put("sexual_orientation", sexualOrientationValue);
        body.put("drinking", drinkingValue);
        body.put("smoking", smokingValue);
        body.put("height_cm", parseInt(text(binding.heightEt)));
        body.put("weight_kg", parseInt(text(binding.weightEt)));
        body.put("bio", limit(text(binding.introEt), 500));
        body.put("intro", limit(text(binding.introEt), 500));
        body.put("ideal_partner", limit(text(binding.idealPartnerEt), 200));
        body.put("dealbreakers", new ArrayList<>(dealbreakers));
        body.put("tags", parseTags(text(binding.tagsEt)));
        body.put("photos", photos);
        body.put("card_photos", photos); // 兼容旧后端字段，不再上传第二套图片。
        body.put("profile_images", photos);

        // 同步字段继续随交友资料保存，保证详情页即时展示。
        body.put("age", profile.age);
        body.put("sex", profile.sex);
        body.put("gender", profile.gender);
        body.put("country", profile.country);
        body.put("country_code", profile.country_code);
        body.put("relationship_status", profile.relationship_status);
        body.put("personality_tags", profile.safePersonalityTags());
        body.put("pet_tags", profile.safePetTags());
        body.put("sport_tags", profile.safeSportTags());
        body.put("movie_tags", profile.safeMovieTags());
        body.put("job_status", profile.job_status);
        body.put("job", profile.job);
        body.put("education", profile.education);

        binding.saveBtn.setEnabled(false);
        binding.saveBtn.setText(R.string.dating_saving);
        final boolean wasEnabled = initiallyEnabled;
        DatingModel.getInstance().saveProfile(body, (code, msg, data) -> {
            if (isFinishing() || isDestroyed() || binding == null) return;
            binding.saveBtn.setEnabled(true);
            binding.saveBtn.setText(R.string.dating_save_profile);
            if (code == HttpResponseCode.success && data != null) {
                profile = data;
                boolean becameEnabled = !wasEnabled && profile.enabled == 1;
                setResult(RESULT_OK);
                toast(getString(becameEnabled ? R.string.dating_saved_and_enabled : R.string.dating_saved));
                finish();
            } else if (code == HttpResponseCode.success) {
                setResult(RESULT_OK);
                toast(getString(R.string.dating_saved));
                finish();
            } else {
                toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_save_failed) : msg);
            }
        });
    }

    private void pickDealbreakers() {
        boolean[] checked = new boolean[dealbreakerOptions.length];
        for (int i = 0; i < dealbreakerOptions.length; i++) {
            checked[i] = dealbreakers.contains(DatingValueFormatter.dealbreakerCode(this, dealbreakerOptions[i]));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_edit_dealbreakers)
                .setMultiChoiceItems(dealbreakerOptions, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(R.string.dating_confirm, (dialog, which) -> {
                    dealbreakers.clear();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) dealbreakers.add(DatingValueFormatter.dealbreakerCode(this, dealbreakerOptions[i]));
                    }
                    updateDealbreakersText();
                    updateProfileScore();
                })
                .show();
    }

    private void updateDealbreakersText() {
        List<String> labels = DatingValueFormatter.dealbreakerLabels(this, dealbreakers);
        binding.dealbreakersValue.setText(labels.isEmpty()
                ? getString(R.string.dating_select)
                : TextUtils.join(getString(R.string.dating_list_separator), labels));
    }

    private void pickSingle(String title, String[] items, String current, ValueCallback callback) {
        int checked = 0;
        for (int i = 0; i < items.length; i++) if (TextUtils.equals(items[i], current)) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    callback.onValue(items[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dating_cancel, null)
                .show();
    }

    private String normalizeIntent(String value) {
        return DatingIntent.normalizeProfileCode(value);
    }

    private String genderText() {
        if (genderPreference == 0) return genderOptions[1];
        if (genderPreference == 1) return genderOptions[2];
        return genderOptions[0];
    }

    private ArrayList<String> parseTags(String raw) {
        ArrayList<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return result;
        for (String item : raw.split("[,，、\\s]+")) {
            String value = item.trim();
            if (TextUtils.isEmpty(value) || result.contains(value)) continue;
            result.add(value);
            if (result.size() >= 20) break;
        }
        return result;
    }

    private void updatePhotoTip() {
        binding.photoTip.setText(getString(R.string.dating_photo_count_tip, photoAdapter.photoCount()));
    }

    private void setUploading(boolean show, int progress, String message) {
        binding.uploadOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.uploadProgress.setProgress(progress);
        binding.uploadText.setText(show ? getString(R.string.dating_photo_upload_percent, message, progress) : message);
    }

    private String text(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }

    private int parseInt(String raw) {
        if (TextUtils.isEmpty(raw)) return 0;
        try { return Integer.parseInt(raw.trim()); } catch (Exception ignore) { return 0; }
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (uploadManager != null) uploadManager.shutdown();
        super.onDestroy();
    }

    private interface ValueCallback { void onValue(String value); }
}
