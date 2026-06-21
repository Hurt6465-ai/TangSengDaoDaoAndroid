package com.chat.room.adapter;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.components.AvatarView;
import com.chat.room.R;
import com.chat.room.entity.RoomEntity;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.Collections;
import java.util.List;

public class RoomListAdapter extends BaseQuickAdapter<RoomEntity, BaseViewHolder> {
    private static final int[] ROOM_BACKGROUNDS = new int[]{
            R.drawable.room_bg_01, R.drawable.room_bg_02, R.drawable.room_bg_03, R.drawable.room_bg_04, R.drawable.room_bg_05,
            R.drawable.room_bg_06, R.drawable.room_bg_07, R.drawable.room_bg_08, R.drawable.room_bg_09, R.drawable.room_bg_10,
            R.drawable.room_bg_11, R.drawable.room_bg_12, R.drawable.room_bg_13, R.drawable.room_bg_14, R.drawable.room_bg_15,
            R.drawable.room_bg_16, R.drawable.room_bg_17, R.drawable.room_bg_18, R.drawable.room_bg_19, R.drawable.room_bg_20
    };

    private static final int[] SIDE_AVATAR_IDS = new int[]{
            R.id.sideAvatar1, R.id.sideAvatar2, R.id.sideAvatar3,
            R.id.sideAvatar4, R.id.sideAvatar5, R.id.sideAvatar6
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

        hideOptionalText(holder, R.id.metaTv);
        hideOptionalText(holder, R.id.onlineTv);
        hideOptionalText(holder, R.id.distanceTv);
        hideOptionalText(holder, R.id.unreadTv);

        bindCardAvatars(holder, room);

        View card = holder.getView(R.id.cardRoot);
        card.setContentDescription(room == null || TextUtils.isEmpty(room.title) ? "聊天室" : room.title);
    }

    private void hideOptionalText(@NonNull BaseViewHolder holder, int id) {
        View view = holder.getView(id);
        if (view != null) view.setVisibility(View.GONE);
    }

    private void bindCardAvatars(@NonNull BaseViewHolder holder, RoomEntity room) {
        AvatarView creatorAvatar = holder.getView(R.id.creatorAvatar);
        bindMemberAvatar(creatorAvatar, room == null ? null : room.getCreatorMember(), 58f);

        List<RoomEntity.RoomMember> members = room == null ? Collections.emptyList() : room.getSideMembers();
        for (int i = 0; i < SIDE_AVATAR_IDS.length; i++) {
            AvatarView avatarView = holder.getView(SIDE_AVATAR_IDS[i]);
            RoomEntity.RoomMember member = members != null && i < members.size() ? members.get(i) : null;
            bindMemberAvatar(avatarView, member, 26f);
        }
    }

    private void bindMemberAvatar(AvatarView avatarView, RoomEntity.RoomMember member, float size) {
        if (avatarView == null) return;
        avatarView.setSize(size);
        if (member != null && !TextUtils.isEmpty(member.uid)) {
            avatarView.setVisibility(View.VISIBLE);
            avatarView.showAvatar(member.uid, WKChannelType.PERSONAL, member.avatar_cache_key);
        } else {
            avatarView.setVisibility(View.INVISIBLE);
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
