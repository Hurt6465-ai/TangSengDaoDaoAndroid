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
import androidx.recyclerview.widget.GridLayoutManager;
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

    private static final String[] INTENT_OPTIONS = {
            "寻找长期伴侣",
            "长期伴侣，但不拒绝短期交往",
            "短期伴侣，但不拒绝长期交往",
            "享受短期交往的乐趣",
            "结交新朋友",
            "顺其自然"
    };
    private static final String[] GENDER_OPTIONS = {"不限", "女生", "男生"};
    private static final String[] AGE_OPTIONS = {"18-28 岁", "20-32 岁", "22-35 岁", "25-40 岁", "30-45 岁"};
    private static final String[] SEXUAL_ORIENTATION_OPTIONS = {"异性恋", "同性恋", "双性恋", "泛性恋", "保密"};
    private static final String[] DRINKING_OPTIONS = {"从不", "偶尔", "社交时", "经常", "保密"};
    private static final String[] SMOKING_OPTIONS = {"从不", "偶尔", "经常", "正在戒烟", "保密"};
    private static final String[] DEALBREAKER_OPTIONS = {
            "不真诚", "冷暴力", "控制欲强", "不尊重边界", "经常失联", "撒谎",
            "酗酒", "赌博", "不爱卫生", "只聊暧昧", "催见面", "索要钱财"
    };

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
    private String sexualOrientationValue = SEXUAL_ORIENTATION_OPTIONS[0];
    private String drinkingValue = DRINKING_OPTIONS[1];
    private String smokingValue = SMOKING_OPTIONS[0];
    private final ArrayList<String> dealbreakers = new ArrayList<>();

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
        syncSharedProfile();
    }

    private void initPhotoGrid() {
        photoAdapter = new DatingPhotoGridAdapter();
        binding.photoRecycler.setLayoutManager(new GridLayoutManager(this, 3));
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
            if (uploading) toast("图片正在上传，请稍候");
            else finish();
        });
        binding.intentRow.setOnClickListener(v -> pickSingle("恋爱意向", INTENT_OPTIONS, intentValue, value -> {
            intentValue = value;
            binding.intentValue.setText(value);
        }));
        binding.genderRow.setOnClickListener(v -> pickSingle("想认识", GENDER_OPTIONS, genderText(), value -> {
            genderPreference = "女生".equals(value) ? 0 : ("男生".equals(value) ? 1 : -1);
            binding.genderValue.setText(value);
        }));
        binding.ageRow.setOnClickListener(v -> pickSingle("年龄偏好", AGE_OPTIONS, ageMin + "-" + ageMax + " 岁", value -> {
            String[] pair = value.replace(" 岁", "").split("-");
            if (pair.length == 2) {
                ageMin = parseInt(pair[0]);
                ageMax = parseInt(pair[1]);
                binding.ageValue.setText(ageMin + "-" + ageMax + " 岁");
            }
        }));
        binding.sexualOrientationRow.setOnClickListener(v -> pickSingle("性取向", SEXUAL_ORIENTATION_OPTIONS, sexualOrientationValue, value -> {
            sexualOrientationValue = value;
            binding.sexualOrientationValue.setText(value);
        }));
        binding.drinkingRow.setOnClickListener(v -> pickSingle("饮酒", DRINKING_OPTIONS, drinkingValue, value -> {
            drinkingValue = value;
            binding.drinkingValue.setText(value);
        }));
        binding.smokingRow.setOnClickListener(v -> pickSingle("吸烟", SMOKING_OPTIONS, smokingValue, value -> {
            smokingValue = value;
            binding.smokingValue.setText(value);
        }));
        binding.dealbreakersRow.setOnClickListener(v -> pickDealbreakers());
        binding.saveBtn.setOnClickListener(v -> saveProfile());
    }

    private void bindProfile() {
        photoAdapter.setPhotos(profile.safeDatingPhotos());
        intentValue = normalizeIntent(profile.safeRelationshipGoal());
        genderPreference = profile.gender_preference;
        ageMin = profile.min_age > 0 ? profile.min_age : 18;
        ageMax = profile.max_age > ageMin ? profile.max_age : 28;
        sexualOrientationValue = TextUtils.isEmpty(profile.sexual_orientation) ? SEXUAL_ORIENTATION_OPTIONS[0] : profile.sexual_orientation;
        drinkingValue = TextUtils.isEmpty(profile.drinking) ? DRINKING_OPTIONS[1] : profile.drinking;
        smokingValue = TextUtils.isEmpty(profile.smoking) ? SMOKING_OPTIONS[0] : profile.smoking;
        dealbreakers.clear();
        dealbreakers.addAll(profile.safeDealbreakers());

        binding.intentValue.setText(intentValue);
        binding.genderValue.setText(genderText());
        binding.ageValue.setText(ageMin + "-" + ageMax + " 岁");
        binding.sexualOrientationValue.setText(sexualOrientationValue);
        binding.drinkingValue.setText(drinkingValue);
        binding.smokingValue.setText(smokingValue);
        binding.dealbreakersValue.setText(dealbreakers.isEmpty() ? "请选择" : TextUtils.join("、", dealbreakers));
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
        binding.sharedBasicTv.setText(fallback("基础：", DatingSharedProfileFormatter.basicLine(profile)));
        binding.sharedRelationshipTv.setText(fallback("感情状态：", DatingSharedProfileFormatter.relationshipLine(profile)));
        binding.sharedPersonalityTv.setText(fallback("性格：", DatingSharedProfileFormatter.personalityLine(profile)));
        binding.sharedInterestTv.setText(fallback("兴趣：", DatingSharedProfileFormatter.interestsLine(profile)));
        binding.sharedCareerTv.setText(fallback("职业与学历：", DatingSharedProfileFormatter.careerLine(profile)));
    }

    private String fallback(String prefix, String value) {
        return prefix + (TextUtils.isEmpty(value) ? "未填写" : value);
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

    private void showPhotoOptions(int position) {
        ArrayList<String> actions = new ArrayList<>();
        if (position != 0) actions.add("设为主图");
        actions.add("删除照片");
        new AlertDialog.Builder(this)
                .setTitle(position == 0 ? "主图" : "照片 " + (position + 1))
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    if ("设为主图".equals(actions.get(which))) {
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

    private void pickDealbreakers() {
        boolean[] checked = new boolean[DEALBREAKER_OPTIONS.length];
        for (int i = 0; i < DEALBREAKER_OPTIONS.length; i++) checked[i] = dealbreakers.contains(DEALBREAKER_OPTIONS[i]);
        new AlertDialog.Builder(this)
                .setTitle("我反感")
                .setMultiChoiceItems(DEALBREAKER_OPTIONS, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    dealbreakers.clear();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) dealbreakers.add(DEALBREAKER_OPTIONS[i]);
                    binding.dealbreakersValue.setText(dealbreakers.isEmpty() ? "请选择" : TextUtils.join("、", dealbreakers));
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
                .setNegativeButton("取消", null)
                .show();
    }

    private String normalizeIntent(String value) {
        if (TextUtils.isEmpty(value)) return INTENT_OPTIONS[0];
        for (String item : INTENT_OPTIONS) if (TextUtils.equals(item, value)) return item;
        if (value.contains("新朋友")) return "结交新朋友";
        if (value.contains("短期")) return "享受短期交往的乐趣";
        if (value.contains("顺其自然")) return "顺其自然";
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
        binding.photoTip.setText("已上传 " + photoAdapter.photoCount() + "/5 · 第一张为主图 · 空位可继续添加");
    }

    private void setUploading(boolean show, int progress, String message) {
        binding.uploadOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.uploadProgress.setProgress(progress);
        binding.uploadText.setText(message + (show ? " " + progress + "%" : ""));
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
