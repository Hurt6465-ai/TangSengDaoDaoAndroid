package com.chat.room.entity;

import android.content.Context;
import android.text.TextUtils;

import com.chat.room.R;

import com.xinbida.wukongim.entity.WKChannelType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 话题聊天室卡片数据。
 * 注意：文件名、public class 名必须都是 RoomTopicEntity。
 */
public class RoomTopicEntity implements Serializable {
    public String room_id;
    public String title;
    public String desc;
    public String tag;
    public String language;
    public String background_url;
    public int background_index;
    public String channel_id;
    public byte channel_type = WKChannelType.GROUP;
    public String type;
    public String status;
    public long created_at;
    public long last_active_at;
    public long last_reply_at;
    public long expire_at;
    public int active_users;
    public int online_count;
    public int member_count;
    public int unread;
    public int unread_count;
    public int mention_unread_count;
    public int distance_meters;
    public double hot_score;
    public double match_score;
    public int reply_count;
    /** 参与过该话题的人数：创建者 + 进入过或回复过的用户。 */
    public int participant_count;
    public int pinned;
    public int hot;
    public long hot_until;
    public String recommend_reason;
    public String last_from_name;
    public String last_text;
    public String last_reply_uid;
    public String last_reply_name;
    public String last_reply_avatar;
    public String last_reply_avatar_cache_key;
    public String last_reply_text;
    public String last_reply_type;
    public long last_message_at;
    public String creator_uid;
    public String creator_name;
    public String creator_avatar;
    public String creator_avatar_cache_key;
    public List<RoomMember> members;
    public List<RoomMember> reply_users;

    public String getRoomId() {
        if (!TextUtils.isEmpty(room_id)) return room_id;
        if (!TextUtils.isEmpty(channel_id)) return channel_id;
        return "";
    }

    public String getChannelId() {
        if (!TextUtils.isEmpty(channel_id)) return channel_id;
        return getRoomId();
    }

    public String getShowTitle() {
        if (!TextUtils.isEmpty(title)) return title;
        return "聊天室";
    }

    public String getRawTag() {
        if (!TextUtils.isEmpty(tag)) return tag;
        if (!TextUtils.isEmpty(type) && "language".equals(type)) return "练口语";
        return "闲谈";
    }

    public String getTagLabel(Context context) {
        String raw = getRawTag();
        if ("练口语".equals(raw)) return context.getString(R.string.peipe_room_tag_speaking);
        if ("找搭子".equals(raw)) return context.getString(R.string.peipe_room_tag_partner);
        if ("工作".equals(raw)) return context.getString(R.string.peipe_room_tag_work);
        if ("影视".equals(raw)) return context.getString(R.string.peipe_room_tag_movie);
        if ("音乐".equals(raw)) return context.getString(R.string.peipe_room_tag_music);
        if ("学习".equals(raw)) return context.getString(R.string.peipe_room_tag_study);
        if ("交友".equals(raw)) return context.getString(R.string.peipe_room_tag_friend);
        return context.getString(R.string.peipe_room_tag_chat);
    }

    public String getLangLabel() {
        if (!TextUtils.isEmpty(language)) return language.toUpperCase();
        return "中文";
    }

    public int getOnlineCount() {
        if (online_count > 0) return online_count;
        if (active_users > 0) return active_users;
        return member_count;
    }

    public int getParticipantCount() {
        if (participant_count > 0) return participant_count;
        if (member_count > 0) return member_count;
        int uniqueCount = countUniqueMembers();
        if (uniqueCount > 0) return uniqueCount;
        if (reply_count > 0) return reply_count + 1;
        return 1;
    }

    private int countUniqueMembers() {
        HashSet<String> seen = new HashSet<>();
        RoomMember creator = getCreatorMember();
        String creatorKey = keyOf(creator);
        if (!TextUtils.isEmpty(creatorKey)) seen.add(creatorKey);
        List<RoomMember> source = getSourceMembers();
        if (source != null) {
            for (RoomMember member : source) {
                String key = keyOf(member);
                if (!TextUtils.isEmpty(key)) seen.add(key);
            }
        }
        return seen.size();
    }

    public String getOnlineLabel() {
        int count = getOnlineCount();
        if (count <= 0) return "在线";
        if (count > 9999) return "9999+ 在线";
        return count + " 在线";
    }

    public String getDistanceLabel() {
        if (distance_meters <= 0) return "";
        if (distance_meters < 1000) return distance_meters + "m";
        double km = distance_meters / 1000.0d;
        if (km < 10) return String.format(java.util.Locale.US, "%.1fkm", km);
        return Math.round(km) + "km";
    }

    public RoomMember getCreatorMember() {
        RoomMember creator = new RoomMember();
        creator.uid = creator_uid;
        creator.name = creator_name;
        creator.avatar = creator_avatar;
        creator.avatar_cache_key = creator_avatar_cache_key;
        if (!TextUtils.isEmpty(creator.uid) || !TextUtils.isEmpty(creator.avatar)) return creator;
        List<RoomMember> source = getSourceMembers();
        if (source != null && source.size() > 0) return source.get(0);
        return creator;
    }

    /** 右侧最多 6 个用户头像，不包含发布者。 */
    public List<RoomMember> getSideMembers() {
        ArrayList<RoomMember> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        RoomMember creator = getCreatorMember();
        String creatorKey = keyOf(creator);
        if (!TextUtils.isEmpty(creatorKey)) seen.add(creatorKey);
        List<RoomMember> source = getSourceMembers();
        if (source != null) {
            for (RoomMember member : source) {
                if (member == null) continue;
                String key = keyOf(member);
                if (TextUtils.isEmpty(key) || seen.contains(key)) continue;
                seen.add(key);
                out.add(member);
                if (out.size() >= 6) break;
            }
        }
        return out;
    }

    private List<RoomMember> getSourceMembers() {
        if (reply_users != null && reply_users.size() > 0) return reply_users;
        return members;
    }

    private String keyOf(RoomMember member) {
        if (member == null) return "";
        if (!TextUtils.isEmpty(member.uid)) return member.uid;
        if (!TextUtils.isEmpty(member.avatar)) return member.avatar;
        return member.name;
    }

    public void mergeFrom(RoomTopicEntity next) {
        if (next == null) return;
        if (!TextUtils.isEmpty(next.room_id)) room_id = next.room_id;
        if (!TextUtils.isEmpty(next.title)) title = next.title;
        if (!TextUtils.isEmpty(next.desc)) desc = next.desc;
        if (!TextUtils.isEmpty(next.tag)) tag = next.tag;
        if (!TextUtils.isEmpty(next.language)) language = next.language;
        if (!TextUtils.isEmpty(next.background_url)) background_url = next.background_url;
        if (next.background_index > 0) background_index = next.background_index;
        if (!TextUtils.isEmpty(next.channel_id)) channel_id = next.channel_id;
        if (next.channel_type != 0) channel_type = next.channel_type;
        if (!TextUtils.isEmpty(next.type)) type = next.type;
        if (!TextUtils.isEmpty(next.status)) status = next.status;
        if (next.created_at > 0) created_at = next.created_at;
        if (next.last_active_at > 0) last_active_at = next.last_active_at;
        if (next.last_reply_at > 0) last_reply_at = next.last_reply_at;
        if (next.expire_at > 0) expire_at = next.expire_at;
        if (next.reply_count >= 0) reply_count = next.reply_count;
        if (next.participant_count >= 0) participant_count = next.participant_count;
        if (next.online_count >= 0) online_count = next.online_count;
        if (next.member_count >= 0) member_count = next.member_count;
        if (next.active_users >= 0) active_users = next.active_users;
        if (next.unread >= 0) unread = next.unread;
        if (next.unread_count >= 0) unread_count = next.unread_count;
        if (next.mention_unread_count >= 0) mention_unread_count = next.mention_unread_count;
        if (next.distance_meters >= 0) distance_meters = next.distance_meters;
        if (next.hot_score >= 0) hot_score = next.hot_score;
        if (next.match_score >= 0) match_score = next.match_score;
        if (next.pinned >= 0) pinned = next.pinned;
        if (next.hot >= 0) hot = next.hot;
        if (next.hot_until > 0) hot_until = next.hot_until;
        if (!TextUtils.isEmpty(next.recommend_reason)) recommend_reason = next.recommend_reason;
        if (!TextUtils.isEmpty(next.last_from_name)) last_from_name = next.last_from_name;
        if (!TextUtils.isEmpty(next.last_text)) last_text = next.last_text;
        if (!TextUtils.isEmpty(next.last_reply_uid)) last_reply_uid = next.last_reply_uid;
        if (!TextUtils.isEmpty(next.last_reply_name)) last_reply_name = next.last_reply_name;
        if (!TextUtils.isEmpty(next.last_reply_avatar)) last_reply_avatar = next.last_reply_avatar;
        if (!TextUtils.isEmpty(next.last_reply_avatar_cache_key)) last_reply_avatar_cache_key = next.last_reply_avatar_cache_key;
        if (!TextUtils.isEmpty(next.last_reply_text)) last_reply_text = next.last_reply_text;
        if (!TextUtils.isEmpty(next.last_reply_type)) last_reply_type = next.last_reply_type;
        if (next.last_message_at > 0) last_message_at = next.last_message_at;
        if (!TextUtils.isEmpty(next.creator_uid)) creator_uid = next.creator_uid;
        if (!TextUtils.isEmpty(next.creator_name)) creator_name = next.creator_name;
        if (!TextUtils.isEmpty(next.creator_avatar)) creator_avatar = next.creator_avatar;
        if (!TextUtils.isEmpty(next.creator_avatar_cache_key)) creator_avatar_cache_key = next.creator_avatar_cache_key;
        if (next.members != null) members = next.members;
        if (next.reply_users != null) reply_users = next.reply_users;
    }

    public static class RoomMember implements Serializable {
        public String uid;
        public String name;
        public String avatar;
        public String avatar_cache_key;
        public String flag;
    }

    /** 兼容旧代码里可能出现的 RoomAvatar 类型。 */
    public static class RoomAvatar extends RoomMember implements Serializable {
    }
}
