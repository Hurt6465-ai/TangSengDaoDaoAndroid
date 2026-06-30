package com.chat.uikit.setting.adapter;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.components.AvatarView;
import com.chat.uikit.R;
import com.chat.uikit.enity.BlacklistUser;
import com.chat.uikit.user.UserDetailActivity;
import com.xinbida.wukongim.entity.WKChannelType;

import org.jetbrains.annotations.NotNull;

public class BlacklistAdapter extends BaseQuickAdapter<BlacklistUser, BaseViewHolder> {
    public interface OnRemoveClickListener {
        void onRemove(int position, BlacklistUser user);
    }

    private OnRemoveClickListener onRemoveClickListener;

    public BlacklistAdapter() {
        super(R.layout.item_blacklist_user_layout);
    }

    public void setOnRemoveClickListener(OnRemoveClickListener listener) {
        this.onRemoveClickListener = listener;
    }

    @Override
    protected void convert(@NotNull BaseViewHolder holder, BlacklistUser item) {
        String name = item == null ? "" : item.displayName();
        String username = item == null ? "" : item.displayUsername();

        holder.setText(R.id.nameTv, name);
        holder.setText(R.id.usernameTv, TextUtils.isEmpty(username) ? "" : username);
        holder.setGone(R.id.usernameTv, TextUtils.isEmpty(username));

        AvatarView avatarView = holder.getView(R.id.avatarView);
        avatarView.setSize(46);
        if (item != null && !TextUtils.isEmpty(item.uid)) {
            avatarView.showAvatar(item.uid, WKChannelType.PERSONAL);
            String country = firstNotEmpty(item.country_code, item.country);
            if (!TextUtils.isEmpty(country)) {
                avatarView.showFlag(country);
            }
        }

        holder.getView(R.id.removeTv).setOnClickListener(v -> {
            if (onRemoveClickListener != null) {
                onRemoveClickListener.onRemove(holder.getAdapterPosition(), item);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (item == null || TextUtils.isEmpty(item.uid)) return;
            Intent intent = new Intent(getContext(), UserDetailActivity.class);
            intent.putExtra("uid", item.uid);
            getContext().startActivity(intent);
        });
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }
}
