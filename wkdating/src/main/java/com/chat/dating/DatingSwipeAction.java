package com.chat.dating;

public final class DatingSwipeAction {
    private DatingSwipeAction() {}

    public static final String LIKE = "like";
    public static final String PASS = "pass";

    /**
     * 收藏是“稍后看”，不能触发匹配。
     * 当前旧后端会把未知 action 归一为 pass，因此不会误触发 match；
     * 新后端增加 dating_favorites 后可原样识别 favorite。
     */
    public static final String FAVORITE = "favorite";
}
