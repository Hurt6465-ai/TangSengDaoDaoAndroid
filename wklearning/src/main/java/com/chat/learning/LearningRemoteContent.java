package com.chat.learning;

import android.content.Context;

import com.chat.base.config.WKApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared HTTPS-only downloader and cache helper for official learning assets. */
final class LearningRemoteContent {
    private static final int MAX_REDIRECTS = 5;
    private static final ExecutorService IO = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "wk-learning-io");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Config config;

    private LearningRemoteContent() { }

    static void execute(Runnable runnable) {
        if (runnable == null) return;
        try {
            IO.execute(runnable);
        } catch (Throwable ignored) {
            // The process is already shutting down. Callers must tolerate a dropped background task.
        }
    }

    static Config config(Context context) {
        Config cached = config;
        if (cached != null) {
            applyApiFallback(cached);
            return cached;
        }
        synchronized (LearningRemoteContent.class) {
            if (config != null) {
                applyApiFallback(config);
                return config;
            }
            Config loaded = new Config();
            try {
                JSONObject root = new JSONObject(readAsset(context, "learning/config/remote_content.json"));
                loaded.baseUrl = normalizeBase(root.optString("base_url", ""));
                loaded.baseHost = hostOf(loaded.baseUrl);
                loaded.allowAbsoluteHttpsUrls = root.optBoolean("allow_absolute_https_urls", false);
                loaded.connectTimeoutMs = clamp(root.optInt("connect_timeout_ms", 8000), 3000, 20000);
                loaded.readTimeoutMs = clamp(root.optInt("read_timeout_ms", 12000), 5000, 30000);
                JSONObject catalogs = root.optJSONObject("catalogs");
                if (catalogs != null) {
                    loaded.wordsCatalog = catalogs.optString("words", "").trim();
                    loaded.learningPathCatalog = catalogs.optString("learning_path", "").trim();
                }
                loaded.packageReadTimeoutMs = clamp(root.optInt("package_read_timeout_ms", 120000),
                        30000, 300000);
                loaded.maxPackageBytes = clampLong(root.optLong("max_package_bytes", 209715200L),
                        10L * 1024L * 1024L, 1024L * 1024L * 1024L);
                loaded.maxUnpackedBytes = clampLong(root.optLong("max_unpacked_bytes", 524288000L),
                        20L * 1024L * 1024L, 2L * 1024L * 1024L * 1024L);
                JSONArray allowed = root.optJSONArray("allowed_hosts");
                if (allowed != null) {
                    HashSet<String> hosts = new HashSet<>();
                    for (int i = 0; i < allowed.length(); i++) {
                        String host = normalizeHost(allowed.optString(i, ""));
                        if (!host.isEmpty()) hosts.add(host);
                    }
                    loaded.allowedHosts = Collections.unmodifiableSet(hosts);
                }
            } catch (Throwable ignored) {
                // Invalid optional remote config means offline-only operation, never an app crash.
            }
            applyApiFallback(loaded);
            config = loaded;
            return loaded;
        }
    }

    static String resolveUrl(Context context, String value) {
        if (value == null) return "";
        String raw = value.trim();
        if (raw.isEmpty()) return "";
        try {
            URL resolved;
            if (raw.regionMatches(true, 0, "https://", 0, 8)) {
                Config cfg = config(context);
                if (!cfg.allowAbsoluteHttpsUrls && !sameOrAllowedHost(cfg, hostOf(raw))) return "";
                resolved = new URL(raw);
            } else if (raw.regionMatches(true, 0, "http://", 0, 7)
                    || raw.startsWith("//")) {
                return "";
            } else {
                String base = config(context).baseUrl;
                if (base.isEmpty()) return "";
                while (raw.startsWith("/")) raw = raw.substring(1);
                resolved = new URL(new URL(base), raw);
            }
            if (!"https".equalsIgnoreCase(resolved.getProtocol())) return "";
            if (resolved.getUserInfo() != null || resolved.getHost() == null
                    || resolved.getHost().trim().isEmpty()) return "";
            return resolved.toExternalForm();
        } catch (Throwable ignored) {
            return "";
        }
    }

    static boolean isAllowedHttpsUrl(Context context, String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            URL url = new URL(value);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && url.getUserInfo() == null
                    && sameOrAllowedHost(config(context), normalizeHost(url.getHost()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static byte[] download(Context context, String urlValue, int maxBytes) throws Exception {
        if (maxBytes <= 0) throw new IllegalArgumentException("Invalid download size limit");
        String resolved = resolveUrl(context, urlValue);
        if (resolved.isEmpty()) throw new IllegalArgumentException("Remote URL is not configured");
        HttpURLConnection connection = openHttps(context, resolved, maxBytes, false);
        try {
            long declared = contentLength(connection);
            if (declared > maxBytes) throw new IllegalStateException("File is too large");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream(
                         (int) Math.max(4096L, Math.min(declared > 0 ? declared : 4096L, 65536L)))) {
                byte[] buffer = new byte[16 * 1024];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw new IllegalStateException("File is too large");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Opens a verified HTTPS connection and follows redirects without allowing protocol downgrade. */
    static HttpURLConnection openHttps(Context context, String resolvedUrl, long maxBytes,
                                       boolean packageDownload) throws Exception {
        URL url = new URL(resolvedUrl);
        Config cfg = config(context);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                throw new SecurityException("HTTPS required");
            }
            if (!sameOrAllowedHost(cfg, normalizeHost(url.getHost()))) {
                throw new SecurityException("Remote host is not allowed");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(cfg.connectTimeoutMs);
            connection.setReadTimeout(packageDownload ? cfg.packageReadTimeoutMs : cfg.readTimeoutMs);
            connection.setUseCaches(!packageDownload);
            connection.setRequestProperty("User-Agent", packageDownload
                    ? "TangSengDaoDao-LearningPackage/2.0"
                    : "TangSengDaoDao-Learning/2.0");
            connection.setRequestProperty("Accept-Encoding", "identity");
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
            if (code < 200 || code >= 300) {
                connection.disconnect();
                throw new IllegalStateException("HTTP " + code);
            }
            long declared = contentLength(connection);
            if (maxBytes > 0L && declared > maxBytes) {
                connection.disconnect();
                throw new IllegalStateException("File is too large");
            }
            return connection;
        }
        throw new IllegalStateException("Too many redirects");
    }

    static boolean verifySha256(byte[] bytes, String expected) {
        if (expected == null || expected.trim().isEmpty()) return true;
        if (bytes == null) return false;
        try {
            return sha256(bytes).equalsIgnoreCase(expected.trim());
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(bytes));
    }

    static String sha256(String value) {
        try {
            return sha256((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return "";
        }
    }

    static void atomicWrite(File target, byte[] bytes) throws Exception {
        if (target == null || bytes == null) throw new IllegalArgumentException("Invalid cache target");
        File parent = target.getParentFile();
        ensureDirectory(parent);
        File temp = new File(target.getAbsolutePath() + ".tmp");
        File backup = new File(target.getAbsolutePath() + ".bak");
        deleteQuietly(temp);
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            output.write(bytes);
            output.flush();
            try { output.getFD().sync(); } catch (Throwable ignored) { }
        }
        deleteQuietly(backup);
        boolean hadTarget = target.isFile();
        if (hadTarget && !target.renameTo(backup)) {
            deleteQuietly(temp);
            throw new IllegalStateException("Cannot back up cache file");
        }
        if (!temp.renameTo(target)) {
            if (hadTarget && backup.isFile()) backup.renameTo(target);
            deleteQuietly(temp);
            throw new IllegalStateException("Cannot move cache file");
        }
        deleteQuietly(backup);
    }

    static String readFile(File file, int maxBytes) throws Exception {
        if (file == null || !file.isFile() || maxBytes <= 0 || file.length() > maxBytes) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                     (int) Math.min(file.length(), 65536L))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) return "";
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static String readAsset(Context context, String path) throws Exception {
        if (context == null || path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid asset path");
        }
        try (InputStream input = context.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static String safeFileName(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        while (safe.startsWith(".")) safe = safe.substring(1);
        if (safe.isEmpty() || ".".equals(safe) || "..".equals(safe)) safe = "item";
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    static void ensureDirectory(File directory) {
        if (directory == null) throw new IllegalStateException("Directory is invalid");
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new IllegalStateException("Cannot create directory");
        }
        if (!directory.isDirectory()) throw new IllegalStateException("Path is not a directory");
    }

    static void deleteQuietly(File file) {
        try { if (file != null && file.exists()) file.delete(); } catch (Throwable ignored) { }
    }

    static long contentLength(HttpURLConnection connection) {
        if (connection == null) return -1L;
        try {
            long value = connection.getContentLengthLong();
            return value >= 0L ? value : connection.getContentLength();
        } catch (Throwable ignored) {
            return connection.getContentLength();
        }
    }

    private static boolean sameOrAllowedHost(Config cfg, String host) {
        String normalized = normalizeHost(host);
        if (normalized.isEmpty()) return false;
        if (cfg.baseHost.isEmpty()) return cfg.allowAbsoluteHttpsUrls || cfg.allowedHosts.contains(normalized);
        return normalized.equals(cfg.baseHost) || cfg.allowedHosts.contains(normalized);
    }

    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307 || code == 308;
    }

    private static void applyApiFallback(Config cfg) {
        if (cfg == null || !cfg.baseUrl.isEmpty()) return;
        String api = WKApiConfig.baseUrl == null ? "" : WKApiConfig.baseUrl.trim();
        if (api.isEmpty()) return;
        String lower = api.toLowerCase(Locale.US);
        int marker = lower.indexOf("/v1/");
        if (marker < 0) marker = lower.endsWith("/v1") ? lower.length() - 3 : -1;
        if (marker < 0) return;
        String root = api.substring(0, marker + 1);
        String fallback = normalizeBase(root + "learning/");
        if (fallback.isEmpty()) return;
        cfg.baseUrl = fallback;
        cfg.baseHost = hostOf(fallback);
    }

    private static String normalizeBase(String value) {
        if (value == null) return "";
        String base = value.trim();
        try {
            URL url = new URL(base);
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null
                    || url.getHost() == null || url.getHost().trim().isEmpty()) return "";
            return base.endsWith("/") ? base : base + "/";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String hostOf(String value) {
        try { return normalizeHost(new URL(value).getHost()); }
        catch (Throwable ignored) { return ""; }
    }

    private static String normalizeHost(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) hex.append(String.format(Locale.US, "%02x", value & 0xff));
        return hex.toString();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Config {
        String baseUrl = "";
        String baseHost = "";
        String wordsCatalog = "";
        String learningPathCatalog = "";
        boolean allowAbsoluteHttpsUrls = false;
        Set<String> allowedHosts = Collections.emptySet();
        int connectTimeoutMs = 8000;
        int readTimeoutMs = 12000;
        int packageReadTimeoutMs = 120000;
        long maxPackageBytes = 200L * 1024L * 1024L;
        long maxUnpackedBytes = 500L * 1024L * 1024L;
    }
}
