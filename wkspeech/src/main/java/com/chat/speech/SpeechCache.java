package com.chat.speech;

import android.content.Context;

import java.io.File;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

public class SpeechCache {
    private static final long MAX_CACHE_BYTES = 100L * 1024L * 1024L;

    private SpeechCache() {}

    public static File audioFile(Context context, String key) {
        return audioFile(context, key, "mp3");
    }

    public static File audioFile(Context context, String key, String extension) {
        File dir = new File(context.getApplicationContext().getCacheDir(), "wkspeech/tts");
        if (!dir.exists()) dir.mkdirs();
        String ext = extension == null ? "mp3" : extension.trim().toLowerCase();
        if (!ext.matches("[a-z0-9]{2,5}")) ext = "mp3";
        return new File(dir, md5(key) + "." + ext);
    }

    public static void trim(Context context) {
        File dir = new File(context.getApplicationContext().getCacheDir(), "wkspeech/tts");
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_CACHE_BYTES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            total -= file.length();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            if (total <= MAX_CACHE_BYTES * 8 / 10) break;
        }
    }

    public static long clear(Context context) {
        File dir = new File(context.getApplicationContext().getCacheDir(), "wkspeech/tts");
        if (!dir.exists()) return 0L;
        long size = deleteRecursive(dir);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return size;
    }

    private static long deleteRecursive(File file) {
        if (file == null || !file.exists()) return 0L;
        long size = file.length();
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) size += deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        return size;
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }
}
