package com.chat.learning.data;

import android.content.Context;
import com.chat.learning.model.LearningCategory;
import com.chat.learning.model.WordItem;
import java.util.ArrayList;
import java.util.List;

/** 旧版词库仓储兼容类。当前主页固定卡片暂不依赖它。 */
public class WordRepository {
    public WordRepository(Context context) {}
    public List<LearningCategory> loadCategories() {
        ArrayList<LearningCategory> list = new ArrayList<>();
        list.add(new LearningCategory("hsk1", "HSK 1", "150 词", 150, "", "背单词"));
        return list;
    }
    public List<WordItem> loadWords(String categoryId) {
        ArrayList<WordItem> list = new ArrayList<>();
        WordItem item = new WordItem();
        item.id = "demo_001";
        item.categoryId = categoryId;
        item.word = "你好";
        item.pinyin = "nǐ hǎo";
        item.translations.put("my", "မင်္ဂလာပါ");
        item.translations.put("en", "Hello");
        list.add(item);
        return list;
    }
}
