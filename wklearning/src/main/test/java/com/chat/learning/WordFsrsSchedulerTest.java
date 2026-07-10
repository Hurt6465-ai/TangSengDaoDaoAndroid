package com.chat.learning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.chat.learning.WordFsrsScheduler.CardState;
import com.chat.learning.WordFsrsScheduler.Rating;
import com.chat.learning.WordFsrsScheduler.Result;
import com.chat.learning.WordFsrsScheduler.State;

import org.junit.Test;

import java.util.Map;

/** Golden-value tests generated from the current official py-fsrs FSRS-6 formulas. */
public class WordFsrsSchedulerTest {
    private static final long MINUTE = 60_000L;
    private static final long DAY = 86_400_000L;
    private static final long T0 = 1_000_000_000_000L;

    private final WordFsrsScheduler scheduler = new WordFsrsScheduler();

    @Test
    public void firstGoodUsesOfficialFsrs6Defaults() {
        Result result = scheduler.review(null, Rating.GOOD, T0);

        assertEquals(State.LEARNING, result.card.state);
        assertEquals(1, result.card.step);
        assertEquals(10L * MINUTE, result.intervalMillis);
        assertEquals(2.3065, result.card.stability, 1e-12);
        assertEquals(2.118103970459015, result.card.difficulty, 1e-12);
    }

    @Test
    public void firstEasyGraduatesAtEightDays() {
        Result result = scheduler.review(null, Rating.EASY, T0);

        assertEquals(State.REVIEW, result.card.state);
        assertEquals(-1, result.card.step);
        assertEquals(8L * DAY, result.intervalMillis);
        assertEquals(8.2956, result.card.stability, 1e-12);
        assertEquals(1.0, result.card.difficulty, 1e-12);
    }

    @Test
    public void secondGoodSameDayGraduatesWithOfficialValues() {
        Result first = scheduler.review(null, Rating.GOOD, T0);
        Result second = scheduler.review(first.card, Rating.GOOD, first.card.dueAt);

        assertEquals(State.REVIEW, second.card.state);
        assertEquals(2L * DAY, second.intervalMillis);
        assertEquals(2.3065, second.card.stability, 1e-12);
        assertEquals(2.1112142357853942, second.card.difficulty, 1e-12);
    }

    @Test
    public void onTimeGoodReviewMatchesOfficialGoldenValues() {
        Result first = scheduler.review(null, Rating.GOOD, T0);
        Result graduated = scheduler.review(first.card, Rating.GOOD, first.card.dueAt);
        Result review = scheduler.review(graduated.card, Rating.GOOD, graduated.card.dueAt);

        assertEquals(State.REVIEW, review.card.state);
        assertEquals(11L * DAY, review.intervalMillis);
        assertEquals(10.971048263078137, review.card.stability, 1e-11);
        assertEquals(2.1043313908464474, review.card.difficulty, 1e-12);
    }

    @Test
    public void againFromReviewEntersRelearning() {
        Result first = scheduler.review(null, Rating.GOOD, T0);
        Result graduated = scheduler.review(first.card, Rating.GOOD, first.card.dueAt);
        Result review = scheduler.review(graduated.card, Rating.GOOD, graduated.card.dueAt);
        Result lapse = scheduler.review(review.card, Rating.AGAIN, review.card.dueAt);

        assertEquals(State.RELEARNING, lapse.card.state);
        assertEquals(0, lapse.card.step);
        assertEquals(10L * MINUTE, lapse.intervalMillis);
        assertEquals(1, lapse.card.lapseCount);
        assertEquals(1.5390125302814703, lapse.card.stability, 1e-11);
        assertEquals(7.389975788014609, lapse.card.difficulty, 1e-11);
    }

    @Test
    public void previewDoesNotMutateSource() {
        Result first = scheduler.review(null, Rating.EASY, T0);
        CardState source = first.card;
        int reviewCount = source.reviewCount;
        long dueAt = source.dueAt;
        double stability = source.stability;

        Map<Rating, Result> preview = scheduler.preview(source, dueAt);

        assertEquals(4, preview.size());
        assertEquals(reviewCount, source.reviewCount);
        assertEquals(dueAt, source.dueAt);
        assertEquals(stability, source.stability, 0.0);
        for (Rating rating : Rating.values()) {
            assertNotNull(preview.get(rating));
        }
    }

    @Test
    public void retrievabilityUsesFullElapsedDays() {
        Result first = scheduler.review(null, Rating.EASY, T0);
        CardState card = first.card;

        double beforeOneDay = scheduler.getRetrievability(card, T0 + DAY - 1L);
        double atOneDay = scheduler.getRetrievability(card, T0 + DAY);

        assertEquals(1.0, beforeOneDay, 1e-12);
        assertTrue(atOneDay < 1.0);
        assertFalse(Double.isNaN(atOneDay));
    }
}
