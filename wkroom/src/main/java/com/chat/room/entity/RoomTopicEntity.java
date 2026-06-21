package com.chat.room.entity;

import android.text.TextUtils;

import com.xinbida.wukongim.entity.WKChannelType;

import java.io.Serializable;
import java.util.List;

/**
 * 临时聊天室列表项。
 * 字段使用 snake_case，方便和唐僧/悟空后端直接对接。
 */
public class RoomEntity implements Serializable {
    public String room_id;
    public String title;
    public String desc;
    public String tag;
    public String language;
    public String channel_id;
    public byte channel_type = WKChannelType.GROUP;
    public String type; // temporary / nearby / language
    public String status; // alive / frozen / deleting
    public long created_at;
    public long last_active_at;
    public long expire_at;
    public int active_users;
    public int online_count;
    public int member_count;
    public int unread;
    public int distance_meters;
    public double hot_score;
    public double match_score;
    public String recommend_reason;
    public String last_from_name;
    public String last_text;
    public long last_message_at;
    public String creator_uid;
    public String creator_name;
    public String creator_avatar;
    public String creator_avatar_cache_key;
    public List<RoomMember> members;
    public List<RoomMember> reply_users;
    public int reply_count;

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

    public String getTagLabel() {
        if (!TextUtils.isEmpty(tag)) return tag;
        if (!TextUtils.isEmpty(type) && "nearby".equals(type)) return "附近";
        if (!TextUtils.isEmpty(type) && "language".equals(type)) return "语伴";
        return "闲谈";
    }

    public String getLangLabel() {
        if (!TextUtils.isEmpty(language)) return language.toUpperCase();
        return "CN";
    }

    public int getOnlineCount() {
        if (online_count > 0) return online_count;
        if (active_users > 0) return active_users;
        return member_count;
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

    public List<RoomMember> getSideMembers() {
        java.util.ArrayList<RoomMember> out = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
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

    public void mergeFrom(RoomEntity next) {
        if (next == null) return;
        if (!TextUtils.isEmpty(next.room_id)) room_id = next.room_id;
        if (!TextUtils.isEmpty(next.title)) title = next.title;
        if (!TextUtils.isEmpty(next.desc)) desc = next.desc;
        if (!TextUtils.isEmpty(next.tag)) tag = next.tag;
        if (!TextUtils.isEmpty(next.language)) language = next.language;
        if (!TextUtils.isEmpty(next.channel_id)) channel_id = next.channel_id;
        if (next.channel_type != 0) channel_type = next.channel_type;
        if (!TextUtils.isEmpty(next.type)) type = next.type;
        if (!TextUtils.isEmpty(next.status)) status = next.status;
        if (next.created_at > 0) created_at = next.created_at;
        if (next.last_active_at > 0) last_active_at = next.last_active_at;
        if (next.expire_at > 0) expire_at = next.expire_at;
        if (next.active_users >= 0) active_users = next.active_users;
        if (next.reply_count >= 0) reply_count = next.reply_count;
        if (next.online_count >= 0) online_count = next.online_count;
        if (next.member_count >= 0) member_count = next.member_count;
        if (next.unread >= 0) unread = next.unread;
        if (next.distance_meters >= 0) distance_meters = next.distance_meters;
        if (next.hot_score >= 0) hot_score = next.hot_score;
        if (next.match_score >= 0) match_score = next.match_score;
        if (!TextUtils.isEmpty(next.recommend_reason)) recommend_reason = next.recommend_reason;
        if (!TextUtils.isEmpty(next.last_from_name)) last_from_name = next.last_from_name;
        if (!TextUtils.isEmpty(next.last_text)) last_text = next.last_text;
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
}
