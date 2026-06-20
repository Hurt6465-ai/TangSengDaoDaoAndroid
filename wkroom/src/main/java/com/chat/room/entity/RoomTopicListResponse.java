package com.chat.room.entity;

import java.io.Serializable;
import java.util.List;

public class RoomTopicListResponse implements Serializable {
    public List<RoomTopicEntity> rooms;
    public long server_time;
}
