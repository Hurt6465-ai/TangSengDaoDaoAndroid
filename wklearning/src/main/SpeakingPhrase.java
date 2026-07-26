package com.chat.learning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable Chinese speaking-card content. */
final class SpeakingPhrase {
    final String packId;
    final String id;
    final String text;
    final String pinyin;
    final String ttsPinyin;
    final String meaningMy;
    final String scene;
    final String sceneMy;
    final String sceneEn;
    final List<Breakdown> breakdown;
    final List<Variant> replacements;
    final List<Variant> alternatives;

    SpeakingPhrase(
            String packId,
            String id,
            String text,
            String pinyin,
            String ttsPinyin,
            String meaningMy,
            String scene,
            String sceneMy,
            String sceneEn,
            List<Breakdown> breakdown,
            List<Variant> replacements,
            List<Variant> alternatives
    ) {
        this.packId = safe(packId);
        this.id = safe(id);
        this.text = safe(text);
        this.pinyin = safe(pinyin);
        this.ttsPinyin = safe(ttsPinyin).length() > 0 ? safe(ttsPinyin) : this.pinyin;
        this.meaningMy = safe(meaningMy);
        this.scene = safe(scene);
        this.sceneMy = safe(sceneMy);
        this.sceneEn = safe(sceneEn);
        this.breakdown = immutableBreakdown(breakdown);
        this.replacements = immutableVariants(replacements);
        this.alternatives = immutableVariants(alternatives);
    }

    String progressKey() {
        return id.length() > 0 ? id : text;
    }

    private static List<Variant> immutableVariants(List<Variant> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static List<Breakdown> immutableBreakdown(List<Breakdown> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Breakdown {
        final String text;
        final String pinyin;
        final String meaningMy;

        Breakdown(String text, String pinyin, String meaningMy) {
            this.text = safe(text);
            this.pinyin = safe(pinyin);
            this.meaningMy = safe(meaningMy);
        }
    }

    static final class Variant {
        final String text;
        final String pinyin;
        final String ttsPinyin;
        final String meaningMy;
        final String label;

        Variant(String text, String pinyin, String ttsPinyin, String meaningMy, String label) {
            this.text = safe(text);
            this.pinyin = safe(pinyin);
            this.ttsPinyin = safe(ttsPinyin).length() > 0 ? safe(ttsPinyin) : this.pinyin;
            this.meaningMy = safe(meaningMy);
            this.label = safe(label);
        }
    }
}
