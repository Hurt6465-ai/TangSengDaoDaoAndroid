package com.chat.learning;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the learning path from a bundled fallback or a remotely updated catalog.
 * Only data and media are remotely replaceable; executable code remains in the APK.
 */
final class LearningPathRepository {
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;
    private static final int MAX_COURSES = 48;
    private static final int MAX_UNITS_PER_COURSE = 120;
    private static final int MAX_LESSONS_PER_COURSE = 240;
    private static final int MAX_REQUIREMENTS = 16;
    private static final int MAX_TITLE_CHARS = 120;
    private static final int MAX_SUBTITLE_CHARS = 360;
    private static final String BUNDLED_CATALOG = "learning/path/catalog.json";

    interface RefreshCallback {
        void onUpdated(Catalog catalog);
        void onUnchanged();
        void onError(String message);
    }

    private LearningPathRepository() { }

    static Catalog load(Context context) {
        Context app = context.getApplicationContext();
        Catalog bundled = null;
        Catalog cached = null;
        try {
            bundled = parse(app, LearningRemoteContent.readAsset(app, BUNDLED_CATALOG));
            bundled.source = Source.BUNDLED;
        } catch (Throwable ignored) { }

        File cache = catalogCache(app);
        try {
            String value = LearningRemoteContent.readFile(cache, MAX_CATALOG_BYTES);
            if (!value.isEmpty()) {
                cached = parse(app, value);
                cached.source = Source.REMOTE_CACHE;
            }
        } catch (Throwable ignored) {
            LearningRemoteContent.deleteQuietly(cache);
        }

        if (cached == null) return bundled != null ? bundled : empty(app);
        if (bundled == null) return cached;
        int comparison = compareCatalogFreshness(cached, bundled);
        if (comparison < 0) {
            // An app update may ship a newer known-good map than an old disk cache.
            LearningRemoteContent.deleteQuietly(cache);
            return bundled;
        }
        return comparison > 0 ? cached : bundled;
    }

    static void refresh(Context context, Catalog current, RefreshCallback callback) {
        Context app = context.getApplicationContext();
        String remote = LearningRemoteContent.resolveUrl(app,
                LearningRemoteContent.config(app).learningPathCatalog);
        if (remote.isEmpty()) {
            safeUnchanged(callback);
            return;
        }
        LearningRemoteContent.execute(() -> {
            try {
                byte[] bytes = LearningRemoteContent.download(app, remote, MAX_CATALOG_BYTES);
                String text = new String(bytes, StandardCharsets.UTF_8);
                Catalog fresh = parse(app, text);
                fresh.source = Source.REMOTE_CACHE;
                int oldVersion = current == null ? 0 : current.version;
                if (fresh.version < oldVersion
                        || (current != null && compareCatalogFreshness(fresh, current) <= 0)) {
                    safeUnchanged(callback);
                    return;
                }
                LearningRemoteContent.atomicWrite(catalogCache(app), bytes);
                safeUpdated(callback, fresh);
            } catch (Throwable error) {
                safeError(callback, userMessage(error, "课程目录更新失败"));
            }
        });
    }

    private static int compareCatalogFreshness(Catalog left, Catalog right) {
        if (left == right) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        int version = Integer.compare(left.version, right.version);
        if (version != 0) return version;
        int updated = left.updatedAt.compareTo(right.updatedAt);
        if (updated != 0) return updated;
        return left.sourceHash.equals(right.sourceHash) ? 0 : -1;
    }

    static Course firstCourse(Catalog catalog) {
        return catalog == null || catalog.courses.isEmpty() ? null : catalog.courses.get(0);
    }

    static Course findCourse(Catalog catalog, String courseId) {
        if (catalog == null || courseId == null) return null;
        for (Course course : catalog.courses) if (courseId.equals(course.id)) return course;
        return null;
    }

    static Lesson findLesson(Course course, String lessonId) {
        if (course == null || lessonId == null) return null;
        for (Unit unit : course.units) {
            for (Lesson lesson : unit.lessons) if (lessonId.equals(lesson.id)) return lesson;
        }
        return null;
    }

    static List<Lesson> flatten(Course course) {
        ArrayList<Lesson> result = new ArrayList<>();
        if (course == null) return result;
        for (Unit unit : course.units) result.addAll(unit.lessons);
        return result;
    }

    private static Catalog parse(Context context, String json) throws Exception {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("Empty catalog");
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CATALOG_BYTES) {
            throw new IllegalArgumentException("Learning catalog is too large");
        }
        JSONObject root = new JSONObject(json);
        Catalog catalog = new Catalog();
        catalog.sourceHash = LearningRemoteContent.sha256(json);
        catalog.schemaVersion = Math.max(1, root.optInt("schema_version", 1));
        if (catalog.schemaVersion > 1) {
            throw new IllegalArgumentException("Unsupported learning catalog schema: "
                    + catalog.schemaVersion);
        }
        catalog.version = positive(root.optInt("version", 1), 1, Integer.MAX_VALUE);
        catalog.updatedAt = limited(root.optString("updated_at", ""), 80);
        if (!catalog.updatedAt.isEmpty() && !catalog.updatedAt.matches(
                "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")) {
            throw new IllegalArgumentException("Invalid catalog updated_at");
        }
        JSONArray courseArray = root.optJSONArray("courses");
        if (courseArray == null || courseArray.length() == 0) {
            throw new IllegalArgumentException("Learning catalog has no courses");
        }
        if (courseArray.length() > MAX_COURSES) {
            throw new IllegalArgumentException("Learning catalog has too many courses");
        }

        Set<String> courseIds = new HashSet<>();
        Map<String, PackageDescriptor> packages = new HashMap<>();
        for (int i = 0; i < courseArray.length(); i++) {
            JSONObject object = courseArray.optJSONObject(i);
            if (object == null) throw new IllegalArgumentException("Invalid course at index " + i);
            Course course = parseCourse(context, object, i, packages);
            if (!courseIds.add(course.id)) {
                throw new IllegalArgumentException("Duplicate course id: " + course.id);
            }
            catalog.courses.add(course);
        }
        if (catalog.courses.isEmpty()) throw new IllegalArgumentException("No valid courses");
        return catalog;
    }

    private static Course parseCourse(Context context, JSONObject object, int index,
                                      Map<String, PackageDescriptor> packages) throws Exception {
        Course course = new Course();
        course.id = requiredId(object.optString("id", "course_" + index), "course");
        course.title = localized(context, object, "title", "中文学习", MAX_TITLE_CHARS);
        course.subtitle = localized(context, object, "subtitle", "按学习路径逐步完成课程",
                MAX_SUBTITLE_CHARS);
        course.version = positive(object.optInt("version", 1), 1, Integer.MAX_VALUE);
        course.minAppVersion = positive(object.optInt("min_app_version", 0), 0, Integer.MAX_VALUE);
        course.accent = parseColor(object.optString("accent", ""), 0xFF635BFF);

        JSONArray units = object.optJSONArray("units");
        if (units == null || units.length() == 0) {
            throw new IllegalArgumentException("Course has no units: " + course.id);
        }
        if (units.length() > MAX_UNITS_PER_COURSE) {
            throw new IllegalArgumentException("Course has too many units: " + course.id);
        }

        Set<String> unitIds = new HashSet<>();
        Set<Integer> unitOrders = new HashSet<>();
        Set<String> lessonIds = new HashSet<>();
        int lessonCount = 0;
        for (int i = 0; i < units.length(); i++) {
            JSONObject unitObject = units.optJSONObject(i);
            if (unitObject == null) throw new IllegalArgumentException("Invalid unit at index " + i);
            Unit unit = parseUnit(context, course, unitObject, i, lessonIds, packages);
            if (!unitIds.add(unit.id)) throw new IllegalArgumentException("Duplicate unit id: " + unit.id);
            if (!unitOrders.add(unit.order)) {
                throw new IllegalArgumentException("Duplicate unit order: " + unit.order);
            }
            lessonCount += unit.lessons.size();
            if (lessonCount > MAX_LESSONS_PER_COURSE) {
                throw new IllegalArgumentException("Course has too many lessons: " + course.id);
            }
            course.units.add(unit);
        }
        Collections.sort(course.units, Comparator.comparingInt(value -> value.order));
        validateRequirements(course, lessonIds);
        return course;
    }

    private static Unit parseUnit(Context context, Course course, JSONObject object, int index,
                                  Set<String> lessonIds,
                                  Map<String, PackageDescriptor> packages) throws Exception {
        Unit unit = new Unit();
        unit.id = requiredId(object.optString("id", "unit_" + index), "unit");
        unit.title = localized(context, object, "title", "第 " + (index + 1) + " 单元",
                MAX_TITLE_CHARS);
        unit.subtitle = localized(context, object, "subtitle", "", MAX_SUBTITLE_CHARS);
        unit.order = object.has("order") ? object.optInt("order", index) : index;
        unit.accent = parseColor(object.optString("accent", ""), unitAccent(index, course.accent));
        unit.character = limited(object.optString("character", characterByIndex(index)), 24);
        JSONArray lessons = object.optJSONArray("lessons");
        if (lessons == null || lessons.length() == 0) {
            throw new IllegalArgumentException("Unit has no lessons: " + unit.id);
        }
        for (int i = 0; i < lessons.length(); i++) {
            JSONObject lessonObject = lessons.optJSONObject(i);
            if (lessonObject == null) throw new IllegalArgumentException("Invalid lesson at index " + i);
            Lesson lesson = parseLesson(context, course, unit, lessonObject, i);
            if (!lessonIds.add(lesson.id)) {
                throw new IllegalArgumentException("Duplicate lesson id: " + lesson.id);
            }
            validatePackageDescriptor(lesson, packages);
            unit.lessons.add(lesson);
        }
        return unit;
    }

    private static Lesson parseLesson(Context context, Course course, Unit unit,
                                      JSONObject object, int index) throws Exception {
        Lesson lesson = new Lesson();
        lesson.courseId = course.id;
        lesson.unitId = unit.id;
        lesson.id = requiredId(object.optString("id", unit.id + "_lesson_" + index), "lesson");
        lesson.title = localized(context, object, "title", "课程 " + (index + 1), MAX_TITLE_CHARS);
        lesson.subtitle = localized(context, object, "subtitle", "", MAX_SUBTITLE_CHARS);
        lesson.type = safeType(object.optString("type", "normal"));
        lesson.position = safePosition(object.optString("position", positionByIndex(index)));
        lesson.icon = limited(object.optString("icon", defaultIcon(lesson.type)), 8);
        if (lesson.icon.isEmpty()) lesson.icon = defaultIcon(lesson.type);
        lesson.exerciseCount = positive(object.optInt("exercise_count", 8), 1, 200);
        lesson.minutes = positive(object.optInt("minutes", 4), 1, 240);
        lesson.requiredLessons.addAll(idList(object.optJSONArray("required_lessons"),
                "required lesson", MAX_REQUIREMENTS));
        lesson.bundledLessonAsset = cleanRelativePath(
                object.optString("bundled_lesson_asset", ""), false);
        lesson.lessonFile = cleanRelativePath(object.optString("lesson_file", "lessons.json"), true);
        lesson.packageId = requiredId(object.optString("package_id", course.id + "_" + unit.id),
                "package");
        lesson.packageVersion = positive(object.optInt("package_version", 1), 1, Integer.MAX_VALUE);
        lesson.packageUrl = limited(object.optString("package_url", "").trim(), 2048);
        lesson.packageSha256 = object.optString("package_sha256", "").trim().toLowerCase();
        lesson.packageSize = Math.max(0L, object.optLong("package_size", 0L));
        lesson.forceUpdate = object.optBoolean("force_update", false); // Deprecated compatibility field.

        if (!lesson.isRewardNode()
                && lesson.bundledLessonAsset.isEmpty() && lesson.packageUrl.isEmpty()) {
            throw new IllegalArgumentException("Lesson has no bundled or remote content: " + lesson.id);
        }
        if (!lesson.packageUrl.isEmpty()) {
            if (lesson.packageUrl.regionMatches(true, 0, "http://", 0, 7)
                    || lesson.packageUrl.startsWith("//")) {
                throw new IllegalArgumentException("Remote lesson must use HTTPS: " + lesson.id);
            }
            if (!lesson.packageSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Remote lesson requires SHA-256: " + lesson.id);
            }
            if (lesson.packageSize <= 0L
                    || lesson.packageSize > LearningRemoteContent.config(context).maxPackageBytes) {
                throw new IllegalArgumentException("Invalid remote package size: " + lesson.id);
            }
            if (lesson.lessonFile.isEmpty()) {
                throw new IllegalArgumentException("Remote lesson requires lesson_file: " + lesson.id);
            }
        }
        return lesson;
    }

    private static void validatePackageDescriptor(Lesson lesson,
                                                  Map<String, PackageDescriptor> packages) {
        if (!lesson.needsRemotePackage()) return;
        String key = lesson.packageId;
        PackageDescriptor current = new PackageDescriptor(lesson);
        PackageDescriptor previous = packages.putIfAbsent(key, current);
        if (previous != null && !previous.sameAs(current)) {
            throw new IllegalArgumentException("Conflicting package metadata: " + key);
        }
    }

    private static void validateRequirements(Course course, Set<String> ids) {
        Set<String> earlierLessons = new HashSet<>();
        for (Unit unit : course.units) {
            for (Lesson lesson : unit.lessons) {
                for (String required : lesson.requiredLessons) {
                    if (!ids.contains(required)) {
                        throw new IllegalArgumentException("Unknown required lesson " + required
                                + " for " + lesson.id);
                    }
                    if (required.equals(lesson.id)) {
                        throw new IllegalArgumentException("Lesson cannot require itself: " + lesson.id);
                    }
                    if (!earlierLessons.contains(required)) {
                        throw new IllegalArgumentException("Required lesson must appear earlier: "
                                + required + " -> " + lesson.id);
                    }
                }
                earlierLessons.add(lesson.id);
            }
        }
    }

    private static Catalog empty(Context context) {
        Catalog catalog = new Catalog();
        catalog.source = Source.EMPTY;
        Course course = new Course();
        course.id = "zh_beginner";
        course.title = context.getString(R.string.learning_path_title);
        course.subtitle = context.getString(R.string.learning_path_empty);
        catalog.courses.add(course);
        return catalog;
    }

    private static File catalogCache(Context context) {
        return new File(context.getFilesDir(), "learning/path/catalog.json");
    }

    private static String localized(Context context, JSONObject object, String key, String fallback,
                                    int maxChars) {
        String suffix = localeSuffix(context);
        String value = suffix.isEmpty() ? "" : object.optString(key + suffix, "").trim();
        if (value.isEmpty()) value = object.optString(key, fallback).trim();
        if (value.isEmpty()) value = fallback;
        return limited(value, maxChars);
    }

    private static String localeSuffix(Context context) {
        java.util.Locale locale;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            locale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = context.getResources().getConfiguration().locale;
        }
        String language = locale == null ? "" : locale.getLanguage();
        if ("my".equalsIgnoreCase(language)) return "_my";
        if ("en".equalsIgnoreCase(language)) return "_en";
        return "";
    }

    private static List<String> idList(JSONArray array, String label, int max) {
        ArrayList<String> values = new ArrayList<>();
        if (array == null) return values;
        if (array.length() > max) throw new IllegalArgumentException("Too many " + label + " values");
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            String value = requiredId(array.optString(i, ""), label);
            if (unique.add(value)) values.add(value);
        }
        return values;
    }

    private static String requiredId(String raw, String label) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 1 || value.length() > 80
                || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")
                || value.endsWith(".") || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("Invalid " + label + " id: " + value);
        }
        return value;
    }

    private static String cleanRelativePath(String raw, boolean requireFile) {
        if (raw == null) return "";
        String value = raw.trim().replace('\\', '/');
        while (value.startsWith("/")) value = value.substring(1);
        if (value.isEmpty()) return "";
        if (value.length() > 512 || value.indexOf('\0') >= 0 || value.contains(":")) return "";
        String[] parts = value.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return "";
        }
        if (requireFile && value.endsWith("/")) return "";
        return value;
    }

    private static String safePosition(String raw) {
        return "left".equals(raw) || "right".equals(raw) ? raw : "center";
    }

    private static String safeType(String raw) {
        if ("review".equals(raw) || "practice".equals(raw) || "test".equals(raw)
                || "speaking".equals(raw) || "listening".equals(raw)
                || "story".equals(raw) || "chest".equals(raw)
                || "checkpoint".equals(raw) || "trophy".equals(raw)) return raw;
        return "normal";
    }

    private static String positionByIndex(int index) {
        int value = index % 4;
        if (value == 0) return "center";
        if (value == 1) return "right";
        if (value == 2) return "center";
        return "left";
    }

    private static String defaultIcon(String type) {
        if ("review".equals(type) || "practice".equals(type)) return "练";
        if ("test".equals(type) || "checkpoint".equals(type)) return "测";
        if ("trophy".equals(type)) return "奖";
        if ("speaking".equals(type)) return "●";
        if ("listening".equals(type)) return "◖))";
        if ("story".equals(type)) return "▤";
        if ("chest".equals(type)) return "◆";
        return "✓";
    }

    private static int unitAccent(int index, int courseAccent) {
        int[] palette = new int[]{
                0xFF58CC02, 0xFF1CB0F6, 0xFFCE82FF, 0xFFFF9600,
                0xFFFF4B4B, 0xFF2B70C9, 0xFF00B8A9, 0xFFE05FA8
        };
        int value = palette[Math.floorMod(index, palette.length)];
        return courseAccent == 0 ? value : blendColor(value, courseAccent, 0.12f);
    }

    private static String characterByIndex(int index) {
        String[] values = new String[]{"mei", "bo", "lin", "ya", "kai", "ning"};
        return values[Math.floorMod(index, values.length)];
    }

    private static int blendColor(int first, int second, float amount) {
        float value = Math.max(0f, Math.min(1f, amount));
        int r = (int) (android.graphics.Color.red(first) * (1f - value)
                + android.graphics.Color.red(second) * value);
        int g = (int) (android.graphics.Color.green(first) * (1f - value)
                + android.graphics.Color.green(second) * value);
        int b = (int) (android.graphics.Color.blue(first) * (1f - value)
                + android.graphics.Color.blue(second) * value);
        return android.graphics.Color.rgb(r, g, b);
    }

    private static int parseColor(String raw, int fallback) {
        try {
            String value = raw == null ? "" : raw.trim();
            if (value.matches("#[0-9a-fA-F]{6}")) {
                return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
            }
            if (value.matches("#[0-9a-fA-F]{8}")) {
                return (int) Long.parseLong(value.substring(1), 16);
            }
        } catch (Throwable ignored) { }
        return fallback;
    }

    private static String limited(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= max) return text;
        return text.substring(0, max);
    }

    private static int positive(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String userMessage(Throwable error, String fallback) {
        String value = error == null ? "" : error.getMessage();
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static void safeUpdated(RefreshCallback callback, Catalog catalog) {
        try { if (callback != null) callback.onUpdated(catalog); } catch (Throwable ignored) { }
    }

    private static void safeUnchanged(RefreshCallback callback) {
        try { if (callback != null) callback.onUnchanged(); } catch (Throwable ignored) { }
    }

    private static void safeError(RefreshCallback callback, String message) {
        try { if (callback != null) callback.onError(message); } catch (Throwable ignored) { }
    }

    enum Source { BUNDLED, REMOTE_CACHE, EMPTY }

    static final class Catalog {
        int schemaVersion = 1;
        int version = 1;
        String updatedAt = "";
        String sourceHash = "";
        Source source = Source.EMPTY;
        final List<Course> courses = new ArrayList<>();
    }

    static final class Course {
        String id = "";
        String title = "";
        String subtitle = "";
        int version = 1;
        int minAppVersion;
        int accent = 0xFF635BFF;
        final List<Unit> units = new ArrayList<>();
    }

    static final class Unit {
        String id = "";
        String title = "";
        String subtitle = "";
        int order;
        int accent = 0xFF58CC02;
        String character = "mei";
        final List<Lesson> lessons = new ArrayList<>();
    }

    static final class Lesson {
        String courseId = "";
        String unitId = "";
        String id = "";
        String title = "";
        String subtitle = "";
        String type = "normal";
        String position = "center";
        String icon = "✓";
        int exerciseCount = 8;
        int minutes = 4;
        final List<String> requiredLessons = new ArrayList<>();
        String bundledLessonAsset = "";
        String lessonFile = "lessons.json";
        String packageId = "";
        int packageVersion = 1;
        String packageUrl = "";
        String packageSha256 = "";
        long packageSize;
        boolean forceUpdate;

        boolean hasBundledContent() { return !bundledLessonAsset.isEmpty(); }
        boolean needsRemotePackage() { return !packageUrl.isEmpty(); }
        boolean isRewardNode() { return "trophy".equals(type) || "chest".equals(type); }
        String packageKey() { return packageId + "@" + packageVersion + "@" + packageSha256; }
    }

    private static final class PackageDescriptor {
        final String courseId;
        final String unitId;
        final int version;
        final String url;
        final String sha;
        final long size;

        PackageDescriptor(Lesson lesson) {
            courseId = lesson.courseId;
            unitId = lesson.unitId;
            version = lesson.packageVersion;
            url = lesson.packageUrl;
            sha = lesson.packageSha256;
            size = lesson.packageSize;
        }

        boolean sameAs(PackageDescriptor other) {
            return other != null && courseId.equals(other.courseId)
                    && unitId.equals(other.unitId) && version == other.version
                    && url.equals(other.url)
                    && sha.equals(other.sha) && size == other.size;
        }
    }
}
