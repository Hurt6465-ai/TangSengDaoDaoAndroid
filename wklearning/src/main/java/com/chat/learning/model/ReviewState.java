package com.chat.learning.model;

/** 无 Room 依赖的复习状态模型，避免旧版 Room 文件残留导致编译失败。 */
public class ReviewState {
    public String wordId = "";
    public double easeFactor = 2.5d;
    public int repetitions = 0;
    public int intervalDays = 0;
    public int lastQuality = -1;
    public long lastReviewAt = 0L;
    public long nextReviewAt = 0L;
    public int reviewCount = 0;
    public int lapseCount = 0;

    public boolean isDue(long now) {
        return nextReviewAt <= 0L || nextReviewAt <= now;
    }
}
