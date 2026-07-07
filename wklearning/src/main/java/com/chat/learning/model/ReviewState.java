package com.chat.learning.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 单个单词的 SM-2 复习记录。
 *
 * wordId 必须和词库 JSON 里的稳定 id 一致。
 * nextReviewAt 是最高频查询字段，必须建索引，避免低端机全表扫描。
 */
@Entity(tableName = "review_state", indices = {@Index(value = "nextReviewAt")})
public class ReviewState {
    @PrimaryKey
    @NonNull
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
