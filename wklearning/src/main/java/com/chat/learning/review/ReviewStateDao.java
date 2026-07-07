package com.chat.learning.review;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.chat.learning.model.ReviewState;

import java.util.List;

@Dao
public interface ReviewStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ReviewState state);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ReviewState> states);

    @Query("SELECT * FROM review_state WHERE wordId = :wordId LIMIT 1")
    ReviewState findById(String wordId);

    @Query("SELECT * FROM review_state WHERE nextReviewAt <= :now ORDER BY nextReviewAt ASC LIMIT :limit")
    List<ReviewState> findDueWords(long now, int limit);

    @Query("SELECT * FROM review_state WHERE wordId IN (:wordIds) AND nextReviewAt <= :now ORDER BY nextReviewAt ASC LIMIT :limit")
    List<ReviewState> findDueWordsByIds(List<String> wordIds, long now, int limit);

    @Query("SELECT COUNT(*) FROM review_state WHERE nextReviewAt <= :now")
    int countDue(long now);

    @Query("SELECT wordId FROM review_state WHERE wordId IN (:wordIds)")
    List<String> findExistingIds(List<String> wordIds);
}
