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

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingEditProfileBinding;
import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑自己的交友资料：6 图上传/删除/拖动排序 + 结构化恋爱资料。
 * “我的收藏 / 谁喜欢我”不放在这里，只属于右上角“我的”中心。
 */
public class DatingEditProfileActivity extends Activity {
    public static final String EXTRA_PROFILE = "dating_edit_profile";
    private static final int REQ_PICK_IMAGES = 701;

    private ActivityWkDatingEditProfileBinding binding;
    private DatingProfile profile;
    private DatingPhotoGridAdapter photoAdapter;
    private ItemTouchHelper touchHelper;
    private DatingPhotoUploadManager uploadManager;
    private boolean uploading;

    private String intentValue = "love";
    private String crossValue = "open";
    private int genderPreference = -1;
    private int ageMin = 18;
    private int ageMax = 35;

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
        binding.intentRow.setOnClickListener(v -> cycleIntent());
        binding.crossRow.setOnClickListener(v -> cycleCross());
        binding.genderRow.setOnClickListener(v -> cycleGenderPreference());
        binding.ageRow.setOnClickListener(v -> cycleAge());
        binding.saveBtn.setOnClickListener(v -> saveProfile());
    }

    private void bindProfile() {
        photoAdapter.setPhotos(profile.safePhotos());
        intentValue = normalizeIntent(profile.safeRelationshipGoal());
        crossValue = normalizeCross(profile.safeCrossBorderPreference());
        genderPreference = profile.gender_preference;
        ageMin = profile.min_age > 0 ? profile.min_age : 18;
        ageMax = profile.max_age > ageMin ? profile.max_age : 35;

        binding.intentValue.setText(intentText());
        binding.crossValue.setText(crossText());
        binding.genderValue.setText(genderText());
        binding.ageValue.setText(ageMin + "-" + ageMax + " 岁");
        binding.cityEt.setText(profile.city);
        binding.countryEt.setText(profile.country);
        binding.countryCodeEt.setText(profile.safeCountryCode());
        binding.jobEt.setText(profile.job);
        binding.educationEt.setText(profile.education);
        binding.relationshipEt.setText(profile.relationship_status);
        binding.bioEt.setText(profile.safeIntro());
        binding.tagsEt.setText(TextUtils.join("、", profile.safeTags()));
        binding.enabledSwitch.setChecked(profile.enabled == 1);
        updatePhotoTip();
    }

    private void openPicker() {
        if (uploading) return;
        int remain = DatingPhotoPolicy.MAX_PHOTO_COUNT - photoAdapter.photoCount();
        if (remain <= 0) {
            toast("最多上传 6 张照片");
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
            @Override
            public void onProgress(int progress, String message) {
                setUploading(true, progress, message);
            }

            @Override
            public void onSuccess(List<String> uploadedUrls) {
                uploading = false;
                setUploading(false, 100, "");
                photoAdapter.appendPhotos(uploadedUrls);
                updatePhotoTip();
                toast("图片上传完成");
            }

            @Override
            public void onError(String message) {
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
                toast("已复制语伴资料，可继续修改");
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
            toast("至少上传 2 张照片才能开启交友");
            return;
        }
        String bio = text(binding.bioEt);
        if (bio.length() > 500) bio = bio.substring(0, 500);
        ArrayList<String> tags = parseTags(text(binding.tagsEt));
        // 旧后端尚未有 cross_border_preference 字段，用系统标签保留此选择，推荐客户端仍可识别。
        removeSystemCrossTags(tags);
        tags.add("cross:" + crossValue);

        Map<String, Object> body = new HashMap<>();
        body.put("enabled", binding.enabledSwitch.isChecked() ? 1 : 0);
        body.put("intent", intentValue);
        body.put("relationship_goal", intentValue);
        body.put("cross_border_preference", crossValue);
        body.put("gender_preference", genderPreference);
        body.put("min_age", ageMin);
        body.put("max_age", ageMax);
        body.put("city", text(binding.cityEt));
        body.put("country", text(binding.countryEt));
        body.put("country_code", text(binding.countryCodeEt).toUpperCase());
        body.put("job", text(binding.jobEt));
        body.put("education", text(binding.educationEt));
        body.put("relationship_status", text(binding.relationshipEt));
        body.put("bio", bio);
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

    private void cycleIntent() {
        if ("love".equals(intentValue)) intentValue = "marriage";
        else if ("marriage".equals(intentValue)) intentValue = "chat";
        else intentValue = "love";
        binding.intentValue.setText(intentText());
    }

    private void cycleCross() {
        if ("open".equals(crossValue)) crossValue = "same_country";
        else if ("same_country".equals(crossValue)) crossValue = "prefer_foreign";
        else crossValue = "open";
        binding.crossValue.setText(crossText());
    }

    private void cycleGenderPreference() {
        if (genderPreference == -1) genderPreference = 0;
        else if (genderPreference == 0) genderPreference = 1;
        else genderPreference = -1;
        binding.genderValue.setText(genderText());
    }

    private void cycleAge() {
        if (ageMin == 18 && ageMax == 35) { ageMin = 22; ageMax = 35; }
        else if (ageMin == 22 && ageMax == 35) { ageMin = 18; ageMax = 45; }
        else if (ageMin == 18 && ageMax == 45) { ageMin = 30; ageMax = 50; }
        else { ageMin = 18; ageMax = 35; }
        binding.ageValue.setText(ageMin + "-" + ageMax + " 岁");
    }

    private String intentText() {
        if ("marriage".equals(intentValue)) return "奔结婚";
        if ("chat".equals(intentValue)) return "先聊天了解";
        return "认真恋爱";
    }

    private String crossText() {
        if ("same_country".equals(crossValue)) return "只接受本国";
        if ("prefer_foreign".equals(crossValue)) return "更喜欢异国恋";
        return "可以异国恋";
    }

    private String genderText() {
        if (genderPreference == 0) return "女生";
        if (genderPreference == 1) return "男生";
        return "不限";
    }

    private String normalizeIntent(String value) {
        if (TextUtils.isEmpty(value)) return "love";
        String v = value.toLowerCase();
        if (v.contains("marriage") || v.contains("结婚")) return "marriage";
        if (v.contains("chat") || v.contains("了解")) return "chat";
        return "love";
    }

    private String normalizeCross(String value) {
        if (TextUtils.isEmpty(value)) {
            for (String tag : profile.safeTags()) {
                if (tag.startsWith("cross:")) return tag.substring("cross:".length());
            }
            return "open";
        }
        String v = value.toLowerCase();
        if (v.contains("same") || v.contains("本国")) return "same_country";
        if (v.contains("prefer") || v.contains("喜欢异国")) return "prefer_foreign";
        return "open";
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

    private void removeSystemCrossTags(ArrayList<String> tags) {
        for (int i = tags.size() - 1; i >= 0; i--) {
            if (tags.get(i).startsWith("cross:")) tags.remove(i);
        }
    }

    private void updatePhotoTip() {
        binding.photoTip.setText("已上传 " + photoAdapter.photoCount() + "/6 · 第一张为主图 · 长按拖动排序");
    }

    private void setUploading(boolean show, int progress, String message) {
        binding.uploadOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.uploadProgress.setProgress(progress);
        binding.uploadText.setText(message + (show ? " " + progress + "%" : ""));
    }

    private String text(android.widget.TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (uploadManager != null) uploadManager.shutdown();
        super.onDestroy();
    }
}
