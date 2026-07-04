package com.chat.speech.importer;

import android.content.Context;
import android.net.Uri;

import com.chat.speech.SpeechPrefs;
import com.chat.speech.model.TtsSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TtsSourceConfigImporter {
    private TtsSourceConfigImporter() {}

    public static Result importFromUri(Context context, Uri uri) throws Exception {
        byte[] data = readAll(context.getContentResolver().openInputStream(uri));
        String text;
        if (data.length >= 2 && data[0] == 'P' && data[1] == 'K') {
            text = readConfigTextFromZip(data);
        } else {
            text = new String(data, "UTF-8");
        }
        return importText(context, text);
    }

    public static Result importText(Context context, String text) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("配置为空");
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) throw new IllegalArgumentException("不是唐僧 TTS 源 JSON 配置");
        JSONObject object = new JSONObject(trimmed);
        SpeechPrefs prefs = new SpeechPrefs(context);
        int count = 0;
        String activatedId = "";
        if (object.optInt("tsddSpeechSource", 0) == 1) {
            TtsSource source = TtsSource.fromJson(object);
            if (source == null) throw new IllegalArgumentException("TTS 源配置无效");
            prefs.upsertSource(source, true);
            prefs.setAudioFormat(source.audioFormat);
            count = 1;
            activatedId = source.id;
        } else if (object.optInt("tsddSpeechSources", 0) == 1) {
            JSONArray sources = object.optJSONArray("sources");
            if (sources == null || sources.length() == 0) throw new IllegalArgumentException("没有 sources");
            for (int i = 0; i < sources.length(); i++) {
                TtsSource source = TtsSource.fromJson(sources.optJSONObject(i));
                if (source == null) continue;
                prefs.upsertSource(source, i == 0);
                if (i == 0) activatedId = source.id;
                count++;
            }
        } else {
            throw new IllegalArgumentException("缺少 tsddSpeechSource 标记");
        }
        return new Result(count, activatedId);
    }

    private static String readConfigTextFromZip(byte[] data) throws Exception {
        String fallback = "";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase(Locale.US);
                if (name.endsWith(".json")) {
                    String text = new String(readEntry(zis), "UTF-8");
                    String trimmed = text.trim();
                    if (trimmed.contains("tsddSpeechSource")) return trimmed;
                    if (fallback.isEmpty()) fallback = trimmed;
                }
            }
        }
        return fallback;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        try (InputStream input = in) {
            return readEntry(input);
        }
    }

    private static byte[] readEntry(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = input.read(buffer)) >= 0) out.write(buffer, 0, len);
        return out.toByteArray();
    }

    public static class Result {
        public final int sourceCount;
        public final String activatedSourceId;

        Result(int sourceCount, String activatedSourceId) {
            this.sourceCount = sourceCount;
            this.activatedSourceId = activatedSourceId;
        }
    }
}
