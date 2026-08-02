package com.chat.learning;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LessonSessionPolicyTest {
    @Test
    public void matchingMistakePreventsFirstAttemptCredit() {
        assertTrue(LessonSessionPolicy.matchingCorrect(false));
        assertFalse(LessonSessionPolicy.matchingCorrect(true));
    }

    @Test
    public void masteringAllQuestionsPassesEvenAfterCorrections() {
        assertTrue(LessonSessionPolicy.passed(10, 10));
        assertFalse(LessonSessionPolicy.passed(9, 10));
        assertEquals(1, LessonSessionPolicy.stars(40, true, 80));
        assertEquals(2, LessonSessionPolicy.stars(80, true, 80));
        assertEquals(3, LessonSessionPolicy.stars(90, true, 80));
    }

    @Test
    public void deterministicShuffleIsStableWithinSession() {
        List<String> first = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        List<String> second = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        LessonSessionPolicy.shuffle(first, 42L, "exercise", "choices");
        LessonSessionPolicy.shuffle(second, 42L, "exercise", "choices");
        assertEquals(first, second);
    }

    @Test
    public void scoreIsClamped() {
        assertEquals(0, LessonSessionPolicy.firstAttemptScore(-1, 10));
        assertEquals(50, LessonSessionPolicy.firstAttemptScore(5, 10));
        assertEquals(100, LessonSessionPolicy.firstAttemptScore(20, 10));
    }

    @Test
    public void ttsTextIsLimitedToListeningExercises() {
        assertEquals("", LessonSessionPolicy.ttsText(
                "single_choice", "", "", "正确答案"));
        assertEquals("你好", LessonSessionPolicy.ttsText(
                "listen_choice", " 你好 ", "备用文本", "备用答案"));
        assertEquals("八点半", LessonSessionPolicy.ttsText(
                "dictation", "", "八点半", "备用答案"));
        assertEquals("现在九点", LessonSessionPolicy.ttsText(
                "dictation", "", "", "现在九点"));
    }
}
