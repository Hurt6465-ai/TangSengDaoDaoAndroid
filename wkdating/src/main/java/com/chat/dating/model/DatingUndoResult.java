package com.chat.dating.model;

/** 服务端撤回滑动结果。 */
public class DatingUndoResult {
    public int status;
    public String action;
    public String target_uid;
    public DatingProfile restored_profile;
    public String message;
    public int like_remaining;
    public int favorite_remaining;
    public int rewind_remaining;
}
