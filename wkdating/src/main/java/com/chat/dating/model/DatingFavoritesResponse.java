package com.chat.dating.model;

import java.util.Collections;
import java.util.List;

public final class DatingFavoritesResponse {
    public List<DatingProfile> items;
    public List<DatingProfile> list;
    public int total;
    public long server_time;

    public List<DatingProfile> getItems() {
        if (items != null) return items;
        if (list != null) return list;
        return Collections.emptyList();
    }
}
