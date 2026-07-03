package com.chat.speech.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.speech.SpeechCache;
import com.chat.speech.SpeechManager;
import com.chat.speech.SpeechPrefs;
import com.chat.speech.importer.MultiTtsImporter;

public class SpeechSettingsActivity extends Activity {
    private static final int REQ_IMPORT = 8201;
    private SpeechPrefs prefs;
    private LinearLayout root;

    public static void open(Context context) {
        context.startActivity(new Intent(context, SpeechSettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new SpeechPrefs(this);
        render();
    }

    private void render() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 249, 252));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scrollView);

        TextView title = text("语音朗读", 28, Color.rgb(17, 24, 39), true);
        root.addView(title);
        TextView sub = text("独立 wkspeech 插件：系统 TTS 兜底 + 微软翻译兼容源测试。微软兼容源不是官方稳定 API，可能失效或限流。", 13, Color.rgb(107, 114, 128), false);
        sub.setPadding(0, dp(8), 0, dp(14));
        root.addView(sub);

        root.addView(card("当前来源", statusText(), prefs.isMsEnabled() ? "关闭微软兼容源" : "启用微软兼容源", () -> {
            prefs.setMsEnabled(!prefs.isMsEnabled());
            toast(prefs.isMsEnabled() ? "已启用微软兼容源" : "已切换系统 TTS 兜底");
            render();
        }));

        root.addView(card("导入 MultiTTS 微软包", "支持导入你发的【在线】微软翻译 zip/json。导入后会启用微软兼容源，并读取 audioFormat / 发音人数量。", "选择文件", this::openImportPicker));

        root.addView(section("中文发音人"));
        root.addView(choice("女声：晓晓", "zh-CN-XiaoxiaoNeural", () -> { prefs.setZhVoice(SpeechPrefs.DEFAULT_ZH_VOICE); toast("中文发音人：晓晓"); render(); }));
        root.addView(choice("男声：云希", "zh-CN-YunxiNeural", () -> { prefs.setZhVoice(SpeechPrefs.DEFAULT_ZH_MALE_VOICE); toast("中文发音人：云希"); render(); }));
        root.addView(choice("多语言：晓辰", "zh-CN-XiaochenMultilingualNeural", () -> { prefs.setZhVoice(SpeechPrefs.DEFAULT_ZH_MULTI_VOICE); toast("中文发音人：晓辰多语言"); render(); }));

        root.addView(section("缅语发音人"));
        root.addView(choice("女声：နီလာ / Nilar", "my-MM-NilarNeural", () -> { prefs.setMyVoice(SpeechPrefs.DEFAULT_MY_VOICE); toast("缅语发音人：Nilar"); render(); }));
        root.addView(choice("男声：သီဟ / Thiha", "my-MM-ThihaNeural", () -> { prefs.setMyVoice(SpeechPrefs.DEFAULT_MY_MALE_VOICE); toast("缅语发音人：Thiha"); render(); }));

        root.addView(section("试听"));
        root.addView(card("中文试听", "使用当前中文发音人朗读。", "播放", () -> SpeechManager.speak(this, "你好，欢迎使用唐僧叨叨学习语音。")));
        root.addView(card("缅语试听", "使用当前缅语发音人朗读。", "播放", () -> SpeechManager.speak(this, "မင်္ဂလာပါ။ ကျွန်မ မြန်မာစကား လေ့ကျင့်နေပါတယ်။")));
        root.addView(card("一句话双发音人", "一句话里同时有中文和缅语时，会按文字切段：中文段走中文发音人，缅语段走缅语发音人，然后顺序播放。", "播放", () -> SpeechManager.speak(this, "你好，我们开始练习口语。 မင်္ဂလာပါ၊ စကားပြော လေ့ကျင့်ကြမယ်။")));

        root.addView(card("清理语音缓存", "微软兼容源合成后的 mp3 会缓存在本机，重复句子不重新请求。", "清理", () -> {
            long size = SpeechCache.clear(this);
            toast("已清理缓存 " + (size / 1024) + " KB");
        }));

        TextView warn = text("说明：这个插件不会读取唐僧登录 token、聊天记录、联系人、相册、定位，也不会自动把文本上传到除所选 TTS 源以外的地址。微软兼容源用于测试，正式上线建议继续保留系统 TTS 兜底。", 12, Color.rgb(107, 114, 128), false);
        warn.setPadding(dp(4), dp(12), dp(4), 0);
        root.addView(warn);
    }

    private String statusText() {
        return "模式：" + (prefs.isMsEnabled() ? "微软翻译兼容源" : "系统 TTS")
                + "\n中文：" + prefs.getZhVoice()
                + "\n缅语：" + prefs.getMyVoice()
                + "\n格式：" + prefs.getAudioFormat()
                + "\n导入：" + prefs.getImportedSourceName() + "，发音人约 " + prefs.getImportedVoiceCount() + " 个";
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                MultiTtsImporter.Result result = MultiTtsImporter.importFromUri(this, uri);
                toast("导入成功：" + result.sourceName + "，发音人约 " + result.voiceCount + " 个");
                render();
            } catch (Exception e) {
                toast("导入失败：" + e.getMessage());
            }
        }
    }

    private View section(String name) {
        TextView v = text(name, 16, Color.rgb(17, 24, 39), true);
        v.setPadding(0, dp(10), 0, dp(8));
        return v;
    }

    private View choice(String title, String code, Runnable click) {
        String current = code.startsWith("my-") ? prefs.getMyVoice() : prefs.getZhVoice();
        String action = code.equals(current) ? "已选" : "选择";
        return card(title, code, action, click);
    }

    private View card(String title, String desc, String action, Runnable click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(Color.WHITE, dp(18), Color.rgb(232, 237, 246), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);

        TextView titleView = text(title, 17, Color.rgb(17, 24, 39), true);
        card.addView(titleView);
        TextView descView = text(desc, 13, Color.rgb(107, 114, 128), false);
        descView.setPadding(0, dp(6), 0, dp(12));
        card.addView(descView);
        TextView btn = text(action, 14, Color.WHITE, true);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(rounded(Color.rgb(24, 119, 242), dp(14), Color.TRANSPARENT, 0));
        card.addView(btn, new LinearLayout.LayoutParams(-1, dp(42)));
        card.setOnClickListener(v -> click.run());
        btn.setOnClickListener(v -> click.run());
        return card;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(s);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setLineSpacing(dp(2), 1f);
        if (bold) textView.setTypeface(Typeface.DEFAULT_BOLD);
        return textView;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
