package com.chat.learning;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AnswerTextNormalizerTest {
    @Test
    public void chineseAnswersIgnoreOptionalSpacesAndPunctuation() {
        assertTrue(AnswerTextNormalizer.sameAnswer("八 点 半！", "八点半"));
    }

    @Test
    public void latinAnswersKeepWordBoundaries() {
        assertFalse(AnswerTextNormalizer.sameAnswer("a nice cream", "an ice cream"));
        assertTrue(AnswerTextNormalizer.sameAnswer("  I am a student. ", "i am a student"));
        assertTrue(AnswerTextNormalizer.sameAnswer("I'm ready", "im ready"));
    }

    @Test
    public void fullWidthCharactersAreCanonicalized() {
        assertEquals("abc123", AnswerTextNormalizer.normalizeAnswer("ＡＢＣ１２３"));
    }

    @Test
    public void tokenSequenceRequiresCorrectOrder() {
        assertTrue(AnswerTextNormalizer.sameTokenSequence(
                Arrays.asList("我", "学习", "中文"),
                Arrays.asList("我", "学习", "中文")));
        assertFalse(AnswerTextNormalizer.sameTokenSequence(
                Arrays.asList("中文", "学习", "我"),
                Arrays.asList("我", "学习", "中文")));
    }
}
