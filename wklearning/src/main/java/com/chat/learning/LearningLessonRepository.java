package com.chat.learning;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict parser for bundled and remotely installed interactive lesson files. */
final class LearningLessonRepository {
    static final int MAX_LESSON_BYTES = 2 * 1024 * 1024;
    private static final int MAX_EXERCISES = 120;
    private static final int MAX_OPTIONS = 12;
    private static final int MAX_WORDS = 40;
    private static final int MAX_PAIRS = 16;
    private static final int MAX_ACCEPTED_ANSWERS = 16;
    private static final int MAX_TEXT = 1200;
    private static final int MAX_SHORT_TEXT = 260;

    private LearningLessonRepository() { }

    static LessonData load(Context context, String expectedLessonId, String bundledAsset,
                           String installedFilePath) throws Exception {
        Throwable installedError = null;
        if (installedFilePath != null && !installedFilePath.trim().isEmpty()) {
            try {
                File file = verifiedInstalledFile(context, installedFilePath);
                String json = LearningRemoteContent.readFile(file, MAX_LESSON_BYTES);
                if (json.isEmpty()) {
                    throw new IllegalStateException("Lesson file is missing or too large");
                }
                LessonData data = parse(context, json, expectedLessonId);
                data.installedSource = true;
                return data;
            } catch (Throwable error) {
                installedError = error;
            }
        }
        if (bundledAsset != null && !bundledAsset.trim().isEmpty()) {
            String asset = cleanRelative(bundledAsset, true);
            if (asset.isEmpty()) throw new SecurityException("Invalid bundled lesson path");
            String json = LearningRemoteContent.readAsset(context, asset);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_LESSON_BYTES) {
                throw new IllegalStateException("Lesson file is too large");
            }
            LessonData data = parse(context, json, expectedLessonId);
            data.installedSource = false;
            return data;
        }
        if (installedError instanceof Exception) throw (Exception) installedError;
        if (installedError != null) throw new IllegalStateException(installedError);
        throw new IllegalStateException("Lesson content is missing");
    }

    static LessonData parse(Context context, String json, String expectedLessonId) throws Exception {
        if (context == null || json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Lesson content is empty");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_LESSON_BYTES) {
            throw new IllegalArgumentException("Lesson content is too large");
        }
        JSONObject root = new JSONObject(json);
        int schema = Math.max(1, root.optInt("schema_version", 1));
        if (schema > 1) throw new IllegalArgumentException("Unsupported lesson schema: " + schema);

        LessonData data = new LessonData();
        data.contentHash = LearningRemoteContent.sha256(json);
        data.schemaVersion = schema;
        data.lessonId = requiredId(root.optString("lesson_id", expectedLessonId), "lesson");
        String expected = expectedLessonId == null ? "" : expectedLessonId.trim();
        if (!expected.isEmpty() && !expected.equals(data.lessonId)) {
            throw new IllegalArgumentException("Lesson id mismatch");
        }
        data.title = localized(context, root, "title", "", 120);
        data.subtitle = localized(context, root, "subtitle", "", 360);
        data.passingScore = clamp(root.optInt("passing_score", 0), 0, 100);
        data.maxRetries = clamp(root.optInt("max_retries", 2), 0, 5);

        JSONArray exercises = root.optJSONArray("exercises");
        if (exercises == null || exercises.length() == 0) {
            throw new IllegalArgumentException("Lesson has no exercises");
        }
        if (exercises.length() > MAX_EXERCISES) {
            throw new IllegalArgumentException("Lesson has too many exercises");
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < exercises.length(); i++) {
            JSONObject object = exercises.optJSONObject(i);
            if (object == null) throw new IllegalArgumentException("Invalid exercise at index " + i);
            Exercise exercise = parseExercise(context, object, i);
            // The map lesson player intentionally excludes repeat-after-me questions. Keep parsing
            // the legacy schema so downloaded packs remain compatible, then ignore that type.
            if ("pronunciation".equals(exercise.type)) continue;
            if (!ids.add(exercise.id)) throw new IllegalArgumentException("Duplicate exercise id: " + exercise.id);
            data.exercises.add(exercise);
        }
        if (data.exercises.isEmpty()) {
            throw new IllegalArgumentException("Lesson has no supported exercises");
        }
        return data;
    }

    private static Exercise parseExercise(Context context, JSONObject object, int index) {
        Exercise value = new Exercise();
        value.id = requiredId(object.optString("id", "exercise_" + index), "exercise");
        value.knowledgeId = optionalId(object.optString("knowledge_id", ""), "knowledge");
        value.type = requiredType(object.optString("type", "single_choice"));
        value.question = localized(context, object, "question", defaultQuestion(context, value.type), MAX_TEXT);
        value.hint = localized(context, object, "hint", "", MAX_TEXT);
        value.text = limited(object.optString("text", ""), MAX_SHORT_TEXT);
        value.pinyin = limited(object.optString("pinyin", ""), MAX_SHORT_TEXT);
        value.audio = cleanRelative(object.optString("audio", ""), true);
        value.audioText = limited(object.optString("audio_text", value.text), MAX_SHORT_TEXT);
        value.answer = parseAnswer(object.opt("answer"));
        value.explanation = localized(context, object, "explanation", "", MAX_TEXT);
        value.placeholder = localized(context, object, "placeholder", "", MAX_SHORT_TEXT);
        value.originalSpeed = (float) Math.max(0.5, Math.min(1.5,
                object.optDouble("original_speed", 1.0)));
        if ("pronunciation".equals(value.type)) value.originalSpeed = 0.5f;
        value.keepOrder = object.optBoolean("keep_order", false);

        parseOptions(context, object.optJSONArray("options"), value.options);
        addStrings(value.words, object.optJSONArray("words"), MAX_WORDS, MAX_SHORT_TEXT);
        addStrings(value.acceptedAnswers, object.optJSONArray("accepted_answers"),
                MAX_ACCEPTED_ANSWERS, MAX_SHORT_TEXT);
        parsePairs(context, object.optJSONArray("pairs"), value.pairs);

        if ("true_false".equals(value.type) && value.options.isEmpty()) {
            String trueText = context.getString(R.string.learning_lesson_true);
            String falseText = context.getString(R.string.learning_lesson_false);
            value.options.add(new ChoiceOption(trueText, "true", "", trueText));
            value.options.add(new ChoiceOption(falseText, "false", "", falseText));
            if (object.has("answer_boolean")) {
                value.answer = object.optBoolean("answer_boolean", false)
                        ? value.options.get(0).value : value.options.get(1).value;
            } else if (isTruthy(value.answer)) {
                value.answer = value.options.get(0).value;
            } else if (isFalsy(value.answer)) {
                value.answer = value.options.get(1).value;
            }
        }
        if (object.has("answer_index") && !value.options.isEmpty()) {
            int answerIndex = object.optInt("answer_index", -1);
            if (answerIndex < 0 || answerIndex >= value.options.size()) {
                throw new IllegalArgumentException("Invalid answer_index in " + value.id);
            }
            value.answer = value.options.get(answerIndex).value;
        }
        if ("word_order".equals(value.type)) {
            Object answer = object.opt("answer");
            if (answer instanceof JSONArray) {
                addStrings(value.answerWords, (JSONArray) answer, MAX_WORDS, MAX_SHORT_TEXT);
                value.answer = join(value.answerWords);
            }
            if (value.answer.isEmpty()) {
                value.answerWords.addAll(value.words);
                value.answer = join(value.words);
            }
        }
        validateExercise(value);
        return value;
    }

    private static void validateExercise(Exercise value) {
        if (value.question.isEmpty()) throw new IllegalArgumentException("Exercise question is empty: " + value.id);
        switch (value.type) {
            case "single_choice":
            case "listen_choice":
            case "true_false":
            case "image_choice":
                validateChoices(value);
                if ("listen_choice".equals(value.type)
                        && value.audio.isEmpty() && value.audioText.isEmpty()) {
                    throw new IllegalArgumentException("Listening exercise has no audio: " + value.id);
                }
                if ("image_choice".equals(value.type)) {
                    boolean hasImage = false;
                    for (ChoiceOption option : value.options) hasImage |= !option.image.isEmpty();
                    if (!hasImage) throw new IllegalArgumentException("Image choice has no images: " + value.id);
                }
                break;
            case "fill_blank":
                requireAnswer(value);
                break;
            case "dictation":
                requireAnswer(value);
                if (value.audio.isEmpty() && value.audioText.isEmpty()) {
                    throw new IllegalArgumentException("Dictation has no audio: " + value.id);
                }
                break;
            case "word_order":
                if (value.words.size() < 2) throw new IllegalArgumentException("Word order needs at least two words: " + value.id);
                for (String word : value.words) {
                    if (normalize(word).isEmpty()) {
                        throw new IllegalArgumentException("Word order contains an empty token: " + value.id);
                    }
                }
                requireAnswer(value);
                if (!value.answerWords.isEmpty() && !sameTokenMultiset(value.words, value.answerWords)) {
                    throw new IllegalArgumentException("Word order answer does not use the same tokens: " + value.id);
                }
                if (value.answerWords.isEmpty() && !normalize(join(value.words)).equals(
                        normalize(value.answer))) {
                    throw new IllegalArgumentException("Word order answer cannot be built from the word bank: " + value.id);
                }
                break;
            case "matching":
                if (value.pairs.size() < 2) throw new IllegalArgumentException("Matching needs at least two pairs: " + value.id);
                break;
            case "pronunciation":
                if (value.text.isEmpty() && value.answer.isEmpty()) {
                    throw new IllegalArgumentException("Pronunciation target is empty: " + value.id);
                }
                if (value.answer.isEmpty()) value.answer = value.text;
                break;
            default:
                throw new IllegalArgumentException("Unsupported exercise type: " + value.type);
        }
    }

    private static void validateChoices(Exercise value) {
        if (value.options.size() < 2) throw new IllegalArgumentException("Choice exercise has too few options: " + value.id);
        Set<String> normalized = new HashSet<>();
        boolean answerFound = false;
        for (ChoiceOption option : value.options) {
            String key = normalize(option.value);
            if (key.isEmpty() || !normalized.add(key)) {
                throw new IllegalArgumentException("Choice options are empty or duplicated: " + value.id);
            }
            if (key.equals(normalize(value.answer))) answerFound = true;
        }
        if (!answerFound) throw new IllegalArgumentException("Choice answer is not in options: " + value.id);
    }

    private static void requireAnswer(Exercise value) {
        if (normalize(value.answer).isEmpty()) {
            throw new IllegalArgumentException("Exercise answer is empty: " + value.id);
        }
    }

    private static void parseOptions(Context context, JSONArray array, List<ChoiceOption> target) {
        if (array == null) return;
        if (array.length() > MAX_OPTIONS) throw new IllegalArgumentException("Too many choice options");
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (raw instanceof JSONObject) {
                JSONObject object = (JSONObject) raw;
                String text = localized(context, object, "text", object.optString("value", ""), MAX_SHORT_TEXT);
                String value = limited(object.optString("value", text), MAX_SHORT_TEXT);
                String image = cleanRelative(object.optString("image", ""), true);
                String description = localized(context, object, "content_description", text, MAX_SHORT_TEXT);
                if (value.isEmpty()) throw new IllegalArgumentException("Choice option value is empty");
                target.add(new ChoiceOption(text, value, image, description));
            } else {
                String value = limited(array.optString(i, ""), MAX_SHORT_TEXT);
                if (!value.isEmpty()) target.add(new ChoiceOption(value, value, "", value));
            }
        }
    }

    private static void parsePairs(Context context, JSONArray array, List<PairItem> target) {
        if (array == null) return;
        if (array.length() > MAX_PAIRS) throw new IllegalArgumentException("Too many matching pairs");
        Set<String> leftValues = new HashSet<>();
        Set<String> rightValues = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject pair = array.optJSONObject(i);
            if (pair == null) throw new IllegalArgumentException("Invalid matching pair at index " + i);
            String left = localized(context, pair, "left", "", MAX_SHORT_TEXT);
            String right = localized(context, pair, "right", "", MAX_SHORT_TEXT);
            String leftKey = normalize(left);
            String rightKey = normalize(right);
            if (leftKey.isEmpty() || rightKey.isEmpty()
                    || !leftValues.add(leftKey) || !rightValues.add(rightKey)) {
                throw new IllegalArgumentException("Matching pairs contain empty or duplicate values");
            }
            target.add(new PairItem(i, left, right));
        }
    }

    private static File verifiedInstalledFile(Context context, String rawPath) throws Exception {
        File root = new File(context.getFilesDir(), "learning/packages").getCanonicalFile();
        File file = new File(rawPath).getCanonicalFile();
        String rootPath = root.getPath() + File.separator;
        if (!file.getPath().startsWith(rootPath) || !file.isFile()) {
            throw new SecurityException("Lesson file is outside the installed package directory");
        }
        return file;
    }

    static String cleanRelative(String value, boolean requireFile) {
        if (value == null) return "";
        String clean = value.trim().replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isEmpty() || clean.length() > 512 || clean.indexOf('\0') >= 0 || clean.contains(":")) return "";
        String[] parts = clean.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return "";
        }
        if (requireFile && clean.endsWith("/")) return "";
        return clean;
    }

    static String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[\\s，。！？、,.!?;；:：'\"“”‘’()（）\\[\\]【】]", "")
                .toLowerCase(Locale.ROOT);
    }

    static String join(List<String> words) {
        StringBuilder builder = new StringBuilder();
        if (words != null) for (String word : words) builder.append(word == null ? "" : word);
        return builder.toString();
    }

    private static boolean sameTokenMultiset(List<String> left, List<String> right) {
        if (left.size() != right.size()) return false;
        ArrayList<String> values = new ArrayList<>();
        for (String value : left) values.add(normalize(value));
        for (String value : right) {
            String normalized = normalize(value);
            int index = values.indexOf(normalized);
            if (index < 0) return false;
            values.remove(index);
        }
        return values.isEmpty();
    }

    private static void addStrings(List<String> target, JSONArray array, int maxItems, int maxChars) {
        if (array == null) return;
        if (array.length() > maxItems) throw new IllegalArgumentException("Too many list items");
        for (int i = 0; i < array.length(); i++) {
            String item = limited(array.optString(i, ""), maxChars);
            if (!item.isEmpty()) target.add(item);
        }
    }

    private static String parseAnswer(Object object) {
        if (object instanceof JSONArray) {
            ArrayList<String> values = new ArrayList<>();
            addStrings(values, (JSONArray) object, MAX_WORDS, MAX_SHORT_TEXT);
            return join(values);
        }
        return object == null || object == JSONObject.NULL ? "" : limited(String.valueOf(object), MAX_TEXT);
    }

    private static String requiredType(String raw) {
        String type = raw == null ? "" : raw.trim();
        if ("single_choice".equals(type) || "listen_choice".equals(type)
                || "true_false".equals(type) || "word_order".equals(type)
                || "fill_blank".equals(type) || "matching".equals(type)
                || "pronunciation".equals(type) || "dictation".equals(type)
                || "image_choice".equals(type)) return type;
        throw new IllegalArgumentException("Unsupported exercise type: " + type);
    }

    private static String requiredId(String raw, String label) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 1 || value.length() > 96
                || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")
                || value.endsWith(".")) {
            throw new IllegalArgumentException("Invalid " + label + " id: " + value);
        }
        return value;
    }

    private static String optionalId(String raw, String label) {
        String value = raw == null ? "" : raw.trim();
        return value.isEmpty() ? "" : requiredId(value, label);
    }

    private static String localized(Context context, JSONObject object, String key, String fallback,
                                    int maxChars) {
        String suffix = localeSuffix(context);
        String value = suffix.isEmpty() ? "" : object.optString(key + suffix, "").trim();
        if (value.isEmpty()) value = object.optString(key, fallback).trim();
        return limited(value.isEmpty() ? fallback : value, maxChars);
    }

    private static String localeSuffix(Context context) {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = context.getResources().getConfiguration().locale;
        }
        String language = locale == null ? "" : locale.getLanguage();
        if ("my".equalsIgnoreCase(language)) return "_my";
        if ("en".equalsIgnoreCase(language)) return "_en";
        return "";
    }

    private static String defaultQuestion(Context context, String type) {
        if ("listen_choice".equals(type) || "dictation".equals(type)) {
            return context.getString(R.string.learning_lesson_q_listen);
        }
        if ("word_order".equals(type)) return context.getString(R.string.learning_lesson_q_order);
        if ("fill_blank".equals(type)) return context.getString(R.string.learning_lesson_q_fill);
        if ("matching".equals(type)) return context.getString(R.string.learning_lesson_q_matching);
        if ("pronunciation".equals(type)) return context.getString(R.string.learning_lesson_q_speak);
        return context.getString(R.string.learning_lesson_q_choice);
    }

    private static boolean isTruthy(String value) {
        String normalized = normalize(value);
        return "true".equals(normalized) || "1".equals(normalized)
                || "正确".equals(normalized) || "မှန်".equals(normalized);
    }

    private static boolean isFalsy(String value) {
        String normalized = normalize(value);
        return "false".equals(normalized) || "0".equals(normalized)
                || "错误".equals(normalized) || "မှား".equals(normalized);
    }

    private static String limited(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class LessonData {
        int schemaVersion = 1;
        String contentHash = "";
        String lessonId = "";
        String title = "";
        String subtitle = "";
        int passingScore;
        int maxRetries = 2;
        boolean installedSource;
        final List<Exercise> exercises = new ArrayList<>();
    }

    static final class Exercise {
        String id = "";
        String knowledgeId = "";
        String type = "single_choice";
        String question = "";
        String hint = "";
        String text = "";
        String pinyin = "";
        String audio = "";
        String audioText = "";
        String answer = "";
        String explanation = "";
        String placeholder = "";
        float originalSpeed = 1f;
        boolean keepOrder;
        final List<ChoiceOption> options = new ArrayList<>();
        final List<String> words = new ArrayList<>();
        final List<String> answerWords = new ArrayList<>();
        final List<String> acceptedAnswers = new ArrayList<>();
        final List<PairItem> pairs = new ArrayList<>();

        boolean accepts(String value) {
            if (normalize(value).equals(normalize(answer))) return true;
            for (String accepted : acceptedAnswers) {
                if (normalize(value).equals(normalize(accepted))) return true;
            }
            return false;
        }
    }

    static final class ChoiceOption {
        final String text;
        final String value;
        final String image;
        final String contentDescription;

        ChoiceOption(String text, String value, String image, String contentDescription) {
            this.text = text == null || text.trim().isEmpty() ? value : text;
            this.value = value == null ? "" : value;
            this.image = image == null ? "" : image;
            this.contentDescription = contentDescription == null
                    || contentDescription.trim().isEmpty() ? this.text : contentDescription;
        }
    }

    static final class PairItem {
        final int index;
        final String left;
        final String right;

        PairItem(int index, String left, String right) {
            this.index = index;
            this.left = left;
            this.right = right;
        }
    }
}
