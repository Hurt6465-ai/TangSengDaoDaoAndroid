package com.chat.learning;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 汉字笔顺/描红入口。
 * 当前版本先提供全屏入口和逐字列表；真实笔顺动画后续接 HanziWriter / makemeahanzi 数据。
 */
public class WordStrokeActivity extends AppCompatActivity {
    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_PINYIN = "pinyin";

    private static final int COLOR_BG_TOP = 0xFFF8FBFF;
    private static final int COLOR_BG_BOTTOM = 0xFFEAF4FF;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_SUB = 0xFF64748B;
    private static final int COLOR_BLUE = 0xFF2563EB;

    private String word;
    private String pinyin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG_TOP);
        window.setNavigationBarColor(COLOR_BG_BOTTOM);

        word = getIntent().getStringExtra(EXTRA_WORD);
        pinyin = getIntent().getStringExtra(EXTRA_PINYIN);
        if (word == null || word.length() == 0) word = "你";
        if (pinyin == null) pinyin = "";
        build();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(pageBg());
        setContentView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text(getString(R.string.stroke_title), 24, COLOR_TEXT, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text(getString(R.string.stroke_subtitle), 14, COLOR_SUB, false);
        sub.setPadding(0, dp(8), 0, dp(20));
        sub.setLineSpacing(dp(3), 1f);
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        TextView wordView = text(word, 52, COLOR_TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        wordView.setBackground(rounded(0xFFFFFFFF, dp(28), 0xFFE2E8F0, 1));
        wordView.setPadding(dp(10), dp(26), dp(10), dp(26));
        root.addView(wordView, new LinearLayout.LayoutParams(-1, -2));

        if (pinyin.length() > 0) {
            TextView py = text(pinyin, 18, COLOR_BLUE, true);
            py.setGravity(Gravity.CENTER);
            py.setPadding(0, dp(12), 0, dp(20));
            root.addView(py, new LinearLayout.LayoutParams(-1, -2));
        }

        TextView charsTitle = text(getString(R.string.stroke_chars_title), 17, COLOR_TEXT, true);
        charsTitle.setPadding(0, dp(8), 0, dp(10));
        root.addView(charsTitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout chars = new LinearLayout(this);
        chars.setOrientation(LinearLayout.HORIZONTAL);
        chars.setGravity(Gravity.CENTER);
        root.addView(chars, new LinearLayout.LayoutParams(-1, dp(92)));
        for (int i = 0; i < word.length(); i++) {
            String ch = String.valueOf(word.charAt(i));
            TextView c = text(ch, 34, COLOR_TEXT, true);
            c.setGravity(Gravity.CENTER);
            c.setBackground(rounded(0xFFFFFFFF, dp(20), 0xFFE5E7EB, 1));
            c.setOnClickListener(v -> Toast.makeText(this, getString(R.string.stroke_data_missing), Toast.LENGTH_SHORT).show());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.setMargins(dp(10), 0, 0, 0);
            chars.addView(c, lp);
        }

        TextView todo = text(getString(R.string.stroke_data_missing), 14, COLOR_SUB, false);
        todo.setLineSpacing(dp(4), 1f);
        todo.setPadding(0, dp(22), 0, dp(18));
        root.addView(todo, new LinearLayout.LayoutParams(-1, -2));

        TextView speak = button(getString(R.string.stroke_speak_word));
        speak.setOnClickListener(v -> LearningTtsBridge.speak(this, word, LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_WORD));
        root.addView(speak, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private TextView text(String v, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String v) {
        TextView t = text(v, 15, COLOR_BLUE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(0xFFEFF6FF, dp(18), 0, 0));
        return t;
    }

    private GradientDrawable pageBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
