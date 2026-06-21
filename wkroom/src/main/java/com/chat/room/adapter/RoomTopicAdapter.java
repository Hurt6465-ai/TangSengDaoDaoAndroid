package com.chat.room.adapter;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.AndroidUtilities;
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
        super(R.layout.item_room_topic_card, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, RoomTopicEntity room) {
        holder.setText(R.id.langTv, room == null ? getContext().getString(R.string.peipe_room_lang_cn) : room.getLangLabel());
        String tag = room == null ? getContext().getString(R.string.peipe_room_tag_chat) : room.getTagLabel(getContext());
        String rawTag = room == null ? "闲谈" : room.getRawTag();
        holder.setText(R.id.tagTv, "# " + tag);
        holder.setTextColor(R.id.tagTv, tagColor(rawTag));
        holder.setText(R.id.titleTv, room == null ? getContext().getString(R.string.peipe_room_tab_title) : room.getShowTitle());
        holder.getView(R.id.tagTv).setBackground(makeTagBackground(rawTag));

        ImageView bgIv = holder.getView(R.id.bgIv);
        bgIv.setImageResource(backgroundRes(room == null ? 1 : room.background_index));

        bindCardAvatars(holder, room);

        View card = holder.getView(R.id.cardRoot);
        card.setContentDescription(room == null || TextUtils.isEmpty(room.title) ? getContext().getString(R.string.peipe_room_tab_title) : room.title);
    }

    private GradientDrawable makeTagBackground(String rawTag) {
        GradientDrawable drawable = new GradientDrawable();
        int color = tagColor(rawTag);
        drawable.setCornerRadius(AndroidUtilities.dp(8));
        drawable.setColor(withAlpha(color, 0x33));
        drawable.setStroke(AndroidUtilities.dp(1), withAlpha(color, 0x66));
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int tagColor(String rawTag) {
        if (TextUtils.isEmpty(rawTag)) return 0xFF64748B;
        if ("练口语".equals(rawTag)) return 0xFF16A34A;
        if ("找搭子".equals(rawTag)) return 0xFFEC4899;
        if ("工作".equals(rawTag)) return 0xFFF97316;
        if ("影视".equals(rawTag)) return 0xFF8B5CF6;
        if ("音乐".equals(rawTag)) return 0xFF06B6D4;
        if ("学习".equals(rawTag)) return 0xFF3B82F6;
        if ("交友".equals(rawTag)) return 0xFFEF4444;
        return 0xFF64748B;
    }

    private int backgroundRes(int index) {
        switch (index) {
            case 2: return R.drawable.room_bg_02;
            case 3: return R.drawable.room_bg_03;
            case 4: return R.drawable.room_bg_04;
            case 5: return R.drawable.room_bg_05;
            case 6: return R.drawable.room_bg_06;
            case 7: return R.drawable.room_bg_07;
            case 8: return R.drawable.room_bg_08;
            case 9: return R.drawable.room_bg_09;
            case 10: return R.drawable.room_bg_10;
            case 11: return R.drawable.room_bg_11;
            case 12: return R.drawable.room_bg_12;
            case 13: return R.drawable.room_bg_13;
            case 14: return R.drawable.room_bg_14;
            case 15: return R.drawable.room_bg_15;
            case 16: return R.drawable.room_bg_16;
            case 17: return R.drawable.room_bg_17;
            case 18: return R.drawable.room_bg_18;
            case 19: return R.drawable.room_bg_19;
            case 20: return R.drawable.room_bg_20;
            default: return R.drawable.room_bg_01;
        }
    }

    private void bindCardAvatars(@NonNull BaseViewHolder holder, RoomTopicEntity room) {
        AvatarView creatorAvatar = holder.getView(R.id.creatorAvatar);
        bindMemberAvatar(creatorAvatar, room == null ? null : room.getCreatorMember(), 46f);

        List<RoomTopicEntity.RoomMember> members = room == null ? Collections.emptyList() : room.getSideMembers();
        for (int i = 0; i < SIDE_AVATAR_IDS.length; i++) {
            AvatarView avatarView = holder.getView(SIDE_AVATAR_IDS[i]);
            RoomTopicEntity.RoomMember member = members != null && i < members.size() ? members.get(i) : null;
            if (member == null || TextUtils.isEmpty(member.uid)) {
                avatarView.setVisibility(View.GONE);
            } else {
                bindMemberAvatar(avatarView, member, 40f);
            }
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
