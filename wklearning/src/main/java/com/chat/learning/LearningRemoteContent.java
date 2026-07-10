package com.chat.learning;

import android.content.Context;

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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared HTTPS-only downloader and cache helper for official learning assets. */
final class LearningRemoteContent {
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static volatile Config config;

    private LearningRemoteContent() {}

    static void execute(Runnable runnable) {
        if (runnable != null) IO.execute(runnable);
    }

    static Config config(Context context) {
        Config cached = config;
        if (cached != null) return cached;
        synchronized (LearningRemoteContent.class) {
            if (config != null) return config;
            Config loaded = new Config();
            try {
                JSONObject root = new JSONObject(readAsset(context, "learning/config/remote_content.json"));
                loaded.baseUrl = normalizeBase(root.optString("base_url", ""));
                loaded.connectTimeoutMs = clamp(root.optInt("connect_timeout_ms", 8000), 3000, 20000);
                loaded.readTimeoutMs = clamp(root.optInt("read_timeout_ms", 12000), 5000, 30000);
                JSONObject catalogs = root.optJSONObject("catalogs");
                if (catalogs != null) loaded.wordsCatalog = catalogs.optString("words", "");
            } catch (Throwable ignored) {}
            config = loaded;
            return loaded;
        }
    }

    static String resolveUrl(Context context, String value) {
        if (value == null) return "";
        String raw = value.trim();
        if (raw.length() == 0) return "";
        if (raw.startsWith("https://")) return raw;
        if (raw.startsWith("http://")) return "";
        String base = config(context).baseUrl;
        if (base.length() == 0) return "";
        while (raw.startsWith("/")) raw = raw.substring(1);
        return base + raw;
    }

    static byte[] download(Context context, String urlValue, int maxBytes) throws Exception {
        String resolved = resolveUrl(context, urlValue);
        if (resolved.length() == 0) throw new IllegalArgumentException("Remote URL is not configured");
        URL url = new URL(resolved);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new SecurityException("HTTPS required");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        Config cfg = config(context);
        connection.setConnectTimeout(cfg.connectTimeoutMs);
        connection.setReadTimeout(cfg.readTimeoutMs);
        connection.setUseCaches(true);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "TangSengDaoDao-Learning/1.0");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            URL finalUrl = connection.getURL();
            if (finalUrl == null || !"https".equalsIgnoreCase(finalUrl.getProtocol())) {
                throw new SecurityException("Redirected to non-HTTPS URL");
            }
            int declared = connection.getContentLength();
            if (declared > maxBytes) throw new IllegalStateException("File is too large");
            InputStream input = connection.getInputStream();
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(4096, Math.min(declared, 65536)));
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw new IllegalStateException("File is too large");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            } finally {
                try { input.close(); } catch (Throwable ignored) {}
            }
        } finally {
            connection.disconnect();
        }
    }

    static boolean verifySha256(byte[] bytes, String expected) {
        if (expected == null || expected.trim().length() == 0) return true;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(result.length * 2);
            for (byte b : result) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            return hex.toString().equalsIgnoreCase(expected.trim());
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void atomicWrite(File target, byte[] bytes) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IllegalStateException("Cannot create cache directory");
        }
        File temp = new File(target.getAbsolutePath() + ".tmp");
        FileOutputStream output = new FileOutputStream(temp, false);
        try {
            output.write(bytes);
            output.flush();
            try { output.getFD().sync(); } catch (Throwable ignored) {}
        } finally {
            output.close();
        }
        if (target.exists() && !target.delete()) throw new IllegalStateException("Cannot replace cache file");
        if (!temp.renameTo(target)) throw new IllegalStateException("Cannot move cache file");
    }

    static String readFile(File file, int maxBytes) throws Exception {
        if (file == null || !file.isFile() || file.length() > maxBytes) return "";
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), 65536));
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) return "";
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
    }

    static String readAsset(Context context, String path) throws Exception {
        InputStream input = context.getAssets().open(path);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
    }

    static String safeFileName(String value) {
        if (value == null || value.length() == 0) return "item";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String normalizeBase(String value) {
        if (value == null) return "";
        String base = value.trim();
        if (base.length() == 0 || !base.startsWith("https://")) return "";
        return base.endsWith("/") ? base : base + "/";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Config {
        String baseUrl = "";
        String wordsCatalog = "";
        int connectTimeoutMs = 8000;
        int readTimeoutMs = 12000;
    }
}
