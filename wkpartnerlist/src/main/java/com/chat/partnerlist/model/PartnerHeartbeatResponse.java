package com.chat.partnerlist.model;

public class PartnerHeartbeatResponse {
    public String uid;
    public long last_active_at;
    public long online_expire_at;
    public int next_heartbeat_in_seconds;
}
