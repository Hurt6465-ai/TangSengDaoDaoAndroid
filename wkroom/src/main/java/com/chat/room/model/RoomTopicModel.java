package com.chat.room.model;

import android.content.Context;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.room.WKRoomApplication;
import com.chat.room.entity.RoomTopicEntity;
import com.chat.room.entity.RoomTopicListResponse;
import com.chat.room.store.RoomTopicStore;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;

/**
 * 话题房间本地目录模型。
 * 发布话题直接调用唐僧已有 group/create 创建原生群聊。
 */
public class RoomTopicModel {
    private RoomTopicModel() {
    }

    private static class Binder {
        private static final RoomTopicModel MODEL = new RoomTopicModel();
    }

    public static RoomTopicModel getInstance() {
        return Binder.MODEL;
    }

    public void listRooms(IRequestResultListener<RoomTopicListResponse> listener) {
        RoomTopicListResponse response = new RoomTopicListResponse();
        Context context = WKRoomApplication.getInstance().getContext();
        response.rooms = RoomTopicStore.loadRooms(context);
        RoomTopicStore.sortRooms(response.rooms);
        response.server_time = System.currentTimeMillis();
        if (listener != null) listener.onSuccess(response);
    }

    public void createRoom(String title, String tag, String language, IRequestResultListener<RoomTopicEntity> listener) {
        String safeTitle = title == null ? "" : title.trim();
        if (TextUtils.isEmpty(safeTitle)) {
            if (listener != null) listener.onFail(400, "请输入话题名");
            return;
        }

        UserInfoEntity userInfo = WKConfig.getInstance().getUserInfo();
        String uid = userInfo == null ? "" : userInfo.uid;
        if (TextUtils.isEmpty(uid)) uid = WKConfig.getInstance().getUid();
        String name = userInfo == null ? "" : (!TextUtils.isEmpty(userInfo.name) ? userInfo.name : userInfo.username);
        if (TextUtils.isEmpty(name)) name = WKConfig.getInstance().getUserName();
        if (TextUtils.isEmpty(name)) name = uid;

        ArrayList<String> members = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        if (!TextUtils.isEmpty(uid)) {
            members.add(uid);
            names.add(name);
        }

        GroupModel.getInstance().createGroup(safeTitle, members, names, (code, msg, groupEntity) -> {
            if (code == HttpResponseCode.success && groupEntity != null && !TextUtils.isEmpty(groupEntity.group_no)) {
                RoomTopicEntity room = RoomTopicStore.createLocalRoom(
                        WKRoomApplication.getInstance().getContext(),
                        safeTitle,
                        tag,
                        language,
                        groupEntity.group_no
                );
                room.channel_id = groupEntity.group_no;
                room.channel_type = WKChannelType.GROUP;
                RoomTopicStore.upsertRoom(WKRoomApplication.getInstance().getContext(), room);
                if (listener != null) listener.onSuccess(room);
            } else if (listener != null) {
                listener.onFail(code, TextUtils.isEmpty(msg) ? "创建话题群聊失败" : msg);
            }
        });
    }

    public void enterRoom(RoomTopicEntity room, IRequestResultListener<RoomTopicEntity> listener) {
        if (room == null || TextUtils.isEmpty(room.getChannelId())) {
            if (listener != null) listener.onFail(404, "话题不存在");
            return;
        }
        if (listener != null) listener.onSuccess(room);
    }

    public void pinRoom(RoomTopicEntity room, boolean pinned, IRequestResultListener<RoomTopicEntity> listener) {
        if (room == null) {
            if (listener != null) listener.onFail(400, "话题不存在");
            return;
        }
        room.pinned = pinned ? 1 : 0;
        RoomTopicStore.upsertRoom(WKRoomApplication.getInstance().getContext(), room);
        if (listener != null) listener.onSuccess(room);
    }

    public void deleteRoom(RoomTopicEntity room, IRequestResultListener<Object> listener) {
        RoomTopicStore.deleteRoom(WKRoomApplication.getInstance().getContext(), room);
        if (listener != null) listener.onSuccess(new Object());
    }
}
