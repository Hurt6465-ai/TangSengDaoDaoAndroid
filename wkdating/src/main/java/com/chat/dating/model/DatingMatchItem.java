package com.chat.dating.model;

import java.io.Serializable;

public class DatingMatchItem implements Serializable {
    private static final long serialVersionUID = 1L;
    public String match_id;
    public int status;
    public long created_at;
    public long updated_at;
    public DatingProfile user;
}
