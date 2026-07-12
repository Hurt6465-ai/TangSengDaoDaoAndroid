package com.chat.uikit.chat.manager;

public class PartnerPendingMessageResponse {
    public int status;
    public int code;
    public String msg;
    public String message;
    public int duplicate;

    // Backend wire type is JSON boolean. Boolean (instead of primitive boolean)
    // lets old servers omit the field without falsely treating the relation as active.
    public Boolean partner_pending;
    public int contact_status = -1;
    public int requester_msg_count;
    public int max_message_count;
    public String client_msg_no;
    public String im_client_msg_no;
    public String message_id;
    public int message_seq;
    public long timestamp;

    public boolean success() {
        return status == 200 || code == 200;
    }

    public boolean hasRelationshipState() {
        return contact_status >= 0 || partner_pending != null;
    }

    public boolean isActiveRelationship() {
        return contact_status == 1 || Boolean.FALSE.equals(partner_pending);
    }

    public String messageSafe() {
        if (msg != null && !msg.trim().isEmpty()) return msg;
        return message == null ? "" : message;
    }
}
