package com.chat.uikit.enity;

/**
 * 黑名单列表用户。
 * 后端旧字段把 username 写成 usename，这里同时兼容 username / usename。
 */
public class BlacklistUser {
    public String uid;
    public String name;
    public String username;
    public String usename;
    public String country_code;
    public String country;
    public int is_upload_avatar;

    public String displayName() {
        if (name != null && name.trim().length() > 0) return name.trim();
        if (username != null && username.trim().length() > 0) return username.trim();
        if (usename != null && usename.trim().length() > 0) return usename.trim();
        return uid == null ? "" : uid;
    }

    public String displayUsername() {
        if (username != null && username.trim().length() > 0) return username.trim();
        if (usename != null && usename.trim().length() > 0) return usename.trim();
        return "";
    }
}
