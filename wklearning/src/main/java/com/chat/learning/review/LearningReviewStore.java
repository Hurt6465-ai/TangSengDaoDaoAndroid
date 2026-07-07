package com.chat.learning.review;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.chat.learning.model.ReviewState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 复习记录仓储。UI 层只调用这个类，不直接碰 DAO。
 * 所有 Room IO 统一走单线程后台队列，避免低端机卡顿和并发写冲突。
 */
public class LearningReviewStore {
    private final ReviewStateDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public LearningReviewStore(Context context) {
        dao = LearningDatabase.get(context).reviewStateDao();
    }

    public ReviewStateDao dao() {
        return dao;
    }

    public void submitReview(String wordId, int quality, ReviewCallback callback) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            ReviewState old = dao.findById(wordId);
            ReviewState next = Sm2Scheduler.schedule(old, wordId, quality, now);
            dao.upsert(next);
            if (callback != null) main.post(() -> callback.onDone(next));
        });
    }

    public void countDue(CountCallback callback) {
        io.execute(() -> {
            int count = dao.countDue(System.currentTimeMillis());
            if (callback != null) main.post(() -> callback.onCount(count));
        });
    }

    public List<ReviewState> getDueWordsSync(int limit) {
        return dao.findDueWords(System.currentTimeMillis(), limit);
    }

    public List<ReviewState> getDueWordsByIdsSync(List<String> ids, int limit) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        return dao.findDueWordsByIds(ids, System.currentTimeMillis(), limit);
    }

    public List<String> findExistingIdsSync(List<String> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        return dao.findExistingIds(ids);
    }

    public void runIo(Runnable runnable) {
        io.execute(runnable);
    }

    public void postMain(Runnable runnable) {
        main.post(runnable);
    }

    public interface ReviewCallback {
        void onDone(ReviewState newState);
    }

    public interface CountCallback {
        void onCount(int dueCount);
    }
}
