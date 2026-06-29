package com.chat.partnerbrowse.model;

import java.util.ArrayList;
import java.util.List;

public class PartnerBrowseResponse {
    public List<PartnerBrowseBean> users;
    public List<PartnerBrowseBean> partners;
    public List<PartnerBrowseBean> list;
    public String cursor;
    public String session_id;
    public int has_more;
    public long server_time;

    public List<PartnerBrowseBean> getListSafe() {
        if (users != null) return users;
        if (partners != null) return partners;
        if (list != null) return list;
        return new ArrayList<>();
    }
}
