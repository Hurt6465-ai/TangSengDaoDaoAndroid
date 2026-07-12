package com.chat.partnerlist.model;

import android.text.TextUtils;

public class PartnerGreetingResponse {
    public int status;
    public int code;
    public String msg;
    public String message;
    public int hello_sent;
    public int greeting_status;
    public long next_allowed_at;
    public int requester_msg_count;
    public int max_greeting_count;
    public int contact_status;
    public int greeting_day_limit;
    public int greeting_day_used;
    public int greeting_day_remaining;
    public String message_id;
    public String client_msg_no;
    public int message_seq;
    public long timestamp;

    public boolean success() {
        return status == 200 || code == 200;
    }

    public String messageSafe() {
        if (!TextUtils.isEmpty(msg)) return msg;
        if (!TextUtils.isEmpty(message)) return message;
        return "";
    }
}
