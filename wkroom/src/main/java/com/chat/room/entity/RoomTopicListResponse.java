package com.chat.room.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RoomTopicListResponse implements Serializable {
    /** 兼容旧后端字段。 */
    public List<RoomTopicEntity> rooms;
    /** 兼容新后端字段。 */
    public List<RoomTopicEntity> list;
    /** 下一页游标。为空表示没有下一页或旧后端不支持分页。 */
    public String cursor;
    /** 新后端通常返回 1/0；这里用 Object 兼容 boolean、number、string 三种序列化结果。 */
    public Object has_more;
    public long server_time;

    public List<RoomTopicEntity> getRoomList() {
        if (rooms != null) return rooms;
        if (list != null) return list;
        return new ArrayList<>();
    }

    public boolean hasMore() {
        if (cursor == null || cursor.length() == 0) return false;
        if (has_more instanceof Boolean) return (Boolean) has_more;
        if (has_more instanceof Number) return ((Number) has_more).intValue() == 1;
        if (has_more instanceof String) {
            String value = ((String) has_more).trim();
            return "1".equals(value) || "true".equalsIgnoreCase(value);
        }
        return false;
    }
}
