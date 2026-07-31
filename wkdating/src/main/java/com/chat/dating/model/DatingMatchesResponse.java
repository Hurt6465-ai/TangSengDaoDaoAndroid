package com.chat.dating.model;

import java.util.Collections;
import java.util.List;

public class DatingMatchesResponse {
    public List<DatingMatchItem> list;
    public List<DatingMatchItem> matches;
    public long server_time;

    public List<DatingMatchItem> getItems() {
        if (list != null) return list;
        if (matches != null) return matches;
        return Collections.emptyList();
    }
}
