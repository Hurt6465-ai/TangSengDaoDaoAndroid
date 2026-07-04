package com.chat.speech.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.speech.SpeechCache;
import com.chat.speech.SpeechManager;
import com.chat.speech.SpeechPrefs;
import com.chat.speech.importer.MultiTtsImporter;
import com.chat.speech.importer.TtsSourceConfigImporter;
import com.chat.speech.model.TtsSource;
import com.chat.speech.model.TtsVoice;

import org.json.JSONObject;

import java.util.List;

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

        addHeader();
        addSourceManagerSection();
        addVoiceSection();
        addControlSection();
        addTestSection();
        addCacheSection();
        addWarnText();
    }

    private void addHeader() {
        TextView title = text("语音朗读", 28, Color.rgb(17, 24, 39), true);
        root.addView(title);
        TextView sub = text("独立 wkspeech 插件。现在支持用户导入/编辑 TTS 源 JSON，不再把接口全部写死在 App 里。聊天窗口、学习页、网页脚本都可以走 SpeechManager.speak(context, text)。", 13, Color.rgb(107, 114, 128), false);
        sub.setPadding(0, dp(8), 0, dp(14));
        root.addView(sub);
        TtsSource active = prefs.getActiveSource();
        root.addView(card("当前状态", statusText(active), "切换语音源", this::chooseSource));
    }

    private void addSourceManagerSection() {
        root.addView(section("TTS 源 / 语音包"));
        root.addView(card("选择语音源", sourceListSummary(), "选择", this::chooseSource));
        root.addView(card("导入 TTS 源配置 / MultiTTS 包", "支持两类文件：\n1. 唐僧 TTS 源 JSON，可热更新 endpoint、headers、audioFormat、签名参数。\n2. MultiTTS 微软翻译 zip/json/yaml，用于导入发音人和音频格式。", "选择文件", this::openImportPicker));
        root.addView(card("编辑当前源 JSON", "适合以后微软接口小改时直接前端修改；如果签名算法彻底变了，仍然需要新引擎或脚本源。", "编辑", this::editActiveSourceJson));
        root.addView(card("复制当前源模板", "复制当前源 JSON 到剪贴板，方便你发给用户或放到 GitHub/群里更新。", "复制", () -> copyText("tts_source.json", prefs.exportActiveSourcePretty())));
        root.addView(card("复制全部源配置", "导出系统源、微软兼容源、Edge WebSocket 预留源、自定义源。", "复制", () -> copyText("tts_sources.json", prefs.exportAllSourcesPretty())));
        root.addView(card("重置语音源", "只重置 TTS 源配置，不删除已经导入的发音人列表。", "重置", () -> confirmResetSources()));
        root.addView(sourceCard("系统 TTS", "调用手机系统或用户已安装的第三方 Android TTS 引擎。最稳定，不走服务器。", TtsSource.TYPE_SYSTEM));
        root.addView(sourceCard("微软翻译兼容源", "当前主力在线源。App 直连用户侧网络，不压你的服务器；非官方，可能失效。", TtsSource.TYPE_MS_TRANSLATOR));
        root.addView(sourceCard("Edge TTS WebSocket", "预留第二在线备用源。需要后续接 OkHttp WebSocket 引擎；配置可以先导入保存。", TtsSource.TYPE_EDGE_WEBSOCKET));
        root.addView(sourceCard("自定义 HTTP / WebSocket", "后续可给火山、阿里、腾讯、OpenAI、自建接口使用；第一版先保存配置，不执行未知接口。", TtsSource.TYPE_CUSTOM_HTTP));
        root.addView(sourceCard("离线语音包", "Piper / sherpa-onnx / 微软离线 / 火山离线需要 native 引擎或模型包，不能只靠 JSON。", TtsSource.TYPE_OFFLINE_RESERVED));
    }

    private void addVoiceSection() {
        root.addView(section("发音人"));
        root.addView(card("中文发音人", prefs.voiceDisplayName(prefs.getZhVoice()) + "\n" + prefs.getZhVoice(), "选择", () -> chooseVoice("zh", "选择中文发音人", true)));
        root.addView(card("缅语发音人", prefs.voiceDisplayName(prefs.getMyVoice()) + "\n" + prefs.getMyVoice(), "选择", () -> chooseVoice("my", "选择缅语发音人", false)));
        root.addView(card("全部发音人", "当前可选发音人 " + prefs.getAllVoices().size() + " 个。导入 MultiTTS 微软全语言包后，这里会按 locale/code 自动识别中文、缅语等发音人。", "查看", this::showAllVoices));
    }

    private void addControlSection() {
        root.addView(section("朗读参数"));
        root.addView(toggleRow("多语言混读", "开启后，一句话里中文走中文发音人，缅语走缅语发音人；关闭后整句使用中文发音人。", prefs.isMixedReadEnabled(), checked -> {
            prefs.setMixedReadEnabled(checked);
            toast(checked ? "已开启混读" : "已关闭混读");
        }));
        root.addView(seekRow("语速", "在线源和系统 TTS 都会尽量应用。建议口语练习 -10% 到 -25%。", prefs.getRatePercent(), -50, 80, value -> prefs.setRatePercent(value)));
        root.addView(seekRow("音调", "正数更尖，负数更低。多数场景保持 0% 最自然。", prefs.getPitchPercent(), -50, 50, value -> prefs.setPitchPercent(value)));
    }

    private void addTestSection() {
        root.addView(section("试听"));
        root.addView(card("中文试听", "使用当前语音源、中文发音人、语速、音调。", "播放", () -> SpeechManager.speak(this, "你好，欢迎使用唐僧叨叨学习语音。")));
        root.addView(card("缅语试听", "使用当前语音源、缅语发音人、语速、音调。", "播放", () -> SpeechManager.speak(this, "မင်္ဂလာပါ။ ကျွန်မ မြန်မာစကား လေ့ကျင့်နေပါတယ်။")));
        root.addView(card("一句话混读测试", "当前是分段准流式：先按语言切段，每段合成到缓存，再顺序播放。下一步可优化成第一段完成就先播。", "播放", () -> SpeechManager.speak(this, "你好，我们开始练习口语。 မင်္ဂလာပါ၊ စကားပြော လေ့ကျင့်ကြမယ်။")));
        root.addView(card("停止播放", "停止当前系统 TTS 或在线音频播放。", "停止", () -> SpeechManager.get(this).stop()));
    }

    private void addCacheSection() {
        root.addView(section("缓存"));
        root.addView(card("清理语音缓存", "在线源合成后的 mp3 会缓存在本机，重复句子不会反复请求。", "清理", () -> {
            long size = SpeechCache.clear(this);
            toast("已清理缓存 " + (size / 1024) + " KB");
        }));
    }

    private void addWarnText() {
        TextView warn = text("说明：前端直连在线源不会压你的服务器，但非官方源可能失效或限流。JSON 配置能解决 endpoint、格式、headers 等小改；如果签名算法或协议彻底变了，需要升级引擎或做 JS 沙盒源。离线模型类语音包需要 native 引擎和模型文件，不是普通配置。", 12, Color.rgb(107, 114, 128), false);
        warn.setPadding(dp(4), dp(12), dp(4), 0);
        root.addView(warn);
    }

    private String statusText(TtsSource active) {
        if (active == null) active = TtsSource.system();
        return "当前源：" + active.name
                + "\n类型：" + active.displayType()
                + "\n导入包：" + prefs.getImportedSourceName()
                + "\n发音人：" + prefs.getImportedVoiceCount() + " 个"
                + "\n中文：" + prefs.voiceDisplayName(prefs.getZhVoice())
                + "\n缅语：" + prefs.voiceDisplayName(prefs.getMyVoice())
                + "\n语速：" + signed(prefs.getRatePercent()) + "%"
                + "，音调：" + signed(prefs.getPitchPercent()) + "%"
                + "\n混读：" + (prefs.isMixedReadEnabled() ? "开启" : "关闭")
                + "\n格式：" + prefs.getAudioFormat();
    }

    private String sourceListSummary() {
        StringBuilder builder = new StringBuilder();
        List<TtsSource> sources = prefs.getSources();
        TtsSource active = prefs.getActiveSource();
        for (TtsSource source : sources) {
            if (builder.length() > 0) builder.append("\n");
            builder.append(active != null && source.id.equals(active.id) ? "✓ " : "  ")
                    .append(source.name).append(" · ").append(source.displayType());
        }
        return builder.toString();
    }

    private View sourceCard(String title, String desc, String type) {
        int count = 0;
        for (TtsSource source : prefs.getSources()) {
            if (type.equals(source.type)) count++;
        }
        return card(title, desc + "\n当前配置数：" + count, count > 0 ? "查看" : "了解", () -> showSourcesByType(type, title));
    }

    private void chooseSource() {
        List<TtsSource> list = prefs.getSources();
        CharSequence[] items = new CharSequence[list.size()];
        TtsSource active = prefs.getActiveSource();
        for (int i = 0; i < list.size(); i++) {
            TtsSource source = list.get(i);
            String mark = active != null && source.id.equals(active.id) ? "✓ " : "";
            items[i] = mark + source.name + "\n" + source.displayType() + " · " + source.id;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择语音源")
                .setItems(items, (dialog, which) -> {
                    TtsSource source = list.get(which);
                    if (!TtsSource.TYPE_SYSTEM.equals(source.type) && !TtsSource.TYPE_MS_TRANSLATOR.equals(source.type)) {
                        toast("该源已保存，但朗读引擎还未接入，播放时会自动兜底系统 TTS");
                    }
                    prefs.setActiveSourceId(source.id);
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSourcesByType(String type, String title) {
        List<TtsSource> all = prefs.getSources();
        int count = 0;
        for (TtsSource source : all) if (type.equals(source.type)) count++;
        if (count == 0) {
            toast("暂无该类型配置");
            return;
        }
        CharSequence[] items = new CharSequence[count];
        int index = 0;
        for (TtsSource source : all) {
            if (!type.equals(source.type)) continue;
            items[index++] = source.name + "\n" + source.id + "\n" + source.shortSummary();
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, null)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void editActiveSourceJson() {
        TtsSource active = prefs.getActiveSource();
        if (active == null) active = TtsSource.system();
        EditText editText = new EditText(this);
        editText.setText(prefs.exportActiveSourcePretty());
        editText.setMinLines(12);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setSingleLine(false);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setTextSize(12);
        int pad = dp(12);
        editText.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("编辑当前源 JSON")
                .setView(editText)
                .setPositiveButton("保存并启用", (dialog, which) -> {
                    try {
                        TtsSourceConfigImporter.Result result = TtsSourceConfigImporter.importText(this, editText.getText().toString());
                        toast("已保存 TTS 源：" + result.sourceCount + " 个");
                        render();
                    } catch (Exception e) {
                        toast("保存失败：" + e.getMessage());
                    }
                })
                .setNeutralButton("复制", (dialog, which) -> copyText("tts_source.json", editText.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmResetSources() {
        new AlertDialog.Builder(this)
                .setTitle("重置语音源")
                .setMessage("会恢复系统 TTS、微软翻译兼容源、Edge WebSocket 模板。不会删除发音人列表。")
                .setPositiveButton("重置", (dialog, which) -> {
                    prefs.resetSources();
                    toast("已重置");
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void chooseVoice(String localePrefix, String title, boolean chinese) {
        List<TtsVoice> list = prefs.getVoicesForLocalePrefix(localePrefix);
        if (list.isEmpty()) list = prefs.getAllVoices();
        if (list.isEmpty()) {
            toast("暂无发音人，请先导入语音包");
            return;
        }
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) {
            TtsVoice voice = list.get(i);
            items[i] = voice.displayName() + "\n" + voice.code;
        }
        List<TtsVoice> finalList = list;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> {
                    TtsVoice voice = finalList.get(which);
                    if (chinese) prefs.setZhVoice(voice.code); else prefs.setMyVoice(voice.code);
                    toast("已选择：" + voice.displayName());
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAllVoices() {
        List<TtsVoice> list = prefs.getAllVoices();
        if (list.isEmpty()) {
            toast("暂无发音人");
            return;
        }
        CharSequence[] items = new CharSequence[Math.min(list.size(), 120)];
        for (int i = 0; i < items.length; i++) {
            TtsVoice voice = list.get(i);
            items[i] = voice.displayName() + "\n" + voice.code + "\n" + voice.sourceName;
        }
        new AlertDialog.Builder(this)
                .setTitle("全部发音人" + (list.size() > items.length ? "（仅显示前 " + items.length + " 个）" : ""))
                .setItems(items, null)
                .setPositiveButton("关闭", null)
                .show();
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
                try {
                    TtsSourceConfigImporter.Result sourceResult = TtsSourceConfigImporter.importFromUri(this, uri);
                    toast("TTS 源导入成功：" + sourceResult.sourceCount + " 个");
                } catch (Exception notSourceConfig) {
                    MultiTtsImporter.Result result = MultiTtsImporter.importFromUri(this, uri);
                    if (SpeechPrefs.SOURCE_TYPE_MS_TRANSLATOR.equals(result.sourceType)) {
                        toast("导入成功：" + result.sourceName + "，发音人 " + result.voiceCount + " 个");
                    } else {
                        toast("已导入配置，但当前还没有适配该语音包引擎");
                    }
                }
                render();
            } catch (Exception e) {
                toast("导入失败：" + e.getMessage());
            }
        }
    }

    private void copyText(String label, String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(label, value == null ? "" : value));
            toast("已复制");
        }
    }

    private View section(String name) {
        TextView v = text(name, 16, Color.rgb(17, 24, 39), true);
        v.setPadding(0, dp(10), 0, dp(8));
        return v;
    }

    private View toggleRow(String title, String desc, boolean checked, ToggleCallback callback) {
        LinearLayout card = baseCard();
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView titleView = text(title, 17, Color.rgb(17, 24, 39), true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        line.addView(titleView, titleLp);
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        line.addView(sw);
        card.addView(line);
        TextView descView = text(desc, 13, Color.rgb(107, 114, 128), false);
        descView.setPadding(0, dp(6), 0, 0);
        card.addView(descView);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onChanged(isChecked));
        card.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        return card;
    }

    private View seekRow(String title, String desc, int value, int min, int max, SeekCallback callback) {
        LinearLayout card = baseCard();
        TextView titleView = text(title + "：" + signed(value) + "%", 17, Color.rgb(17, 24, 39), true);
        card.addView(titleView);
        TextView descView = text(desc, 13, Color.rgb(107, 114, 128), false);
        descView.setPadding(0, dp(6), 0, dp(8));
        card.addView(descView);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(value - min);
        card.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(42)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                titleView.setText(title + "：" + signed(v) + "%");
                if (fromUser) callback.onChanged(v);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return card;
    }

    private View card(String title, String desc, String action, Runnable click) {
        LinearLayout card = baseCard();
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

    private LinearLayout baseCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(Color.WHITE, dp(18), Color.rgb(232, 237, 246), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
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

    private String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private interface ToggleCallback { void onChanged(boolean checked); }
    private interface SeekCallback { void onChanged(int value); }
}
