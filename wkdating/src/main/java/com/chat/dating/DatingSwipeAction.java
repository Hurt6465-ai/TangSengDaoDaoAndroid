package com.chat.dating;

public final class DatingSwipeAction {
    private DatingSwipeAction() {}
    public static final String LIKE = "like";
    public static final String PASS = "pass";
    /**
     * 当前后端还没有独立收藏表，heart 会被后端 normalize 成 like，
     * 这样下拉收藏/星标不会破坏“互相喜欢后才聊天”的闭环。
     * 后面如果后端新增 favorite/super_like，再把这里改成对应 action 即可。
     */
    public static final String FAVORITE = "heart";
}
