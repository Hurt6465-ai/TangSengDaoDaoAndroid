package com.chat.learning.review;

import com.chat.learning.model.ReviewState;
import com.chat.learning.model.WordItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 构建全屏背单词队列：先到期旧词，再补新词。
 * 不能只取到期词，否则新用户第一次进来是空队列。
 */
public class ReviewQueueBuilder {
    private static final int DEFAULT_SESSION_SIZE = 20;
    private static final int DEFAULT_MAX_NEW = 10;

    private final LearningReviewStore store;

    public ReviewQueueBuilder(LearningReviewStore store) {
        this.store = store;
    }

    public List<WordItem> build(List<WordItem> candidates) {
        return build(candidates, DEFAULT_SESSION_SIZE, DEFAULT_MAX_NEW);
    }

    public List<WordItem> build(List<WordItem> candidates, int sessionSize, int maxNew) {
        List<WordItem> queue = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) return queue;

        Map<String, WordItem> wordMap = new HashMap<>();
        ArrayList<String> allIds = new ArrayList<>();
        for (WordItem word : candidates) {
            if (word == null || word.id == null || word.id.length() == 0) continue;
            wordMap.put(word.id, word);
            allIds.add(word.id);
        }

        List<ReviewState> due = store.getDueWordsByIdsSync(allIds, sessionSize);
        Set<String> dueIds = new HashSet<>();
        for (ReviewState state : due) {
            WordItem word = wordMap.get(state.wordId);
            if (word != null && queue.size() < sessionSize) {
                queue.add(word);
                dueIds.add(state.wordId);
            }
        }

        int newQuota = Math.min(maxNew, sessionSize - queue.size());
        if (newQuota > 0) {
            Set<String> existing = new HashSet<>(store.findExistingIdsSync(allIds));
            int added = 0;
            for (WordItem word : candidates) {
                if (word == null || word.id == null) continue;
                if (added >= newQuota || queue.size() >= sessionSize) break;
                if (!existing.contains(word.id) && !dueIds.contains(word.id)) {
                    queue.add(word);
                    added++;
                }
            }
        }
        return queue;
    }
}
