package com.chat.dating;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingListBinding;
import com.chat.dating.model.DatingMatchItem;
import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatingMatchesActivity extends Activity {
    private ActivityWkDatingListBinding binding;
    private DatingProfileGridAdapter adapter;
    private final Map<String, DatingMatchItem> matchByUid = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(247, 247, 249));
        binding = ActivityWkDatingListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.titleTv.setText("我的匹配");
        binding.subtitleTv.setText("互相喜欢后才能开始聊天");
        binding.backBtn.setOnClickListener(v -> finish());
        adapter = new DatingProfileGridAdapter();
        binding.recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerView.setAdapter(adapter);
        adapter.setListener(new DatingProfileGridAdapter.Listener() {
            @Override
            public void onClick(DatingProfile profile, int position) {
                DatingUi.openChat(DatingMatchesActivity.this, profile.safeUid());
            }

            @Override
            public void onLongClick(DatingProfile profile, int position) {
                DatingMatchItem match = matchByUid.get(profile.safeUid());
                if (match == null || TextUtils.isEmpty(match.match_id)) return;
                new android.app.AlertDialog.Builder(DatingMatchesActivity.this)
                        .setTitle("解除匹配？")
                        .setMessage("解除后双方将不能继续通过交友白名单聊天。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("解除", (dialog, which) -> cancel(match.match_id))
                        .show();
            }
        });
        load();
    }

    private void load() {
        binding.loadingBar.setVisibility(View.VISIBLE);
        DatingModel.getInstance().matches(60, (code, msg, data) -> {
            binding.loadingBar.setVisibility(View.GONE);
            if (code != HttpResponseCode.success || data == null) {
                binding.emptyTv.setText(TextUtils.isEmpty(msg) ? "加载匹配失败" : msg);
                binding.emptyTv.setVisibility(View.VISIBLE);
                return;
            }
            List<DatingMatchItem> items = data.getItems();
            ArrayList<DatingProfile> profiles = new ArrayList<>();
            matchByUid.clear();
            for (DatingMatchItem item : items) {
                if (item == null || item.user == null) continue;
                profiles.add(item.user);
                matchByUid.put(item.user.safeUid(), item);
            }
            adapter.setItems(profiles);
            binding.emptyTv.setText("还没有匹配，继续去发现心动的人吧");
            binding.emptyTv.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void cancel(String matchId) {
        DatingModel.getInstance().cancelMatch(matchId, (code, msg, data) -> {
            android.widget.Toast.makeText(this,
                    code == HttpResponseCode.success ? "已解除匹配" : (TextUtils.isEmpty(msg) ? "操作失败" : msg),
                    android.widget.Toast.LENGTH_SHORT).show();
            if (code == HttpResponseCode.success) load();
        });
    }
}
