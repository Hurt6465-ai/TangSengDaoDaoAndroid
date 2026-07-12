package com.chat.uikit.chat.manager;

public class PartnerPendingMessageResponse {
    public int status;
    public int code;
    public String msg;
    public String message;
    public int duplicate;
    public int partner_pending;
    public int requester_msg_count;
    public int max_message_count;
    public String client_msg_no;
    public String im_client_msg_no;
    public String message_id;
    public long message_seq;

    public boolean success() {
        return status == 200 || code == 200;
    }

    public String messageSafe() {
        if (msg != null && !msg.trim().isEmpty()) return msg;
        return message == null ? "" : message;
    }
}
