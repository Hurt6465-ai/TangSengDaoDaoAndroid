package com.chat.room.adapter;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

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
    private static final int SIDE_AVATAR_SIZE_DP = 24;
    private static final int SIDE_AVATAR_STEP_DP = 17;
    private static final int COUNT_BUBBLE_SIZE_DP = 30;
    private static final int COUNT_GAP_DP = 6;

    public RoomTopicAdapter(List<RoomTopicEntity> data) {
        super(R.layout.item_room_topic_card, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, RoomTopicEntity room) {
        holder.setText(R.id.langTv, room == null ? getContext().getString(R.string.peipe_room_lang_cn) : room.getLangLabel());
        String tag = room == null ? getContext().getString(R.string.peipe_room_tag_chat) : room.getTagLabel(getContext());
        String rawTag = room == null ? "闲谈" : room.getRawTag();
        holder.setText(R.id.tagTv, "# " + tag);
        holder.setTextColor(R.id.tagTv, tagTextColor(rawTag));
        holder.setText(R.id.titleTv, room == null ? getContext().getString(R.string.peipe_room_tab_title) : room.getShowTitle());
        holder.setText(R.id.creatorNameTv, creatorName(room));
        holder.getView(R.id.tagTv).setBackground(makeTagBackground(rawTag));

        ImageView bgIv = holder.getView(R.id.bgIv);
        bgIv.setImageResource(backgroundRes(room == null ? 1 : room.background_index));

        bindCardAvatars(holder, room);

        View card = holder.getView(R.id.cardRoot);
        card.setContentDescription(room == null || TextUtils.isEmpty(room.title) ? getContext().getString(R.string.peipe_room_tab_title) : room.title);
    }

    private String creatorName(RoomTopicEntity room) {
        if (room == null) return "发布者";
        RoomTopicEntity.RoomMember creator = room.getCreatorMember();
        if (creator != null && !TextUtils.isEmpty(creator.name)) return creator.name;
        if (!TextUtils.isEmpty(room.creator_name)) return room.creator_name;
        return "发布者";
    }

    private GradientDrawable makeTagBackground(String rawTag) {
        GradientDrawable drawable = new GradientDrawable();
        int color = tagTextColor(rawTag);
        drawable.setCornerRadius(AndroidUtilities.dp(10));
        // 假磨砂：高透明白底 + 标签色描边。比直接彩色透明底更清楚，也兼容低版本 Android。
        drawable.setColor(0xE6FFFFFF);
        drawable.setStroke(AndroidUtilities.dp(1), withAlpha(color, 0x4D));
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int tagTextColor(String rawTag) {
        if (TextUtils.isEmpty(rawTag)) return 0xFF475569;
        if ("练口语".equals(rawTag)) return 0xFF15803D;
        if ("找搭子".equals(rawTag)) return 0xFFBE123C;
        if ("工作".equals(rawTag)) return 0xFFC2410C;
        if ("影视".equals(rawTag)) return 0xFF6D28D9;
        if ("音乐".equals(rawTag)) return 0xFF0F766E;
        if ("学习".equals(rawTag)) return 0xFF1D4ED8;
        if ("交友".equals(rawTag)) return 0xFFDC2626;
        return 0xFF475569;
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
        bindMemberAvatar(creatorAvatar, room == null ? null : room.getCreatorMember(), 38f);

        List<RoomTopicEntity.RoomMember> members = room == null ? Collections.emptyList() : room.getSideMembers();
        int participantCount = room == null ? 0 : room.getParticipantCount();
        boolean showCount = participantCount > SIDE_AVATAR_IDS.length;
        TextView countTv = holder.getView(R.id.participantCountTv);
        countTv.setVisibility(showCount ? View.VISIBLE : View.GONE);
        if (showCount) {
            countTv.setText(formatParticipantCount(participantCount));
        }

        int visibleAvatarCount = showCount ? 5 : SIDE_AVATAR_IDS.length;
        int baseEndMargin = showCount ? COUNT_BUBBLE_SIZE_DP + COUNT_GAP_DP : 0;
        for (int i = 0; i < SIDE_AVATAR_IDS.length; i++) {
            AvatarView avatarView = holder.getView(SIDE_AVATAR_IDS[i]);
            RoomTopicEntity.RoomMember member = members != null && i < members.size() ? members.get(i) : null;
            if (i >= visibleAvatarCount || member == null || (TextUtils.isEmpty(member.uid) && TextUtils.isEmpty(member.avatar) && TextUtils.isEmpty(member.name))) {
                avatarView.setVisibility(View.GONE);
            } else {
                layoutSideAvatar(avatarView, baseEndMargin + i * SIDE_AVATAR_STEP_DP);
                bindMemberAvatar(avatarView, member, SIDE_AVATAR_SIZE_DP);
            }
        }
    }

    private void layoutSideAvatar(AvatarView avatarView, int endMarginDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(AndroidUtilities.dp(SIDE_AVATAR_SIZE_DP), AndroidUtilities.dp(SIDE_AVATAR_SIZE_DP));
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.setMarginEnd(AndroidUtilities.dp(endMarginDp));
        avatarView.setLayoutParams(lp);
    }

    private String formatParticipantCount(int count) {
        if (count > 99) return "+99";
        return "+" + Math.max(count, 0);
    }

    private void bindMemberAvatar(AvatarView avatarView, RoomTopicEntity.RoomMember member, float size) {
        if (avatarView == null) return;
        avatarView.setSize(size, size / 2f);
        avatarView.setStrokeWidth(1f);
        if (member != null && !TextUtils.isEmpty(member.avatar)) {
            avatarView.setVisibility(View.VISIBLE);
            avatarView.showAvatarUrl(member.avatar, member.avatar_cache_key, member.name);
        } else if (member != null && !TextUtils.isEmpty(member.uid)) {
            avatarView.setVisibility(View.VISIBLE);
            avatarView.showAvatar(member.uid, WKChannelType.PERSONAL, member.avatar_cache_key);
        } else if (member != null && !TextUtils.isEmpty(member.name)) {
            avatarView.setVisibility(View.VISIBLE);
            avatarView.showDefaultAvatar(member.name);
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
