package com.chat.room.entity;

public class CreateRoomTopicRequest implements java.io.Serializable {
    public String title;
    public String tag;
    public String language;
    /** 同一次发布重试必须复用，服务端据此保证幂等。 */
    public String create_request_no;
}
