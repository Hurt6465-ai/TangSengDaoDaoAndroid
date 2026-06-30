package com.chat.uikit.setting;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKDialogUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActBlacklistLayoutBinding;
import com.chat.uikit.enity.BlacklistUser;
import com.chat.uikit.setting.adapter.BlacklistAdapter;
import com.chat.uikit.user.service.UserModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的黑名单列表。
 * 列表接口：GET /v1/user/blacklists
 * 移出接口：DELETE /v1/user/blacklist/{uid}
 */
public class BlacklistActivity extends WKBaseActivity<ActBlacklistLayoutBinding> {
    private BlacklistAdapter adapter;

    @Override
    protected ActBlacklistLayoutBinding getViewBinding() {
        return ActBlacklistLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.blacklist);
    }

    @Override
    protected void initView() {
        adapter = new BlacklistAdapter();
        wkVBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        wkVBinding.recyclerView.setAdapter(adapter);
    }

    @Override
    protected void initListener() {
        adapter.setOnRemoveClickListener((position, user) -> {
            if (user == null || TextUtils.isEmpty(user.uid)) return;
            WKDialogUtils.getInstance().showDialog(this, getString(R.string.remove_from_blacklist), getString(R.string.remove_from_blacklist_tips), true, "", getString(R.string.remove_from_blacklist), 0, 0, index -> {
                if (index == 1) removeFromBlacklist(position, user);
            });
        });
    }

    @Override
    protected void initData() {
        loadBlacklist();
    }

    private void loadBlacklist() {
        UserModel.getInstance().blacklists((code, msg, list) -> {
            if (code == HttpResponseCode.success || code == 200 || code == 0) {
                List<BlacklistUser> result = list == null ? new ArrayList<>() : list;
                adapter.setList(result);
                wkVBinding.emptyTv.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                wkVBinding.emptyTv.setVisibility(View.VISIBLE);
                if (!TextUtils.isEmpty(msg)) showToast(msg);
            }
        });
    }

    private void removeFromBlacklist(int position, BlacklistUser user) {
        UserModel.getInstance().removeBlackList(user.uid, (code, msg) -> {
            if (code == HttpResponseCode.success || code == 200 || code == 0) {
                showToast(getString(R.string.removed_from_blacklist));
                if (position >= 0 && position < adapter.getData().size()) {
                    adapter.removeAt(position);
                } else {
                    loadBlacklist();
                }
                wkVBinding.emptyTv.setVisibility(adapter.getData().isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.operation_failed) : msg);
            }
        });
    }
}
