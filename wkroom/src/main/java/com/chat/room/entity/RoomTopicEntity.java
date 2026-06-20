package com.chat.room.entity;

import android.text.TextUtils;

import com.xinbida.wukongim.entity.WKChannelType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 话题聊天室卡片数据。
 * 房间名就是话题名；真实聊天走唐僧/悟空原生群聊 channel。
 */
public class RoomTopicEntity implements Serializable {
    public String room_id;
    public String title;
    public String tag;
    public String language;
    public String background_url;
    public int background_index;
    public String channel_id;
    public byte channel_type = WKChannelType.GROUP;
    public int unread;
    public int reply_count;
    public int pinned; // 1置顶 0普通
    public long created_at;
    public long last_reply_at;

    public String creator_uid;
    public String creator_name;
    public String creator_avatar;
    public String creator_avatar_cache_key;
    public String creator_flag;

    public String last_reply_uid;
    public String last_reply_name;
    public String last_reply_avatar;
    public String last_reply_avatar_cache_key;
    public String last_reply_flag;
    public String last_reply_text;

    /** 后端按时间倒序返回最近回复用户，前端再做去重+限制6个。 */
    public List<RoomAvatar> reply_users;

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
        return "话题聊天室";
    }

    public String getTagLabel() {
        if (!TextUtils.isEmpty(tag)) return tag;
        return "闲谈";
    }

    public String getLangLabel() {
        if (!TextUtils.isEmpty(language)) return language.toUpperCase();
        return "中文";
    }

    public String getMetaText() {
        if (!TextUtils.isEmpty(last_reply_name)) {
            if (reply_count > 0) return last_reply_name + " · " + reply_count + "条回复";
            return last_reply_name + " 回复了";
        }
        if (!TextUtils.isEmpty(creator_name)) return creator_name + " 发布";
        return "刚刚发布";
    }

    public List<RoomAvatar> getDedupReplyAvatars() {
        LinkedHashMap<String, RoomAvatar> map = new LinkedHashMap<>();
        if (reply_users != null) {
            for (RoomAvatar avatar : reply_users) {
                addReplyAvatar(map, avatar);
                if (map.size() >= 6) break;
            }
        }
        if (map.size() < 6 && !TextUtils.isEmpty(last_reply_uid)) {
            RoomAvatar last = new RoomAvatar();
            last.uid = last_reply_uid;
            last.name = last_reply_name;
            last.avatar = last_reply_avatar;
            last.avatar_cache_key = last_reply_avatar_cache_key;
            last.flag = last_reply_flag;
            addReplyAvatar(map, last);
        }
        return new ArrayList<>(map.values());
    }

    private void addReplyAvatar(LinkedHashMap<String, RoomAvatar> map, RoomAvatar avatar) {
        if (avatar == null) return;
        String uid = avatar.uid;
        if (TextUtils.isEmpty(uid)) uid = avatar.name;
        if (TextUtils.isEmpty(uid)) uid = avatar.avatar;
        if (TextUtils.isEmpty(uid)) return;
        if (!TextUtils.isEmpty(creator_uid) && creator_uid.equals(uid)) return;
        if (!map.containsKey(uid)) map.put(uid, avatar);
    }

    public static class RoomAvatar implements Serializable {
        public String uid;
        public String name;
        public String avatar;
        public String avatar_cache_key;
        public String flag;
    }
}
