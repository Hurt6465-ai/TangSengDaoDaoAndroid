package com.chat.partnerbrowse.model;

import android.text.TextUtils;

public class PartnerGreetingResponse {
    public int status;
    public int code;
    public String msg;
    public String message;
    public boolean hello_sent;
    public int greeting_status;
    public long next_allowed_at;

    public boolean isSuccessOrAlreadySent() {
        return status == 1 || status == 200 || code == 200 || hello_sent || greeting_status == 1;
    }

    public String getMessageSafe() {
        if (!TextUtils.isEmpty(msg)) return msg;
        if (!TextUtils.isEmpty(message)) return message;
        return "";
    }
}
