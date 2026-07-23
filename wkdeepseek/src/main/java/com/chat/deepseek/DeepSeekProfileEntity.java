package com.chat.deepseek;

import java.util.ArrayList;
import java.util.List;

final class DeepSeekProfileEntity {
    public String uid;
    public Object native_languages;
    public Object learning_languages;
    public String native_language;
    public String learning_language;

    String nativeLanguageText() {
        return join(native_languages, native_language);
    }

    String learningLanguageText() {
        return join(learning_languages, learning_language);
    }

    @SuppressWarnings("unchecked")
    private String join(Object value, String fallback) {
        ArrayList<String> out = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) add(out, item == null ? "" : String.valueOf(item));
        } else if (value != null) {
            add(out, String.valueOf(value));
        }
        if (out.isEmpty()) add(out, fallback);
        StringBuilder joined = new StringBuilder();
        for (String item : out) {
            if (joined.length() > 0) joined.append(", ");
            joined.append(item);
        }
        return joined.toString();
    }

    private void add(ArrayList<String> out, String text) {
        if (text == null) return;
        String clean = text.trim();
        if (clean.isEmpty() || "null".equalsIgnoreCase(clean)) return;
        clean = clean.replace("[", " ").replace("]", " ").replace("\"", " ");
        String[] parts = clean.split("[,，/\\s]+");
        for (String part : parts) {
            String item = part == null ? "" : part.trim();
            if (!item.isEmpty() && !out.contains(item)) out.add(item);
        }
    }
}
