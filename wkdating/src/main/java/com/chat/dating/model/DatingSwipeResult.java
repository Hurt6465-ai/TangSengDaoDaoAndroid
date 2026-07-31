package com.chat.dating.model;

public class DatingSwipeResult {
    public boolean matched;
    public boolean match;
    public boolean notice_sent;
    public boolean system_notice_sent;
    public boolean can_chat;
    public boolean friend_created;
    public boolean friends_created;
    public String match_id;
    public String target_uid;
    public String message;

    public boolean isMatched() {
        return matched || match;
    }

    public boolean isFriendCreated() {
        return friend_created || friends_created;
    }

    public boolean isNoticeSent() {
        return notice_sent || system_notice_sent;
    }
}
