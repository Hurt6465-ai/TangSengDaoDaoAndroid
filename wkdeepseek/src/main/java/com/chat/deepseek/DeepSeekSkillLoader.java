package com.chat.deepseek;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;

final class DeepSeekSkillLoader {
    private DeepSeekSkillLoader() {}

    static String read(Context context, String path) {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
            }
        } catch (Exception ignored) {
            return "";
        }
        return out.toString().trim();
    }
}
