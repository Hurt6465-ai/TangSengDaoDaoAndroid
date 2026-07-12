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

    public boolean success() {
        return status == 1 || status == 200 || code == 200 || hello_sent == 1 || greeting_status == 1 || requester_msg_count > 0;
    }

    public String messageSafe() {
        if (!TextUtils.isEmpty(msg)) return msg;
        if (!TextUtils.isEmpty(message)) return message;
        return "";
    }
}
