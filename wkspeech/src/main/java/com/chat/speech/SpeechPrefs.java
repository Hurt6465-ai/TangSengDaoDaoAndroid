package com.chat.speech;

import android.content.Context;
import android.content.SharedPreferences;

import com.chat.speech.model.SpeechSegment;
import com.chat.speech.model.TtsSource;
import com.chat.speech.model.TtsVoice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpeechPrefs {
    private static final String PREF = "tsdd_speech_prefs";
    private static final String KEY_ACTIVE_SOURCE_ID = "active_source_id";
    private static final String KEY_SOURCES_JSON = "sources_json";
    private static final String KEY_MS_ENABLED = "ms_enabled"; // backward compatibility
    private static final String KEY_ZH_VOICE = "zh_voice";
    private static final String KEY_MY_VOICE = "my_voice";
    private static final String KEY_AUDIO_FORMAT = "audio_format";
    private static final String KEY_IMPORTED_VOICE_COUNT = "imported_voice_count";
    private static final String KEY_IMPORTED_SOURCE_NAME = "imported_source_name";
    private static final String KEY_IMPORTED_SOURCE_TYPE = "imported_source_type";
    private static final String KEY_IMPORTED_VOICES_JSON = "imported_voices_json";
    private static final String KEY_MIXED_READ_ENABLED = "mixed_read_enabled";
    private static final String KEY_RATE_PERCENT = "rate_percent";
    private static final String KEY_PITCH_PERCENT = "pitch_percent";
    private static final String KEY_EDGE_ENGINE_MIGRATION_V1 = "edge_engine_migration_v1";
    private static final String KEY_BYTEDANCE_PACKAGE_ROOT = "bytedance_package_root";
    private static final String KEY_BYTEDANCE_VOICE = "bytedance_voice";

    public static final String SOURCE_TYPE_MS_TRANSLATOR = TtsSource.TYPE_MS_TRANSLATOR;
    public static final String SOURCE_TYPE_EDGE_WEBSOCKET = TtsSource.TYPE_EDGE_WEBSOCKET;
    public static final String SOURCE_TYPE_BYTEDANCE_OFFLINE = TtsSource.TYPE_BYTEDANCE_OFFLINE;
    public static final String SOURCE_TYPE_UNKNOWN = TtsSource.TYPE_UNKNOWN;

    public static final String DEFAULT_AUDIO_FORMAT = "audio-24khz-48kbitrate-mono-mp3";
    public static final String DEFAULT_ZH_VOICE = "zh-CN-XiaoxiaoNeural";
    public static final String DEFAULT_ZH_MALE_VOICE = "zh-CN-YunxiNeural";
    public static final String DEFAULT_ZH_MULTI_VOICE = "zh-CN-XiaochenMultilingualNeural";
    public static final String DEFAULT_MY_VOICE = "my-MM-NilarNeural";
    public static final String DEFAULT_MY_MALE_VOICE = "my-MM-ThihaNeural";

    private final Context appContext;
    private final SharedPreferences sp;

    public SpeechPrefs(Context context) {
        appContext = context.getApplicationContext();
        sp = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        ensureDefaultSources();
        migrateEdgeEngineV1();
    }

    public boolean isMsEnabled() {
        TtsSource active = getActiveSource();
        if (active != null) return TtsSource.TYPE_MS_TRANSLATOR.equals(active.type);
        return sp.getBoolean(KEY_MS_ENABLED, false);
    }

    public void setMsEnabled(boolean enabled) {
        if (enabled) {
            setActiveSourceId(TtsSource.builtinMsTranslator().id);
        } else {
            setActiveSourceId(TtsSource.edgeWebSocketTemplate().id);
        }
        sp.edit().putBoolean(KEY_MS_ENABLED, enabled).apply();
    }

    public boolean isMixedReadEnabled() {
        return sp.getBoolean(KEY_MIXED_READ_ENABLED, true);
    }

    public void setMixedReadEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_MIXED_READ_ENABLED, enabled).apply();
    }

    public int getRatePercent() {
        return sp.getInt(KEY_RATE_PERCENT, 0);
    }

    public void setRatePercent(int value) {
        sp.edit().putInt(KEY_RATE_PERCENT, clamp(value, -50, 80)).apply();
    }

    public int getPitchPercent() {
        return sp.getInt(KEY_PITCH_PERCENT, 0);
    }

    public void setPitchPercent(int value) {
        sp.edit().putInt(KEY_PITCH_PERCENT, clamp(value, -50, 50)).apply();
    }

    public float getSystemRate() {
        return Math.max(0.5f, Math.min(2.0f, 1.0f + getRatePercent() / 100f));
    }

    public float getSystemPitch() {
        return Math.max(0.5f, Math.min(1.8f, 1.0f + getPitchPercent() / 100f));
    }

    public String getZhVoice() {
        return sp.getString(KEY_ZH_VOICE, DEFAULT_ZH_VOICE);
    }

    public void setZhVoice(String voice) {
        if (voice == null || voice.trim().isEmpty()) return;
        sp.edit().putString(KEY_ZH_VOICE, voice.trim()).apply();
    }

    public String getMyVoice() {
        return sp.getString(KEY_MY_VOICE, DEFAULT_MY_VOICE);
    }

    public void setMyVoice(String voice) {
        if (voice == null || voice.trim().isEmpty()) return;
        sp.edit().putString(KEY_MY_VOICE, voice.trim()).apply();
    }

    public String getByteDancePackageRoot() {
        String path = sp.getString(KEY_BYTEDANCE_PACKAGE_ROOT, "");
        return path == null ? "" : path.trim();
    }

    public void setByteDancePackageRoot(String path) {
        sp.edit().putString(KEY_BYTEDANCE_PACKAGE_ROOT, path == null ? "" : path.trim()).apply();
    }

    public boolean isByteDancePackageReady() {
        String root = getByteDancePackageRoot();
        if (root.isEmpty()) return false;
        File bytedance = new File(root);
        return bytedance.isDirectory()
                && new File(bytedance, "midu/speech_license.licbag").isFile()
                && new File(bytedance, "midu/zh-cn/ptl.dat").isFile()
                && new File(bytedance, "midu/BV119_24k_streaming/ptl.dat").isFile();
    }

    public String getByteDanceVoice() {
        String voice = sp.getString(KEY_BYTEDANCE_VOICE, "BV119_24k");
        return voice == null || voice.trim().isEmpty() ? "BV119_24k" : voice.trim();
    }

    public void setByteDanceVoice(String voice) {
        if (voice == null || voice.trim().isEmpty()) return;
        sp.edit().putString(KEY_BYTEDANCE_VOICE, voice.trim()).apply();
    }

    public int getByteDanceSampleRate() {
        return getByteDanceVoice().contains("_24k") ? 24000 : 16000;
    }

    public List<TtsVoice> getByteDanceVoices() {
        List<TtsVoice> result = new ArrayList<>();
        for (TtsVoice voice : getAllVoices()) {
            if (TtsSource.byteDanceOffline().id.equals(voice.sourceId)) result.add(voice);
        }
        return result;
    }

    public String getAudioFormat() {
        TtsSource source = getActiveSource();
        if (source != null && source.audioFormat != null && !source.audioFormat.trim().isEmpty() && !TtsSource.TYPE_SYSTEM.equals(source.type)) {
            return source.audioFormat.trim();
        }
        return sp.getString(KEY_AUDIO_FORMAT, DEFAULT_AUDIO_FORMAT);
    }

    public void setAudioFormat(String format) {
        if (format == null || format.trim().isEmpty()) return;
        sp.edit().putString(KEY_AUDIO_FORMAT, format.trim()).apply();
        TtsSource source = getActiveSource();
        if (source != null && !TtsSource.TYPE_SYSTEM.equals(source.type)) {
            source.audioFormat = format.trim();
            upsertSource(source, false);
        }
    }

    public String voiceForLang(String lang) {
        if (SpeechSegment.LANG_MY.equals(lang)) return getMyVoice();
        return getZhVoice();
    }

    public String getActiveSourceId() {
        String id = sp.getString(KEY_ACTIVE_SOURCE_ID, "");
        if (id == null || id.trim().isEmpty()) {
            id = sp.getBoolean(KEY_MS_ENABLED, false)
                    ? TtsSource.builtinMsTranslator().id
                    : TtsSource.edgeWebSocketTemplate().id;
            setActiveSourceId(id);
        }
        return id;
    }

    public void setActiveSourceId(String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            sourceId = TtsSource.edgeWebSocketTemplate().id;
        }
        String finalId = sourceId.trim();
        List<TtsSource> sources = getSources();
        boolean has = false;
        for (TtsSource source : sources) {
            if (finalId.equals(source.id)) has = true;
            source.enabled = finalId.equals(source.id);
        }
        if (!has) {
            finalId = TtsSource.edgeWebSocketTemplate().id;
            for (TtsSource source : sources) source.enabled = finalId.equals(source.id);
        }
        saveSources(sources);
        TtsSource selected = null;
        for (TtsSource source : sources) {
            if (finalId.equals(source.id)) {
                selected = source;
                break;
            }
        }
        sp.edit()
                .putString(KEY_ACTIVE_SOURCE_ID, finalId)
                .putBoolean(KEY_MS_ENABLED, selected != null && TtsSource.TYPE_MS_TRANSLATOR.equals(selected.type))
                .apply();
        TtsCircuitBreaker.reset(spContext(), finalId);
    }

    public TtsSource getActiveSource() {
        TtsSource source = getSourceById(getActiveSourceId());
        return source == null ? TtsSource.edgeWebSocketTemplate() : source;
    }

    public TtsSource getSourceById(String id) {
        if (id == null) return null;
        for (TtsSource source : getSources()) {
            if (id.equals(source.id)) return source;
        }
        return null;
    }

    public List<TtsSource> getSources() {
        List<TtsSource> result = new ArrayList<>();
        String json = sp.getString(KEY_SOURCES_JSON, "");
        if (json != null && !json.trim().isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    TtsSource source = TtsSource.fromJson(array.optJSONObject(i));
                    if (source != null && !TtsSource.isEmpty(source.id)) result.add(source);
                }
            } catch (Exception ignored) {
            }
        }
        if (result.isEmpty()) result = defaultSources();
        return dedupeSources(result);
    }

    public void saveSources(List<TtsSource> sources) {
        JSONArray array = new JSONArray();
        try {
            for (TtsSource source : dedupeSources(sources)) {
                if (source == null) continue;
                source.normalize();
                array.put(source.toJson());
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString(KEY_SOURCES_JSON, array.toString()).apply();
    }

    public void upsertSource(TtsSource source, boolean activate) {
        if (source == null) return;
        source.normalize();
        List<TtsSource> sources = getSources();
        boolean replaced = false;
        for (int i = 0; i < sources.size(); i++) {
            if (source.id.equals(sources.get(i).id)) {
                sources.set(i, source);
                replaced = true;
                break;
            }
        }
        if (!replaced) sources.add(source);
        saveSources(sources);
        if (activate) setActiveSourceId(source.id);
    }

    public void deleteSource(String sourceId) {
        if (sourceId == null) return;
        if (TtsSource.system().id.equals(sourceId)
                || TtsSource.builtinMsTranslator().id.equals(sourceId)
                || TtsSource.edgeWebSocketTemplate().id.equals(sourceId)) return;
        List<TtsSource> sources = getSources();
        List<TtsSource> kept = new ArrayList<>();
        for (TtsSource source : sources) {
            if (!sourceId.equals(source.id)) kept.add(source);
        }
        saveSources(kept);
        if (sourceId.equals(getActiveSourceId())) {
            setActiveSourceId(TtsSource.edgeWebSocketTemplate().id);
        }
    }

    public void resetSources() {
        saveSources(defaultSources());
        setActiveSourceId(TtsSource.edgeWebSocketTemplate().id);
    }

    public void setImportedSource(String name, int voiceCount) {
        setImportedSource(name, voiceCount, SOURCE_TYPE_MS_TRANSLATOR);
    }

    public void setImportedSource(String name, int voiceCount, String sourceType) {
        sp.edit()
                .putString(KEY_IMPORTED_SOURCE_NAME, name == null ? "用户导入语音包" : name)
                .putInt(KEY_IMPORTED_VOICE_COUNT, Math.max(0, voiceCount))
                .putString(KEY_IMPORTED_SOURCE_TYPE, sourceType == null ? SOURCE_TYPE_UNKNOWN : sourceType)
                .apply();
    }

    public String getImportedSourceName() {
        return sp.getString(KEY_IMPORTED_SOURCE_NAME, "未导入");
    }

    public int getImportedVoiceCount() {
        return sp.getInt(KEY_IMPORTED_VOICE_COUNT, 0);
    }

    public String getImportedSourceType() {
        return sp.getString(KEY_IMPORTED_SOURCE_TYPE, SOURCE_TYPE_UNKNOWN);
    }

    public void saveImportedVoices(List<TtsVoice> voices) {
        JSONArray array = new JSONArray();
        if (voices != null) {
            for (TtsVoice voice : voices) {
                if (voice == null || voice.code.isEmpty()) continue;
                try {
                    array.put(voice.toJson());
                } catch (Exception ignored) {
                }
            }
        }
        sp.edit().putString(KEY_IMPORTED_VOICES_JSON, array.toString()).apply();
    }

    public List<TtsVoice> getAllVoices() {
        List<TtsVoice> list = new ArrayList<>();
        String json = sp.getString(KEY_IMPORTED_VOICES_JSON, "");
        if (json != null && !json.trim().isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    TtsVoice voice = TtsVoice.fromJson(array.optJSONObject(i));
                    if (voice != null && !voice.code.isEmpty()) list.add(voice);
                }
            } catch (Exception ignored) {
            }
        }
        if (list.isEmpty()) list.addAll(defaultVoices());
        return list;
    }

    public List<TtsVoice> getVoicesForLocalePrefix(String prefix) {
        List<TtsVoice> result = new ArrayList<>();
        if (prefix == null) prefix = "";
        for (TtsVoice voice : getAllVoices()) {
            if (voice.locale.startsWith(prefix) || voice.code.startsWith(prefix)) {
                result.add(voice);
            }
        }
        return result;
    }

    public TtsVoice findVoice(String code) {
        if (code == null) return null;
        for (TtsVoice voice : getAllVoices()) {
            if (code.equals(voice.code)) return voice;
        }
        return null;
    }

    public String voiceDisplayName(String code) {
        TtsVoice voice = findVoice(code);
        return voice == null ? code : voice.displayName();
    }

    public String exportActiveSourcePretty() {
        try {
            return getActiveSource().toJson().toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public String exportAllSourcesPretty() {
        JSONArray array = new JSONArray();
        try {
            for (TtsSource source : getSources()) array.put(source.toJson());
            JSONObject wrapper = new JSONObject();
            wrapper.put("tsddSpeechSources", 1);
            wrapper.put("sources", array);
            return wrapper.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static List<TtsVoice> defaultVoices() {
        List<TtsVoice> list = new ArrayList<>();
        list.add(new TtsVoice(DEFAULT_ZH_VOICE, "晓晓", "zh-CN", TtsVoice.GENDER_FEMALE));
        list.add(new TtsVoice(DEFAULT_ZH_MALE_VOICE, "云希", "zh-CN", TtsVoice.GENDER_MALE));
        list.add(new TtsVoice(DEFAULT_ZH_MULTI_VOICE, "晓辰 多语言", "zh-CN", TtsVoice.GENDER_FEMALE));
        list.add(new TtsVoice("zh-CN-XiaoxiaoMultilingualNeural", "晓晓 多语言", "zh-CN", TtsVoice.GENDER_FEMALE));
        list.add(new TtsVoice(DEFAULT_MY_VOICE, "Nilar", "my-MM", TtsVoice.GENDER_FEMALE));
        list.add(new TtsVoice(DEFAULT_MY_MALE_VOICE, "Thiha", "my-MM", TtsVoice.GENDER_MALE));
        list.add(new TtsVoice("en-US-JennyNeural", "Jenny", "en-US", TtsVoice.GENDER_FEMALE));
        list.add(new TtsVoice("en-US-GuyNeural", "Guy", "en-US", TtsVoice.GENDER_MALE));
        return list;
    }

    private void migrateEdgeEngineV1() {
        if (sp.getBoolean(KEY_EDGE_ENGINE_MIGRATION_V1, false)) return;
        String activeId = sp.getString(KEY_ACTIVE_SOURCE_ID, "");
        if (activeId == null || activeId.trim().isEmpty()
                || TtsSource.system().id.equals(activeId)
                || TtsSource.edgeWebSocketTemplate().id.equals(activeId)) {
            setActiveSourceId(TtsSource.edgeWebSocketTemplate().id);
        }
        sp.edit().putBoolean(KEY_EDGE_ENGINE_MIGRATION_V1, true).apply();
    }

    private void ensureDefaultSources() {
        String json = sp.getString(KEY_SOURCES_JSON, "");
        if (json == null || json.trim().isEmpty()) {
            saveSources(defaultSources());
            if (sp.getString(KEY_ACTIVE_SOURCE_ID, "").isEmpty()) {
                setActiveSourceId(sp.getBoolean(KEY_MS_ENABLED, false)
                        ? TtsSource.builtinMsTranslator().id
                        : TtsSource.edgeWebSocketTemplate().id);
            }
        }
    }

    private static List<TtsSource> defaultSources() {
        List<TtsSource> list = new ArrayList<>();
        list.add(TtsSource.edgeWebSocketTemplate());
        list.add(TtsSource.system());
        list.add(TtsSource.builtinMsTranslator());
        return list;
    }

    private static List<TtsSource> dedupeSources(List<TtsSource> sources) {
        Map<String, TtsSource> map = new LinkedHashMap<>();
        // Ensure required built-ins always exist.
        map.put(TtsSource.edgeWebSocketTemplate().id, TtsSource.edgeWebSocketTemplate());
        map.put(TtsSource.system().id, TtsSource.system());
        map.put(TtsSource.builtinMsTranslator().id, TtsSource.builtinMsTranslator());
        if (sources != null) {
            for (TtsSource source : sources) {
                if (source == null) continue;
                source.normalize();
                map.put(source.id, source);
            }
        }
        return new ArrayList<>(map.values());
    }

    private Context spContext() {
        return appContext;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
