package com.chat.partnerlist.model;

import java.util.ArrayList;
import java.util.List;

public class PartnerOnlineBatchResponse {
    public List<PartnerOnlineState> users;
    public long server_time;

    public List<PartnerOnlineState> usersSafe() {
        return users == null ? new ArrayList<>() : users;
    }
}
