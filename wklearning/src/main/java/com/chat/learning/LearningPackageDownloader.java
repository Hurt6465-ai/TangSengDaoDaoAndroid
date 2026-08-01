package com.chat.learning;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Resumable, checksum-verified and zip-slip-safe course package installer. */
final class LearningPackageDownloader {
    private static final long DEFAULT_MAX_PACKAGE_BYTES = 200L * 1024L * 1024L;
    private static final long DEFAULT_MAX_UNPACKED_BYTES = 500L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 80L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 5000;
    private static final int MAX_REDIRECTS = 5;
    private static final ConcurrentHashMap<String, Task> TASKS = new ConcurrentHashMap<>();

    enum State { DOWNLOADING, VERIFYING, INSTALLING, READY, ERROR }

    interface Callback {
        void onState(State state, int progress, String message);
    }

    interface Subscription {
        void cancel();
    }

    private static final Subscription EMPTY_SUBSCRIPTION = () -> { };

    static final class Status {
        final State state;
        final int progress;
        final String message;

        Status(State state, int progress, String message) {
            this.state = state;
            this.progress = progress;
            this.message = message == null ? "" : message;
        }

        boolean active() {
            return state == State.DOWNLOADING || state == State.VERIFYING || state == State.INSTALLING;
        }
    }

    private LearningPackageDownloader() { }

    static Status status(LearningPathRepository.Lesson lesson) {
        if (lesson == null) return null;
        Task task = TASKS.get(lesson.packageKey());
        return task == null ? null : task.snapshot();
    }

    static boolean isInstalled(Context context, LearningPathRepository.Lesson lesson) {
        if (context == null || lesson == null || lesson.packageId.isEmpty()) return false;
        File directory = installDirectory(context, lesson);
        if (!directory.isDirectory()) return false;
        File manifest = new File(directory, "manifest.json");
        File marker = new File(directory, ".install.json");
        File lessonFile = safeInstalledChild(directory, lesson.lessonFile);
        if (!manifest.isFile() || !marker.isFile() || lessonFile == null || !lessonFile.isFile()) return false;
        try {
            JSONObject packageManifest = new JSONObject(readSmallFile(manifest, 512 * 1024));
            JSONObject installMarker = new JSONObject(readSmallFile(marker, 128 * 1024));
            return lesson.packageId.equals(packageManifest.optString("package_id", ""))
                    && lesson.packageVersion == packageManifest.optInt("version", 0)
                    && lesson.courseId.equals(packageManifest.optString("course_id", ""))
                    && lesson.unitId.equals(packageManifest.optString("unit_id", ""))
                    && lesson.packageId.equals(installMarker.optString("package_id", ""))
                    && lesson.packageVersion == installMarker.optInt("version", 0)
                    && lesson.courseId.equals(installMarker.optString("course_id", ""))
                    && lesson.unitId.equals(installMarker.optString("unit_id", ""))
                    && lesson.packageSize == Math.max(0L,
                            installMarker.optLong("package_size", 0L))
                    && lesson.packageSha256.equalsIgnoreCase(
                            installMarker.optString("sha256", ""));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static File installedPackageDirectory(Context context, LearningPathRepository.Lesson lesson) {
        if (!isInstalled(context, lesson)) return null;
        File directory = installDirectory(context, lesson);
        return directory.isDirectory() ? directory : null;
    }

    static File installedLessonFile(Context context, LearningPathRepository.Lesson lesson) {
        if (!isInstalled(context, lesson)) return null;
        File file = safeInstalledChild(installDirectory(context, lesson), lesson.lessonFile);
        if (file == null || !file.isFile()) return null;
        try {
            String json = LearningRemoteContent.readFile(
                    file, LearningLessonRepository.MAX_LESSON_BYTES);
            LearningLessonRepository.LessonData data =
                    LearningLessonRepository.parse(context, json, lesson.id);
            validateMediaFiles(context, installDirectory(context, lesson), data);
            return file;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Subscription downloadAndInstall(Context context, LearningPathRepository.Lesson lesson,
                                           Callback callback) {
        if (context == null || lesson == null || !lesson.needsRemotePackage()) {
            return EMPTY_SUBSCRIPTION;
        }
        Context app = context.getApplicationContext();
        String key = lesson.packageKey();
        Task created = new Task();
        Task task = TASKS.putIfAbsent(key, created);
        if (task == null) task = created;
        Subscription subscription = task.add(callback);
        if (task != created) return subscription;

        Task runningTask = task;
        LearningRemoteContent.execute(() -> {
            File downloadedZip = null;
            try {
                if (installedLessonFile(app, lesson) != null) {
                    runningTask.update(State.READY, 100,
                            app.getString(R.string.learning_package_ready));
                    return;
                }
                downloadedZip = downloadResumable(app, lesson, runningTask);
                File zip = downloadedZip;
                runningTask.update(State.VERIFYING, 100,
                        app.getString(R.string.learning_package_verifying));
                if (!verifySha256(zip, lesson.packageSha256)) {
                    deleteDownloadFiles(zip);
                    throw new IllegalStateException(app.getString(
                            R.string.learning_package_checksum_failed));
                }
                runningTask.update(State.INSTALLING, 100,
                        app.getString(R.string.learning_package_installing));
                install(app, lesson, zip);
                deleteDownloadFiles(zip);
                runningTask.update(State.READY, 100,
                        app.getString(R.string.learning_package_ready));
            } catch (Throwable error) {
                if (downloadedZip != null) deleteDownloadFiles(downloadedZip);
                runningTask.update(State.ERROR, -1, message(app, error,
                        R.string.learning_package_download_failed));
            } finally {
                TASKS.remove(key, runningTask);
            }
        });
        return subscription;
    }

    private static File downloadResumable(Context context, LearningPathRepository.Lesson lesson,
                                          Task task) throws Exception {
        String resolved = LearningRemoteContent.resolveUrl(context, lesson.packageUrl);
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException(context.getString(R.string.learning_package_url_missing));
        }
        if (!LearningRemoteContent.isAllowedHttpsUrl(context, resolved)) {
            throw new SecurityException(context.getString(R.string.learning_package_https_required));
        }

        File downloadDir = new File(context.getFilesDir(), "learning/downloads");
        LearningRemoteContent.ensureDirectory(downloadDir);
        ensureDiskSpace(downloadDir, lesson.packageSize, context);
        String baseName = LearningRemoteContent.safeFileName(
                lesson.packageId + "_v" + lesson.packageVersion + "_"
                        + lesson.packageSha256.substring(0, 12));
        File part = new File(downloadDir, baseName + ".zip.part");
        File meta = new File(downloadDir, baseName + ".zip.part.json");
        File complete = new File(downloadDir, baseName + ".zip");
        LearningRemoteContent.deleteQuietly(complete);

        long configuredMax = LearningRemoteContent.config(context).maxPackageBytes;
        long maxBytes = configuredMax > 0L ? configuredMax : DEFAULT_MAX_PACKAGE_BYTES;
        if (lesson.packageSize > 0L) maxBytes = Math.min(maxBytes, lesson.packageSize);
        ResumeMeta resumeMeta = readResumeMeta(meta);
        long existing = part.isFile() ? part.length() : 0L;
        if (!resolved.equals(resumeMeta.url) || existing < 0L || existing > maxBytes
                || (lesson.packageSize > 0L && existing > lesson.packageSize)) {
            resetPartial(part, meta);
            existing = 0L;
            resumeMeta = new ResumeMeta();
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            DownloadConnection opened = openDownloadConnection(context, resolved, existing, resumeMeta);
            HttpURLConnection connection = opened.connection;
            try {
                int code = opened.code;
                if (code == 416 && existing > 0L) {
                    boolean expectedLength = lesson.packageSize <= 0L
                            || existing == lesson.packageSize;
                    if (expectedLength && verifySha256(part, lesson.packageSha256)) {
                        if (!part.renameTo(complete)) {
                            throw new IllegalStateException(context.getString(
                                    R.string.learning_package_save_failed));
                        }
                        LearningRemoteContent.deleteQuietly(meta);
                        return complete;
                    }
                    resetPartial(part, meta);
                    existing = 0L;
                    resumeMeta = new ResumeMeta();
                    continue;
                }
                boolean append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL;
                if (append) {
                    ContentRange range = ContentRange.parse(connection.getHeaderField("Content-Range"));
                    long responseLength = LearningRemoteContent.contentLength(connection);
                    if (range == null || range.start != existing
                            || (responseLength > 0L && responseLength != range.length())
                            || (lesson.packageSize > 0L && range.total != lesson.packageSize)) {
                        resetPartial(part, meta);
                        existing = 0L;
                        resumeMeta = new ResumeMeta();
                        continue;
                    }
                } else {
                    existing = 0L;
                }

                long responseLength = LearningRemoteContent.contentLength(connection);
                long total = append
                        ? contentRangeTotal(connection, existing, responseLength)
                        : responseLength;
                if (lesson.packageSize > 0L && total > 0L && total != lesson.packageSize) {
                    throw new IllegalStateException(context.getString(
                            R.string.learning_package_size_mismatch));
                }
                if (total > maxBytes) {
                    throw new IllegalStateException(context.getString(
                            R.string.learning_package_too_large));
                }

                ResumeMeta nextMeta = new ResumeMeta();
                nextMeta.url = resolved;
                nextMeta.etag = safeHeader(connection.getHeaderField("ETag"));
                nextMeta.lastModified = safeHeader(connection.getHeaderField("Last-Modified"));
                nextMeta.expectedSize = lesson.packageSize;
                writeResumeMeta(meta, nextMeta);

                try (InputStream input = new BufferedInputStream(connection.getInputStream(), 32 * 1024);
                     FileOutputStream fileOutput = new FileOutputStream(part, append);
                     BufferedOutputStream output = new BufferedOutputStream(fileOutput, 32 * 1024)) {
                    byte[] buffer = new byte[32 * 1024];
                    long written = existing;
                    int lastProgress = Integer.MIN_VALUE;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        written += read;
                        if (written > maxBytes) {
                            throw new IllegalStateException(context.getString(
                                    R.string.learning_package_too_large));
                        }
                        output.write(buffer, 0, read);
                        int progress = total > 0L
                                ? (int) Math.min(99L, written * 100L / total) : -1;
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            task.update(State.DOWNLOADING, progress,
                                    progress >= 0
                                            ? context.getString(R.string.learning_package_downloading_percent,
                                            progress)
                                            : context.getString(R.string.learning_package_downloading));
                        }
                    }
                    output.flush();
                    try { fileOutput.getFD().sync(); } catch (Throwable ignored) { }
                    if (lesson.packageSize > 0L && written != lesson.packageSize) {
                        throw new IllegalStateException(context.getString(
                                R.string.learning_package_incomplete));
                    }
                    if (total > 0L && written != total) {
                        throw new IllegalStateException(context.getString(
                                R.string.learning_package_interrupted));
                    }
                }
                if (!part.renameTo(complete)) {
                    throw new IllegalStateException(context.getString(
                            R.string.learning_package_save_failed));
                }
                LearningRemoteContent.deleteQuietly(meta);
                return complete;
            } finally {
                connection.disconnect();
            }
        }
        throw new IllegalStateException(context.getString(R.string.learning_package_resume_failed));
    }

    private static DownloadConnection openDownloadConnection(Context context, String original,
                                                             long existing, ResumeMeta meta)
            throws Exception {
        URL url = new URL(original);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            if (!LearningRemoteContent.isAllowedHttpsUrl(context, url.toExternalForm())) {
                throw new SecurityException(context.getString(R.string.learning_package_https_required));
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            LearningRemoteContent.Config config = LearningRemoteContent.config(context);
            connection.setConnectTimeout(Math.max(8000, config.connectTimeoutMs));
            connection.setReadTimeout(Math.max(45000, config.packageReadTimeoutMs));
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "TangSengDaoDao-LearningPackage/2.0");
            connection.setRequestProperty("Accept", "application/zip,application/octet-stream");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (existing > 0L) {
                connection.setRequestProperty("Range", "bytes=" + existing + "-");
                String ifRange = !meta.etag.isEmpty() ? meta.etag : meta.lastModified;
                if (!ifRange.isEmpty()) connection.setRequestProperty("If-Range", ifRange);
            }
            int code = connection.getResponseCode();
            if (isRedirect(code)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IllegalStateException("Redirect location is missing");
                }
                url = new URL(url, location);
                continue;
            }
            if ((code < 200 || code >= 300) && code != 416) {
                connection.disconnect();
                throw new IllegalStateException("HTTP " + code);
            }
            return new DownloadConnection(connection, code);
        }
        throw new IllegalStateException("Too many redirects");
    }

    private static void install(Context context, LearningPathRepository.Lesson lesson, File zip)
            throws Exception {
        File packageRoot = packageRoot(context, lesson);
        LearningRemoteContent.ensureDirectory(packageRoot);
        File target = installDirectory(context, lesson);
        File backup = new File(packageRoot, ".backup");
        cleanupTransientDirectories(packageRoot);
        File staging = new File(packageRoot, ".staging_" + System.currentTimeMillis());

        if (!target.exists() && backup.exists() && !backup.renameTo(target)) {
            throw new IllegalStateException(context.getString(R.string.learning_package_restore_failed));
        }
        deleteRecursively(staging);
        LearningRemoteContent.ensureDirectory(staging);

        long configuredMax = LearningRemoteContent.config(context).maxUnpackedBytes;
        long maxUnpacked = configuredMax > 0L ? configuredMax : DEFAULT_MAX_UNPACKED_BYTES;
        unzipSecure(context, zip, staging, maxUnpacked);
        validateInstalledPackage(context, staging, lesson);
        writeInstallMarker(staging, lesson);

        deleteRecursively(backup);
        boolean hadTarget = target.exists();
        if (hadTarget && !target.renameTo(backup)) {
            deleteRecursively(staging);
            throw new IllegalStateException(context.getString(R.string.learning_package_backup_failed));
        }
        if (!staging.renameTo(target)) {
            deleteRecursively(staging);
            boolean restored = !hadTarget || !backup.exists() || backup.renameTo(target);
            throw new IllegalStateException(context.getString(restored
                    ? R.string.learning_package_switch_failed
                    : R.string.learning_package_restore_failed));
        }
        deleteRecursively(backup);
        cleanupOldVersions(packageRoot, target.getName());
    }

    private static void unzipSecure(Context context, File zip, File destination, long maxUnpacked)
            throws Exception {
        String destinationPath = destination.getCanonicalPath() + File.separator;
        Set<String> names = new HashSet<>();
        int entries = 0;
        long total = 0L;
        try (ZipInputStream input = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buffer = new byte[32 * 1024];
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalStateException(context.getString(
                            R.string.learning_package_too_many_files));
                }
                String name = sanitizeZipName(entry.getName());
                if (name.isEmpty() || !names.add(name) || isExecutableEntry(name)) {
                    throw new SecurityException(context.getString(
                            R.string.learning_package_unsafe_path));
                }
                File outputFile = new File(destination, name);
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(destinationPath)) {
                    throw new SecurityException(context.getString(
                            R.string.learning_package_unsafe_path));
                }
                if (entry.isDirectory()) {
                    LearningRemoteContent.ensureDirectory(outputFile);
                    input.closeEntry();
                    continue;
                }
                if (entry.getSize() > MAX_ENTRY_BYTES) {
                    throw new IllegalStateException(context.getString(
                            R.string.learning_package_unpacked_too_large));
                }
                LearningRemoteContent.ensureDirectory(outputFile.getParentFile());
                try (BufferedOutputStream output = new BufferedOutputStream(
                        new FileOutputStream(outputFile), 32 * 1024)) {
                    long entryBytes = 0L;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        entryBytes += count;
                        total += count;
                        if (entryBytes > MAX_ENTRY_BYTES || total > maxUnpacked) {
                            throw new IllegalStateException(context.getString(
                                    R.string.learning_package_unpacked_too_large));
                        }
                        output.write(buffer, 0, count);
                    }
                    output.flush();
                }
                input.closeEntry();
            }
        }
    }

    private static void validateInstalledPackage(Context context, File directory,
                                                 LearningPathRepository.Lesson lesson)
            throws Exception {
        File manifest = new File(directory, "manifest.json");
        if (!manifest.isFile()) {
            throw new IllegalStateException(context.getString(R.string.learning_package_manifest_missing));
        }
        JSONObject object = new JSONObject(readSmallFile(manifest, 512 * 1024));
        if (!lesson.packageId.equals(object.optString("package_id", ""))) {
            throw new IllegalStateException(context.getString(R.string.learning_package_id_mismatch));
        }
        if (lesson.packageVersion != object.optInt("version", 0)) {
            throw new IllegalStateException(context.getString(R.string.learning_package_version_mismatch));
        }
        if (!lesson.courseId.equals(object.optString("course_id", ""))) {
            throw new IllegalStateException(context.getString(R.string.learning_package_course_mismatch));
        }
        if (!lesson.unitId.equals(object.optString("unit_id", ""))) {
            throw new IllegalStateException(context.getString(R.string.learning_package_unit_mismatch));
        }
        File lessonFile = safeInstalledChild(directory, lesson.lessonFile);
        if (lessonFile == null || !lessonFile.isFile()) {
            throw new IllegalStateException(context.getString(R.string.learning_package_lesson_missing));
        }
        String json = readSmallFile(lessonFile, LearningLessonRepository.MAX_LESSON_BYTES);
        LearningLessonRepository.LessonData data =
                LearningLessonRepository.parse(context, json, lesson.id);
        validateMediaFiles(context, directory, data);
    }

    private static void validateMediaFiles(Context context, File directory,
                                           LearningLessonRepository.LessonData data) {
        for (LearningLessonRepository.Exercise exercise : data.exercises) {
            if (!exercise.audio.isEmpty()) requireMediaFile(context, directory, exercise.audio);
            for (LearningLessonRepository.ChoiceOption option : exercise.options) {
                if (!option.image.isEmpty()) requireMediaFile(context, directory, option.image);
            }
        }
    }

    private static void requireMediaFile(Context context, File directory, String relative) {
        File media = safeInstalledChild(directory, relative);
        if (media == null || !media.isFile() || media.length() <= 0L) {
            throw new IllegalStateException(context.getString(
                    R.string.learning_package_media_missing, relative));
        }
    }

    private static void writeInstallMarker(File directory, LearningPathRepository.Lesson lesson)
            throws Exception {
        JSONObject marker = new JSONObject();
        marker.put("package_id", lesson.packageId);
        marker.put("version", lesson.packageVersion);
        marker.put("course_id", lesson.courseId);
        marker.put("unit_id", lesson.unitId);
        marker.put("sha256", lesson.packageSha256);
        marker.put("package_size", lesson.packageSize);
        marker.put("installed_at", System.currentTimeMillis());
        LearningRemoteContent.atomicWrite(new File(directory, ".install.json"),
                marker.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static File packageRoot(Context context, LearningPathRepository.Lesson lesson) {
        return new File(context.getFilesDir(), "learning/packages/"
                + LearningRemoteContent.safeFileName(lesson.packageId));
    }

    private static File installDirectory(Context context, LearningPathRepository.Lesson lesson) {
        return new File(packageRoot(context, lesson), "v" + lesson.packageVersion);
    }

    private static File safeInstalledChild(File directory, String relative) {
        try {
            String clean = LearningLessonRepository.cleanRelative(relative, true);
            if (directory == null || clean.isEmpty()) return null;
            File child = new File(directory, clean).getCanonicalFile();
            String root = directory.getCanonicalPath() + File.separator;
            return child.getPath().startsWith(root) ? child : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean verifySha256(File file, String expected) {
        if (file == null || !file.isFile() || expected == null || expected.trim().isEmpty()) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return hex.toString().equalsIgnoreCase(expected.trim());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String readSmallFile(File file, int maxBytes) throws Exception {
        if (file == null || !file.isFile() || file.length() > maxBytes) {
            throw new IllegalStateException("Course file is too large or missing");
        }
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                     (int) Math.min(file.length(), 65536L))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalStateException("Course file is too large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static long contentRangeTotal(HttpURLConnection connection, long existing,
                                          long responseLength) {
        ContentRange range = ContentRange.parse(connection.getHeaderField("Content-Range"));
        if (range != null && range.total > 0L) return range.total;
        return responseLength > 0L ? existing + responseLength : -1L;
    }

    private static ResumeMeta readResumeMeta(File file) {
        ResumeMeta result = new ResumeMeta();
        try {
            String text = LearningRemoteContent.readFile(file, 128 * 1024);
            if (text.isEmpty()) return result;
            JSONObject object = new JSONObject(text);
            result.url = object.optString("url", "");
            result.etag = object.optString("etag", "");
            result.lastModified = object.optString("last_modified", "");
            result.expectedSize = Math.max(0L, object.optLong("expected_size", 0L));
        } catch (Throwable ignored) { }
        return result;
    }

    private static void writeResumeMeta(File file, ResumeMeta meta) throws Exception {
        JSONObject object = new JSONObject();
        object.put("url", meta.url);
        object.put("etag", meta.etag);
        object.put("last_modified", meta.lastModified);
        object.put("expected_size", meta.expectedSize);
        LearningRemoteContent.atomicWrite(file, object.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void resetPartial(File part, File meta) {
        LearningRemoteContent.deleteQuietly(part);
        LearningRemoteContent.deleteQuietly(meta);
    }

    private static void deleteDownloadFiles(File complete) {
        if (complete == null) return;
        LearningRemoteContent.deleteQuietly(complete);
        LearningRemoteContent.deleteQuietly(new File(complete.getAbsolutePath() + ".part"));
        LearningRemoteContent.deleteQuietly(new File(complete.getAbsolutePath() + ".part.json"));
    }

    private static void cleanupTransientDirectories(File packageRoot) {
        File[] files = packageRoot.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(".staging_")) deleteRecursively(file);
        }
    }

    private static void cleanupOldVersions(File packageRoot, String keepName) {
        File[] files = packageRoot.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().equals(keepName)) continue;
            if (file.getName().startsWith(".staging_")) deleteRecursively(file);
            else if (file.isDirectory() && file.getName().matches("v[0-9]+")) deleteRecursively(file);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        try { file.delete(); } catch (Throwable ignored) { }
    }

    private static void ensureDiskSpace(File directory, long packageSize, Context context) {
        if (packageSize <= 0L) return;
        long needed = Math.max(20L * 1024L * 1024L, packageSize * 2L);
        if (directory.getUsableSpace() > 0L && directory.getUsableSpace() < needed) {
            throw new IllegalStateException(context.getString(R.string.learning_package_disk_space_low));
        }
    }

    private static String sanitizeZipName(String raw) {
        if (raw == null) return "";
        String name = raw.trim().replace('\\', '/');
        while (name.startsWith("/")) name = name.substring(1);
        if (name.isEmpty() || name.length() > 768 || name.indexOf('\0') >= 0 || name.contains(":")) return "";
        String[] parts = name.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return "";
        }
        return name;
    }

    private static boolean isExecutableEntry(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".apk") || lower.endsWith(".dex") || lower.endsWith(".so")
                || lower.endsWith(".jar") || lower.endsWith(".class") || lower.endsWith(".sh");
    }

    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307 || code == 308;
    }

    private static String safeHeader(String value) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() > 512 ? text.substring(0, 512) : text;
    }

    private static String message(Context context, Throwable error, int fallbackRes) {
        String value = error == null ? "" : error.getMessage();
        return value == null || value.trim().isEmpty()
                ? context.getString(fallbackRes) : value.trim();
    }

    private static final class Task {
        private final CopyOnWriteArrayList<Callback> callbacks = new CopyOnWriteArrayList<>();
        private volatile State state = State.DOWNLOADING;
        private volatile int progress = 0;
        private volatile String message = "";

        Subscription add(Callback callback) {
            if (callback == null) return EMPTY_SUBSCRIPTION;
            callbacks.add(callback);
            notifyOne(callback, snapshot());
            return () -> callbacks.remove(callback);
        }

        Status snapshot() {
            return new Status(state, progress, message);
        }

        void update(State state, int progress, String message) {
            this.state = state;
            this.progress = progress;
            this.message = message == null ? "" : message;
            Status status = snapshot();
            for (Callback callback : callbacks) notifyOne(callback, status);
            if (state == State.READY || state == State.ERROR) callbacks.clear();
        }

        private void notifyOne(Callback callback, Status status) {
            try { callback.onState(status.state, status.progress, status.message); }
            catch (Throwable ignored) { }
        }
    }

    private static final class ResumeMeta {
        String url = "";
        String etag = "";
        String lastModified = "";
        long expectedSize;
    }

    private static final class DownloadConnection {
        final HttpURLConnection connection;
        final int code;

        DownloadConnection(HttpURLConnection connection, int code) {
            this.connection = connection;
            this.code = code;
        }
    }

    private static final class ContentRange {
        final long start;
        final long end;
        final long total;

        ContentRange(long start, long end, long total) {
            this.start = start;
            this.end = end;
            this.total = total;
        }

        long length() { return end - start + 1L; }

        static ContentRange parse(String value) {
            if (value == null) return null;
            try {
                String text = value.trim();
                int space = text.indexOf(' ');
                int dash = text.indexOf('-', space + 1);
                int slash = text.indexOf('/', dash + 1);
                if (space < 0 || dash < 0 || slash < 0) return null;
                long start = Long.parseLong(text.substring(space + 1, dash));
                long end = Long.parseLong(text.substring(dash + 1, slash));
                long total = Long.parseLong(text.substring(slash + 1));
                return start >= 0L && end >= start && total > end
                        ? new ContentRange(start, end, total) : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
