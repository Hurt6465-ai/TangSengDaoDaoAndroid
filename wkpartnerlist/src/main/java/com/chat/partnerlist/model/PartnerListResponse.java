package com.chat.partnerlist.model;

import java.util.ArrayList;
import java.util.List;

public class PartnerListResponse {
    public String day_key;
    public int algorithm_version;
    public int list_version;
    public long first_served_at;
    public long rotate_at;
    public boolean rotation_done;
    public long rotation_retry_at;
    public int updated_count;
    public int unique_assigned_count;
    public int daily_candidate_limit;
    public int greeting_limit;
    public int greeting_used;
    public int greeting_remaining;
    public List<String> added_user_ids;
    public List<String> removed_user_ids;
    public List<PartnerListUser> users;
    public long server_time;

    public List<PartnerListUser> usersSafe() {
        return users == null ? new ArrayList<>() : users;
    }
}
