package com.chat.dating;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingListBinding;
import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;

/** 我的收藏以服务端为唯一数据源，本地不再序列化完整用户资料。 */
public class DatingFavoritesActivity extends Activity {
    private static final int REQ_DETAIL = 601;
    private ActivityWkDatingListBinding binding;
    private DatingProfileGridAdapter adapter;
    private DatingProfile selected;
    private DatingProfile myProfile;
    private boolean loading;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(247, 247, 249));
        binding = ActivityWkDatingListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        DatingUi.applyPageInsets(this, binding.getRoot());
        DatingFavoriteStore.clearLegacyCache(this);
        binding.titleTv.setText(R.string.dating_favorites_title);
        binding.subtitleTv.setText(R.string.dating_favorites_subtitle);
        binding.backBtn.setOnClickListener(v -> finish());
        adapter = new DatingProfileGridAdapter();
        binding.recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerView.setAdapter(adapter);
        adapter.setListener(new DatingProfileGridAdapter.Listener() {
            @Override public void onClick(DatingProfile profile, int position) {
                selected = profile;
                Intent intent = new Intent(DatingFavoritesActivity.this, DatingProfileDetailActivity.class);
                intent.putExtra(DatingProfileDetailActivity.EXTRA_PROFILE, profile);
                startActivityForResult(intent, REQ_DETAIL);
            }

            @Override public void onLongClick(DatingProfile profile, int position) {
                new android.app.AlertDialog.Builder(DatingFavoritesActivity.this)
                        .setTitle(R.string.dating_favorite_remove_title)
                        .setNegativeButton(R.string.dating_cancel, null)
                        .setPositiveButton(R.string.dating_favorite_remove, (d, w) -> removeFavorite(profile))
                        .show();
            }
        });
        DatingModel.getInstance().getMyDatingProfile((code, msg, data) -> myProfile = data);
        refresh();
    }

    private void refresh() {
        if (loading) return;
        loading = true;
        binding.loadingBar.setVisibility(View.VISIBLE);
        DatingModel.getInstance().favorites(100, (code, msg, data) -> {
            if (isFinishing() || isDestroyed()) return;
            loading = false;
            binding.loadingBar.setVisibility(View.GONE);
            if (code != HttpResponseCode.success || data == null) {
                adapter.setItems(new ArrayList<>());
                binding.emptyTv.setText(TextUtils.isEmpty(msg) ? getString(R.string.dating_favorites_load_failed) : msg);
                binding.emptyTv.setVisibility(View.VISIBLE);
                return;
            }
            adapter.setItems(data.getItems());
            binding.emptyTv.setText(R.string.dating_favorites_empty);
            binding.emptyTv.setVisibility(data.getItems().isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void removeFavorite(DatingProfile profile) {
        if (profile == null) return;
        DatingModel.getInstance().removeFavorite(profile.safeUid(), (code, msg, data) -> {
            if (code == HttpResponseCode.success) {
                toast(getString(R.string.dating_favorite_removed));
                refresh();
            } else {
                toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_action_failed) : msg);
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_DETAIL || resultCode != RESULT_OK || data == null || selected == null) return;
        String action = data.getStringExtra(DatingProfileDetailActivity.EXTRA_ACTION);
        if (DatingSwipeAction.FAVORITE.equals(action)) return;
        if (DatingSwipeAction.PASS.equals(action)) {
            sendSwipe(selected, DatingSwipeAction.PASS, false);
        } else if (DatingSwipeAction.LIKE.equals(action)) {
            if (!DatingQuotaManager.consume(this, myProfile, DatingSwipeAction.LIKE)) {
                toast(getString(R.string.dating_like_quota_empty));
                return;
            }
            sendSwipe(selected, DatingSwipeAction.LIKE, true);
        }
    }

    private void sendSwipe(DatingProfile target, String action, boolean quotaConsumed) {
        DatingModel.getInstance().swipe(target.safeUid(), action, 0, "favorites", (code, msg, result) -> {
            if (code != HttpResponseCode.success) {
                if (quotaConsumed) DatingQuotaManager.refund(this, action);
                toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_action_failed) : msg);
                return;
            }
            refresh();
            if (result != null && result.isMatched()) {
                DatingMatchDialog.show(this, myProfile, target, () -> DatingUi.openChat(this, target.safeUid()));
            }
        });
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
