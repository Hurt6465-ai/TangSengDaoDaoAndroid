package com.chat.room.adapter;

import android.text.TextUtils;
import android.view.View;

import com.chat.base.ui.components.AvatarView;
import com.chat.base.glide.GlideUtils;
import java.util.Collections;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.room.R;
import com.chat.room.entity.RoomEntity;

import java.util.List;

public class RoomListAdapter extends BaseQuickAdapter<RoomEntity, BaseViewHolder> {
    private static final int[] ROOM_BACKGROUNDS = new int[]{
            R.drawable.room_bg_01, R.drawable.room_bg_02, R.drawable.room_bg_03, R.drawable.room_bg_04, R.drawable.room_bg_05,
            R.drawable.room_bg_06, R.drawable.room_bg_07, R.drawable.room_bg_08, R.drawable.room_bg_09, R.drawable.room_bg_10,
            R.drawable.room_bg_11, R.drawable.room_bg_12, R.drawable.room_bg_13, R.drawable.room_bg_14, R.drawable.room_bg_15,
            R.drawable.room_bg_16, R.drawable.room_bg_17, R.drawable.room_bg_18, R.drawable.room_bg_19, R.drawable.room_bg_20
    };

    public RoomListAdapter(List<RoomEntity> data) {
        super(R.layout.item_room_card, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, RoomEntity room) {
        String seed = room == null ? "" : room.getRoomId();
        int bgIndex = (seed.hashCode() & 0x7fffffff) % ROOM_BACKGROUNDS.length;
        holder.setImageResource(R.id.bgIv, ROOM_BACKGROUNDS[bgIndex]);
        holder.setText(R.id.langTv, room == null ? "CN" : room.getLangLabel());
        holder.setText(R.id.tagTv, "# " + (room == null ? "闲谈" : room.getTagLabel()));
        holder.setText(R.id.titleTv, room == null ? "聊天室" : room.getShowTitle());
        holder.getView(R.id.metaTv).setVisibility(View.GONE);
        holder.getView(R.id.onlineTv).setVisibility(View.GONE);

        String distance = room == null ? "" : room.getDistanceLabel();
        holder.setText(R.id.distanceTv, distance);
        holder.getView(R.id.distanceTv).setVisibility(TextUtils.isEmpty(distance) ? View.GONE : View.VISIBLE);

        holder.getView(R.id.unreadTv).setVisibility(View.GONE);

        bindCardAvatars(holder, room);

        View card = holder.getView(R.id.cardRoot);
        card.setContentDescription(room == null || TextUtils.isEmpty(room.title) ? "聊天室" : room.title);
    }

    private void bindCardAvatars(@NonNull BaseViewHolder holder, RoomEntity room) {
        AvatarView creatorAvatar = holder.getView(R.id.creatorAvatar);
        creatorAvatar.setSize(56);
        RoomEntity.RoomMember creator = room == null ? null : room.getCreatorMember();
        if (creator != null && !TextUtils.isEmpty(creator.avatar)) {
            creatorAvatar.setVisibility(View.VISIBLE);
            GlideUtils.getInstance().showAvatarImg(getContext(), creator.avatar, creator.avatar_cache_key, creatorAvatar.imageView);
        } else {
            creatorAvatar.setVisibility(View.INVISIBLE);
        }

        int[] ids = new int[]{R.id.sideAvatar1, R.id.sideAvatar2, R.id.sideAvatar3, R.id.sideAvatar4, R.id.sideAvatar5, R.id.sideAvatar6};
        List<RoomEntity.RoomMember> members = room == null ? Collections.emptyList() : room.getSideMembers();
        for (int i = 0; i < ids.length; i++) {
            AvatarView avatarView = holder.getView(ids[i]);
            avatarView.setSize(26);
            if (members != null && i < members.size() && members.get(i) != null && !TextUtils.isEmpty(members.get(i).avatar)) {
                avatarView.setVisibility(View.VISIBLE);
                GlideUtils.getInstance().showAvatarImg(getContext(), members.get(i).avatar, members.get(i).avatar_cache_key, avatarView.imageView);
            } else {
                avatarView.setVisibility(View.INVISIBLE);
            }
        }
    }

    public int indexOfRoom(String roomId, String channelId) {
        for (int i = 0; i < getData().size(); i++) {
            RoomEntity item = getData().get(i);
            if (item == null) continue;
            if (!TextUtils.isEmpty(roomId) && roomId.equals(item.getRoomId())) return i;
            if (!TextUtils.isEmpty(channelId) && channelId.equals(item.getChannelId())) return i;
        }
        return -1;
    }
}
