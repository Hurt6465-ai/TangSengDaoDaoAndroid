package com.chat.learning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable word-card content model. */
final class WordItem {
    final String packId;
    final String id;
    final String word;
    final String pinyin;
    final String ttsPinyin;
    final String phoneticMy;
    final String partOfSpeech;
    final String meaningMy;
    final String usageSceneMy;
    final String memoryTip;
    final String example;
    final String examplePinyin;
    final String exampleMy;
    final String notesMy;
    final List<String> synonyms;
    final List<String> antonyms;
    final List<String> collocations;
    final String audioOverride;
    final String exampleAudioOverride;

    WordItem(
            String packId,
            String id,
            String word,
            String pinyin,
            String ttsPinyin,
            String phoneticMy,
            String partOfSpeech,
            String meaningMy,
            String usageSceneMy,
            String memoryTip,
            String example,
            String examplePinyin,
            String exampleMy,
            String notesMy,
            List<String> synonyms,
            List<String> antonyms,
            List<String> collocations,
            String audioOverride,
            String exampleAudioOverride) {
        this.packId = safe(packId);
        this.id = safe(id);
        this.word = safe(word);
        this.pinyin = safe(pinyin);
        this.ttsPinyin = safe(ttsPinyin);
        this.phoneticMy = safe(phoneticMy);
        this.partOfSpeech = safe(partOfSpeech);
        this.meaningMy = safe(meaningMy);
        this.usageSceneMy = safe(usageSceneMy);
        this.memoryTip = safe(memoryTip);
        this.example = safe(example);
        this.examplePinyin = safe(examplePinyin);
        this.exampleMy = safe(exampleMy);
        this.notesMy = safe(notesMy);
        this.synonyms = immutable(synonyms);
        this.antonyms = immutable(antonyms);
        this.collocations = immutable(collocations);
        this.audioOverride = safe(audioOverride);
        this.exampleAudioOverride = safe(exampleAudioOverride);
    }

    String progressKey() {
        return packId + ":" + id;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> immutable(List<String> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
