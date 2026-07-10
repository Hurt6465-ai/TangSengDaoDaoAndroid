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

import java.util.List;

public class DatingFavoritesActivity extends Activity {
    private static final int REQ_DETAIL = 601;
    private ActivityWkDatingListBinding binding;
    private DatingProfileGridAdapter adapter;
    private DatingProfile selected;
    private DatingProfile myProfile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(247, 247, 249));
        binding = ActivityWkDatingListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.titleTv.setText("我的收藏");
        binding.subtitleTv.setText("收藏不会触发匹配，可稍后再决定");
        binding.backBtn.setOnClickListener(v -> finish());
        adapter = new DatingProfileGridAdapter();
        binding.recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerView.setAdapter(adapter);
        adapter.setListener(new DatingProfileGridAdapter.Listener() {
            @Override
            public void onClick(DatingProfile profile, int position) {
                selected = profile;
                Intent intent = new Intent(DatingFavoritesActivity.this, DatingProfileDetailActivity.class);
                intent.putExtra(DatingProfileDetailActivity.EXTRA_PROFILE, profile);
                startActivityForResult(intent, REQ_DETAIL);
            }

            @Override
            public void onLongClick(DatingProfile profile, int position) {
                new android.app.AlertDialog.Builder(DatingFavoritesActivity.this)
                        .setTitle("取消收藏？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("取消收藏", (d, w) -> {
                            DatingFavoriteStore.remove(DatingFavoritesActivity.this, profile.safeUid());
                            refresh();
                        }).show();
            }
        });
        DatingModel.getInstance().getMyDatingProfile((code, msg, data) -> myProfile = data);
        refresh();
    }

    private void refresh() {
        List<DatingProfile> values = DatingFavoriteStore.list(this);
        adapter.setItems(values);
        binding.emptyTv.setVisibility(values.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_DETAIL || resultCode != RESULT_OK || data == null || selected == null) return;
        String action = data.getStringExtra(DatingProfileDetailActivity.EXTRA_ACTION);
        if (DatingSwipeAction.FAVORITE.equals(action)) return;
        DatingFavoriteStore.remove(this, selected.safeUid());
        if (DatingSwipeAction.PASS.equals(action)) {
            DatingModel.getInstance().swipe(selected.safeUid(), DatingSwipeAction.PASS, 0, "favorites", null);
            refresh();
            return;
        }
        if (DatingSwipeAction.LIKE.equals(action)) {
            if (!DatingQuotaManager.consume(this, myProfile, DatingSwipeAction.LIKE)) {
                toast("今日喜欢额度已用完");
                return;
            }
            DatingProfile target = selected;
            DatingModel.getInstance().swipe(target.safeUid(), DatingSwipeAction.LIKE, 0, "favorites", (code, msg, result) -> {
                if (code != HttpResponseCode.success) toast(TextUtils.isEmpty(msg) ? "操作失败" : msg);
                else if (result != null && result.isMatched()) {
                    DatingMatchDialog.show(this, myProfile, target, () -> DatingUi.openChat(this, target.safeUid()));
                }
            });
            refresh();
        }
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
