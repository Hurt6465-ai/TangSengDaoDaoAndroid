package com.chat.learning.data;

import android.content.Context;

import com.chat.learning.model.LearningCategory;
import com.chat.learning.model.WordItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 词库读取：优先 assets 静态 JSON，后续可在这里加 files/learning 远程覆盖。 */
public class WordRepository {
    private final Context app;

    public WordRepository(Context context) {
        app = context.getApplicationContext();
    }

    public List<LearningCategory> loadCategories() {
        try {
            String json = readAsset("learning/words/index.json");
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("categories");
            ArrayList<LearningCategory> out = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    out.add(new LearningCategory(
                            o.optString("id"),
                            o.optString("title"),
                            o.optString("subtitle"),
                            o.optInt("count"),
                            o.optString("cover"),
                            o.optString("action", "背单词")
                    ));
                }
            }
            if (!out.isEmpty()) return out;
        } catch (Throwable ignored) {
        }
        return fallbackCategories();
    }

    public List<WordItem> loadWords(String categoryId) {
        String safeId = categoryId == null || categoryId.length() == 0 ? "greeting" : categoryId;
        try {
            String json = readAsset("learning/words/" + safeId + ".json");
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("items");
            ArrayList<WordItem> out = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    WordItem item = new WordItem();
                    item.id = o.optString("id");
                    item.categoryId = root.optString("categoryId", safeId);
                    item.word = o.optString("word");
                    item.pinyin = o.optString("pinyin");
                    item.example = o.optString("example");
                    item.examplePinyin = o.optString("examplePinyin");
                    item.audio = o.optString("audio");
                    item.level = o.optString("level");
                    readMap(o.optJSONObject("translations"), item.translations);
                    readMap(o.optJSONObject("exampleTranslations"), item.exampleTranslations);
                    out.add(item);
                }
            }
            if (!out.isEmpty()) return out;
        } catch (Throwable ignored) {
        }
        return fallbackWords(safeId);
    }

    private void readMap(JSONObject object, java.util.Map<String, String> target) {
        if (object == null || target == null) return;
        JSONArray names = object.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            target.put(key, object.optString(key));
        }
    }

    private String readAsset(String path) throws Exception {
        InputStream input = app.getAssets().open(path);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
        input.close();
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private List<LearningCategory> fallbackCategories() {
        ArrayList<LearningCategory> list = new ArrayList<>();
        list.add(new LearningCategory("greeting", "基础问候", "你好、谢谢、再见等高频词", 8, "", "背单词"));
        list.add(new LearningCategory("work", "工作求职", "面试、岗位、工资、经验", 0, "", "即将上线"));
        list.add(new LearningCategory("food", "吃饭点餐", "点餐、买东西、价格", 0, "", "即将上线"));
        return list;
    }

    private List<WordItem> fallbackWords(String categoryId) {
        ArrayList<WordItem> list = new ArrayList<>();
        String[][] words = new String[][]{
                {"greeting_001", "你好", "nǐ hǎo", "မင်္ဂလာပါ", "Hello", "你好，很高兴认识你。"},
                {"greeting_002", "谢谢", "xiè xie", "ကျေးဇူးတင်ပါတယ်", "Thank you", "谢谢你的帮助。"},
                {"greeting_003", "再见", "zài jiàn", "နောက်မှတွေ့မယ်", "Goodbye", "明天再见。"},
                {"greeting_004", "可以", "kě yǐ", "ရပါတယ်", "Okay / can", "这样可以吗？"},
                {"greeting_005", "不可以", "bù kě yǐ", "မရပါဘူး", "Cannot", "这里不可以拍照。"},
                {"greeting_006", "朋友", "péng you", "သူငယ်ချင်း", "Friend", "他是我的朋友。"},
                {"greeting_007", "学习", "xué xí", "သင်ယူသည်", "Study", "我每天学习中文。"},
                {"greeting_008", "工作", "gōng zuò", "အလုပ်", "Work", "我想找工作。"}
        };
        for (String[] row : words) {
            WordItem item = new WordItem();
            item.id = row[0];
            item.categoryId = categoryId;
            item.word = row[1];
            item.pinyin = row[2];
            item.translations.put("my", row[3]);
            item.translations.put("en", row[4]);
            item.example = row[5];
            item.level = "A1";
            list.add(item);
        }
        return list;
    }
}
