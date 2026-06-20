package com.chat.room.store;

import android.content.Context;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.room.entity.RoomTopicEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoomTopicStore {
    private static final String SP_NAME = "peipe_topic_rooms";
    private static final String KEY_ROOMS = "rooms";

    private RoomTopicStore() {
    }

    public static List<RoomTopicEntity> loadRooms(Context context) {
        if (context == null) return new ArrayList<>();
        String json = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getString(KEY_ROOMS, "");
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            List<RoomTopicEntity> list = JSON.parseArray(json, RoomTopicEntity.class);
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    public static void saveRooms(Context context, List<RoomTopicEntity> rooms) {
        if (context == null || rooms == null) return;
        context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_ROOMS, JSON.toJSONString(rooms)).apply();
    }

    public static RoomTopicEntity createLocalRoom(Context context, String title, String tag, String language, String channelId) {
        UserInfoEntity user = WKConfig.getInstance().getUserInfo();
        RoomTopicEntity room = new RoomTopicEntity();
        room.room_id = channelId;
        room.channel_id = channelId;
        room.channel_type = 2;
        room.title = title;
        room.tag = TextUtils.isEmpty(tag) ? "闲谈" : tag;
        room.language = TextUtils.isEmpty(language) ? "中文" : language;
        room.creator_uid = user.uid;
        if (TextUtils.isEmpty(room.creator_uid)) room.creator_uid = WKConfig.getInstance().getUid();
        room.creator_name = !TextUtils.isEmpty(user.name) ? user.name : user.username;
        if (TextUtils.isEmpty(room.creator_name)) room.creator_name = WKConfig.getInstance().getUserName();
        room.creator_avatar = user.avatar;
        room.background_url = "";
        room.background_index = Math.abs(channelId == null ? title.hashCode() : channelId.hashCode()) % 8 + 1;
        room.created_at = System.currentTimeMillis();
        room.last_reply_at = 0;
        List<RoomTopicEntity> rooms = loadRooms(context);
        rooms.add(0, room);
        sortRooms(rooms);
        saveRooms(context, rooms);
        return room;
    }

    public static void upsertRoom(Context context, RoomTopicEntity room) {
        if (context == null || room == null) return;
        List<RoomTopicEntity> rooms = loadRooms(context);
        int index = indexOf(rooms, room.getRoomId(), room.getChannelId());
        if (index >= 0) rooms.set(index, room);
        else rooms.add(0, room);
        sortRooms(rooms);
        saveRooms(context, rooms);
    }

    public static void deleteRoom(Context context, RoomTopicEntity room) {
        if (context == null || room == null) return;
        List<RoomTopicEntity> rooms = loadRooms(context);
        int index = indexOf(rooms, room.getRoomId(), room.getChannelId());
        if (index >= 0) rooms.remove(index);
        saveRooms(context, rooms);
    }

    public static void sortRooms(List<RoomTopicEntity> rooms) {
        if (rooms == null || rooms.size() <= 1) return;
        Collections.sort(rooms, (a, b) -> {
            int pinCompare = Integer.compare(b == null ? 0 : b.pinned, a == null ? 0 : a.pinned);
            if (pinCompare != 0) return pinCompare;
            long at = a == null ? 0 : Math.max(a.last_reply_at, a.created_at);
            long bt = b == null ? 0 : Math.max(b.last_reply_at, b.created_at);
            return Long.compare(bt, at);
        });
    }

    public static int indexOf(List<RoomTopicEntity> rooms, String roomId, String channelId) {
        if (rooms == null) return -1;
        for (int i = 0; i < rooms.size(); i++) {
            RoomTopicEntity item = rooms.get(i);
            if (item == null) continue;
            if (!TextUtils.isEmpty(roomId) && roomId.equals(item.getRoomId())) return i;
            if (!TextUtils.isEmpty(channelId) && channelId.equals(item.getChannelId())) return i;
        }
        return -1;
    }
}
