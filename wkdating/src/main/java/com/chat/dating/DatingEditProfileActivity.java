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
import android.text.TextUtils;
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

/** 编辑自己的交友资料。共享字段进入页面后自动从语伴资料同步。 */
public class DatingEditProfileActivity extends Activity {
    public static final String EXTRA_PROFILE = "dating_edit_profile";
    private static final int REQ_PICK_IMAGES = 701;

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
        sexualOrientationValue = sexualOrientationOptions[0];
        drinkingValue = drinkingOptions[Math.min(1, drinkingOptions.length - 1)];
        smokingValue = smokingOptions[0];
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
                    });
        });
        binding.genderRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_gender), genderOptions, genderText(), value -> {
            genderPreference = genderOptions[1].equals(value) ? 0 : (genderOptions[2].equals(value) ? 1 : -1);
            binding.genderValue.setText(value);
        }));
        binding.ageRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_age), ageOptions, getString(R.string.dating_age_range_plain, ageMin, ageMax), value -> {
            String[] pair = value.replaceAll("[^0-9-]", "").split("-");
            if (pair.length == 2) {
                ageMin = parseInt(pair[0]);
                ageMax = parseInt(pair[1]);
                binding.ageValue.setText(getString(R.string.dating_age_range_plain, ageMin, ageMax));
            }
        }));
        binding.sexualOrientationRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_orientation), sexualOrientationOptions, sexualOrientationValue, value -> {
            sexualOrientationValue = value;
            binding.sexualOrientationValue.setText(value);
        }));
        binding.drinkingRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_drinking), drinkingOptions, drinkingValue, value -> {
            drinkingValue = value;
            binding.drinkingValue.setText(value);
        }));
        binding.smokingRow.setOnClickListener(v -> pickSingle(getString(R.string.dating_edit_smoking), smokingOptions, smokingValue, value -> {
            smokingValue = value;
            binding.smokingValue.setText(value);
        }));
        binding.dealbreakersRow.setOnClickListener(v -> pickDealbreakers());
        binding.saveBtn.setOnClickListener(v -> saveProfile());
    }

    private void bindProfile() {
        photoAdapter.setPhotos(profile.safeDatingPhotos(), profile.safeCardPhotos());
        intentValue = normalizeIntent(profile.safeRelationshipGoal());
        genderPreference = profile.gender_preference;
        ageMin = profile.min_age > 0 ? profile.min_age : 18;
        ageMax = profile.max_age > ageMin ? profile.max_age : 28;
        sexualOrientationValue = TextUtils.isEmpty(profile.sexual_orientation) ? sexualOrientationOptions[0] : profile.sexual_orientation;
        drinkingValue = TextUtils.isEmpty(profile.drinking) ? drinkingOptions[1] : profile.drinking;
        smokingValue = TextUtils.isEmpty(profile.smoking) ? smokingOptions[0] : profile.smoking;
        dealbreakers.clear();
        dealbreakers.addAll(profile.safeDealbreakers());

        binding.intentValue.setText(DatingIntent.displayLabel(this, intentValue));
        binding.genderValue.setText(genderText());
        binding.ageValue.setText(getString(R.string.dating_age_range_plain, ageMin, ageMax));
        binding.sexualOrientationValue.setText(sexualOrientationValue);
        binding.drinkingValue.setText(drinkingValue);
        binding.smokingValue.setText(smokingValue);
        binding.dealbreakersValue.setText(dealbreakers.isEmpty() ? getString(R.string.dating_select) : TextUtils.join("、", dealbreakers));
        binding.cityEt.setText(profile.city);
        if (profile.height_cm > 0) binding.heightEt.setText(String.valueOf(profile.height_cm));
        if (profile.weight_kg > 0) binding.weightEt.setText(String.valueOf(profile.weight_kg));
        binding.bioEt.setText(profile.safeIntro());
        binding.idealPartnerEt.setText(profile.ideal_partner);
        binding.tagsEt.setText(TextUtils.join("、", profile.tags == null ? new ArrayList<>() : profile.tags));
        binding.enabledSwitch.setChecked(profile.enabled == 1);
        bindSharedFields();
        updatePhotoTip();
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
        binding.sharedBasicTv.setText(sharedLine(R.string.dating_shared_basic, DatingSharedProfileFormatter.basicLine(this, profile)));
        binding.sharedRelationshipTv.setText(sharedLine(R.string.dating_shared_relationship, DatingSharedProfileFormatter.relationshipLine(this, profile)));
        binding.sharedPersonalityTv.setText(sharedLine(R.string.dating_shared_personality, DatingSharedProfileFormatter.personalityLine(this, profile)));
        binding.sharedInterestTv.setText(sharedLine(R.string.dating_shared_interests, DatingSharedProfileFormatter.interestsLine(this, profile)));
        binding.sharedCareerTv.setText(sharedLine(R.string.dating_shared_career, DatingSharedProfileFormatter.careerLine(this, profile)));
    }

    private String sharedLine(int formatRes, String value) {
        return getString(formatRes, TextUtils.isEmpty(value) ? getString(R.string.dating_not_filled) : value);
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
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, remain);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
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
            @Override public void onSuccess(List<String> masterUrls, List<String> cardUrls) {
                uploading = false;
                setUploading(false, 100, "");
                photoAdapter.appendPhotos(masterUrls, cardUrls);
                updatePhotoTip();
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
                }).show();
    }

    private void saveProfile() {
        if (uploading) {
            toast(getString(R.string.dating_uploading_wait));
            return;
        }
        ArrayList<String> photos = photoAdapter.getPhotos();
        if (binding.enabledSwitch.isChecked() && !DatingPhotoPolicy.canEnableDating(photos)) {
            toast(getString(R.string.dating_min_photo_enable));
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("enabled", binding.enabledSwitch.isChecked() ? 1 : 0);
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
        body.put("bio", limit(text(binding.bioEt), 500));
        body.put("intro", limit(text(binding.bioEt), 500));
        body.put("ideal_partner", limit(text(binding.idealPartnerEt), 200));
        body.put("dealbreakers", new ArrayList<>(dealbreakers));
        body.put("tags", parseTags(text(binding.tagsEt)));
        body.put("photos", photos);
        body.put("card_photos", photoAdapter.getCardPhotos());
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
        DatingModel.getInstance().saveProfile(body, (code, msg, data) -> {
            binding.saveBtn.setEnabled(true);
            binding.saveBtn.setText(R.string.dating_save_profile);
            if (code == HttpResponseCode.success) {
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
        for (int i = 0; i < dealbreakerOptions.length; i++) checked[i] = dealbreakers.contains(dealbreakerOptions[i]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_edit_dealbreakers)
                .setMultiChoiceItems(dealbreakerOptions, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(R.string.dating_confirm, (dialog, which) -> {
                    dealbreakers.clear();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) dealbreakers.add(dealbreakerOptions[i]);
                    binding.dealbreakersValue.setText(dealbreakers.isEmpty() ? getString(R.string.dating_select) : TextUtils.join("、", dealbreakers));
                })
                .show();
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
