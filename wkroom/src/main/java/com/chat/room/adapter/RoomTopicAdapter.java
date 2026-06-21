package com.chat.room.adapter;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.components.AvatarView;
import com.chat.room.R;
import com.chat.room.entity.RoomTopicEntity;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.Collections;
import java.util.List;

/**
 * 话题聊天室卡片 Adapter。
 * 注意：文件名、public class 名必须都是 RoomTopicAdapter。
 */
public class RoomTopicAdapter extends BaseQuickAdapter<RoomTopicEntity, BaseViewHolder> {
    private static final int[] SIDE_AVATAR_IDS = new int[]{
            R.id.sideAvatar1, R.id.sideAvatar2, R.id.sideAvatar3,
            R.id.sideAvatar4, R.id.sideAvatar5, R.id.sideAvatar6
    };

    public RoomTopicAdapter(List<RoomTopicEntity> data) {
        super(R.layout.item_room_card, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, RoomTopicEntity room) {
        holder.setText(R.id.langTv, room == null ? "中文" : room.getLangLabel());
        holder.setText(R.id.tagTv, "# " + (room == null ? "闲谈" : room.getTagLabel()));
        holder.setText(R.id.titleTv, room == null ? "聊天室" : room.getShowTitle());

        bindCardAvatars(holder, room);

        View card = holder.getView(R.id.cardRoot);
        card.setContentDescription(room == null || TextUtils.isEmpty(room.title) ? "聊天室" : room.title);
    }

    private void bindCardAvatars(@NonNull BaseViewHolder holder, RoomTopicEntity room) {
        AvatarView creatorAvatar = holder.getView(R.id.creatorAvatar);
        bindMemberAvatar(creatorAvatar, room == null ? null : room.getCreatorMember(), 58f);

        List<RoomTopicEntity.RoomMember> members = room == null ? Collections.emptyList() : room.getSideMembers();
        for (int i = 0; i < SIDE_AVATAR_IDS.length; i++) {
            AvatarView avatarView = holder.getView(SIDE_AVATAR_IDS[i]);
            RoomTopicEntity.RoomMember member = members != null && i < members.size() ? members.get(i) : null;
            bindMemberAvatar(avatarView, member, 26f);
        }
    }

    private void bindMemberAvatar(AvatarView avatarView, RoomTopicEntity.RoomMember member, float size) {
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
            RoomTopicEntity item = getData().get(i);
            if (item == null) continue;
            if (!TextUtils.isEmpty(roomId) && roomId.equals(item.getRoomId())) return i;
            if (!TextUtils.isEmpty(channelId) && channelId.equals(item.getChannelId())) return i;
        }
        return -1;
    }
}
