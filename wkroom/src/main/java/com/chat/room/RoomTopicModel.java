package com.chat.room.model;

import android.text.TextUtils;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.room.entity.CreateRoomTopicRequest;
import com.chat.room.entity.RoomTopicEntity;
import com.chat.room.entity.RoomTopicListResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 话题聊天室模型。
 * 不再调用普通 group/create，避免普通建群接口的好友关系校验。
 * 所有开放话题房走服务端专用 /v1/chatrooms/* 接口。
 */
public class RoomTopicModel extends WKBaseModel {
    private static final int DEFAULT_ROOM_LIST_LIMIT = 30;
    private static final int MAX_ROOM_LIST_LIMIT = 50;

    private RoomTopicModel() {
    }

    private static class Binder {
        private static final RoomTopicModel MODEL = new RoomTopicModel();
    }

    public static RoomTopicModel getInstance() {
        return Binder.MODEL;
    }

    public void listRooms(IRequestResultListener<RoomTopicListResponse> listener) {
        listRooms("", DEFAULT_ROOM_LIST_LIMIT, listener);
    }

    public void listRooms(String cursor, int limit, IRequestResultListener<RoomTopicListResponse> listener) {
        int safeLimit = limit <= 0 ? DEFAULT_ROOM_LIST_LIMIT : Math.min(limit, MAX_ROOM_LIST_LIMIT);
        String safeCursor = TextUtils.isEmpty(cursor) ? null : cursor;
        request(createService(RoomTopicService.class).listRooms(safeLimit, safeCursor), listener);
    }

    public void createRoom(String title, String tag, String language, IRequestResultListener<RoomTopicEntity> listener) {
        createRoom(title, tag, language, UUID.randomUUID().toString(), listener);
    }

    /**
     * createRequestNo 由发布界面创建一次并在同一次重试中复用，避免超时重试生成重复房间。
     */
    public void createRoom(String title, String tag, String language, String createRequestNo,
                           IRequestResultListener<RoomTopicEntity> listener) {
        String safeTitle = title == null ? "" : title.trim();
        if (TextUtils.isEmpty(safeTitle)) {
            if (listener != null) listener.onFail(400, "请输入话题名");
            return;
        }
        CreateRoomTopicRequest request = new CreateRoomTopicRequest();
        request.title = safeTitle;
        request.tag = normalizeTag(tag);
        request.language = TextUtils.isEmpty(language) ? "中文" : language.trim();
        request.create_request_no = TextUtils.isEmpty(createRequestNo)
                ? UUID.randomUUID().toString()
                : createRequestNo.trim();
        this.request(createService(RoomTopicService.class).createRoom(request), listener);
    }

    public void enterRoom(RoomTopicEntity room, IRequestResultListener<RoomTopicEntity> listener) {
        if (room == null) {
            if (listener != null) listener.onFail(404, "话题不存在");
            return;
        }
        request(createService(RoomTopicService.class).enterRoom(baseRoomRequest(room)), listener);
    }

    public void readRoom(RoomTopicEntity room, IRequestResultListener<RoomTopicEntity> listener) {
        if (room == null) {
            if (listener != null) listener.onFail(404, "话题不存在");
            return;
        }
        readRoom(room.getRoomId(), room.getChannelId(), room.channel_type, listener);
    }

    public void readRoom(String roomId, String channelId, byte channelType,
                         IRequestResultListener<RoomTopicEntity> listener) {
        Map<String, Object> request = new HashMap<>();
        request.put("room_id", TextUtils.isEmpty(roomId) ? channelId : roomId);
        request.put("channel_id", TextUtils.isEmpty(channelId) ? roomId : channelId);
        request.put("channel_type", channelType == 0 ? 2 : channelType);
        this.request(createService(RoomTopicService.class).readRoom(request), listener);
    }

    public void pinRoom(RoomTopicEntity room, boolean pinned, IRequestResultListener<RoomTopicEntity> listener) {
        if (room == null) {
            if (listener != null) listener.onFail(400, "话题不存在");
            return;
        }
        Map<String, Object> request = baseRoomRequest(room);
        request.put("pinned", pinned ? 1 : 0);
        this.request(createService(RoomTopicService.class).pinRoom(request), listener);
    }

    public void deleteRoom(RoomTopicEntity room, IRequestResultListener<Object> listener) {
        if (room == null) {
            if (listener != null) listener.onFail(400, "话题不存在");
            return;
        }
        this.request(createService(RoomTopicService.class).deleteRoom(baseRoomRequest(room)), listener);
    }

    private String normalizeTag(String tag) {
        if (TextUtils.isEmpty(tag)) return "闲谈";
        String safeTag = tag.trim();
        return "音乐".equals(safeTag) ? "游戏" : safeTag;
    }

    private Map<String, Object> baseRoomRequest(RoomTopicEntity room) {
        Map<String, Object> request = new HashMap<>();
        request.put("room_id", room == null ? "" : room.getRoomId());
        request.put("channel_id", room == null ? "" : room.getChannelId());
        request.put("channel_type", room == null ? 2 : room.channel_type);
        return request;
    }
}
