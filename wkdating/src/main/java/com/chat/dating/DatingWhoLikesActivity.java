package com.chat.dating;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.chat.dating.databinding.ActivityWkDatingWhoLikesBinding;

/** 谁喜欢我是会员入口；旧后端尚无列表接口，因此不伪造用户数据。 */
public class DatingWhoLikesActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(247, 247, 249));
        ActivityWkDatingWhoLikesBinding binding = ActivityWkDatingWhoLikesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        DatingUi.applyPageInsets(this, binding.getRoot());
        binding.backBtn.setOnClickListener(v -> finish());
        binding.actionBtn.setOnClickListener(v -> android.widget.Toast.makeText(this,
                getString(R.string.dating_membership_pending), android.widget.Toast.LENGTH_SHORT).show());
    }
}
