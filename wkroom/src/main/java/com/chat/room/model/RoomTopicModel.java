package com.chat.room.model;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.room.entity.CreateRoomTopicRequest;
import com.chat.room.entity.RoomTopicEntity;
import com.chat.room.entity.RoomTopicListResponse;

import java.util.HashMap;
import java.util.Map;

public class RoomTopicModel extends WKBaseModel {
    private RoomTopicModel() {
    }

    private static class Binder {
        private static final RoomTopicModel MODEL = new RoomTopicModel();
    }

    public static RoomTopicModel getInstance() {
        return Binder.MODEL;
    }

    public void listRooms(IRequestResultListener<RoomTopicListResponse> listener) {
        request(createService(RoomTopicService.class).listRooms(), listener);
    }

    public void createRoom(String title, String tag, String language, IRequestResultListener<RoomTopicEntity> listener) {
        CreateRoomTopicRequest request = new CreateRoomTopicRequest();
        request.title = title;
        request.tag = tag;
        request.language = language;
        request(createService(RoomTopicService.class).createRoom(request), listener);
    }

    public void enterRoom(RoomTopicEntity room, IRequestResultListener<RoomTopicEntity> listener) {
        Map<String, Object> request = baseRoomRequest(room);
        request(createService(RoomTopicService.class).enterRoom(request), listener);
    }

    public void pinRoom(RoomTopicEntity room, boolean pinned, IRequestResultListener<RoomTopicEntity> listener) {
        Map<String, Object> request = baseRoomRequest(room);
        request.put("pinned", pinned ? 1 : 0);
        request(createService(RoomTopicService.class).pinRoom(request), listener);
    }

    public void deleteRoom(RoomTopicEntity room, IRequestResultListener<Object> listener) {
        request(createService(RoomTopicService.class).deleteRoom(baseRoomRequest(room)), listener);
    }

    private Map<String, Object> baseRoomRequest(RoomTopicEntity room) {
        Map<String, Object> request = new HashMap<>();
        request.put("room_id", room == null ? "" : room.getRoomId());
        request.put("channel_id", room == null ? "" : room.getChannelId());
        request.put("channel_type", room == null ? 2 : room.channel_type);
        return request;
    }
}
