package com.chat.room.entity;

import android.text.TextUtils;

import com.xinbida.wukongim.entity.WKChannelType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class RoomTopicEntity implements Serializable {
    public String room_id;
    public String title;
    public String tag;
    public String language;
    public String background_url;
    public int background_index;
    public String channel_id;
    public byte channel_type = WKChannelType.GROUP;
    public int unread_count;
    public int mention_unread_count;
    public int reply_count;
    public int pinned;
    public int hot;
    public long created_at;
    public long last_reply_at;

    public String creator_uid;
    public String creator_name;
    public String creator_avatar;
    public String creator_avatar_cache_key;

    public String last_reply_uid;
    public String last_reply_name;
    public String last_reply_avatar;
    public String last_reply_avatar_cache_key;
    public String last_reply_text;
    public String last_reply_type;

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
        return TextUtils.isEmpty(title) ? "话题聊天室" : title;
    }

    public String getTagLabel() {
        return TextUtils.isEmpty(tag) ? "闲谈" : tag;
    }

    public String getLangLabel() {
        return TextUtils.isEmpty(language) ? "中文" : language;
    }

    public String getMetaText() {
        String name = TextUtils.isEmpty(creator_name) ? "发布者" : creator_name;
        if (reply_count > 0) return name + " 发布 · " + reply_count + " 条回复";
        return name + " 发布";
    }

    public List<RoomAvatar> getCardAvatars() {
        LinkedHashMap<String, RoomAvatar> map = new LinkedHashMap<>();
        RoomAvatar creator = new RoomAvatar();
        creator.uid = creator_uid;
        creator.name = creator_name;
        creator.avatar = creator_avatar;
        creator.avatar_cache_key = creator_avatar_cache_key;
        addAvatar(map, creator);
        if (reply_users != null) {
            for (RoomAvatar avatar : reply_users) {
                addAvatar(map, avatar);
                if (map.size() >= 4) break;
            }
        }
        if (map.size() < 4 && !TextUtils.isEmpty(last_reply_uid)) {
            RoomAvatar avatar = new RoomAvatar();
            avatar.uid = last_reply_uid;
            avatar.name = last_reply_name;
            avatar.avatar = last_reply_avatar;
            avatar.avatar_cache_key = last_reply_avatar_cache_key;
            addAvatar(map, avatar);
        }
        return new ArrayList<>(map.values());
    }

    public List<RoomAvatar> getDedupReplyAvatars() {
        LinkedHashMap<String, RoomAvatar> map = new LinkedHashMap<>();
        if (reply_users != null) {
            for (RoomAvatar avatar : reply_users) {
                addAvatar(map, avatar);
                if (map.size() >= 6) break;
            }
        }
        if (map.size() < 6 && !TextUtils.isEmpty(last_reply_uid)) {
            RoomAvatar avatar = new RoomAvatar();
            avatar.uid = last_reply_uid;
            avatar.name = last_reply_name;
            avatar.avatar = last_reply_avatar;
            avatar.avatar_cache_key = last_reply_avatar_cache_key;
            addAvatar(map, avatar);
        }
        return new ArrayList<>(map.values());
    }

    private void addAvatar(LinkedHashMap<String, RoomAvatar> map, RoomAvatar avatar) {
        if (avatar == null) return;
        String key = !TextUtils.isEmpty(avatar.uid) ? avatar.uid : avatar.avatar;
        if (TextUtils.isEmpty(key)) return;
        if (!map.containsKey(key)) map.put(key, avatar);
    }

    public static class RoomAvatar implements Serializable {
        public String uid;
        public String name;
        public String avatar;
        public String avatar_cache_key;
    }
}
