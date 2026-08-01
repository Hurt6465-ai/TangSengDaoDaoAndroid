package com.chat.partnerbrowse.model;

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
    // Null means an older server omitted the role field.
    public Boolean requester;
    public String text;
    public long last_greet_at;
    public String message_id;
    public String client_msg_no;
    public int message_seq;
    public long timestamp;

    public boolean isSuccessOrAlreadySent() {
        return status == 200 || code == 200;
    }

    public String getMessageSafe() {
        if (!TextUtils.isEmpty(msg)) return msg;
        if (!TextUtils.isEmpty(message)) return message;
        return "";
    }
}
