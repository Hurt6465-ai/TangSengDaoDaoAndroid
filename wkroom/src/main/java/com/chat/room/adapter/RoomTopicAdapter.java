package com.chat.room.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.config.WKApiConfig;
import com.chat.base.glide.GlideUtils;
import com.chat.base.ui.components.AvatarView;
import com.chat.room.R;
import com.chat.room.entity.RoomTopicEntity;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.List;

public class RoomTopicAdapter extends BaseQuickAdapter<RoomTopicEntity, BaseViewHolder> {
    private static final int[] ROOM_BACKGROUNDS = new int[]{
            R.drawable.room_bg_01, R.drawable.room_bg_02, R.drawable.room_bg_03, R.drawable.room_bg_04,
            R.drawable.room_bg_05, R.drawable.room_bg_06, R.drawable.room_bg_07, R.drawable.room_bg_08
    };

    private static final int[] CARD_AVATAR_IDS = new int[]{
            R.id.cardAvatar1, R.id.cardAvatar2, R.id.cardAvatar3, R.id.cardAvatar4
    };

    public RoomTopicAdapter(List<RoomTopicEntity> data) {
        super(R.layout.item_room_topic_card, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, RoomTopicEntity room) {
        Context context = holder.itemView.getContext();
        bindBackground(context, holder, room);
        holder.setText(R.id.langTv, room == null ? "中文" : room.getLangLabel());
        holder.setText(R.id.tagTv, "# " + (room == null ? "闲谈" : room.getTagLabel()));
        holder.setText(R.id.titleTv, room == null ? "话题聊天室" : room.getShowTitle());
        holder.setText(R.id.metaTv, room == null ? "发布" : room.getMetaText());
        holder.getView(R.id.hotTv).setVisibility(room != null && room.hot == 1 ? View.VISIBLE : View.GONE);

        int unread = 0;
        if (room != null) unread = Math.max(room.mention_unread_count, room.unread_count);
        holder.setText(R.id.unreadTv, room != null && room.mention_unread_count > 0 ? "@" : (unread > 99 ? "99+" : String.valueOf(unread)));
        holder.getView(R.id.unreadTv).setVisibility(unread > 0 ? View.VISIBLE : View.GONE);

        List<RoomTopicEntity.RoomAvatar> avatars = room == null ? null : room.getCardAvatars();
        for (int i = 0; i < CARD_AVATAR_IDS.length; i++) {
            AvatarView avatarView = holder.getView(CARD_AVATAR_IDS[i]);
            avatarView.setSize(28);
            if (avatars != null && i < avatars.size()) {
                RoomTopicEntity.RoomAvatar avatar = avatars.get(i);
                avatarView.setVisibility(View.VISIBLE);
                bindAvatar(context, avatarView, avatar.uid, avatar.avatar, avatar.avatar_cache_key);
            } else {
                avatarView.setVisibility(View.GONE);
            }
        }
    }

    private void bindBackground(Context context, BaseViewHolder holder, RoomTopicEntity room) {
        if (room != null && !TextUtils.isEmpty(room.background_url)) {
            GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(room.background_url), holder.getView(R.id.bgIv));
            return;
        }
        String seed = room == null ? "" : room.getRoomId();
        int index = room != null && room.background_index > 0 ? (room.background_index - 1) : ((seed.hashCode() & 0x7fffffff) % ROOM_BACKGROUNDS.length);
        holder.setImageResource(R.id.bgIv, ROOM_BACKGROUNDS[Math.abs(index) % ROOM_BACKGROUNDS.length]);
    }

    private void bindAvatar(Context context, AvatarView avatarView, String uid, String avatarUrl, String cacheKey) {
        avatarView.spotView.setVisibility(View.GONE);
        avatarView.onlineTv.setVisibility(View.INVISIBLE);
        avatarView.defaultAvatarTv.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(avatarUrl)) {
            GlideUtils.getInstance().showAvatarImg(context, WKApiConfig.getShowUrl(avatarUrl), cacheKey == null ? "" : cacheKey, avatarView.imageView);
        } else if (!TextUtils.isEmpty(uid)) {
            avatarView.showAvatar(uid, WKChannelType.PERSONAL);
        } else {
            avatarView.imageView.setImageResource(com.chat.base.R.drawable.default_view_bg);
        }
    }

    public int indexOfRoom(String roomId, String channelId) {
        for (int i = 0; i < getData().size(); i++) {
            RoomTopicEntity item = getData().get(i);
            if (item == null) continue;
            if (!TextUtils.isEmpty(roomId) && roomId.equals(item.getRoomId())) return i;
            if (!TextUtils.isEmpty(channelId) && channelId.equals(item.getChannelId())) return i;
        }
        return -1;
    }
}
