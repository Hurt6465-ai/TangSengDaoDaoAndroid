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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingEditProfileBinding;
import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 编辑自己的交友资料。 */
public class DatingEditProfileActivity extends Activity {
    public static final String EXTRA_PROFILE = "dating_edit_profile";
    private static final int REQ_PICK_IMAGES = 701;

    private static final String[] INTENT_OPTIONS = new String[]{
            "寻找长期伴侣",
            "长期伴侣，但不拒绝短期交往",
            "短期伴侣，但不拒绝长期交往",
            "享受短期交往的乐趣",
            "结交新朋友",
            "顺其自然"
    };
    private static final String[] GENDER_OPTIONS = new String[]{"不限", "女生", "男生"};
    private static final String[] AGE_OPTIONS = new String[]{"18-28 岁", "20-32 岁", "22-35 岁", "25-40 岁", "30-45 岁"};
    private static final String[] RELATIONSHIP_STATUS_OPTIONS = new String[]{"单身", "暧昧中", "离异", "已婚", "保密"};
    private static final String[] SEXUAL_ORIENTATION_OPTIONS = new String[]{"异性恋", "同性恋", "双性恋", "泛性恋", "保密"};
    private static final String[] DRINKING_OPTIONS = new String[]{"从不", "偶尔", "社交时", "经常", "保密"};
    private static final String[] SMOKING_OPTIONS = new String[]{"从不", "偶尔", "经常", "正在戒烟", "保密"};

    private ActivityWkDatingEditProfileBinding binding;
    private DatingProfile profile;
    private DatingPhotoGridAdapter photoAdapter;
    private ItemTouchHelper touchHelper;
    private DatingPhotoUploadManager uploadManager;
    private boolean uploading;

    private String intentValue = INTENT_OPTIONS[0];
    private int genderPreference = -1;
    private int ageMin = 18;
    private int ageMax = 28;
    private String relationshipStatusValue = RELATIONSHIP_STATUS_OPTIONS[0];
    private String sexualOrientationValue = SEXUAL_ORIENTATION_OPTIONS[0];
    private String drinkingValue = DRINKING_OPTIONS[1];
    private String smokingValue = SMOKING_OPTIONS[0];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(247, 247, 249));
        binding = ActivityWkDatingEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Object value = getIntent().getSerializableExtra(EXTRA_PROFILE);
        if (value instanceof DatingProfile) profile = (DatingProfile) value;
        if (profile == null) profile = new DatingProfile();
        uploadManager = new DatingPhotoUploadManager(this);
        initPhotoGrid();
        initListeners();
        bindProfile();
    }

    private void initPhotoGrid() {
        photoAdapter = new DatingPhotoGridAdapter();
        StaggeredGridLayoutManager photoLayout = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        photoLayout.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        binding.photoRecycler.setLayoutManager(photoLayout);
        binding.photoRecycler.setAdapter(photoAdapter);
        binding.photoRecycler.setNestedScrollingEnabled(false);
        touchHelper = new ItemTouchHelper(DatingPhotoGridAdapter.touchCallback(photoAdapter));
        touchHelper.attachToRecyclerView(binding.photoRecycler);
        photoAdapter.setListener(new DatingPhotoGridAdapter.Listener() {
            @Override public void onAddPhoto() { openPicker(); }
            @Override public void onDeletePhoto(int position, String url) { confirmDelete(position); }
            @Override public void onPreviewPhoto(int position, String url) { showPhotoOptions(position, url); }
            @Override public void onStartDrag(androidx.recyclerview.widget.RecyclerView.ViewHolder holder) { touchHelper.startDrag(holder); }
        });
    }

    private void initListeners() {
        binding.backBtn.setOnClickListener(v -> {
            if (uploading) toast("图片正在上传，请稍候");
            else finish();
        });
        binding.copyPartnerBtn.setOnClickListener(v -> copyPartnerProfile());
        binding.intentRow.setOnClickListener(v -> pickIntent());
        binding.genderRow.setOnClickListener(v -> pickGenderPreference());
        binding.ageRow.setOnClickListener(v -> pickAgeRange());
        binding.relationshipStatusRow.setOnClickListener(v -> pickSimple("感情状态", RELATIONSHIP_STATUS_OPTIONS, relationshipStatusValue, value -> {
            relationshipStatusValue = value;
            binding.relationshipStatusValue.setText(value);
        }));
        binding.sexualOrientationRow.setOnClickListener(v -> pickSimple("性取向", SEXUAL_ORIENTATION_OPTIONS, sexualOrientationValue, value -> {
            sexualOrientationValue = value;
            binding.sexualOrientationValue.setText(value);
        }));
        binding.drinkingRow.setOnClickListener(v -> pickSimple("饮酒", DRINKING_OPTIONS, drinkingValue, value -> {
            drinkingValue = value;
            binding.drinkingValue.setText(value);
        }));
        binding.smokingRow.setOnClickListener(v -> pickSimple("吸烟", SMOKING_OPTIONS, smokingValue, value -> {
            smokingValue = value;
            binding.smokingValue.setText(value);
        }));
        binding.saveBtn.setOnClickListener(v -> saveProfile());
    }

    private void bindProfile() {
        photoAdapter.setPhotos(profile.safePhotos());
        intentValue = normalizeIntent(profile.safeRelationshipGoal());
        genderPreference = profile.gender_preference;
        ageMin = profile.min_age > 0 ? profile.min_age : 18;
        ageMax = profile.max_age > ageMin ? profile.max_age : 28;
        relationshipStatusValue = TextUtils.isEmpty(profile.relationship_status) ? RELATIONSHIP_STATUS_OPTIONS[0] : profile.relationship_status;
        sexualOrientationValue = TextUtils.isEmpty(profile.sexual_orientation) ? SEXUAL_ORIENTATION_OPTIONS[0] : profile.sexual_orientation;
        drinkingValue = TextUtils.isEmpty(profile.drinking) ? DRINKING_OPTIONS[1] : profile.drinking;
        smokingValue = TextUtils.isEmpty(profile.smoking) ? SMOKING_OPTIONS[0] : profile.smoking;

        binding.intentValue.setText(intentValue);
        binding.genderValue.setText(genderText());
        binding.ageValue.setText(ageMin + "-" + ageMax + " 岁");
        binding.relationshipStatusValue.setText(relationshipStatusValue);
        binding.sexualOrientationValue.setText(sexualOrientationValue);
        binding.drinkingValue.setText(drinkingValue);
        binding.smokingValue.setText(smokingValue);
        binding.cityEt.setText(profile.city);
        binding.jobEt.setText(profile.job);
        binding.educationEt.setText(profile.education);
        if (profile.height_cm > 0) binding.heightEt.setText(String.valueOf(profile.height_cm));
        if (profile.weight_kg > 0) binding.weightEt.setText(String.valueOf(profile.weight_kg));
        binding.bioEt.setText(profile.safeIntro());
        binding.tagsEt.setText(TextUtils.join("、", profile.safeTags()));
        binding.enabledSwitch.setChecked(profile.enabled == 1);
        updatePhotoTip();
    }

    private void openPicker() {
        if (uploading) return;
        int remain = DatingPhotoPolicy.MAX_PHOTO_COUNT - photoAdapter.photoCount();
        if (remain <= 0) {
            toast("最多上传 5 张照片");
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
        setUploading(true, 0, "正在准备图片");
        uploadManager.upload(uris, new DatingPhotoUploadManager.Callback() {
            @Override public void onProgress(int progress, String message) { setUploading(true, progress, message); }
            @Override public void onSuccess(List<String> uploadedUrls) {
                uploading = false;
                setUploading(false, 100, "");
                photoAdapter.appendPhotos(uploadedUrls);
                updatePhotoTip();
                toast("图片上传完成");
            }
            @Override public void onError(String message) {
                uploading = false;
                setUploading(false, 0, "");
                toast(message);
            }
        });
    }

    private void showPhotoOptions(int position, String url) {
        ArrayList<String> actions = new ArrayList<>();
        if (position != 0) actions.add("设为主图");
        actions.add("删除照片");
        new AlertDialog.Builder(this)
                .setTitle(position == 0 ? "主图" : "照片 " + (position + 1))
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("设为主图".equals(action)) {
                        photoAdapter.movePhoto(position, 0);
                        photoAdapter.notifyDataSetChanged();
                    } else {
                        confirmDelete(position);
                    }
                }).show();
    }

    private void confirmDelete(int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除这张照片？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    photoAdapter.removePhoto(position);
                    updatePhotoTip();
                }).show();
    }

    private void copyPartnerProfile() {
        binding.copyPartnerBtn.setEnabled(false);
        DatingModel.getInstance().copyPartnerProfile((code, msg, data) -> {
            binding.copyPartnerBtn.setEnabled(true);
            if (code == HttpResponseCode.success && data != null) {
                profile = data;
                bindProfile();
                toast("已从语伴同步基础资料，可继续修改交友专属字段");
            } else {
                toast(TextUtils.isEmpty(msg) ? "复制失败" : msg);
            }
        });
    }

    private void saveProfile() {
        if (uploading) {
            toast("图片正在上传，请稍候");
            return;
        }
        ArrayList<String> photos = photoAdapter.getPhotos();
        if (binding.enabledSwitch.isChecked() && !DatingPhotoPolicy.canEnableDating(photos)) {
            toast("至少保留 1 张照片才能开启交友");
            return;
        }
        String bio = text(binding.bioEt);
        if (bio.length() > 500) bio = bio.substring(0, 500);
        ArrayList<String> tags = parseTags(text(binding.tagsEt));

        Map<String, Object> body = new HashMap<>();
        body.put("enabled", binding.enabledSwitch.isChecked() ? 1 : 0);
        body.put("intent", intentValue);
        body.put("relationship_goal", intentValue);
        body.put("gender_preference", genderPreference);
        body.put("min_age", ageMin);
        body.put("max_age", ageMax);
        body.put("city", text(binding.cityEt));
        body.put("job", text(binding.jobEt));
        body.put("education", text(binding.educationEt));
        body.put("relationship_status", relationshipStatusValue);
        body.put("sexual_orientation", sexualOrientationValue);
        body.put("drinking", drinkingValue);
        body.put("smoking", smokingValue);
        body.put("height_cm", parseInt(text(binding.heightEt)));
        body.put("weight_kg", parseInt(text(binding.weightEt)));
        body.put("bio", bio);
        body.put("intro", bio);
        body.put("tags", tags);
        body.put("photos", photos);
        body.put("profile_images", photos);
        body.put("show_distance", 1);
        body.put("allow_voice", 1);
        body.put("allow_video", 0);

        binding.saveBtn.setEnabled(false);
        binding.saveBtn.setText("保存中…");
        DatingModel.getInstance().saveProfile(body, (code, msg, data) -> {
            binding.saveBtn.setEnabled(true);
            binding.saveBtn.setText("保存资料");
            if (code == HttpResponseCode.success) {
                setResult(RESULT_OK);
                toast("交友资料已保存");
                finish();
            } else {
                toast(TextUtils.isEmpty(msg) ? "保存失败" : msg);
            }
        });
    }

    private void pickIntent() {
        pickSimple("恋爱意向", INTENT_OPTIONS, intentValue, value -> {
            intentValue = value;
            binding.intentValue.setText(value);
        });
    }

    private void pickGenderPreference() {
        pickSimple("想认识", GENDER_OPTIONS, genderText(), value -> {
            if ("女生".equals(value)) genderPreference = 0;
            else if ("男生".equals(value)) genderPreference = 1;
            else genderPreference = -1;
            binding.genderValue.setText(value);
        });
    }

    private void pickAgeRange() {
        String current = ageMin + "-" + ageMax + " 岁";
        pickSimple("年龄偏好", AGE_OPTIONS, current, value -> {
            String only = value.replace(" 岁", "");
            String[] pair = only.split("-");
            if (pair.length == 2) {
                ageMin = parseInt(pair[0]);
                ageMax = parseInt(pair[1]);
                binding.ageValue.setText(ageMin + "-" + ageMax + " 岁");
            }
        });
    }

    private void pickSimple(String title, String[] items, String current, ValueCallback callback) {
        int checked = 0;
        for (int i = 0; i < items.length; i++) {
            if (TextUtils.equals(items[i], current)) { checked = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    callback.onValue(items[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String normalizeIntent(String value) {
        if (TextUtils.isEmpty(value)) return INTENT_OPTIONS[0];
        for (String item : INTENT_OPTIONS) if (TextUtils.equals(item, value)) return item;
        if (value.contains("顺其自然")) return "顺其自然";
        if (value.contains("新朋友")) return "结交新朋友";
        if (value.contains("短期")) return "享受短期交往的乐趣";
        return INTENT_OPTIONS[0];
    }

    private String genderText() {
        if (genderPreference == 0) return "女生";
        if (genderPreference == 1) return "男生";
        return "不限";
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
        binding.photoTip.setText("已上传 " + photoAdapter.photoCount() + "/5 · 第一张为主图 · 长按拖动排序");
    }

    private void setUploading(boolean show, int progress, String message) {
        binding.uploadOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.uploadProgress.setProgress(progress);
        binding.uploadText.setText(message + (show ? " " + progress + "%" : ""));
    }

    private String text(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
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
