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

import com.chat.speech.PinyinNormalizer;
import com.chat.speech.SpeechCache;
import com.chat.speech.SpeechManager;
import com.chat.speech.SpeechPrefs;
import com.chat.speech.debug.SpeechDebugLog;
import com.chat.speech.importer.ByteDanceOfflinePackageImporter;
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
    private boolean showAdvanced;
    private EditText customSpeechInput;
    private EditText customHanziInput;
    private String customSpeechValue = "nǐ hǎo";
    private String customHanziValue = "你好";

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
        captureCustomTestInputs();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 249, 252));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scrollView);

        addHeader();
        addVoiceSection();
        addControlSection();
        addTestSection();
        addCacheSection();
        addAdvancedToggle();
        if (showAdvanced) addSourceManagerSection();
        addWarnText();
    }

    private void addHeader() {
        TextView title = text("语音朗读", 28, Color.rgb(17, 24, 39), true);
        root.addView(title);
        TextView sub = text("可使用字节跳动第三方离线语音包，也可使用 Edge 在线自然语音。离线包导入后不需要 AppID、Token 或网络。", 13, Color.rgb(107, 114, 128), false);
        sub.setPadding(0, dp(8), 0, dp(14));
        root.addView(sub);
        TtsSource active = prefs.getActiveSource();
        root.addView(card("当前状态", statusText(active), "切换朗读模式", this::chooseSimpleMode));
    }

    private void addSourceManagerSection() {
        root.addView(section("开发者高级设置"));
        root.addView(seekRow("音调（高级）", "Edge 按 Hz 调整，微软兼容源按百分比调整。通常保持 0 最自然。", prefs.getPitchPercent(), -50, 50, value -> prefs.setPitchPercent(value)));
        root.addView(card("选择语音源", sourceListSummary(), "选择", this::chooseSource));
        root.addView(card("导入离线语音包 / TTS 配置", "支持你提供的 MultiTTS 字节跳动离线 zip，也兼容唐僧 TTS 源 JSON 和微软翻译配置。大型离线包会在后台解压。", "选择文件", this::openImportPicker));
        root.addView(card("编辑当前源 JSON", "适合以后 Edge 或微软兼容协议小改时直接更新参数；如果签名算法彻底变化，仍然需要升级引擎。", "编辑", this::editActiveSourceJson));
        root.addView(card("复制当前源模板", "复制当前源 JSON 到剪贴板，方便你发给用户或放到 GitHub/群里更新。", "复制", () -> copyText("tts_source.json", prefs.exportActiveSourcePretty())));
        root.addView(card("复制全部源配置", "导出 Edge、微软兼容源、系统源和自定义源配置。", "复制", () -> copyText("tts_sources.json", prefs.exportAllSourcesPretty())));
        root.addView(card("重置语音源", "只重置 TTS 源配置，不删除已经导入的发音人列表。", "重置", () -> confirmResetSources()));
        root.addView(sourceCard("系统 TTS", "调用手机系统或用户已安装的第三方 Android TTS 引擎。最稳定，不走服务器。", TtsSource.TYPE_SYSTEM));
        root.addView(sourceCard("Edge 在线自然语音", "当前默认在线主源。已经接入 OkHttp WebSocket；协议变化时可通过源 JSON 更新参数。", TtsSource.TYPE_EDGE_WEBSOCKET));
        root.addView(sourceCard("微软翻译兼容源", "Edge 失败后的在线备用源。App 直连用户侧网络，不压你的服务器；同样属于非官方兼容链路。", TtsSource.TYPE_MS_TRANSLATOR));
        root.addView(sourceCard("自定义 HTTP / WebSocket", "后续可给火山、阿里、腾讯、OpenAI、自建接口使用；第一版先保存配置，不执行未知接口。", TtsSource.TYPE_CUSTOM_HTTP));
        root.addView(sourceCard("字节跳动第三方离线语音", "已接入本地模型、native 运行库和教学拼读。模型由用户导入，不放进 APK；当前只支持 arm64-v8a 中文设备。", TtsSource.TYPE_BYTEDANCE_OFFLINE));
        root.addView(sourceCard("其他离线语音包", "Piper / sherpa-onnx 等其他格式仍需要单独适配。", TtsSource.TYPE_OFFLINE_RESERVED));
    }

    private void addVoiceSection() {
        root.addView(section("发音人"));
        TtsSource active = prefs.getActiveSource();
        boolean byteDance = active != null && TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(active.type);
        if (byteDance) {
            String code = prefs.getByteDanceVoice();
            root.addView(card("中文离线发音人", prefs.voiceDisplayName(code) + "\n" + code, "选择", this::chooseByteDanceVoice));
            root.addView(card("离线模型状态", prefs.isByteDancePackageReady()
                    ? "模型已导入，可完全离线朗读中文和按拼音声调发音。"
                    : "尚未导入完整模型，请选择【离线】字节跳动.zip。", "导入", this::openImportPicker));
        } else {
            root.addView(card("中文发音人", prefs.voiceDisplayName(prefs.getZhVoice()) + "\n" + prefs.getZhVoice(), "选择", () -> chooseVoice("zh", "选择中文发音人", true)));
            root.addView(card("缅语发音人", prefs.voiceDisplayName(prefs.getMyVoice()) + "\n" + prefs.getMyVoice(), "选择", () -> chooseVoice("my", "选择缅语发音人", false)));
        }
        root.addView(card("全部发音人", "当前可选发音人 " + prefs.getAllVoices().size() + " 个。字节离线音色和在线音色会标注各自来源。", "查看", this::showAllVoices));
    }

    private void addControlSection() {
        root.addView(section("朗读参数"));
        root.addView(toggleRow("多语言混读", "开启后，一句话里中文走中文发音人，缅语走缅语发音人；关闭后整句使用中文发音人。", prefs.isMixedReadEnabled(), checked -> {
            prefs.setMixedReadEnabled(checked);
            toast(checked ? "已开启混读" : "已关闭混读");
        }));
        root.addView(seekRow("语速", "在线源和系统 TTS 都会尽量应用。建议口语练习 -10% 到 -25%。", prefs.getRatePercent(), -50, 80, value -> prefs.setRatePercent(value)));
    }

    private void addTestSection() {
        root.addView(section("试听"));
        root.addView(card("中文试听", "使用当前朗读模式、中文发音人和语速。", "播放", () -> SpeechManager.speak(this, "你好，欢迎使用唐僧叨叨学习语音。")));
        root.addView(card("缅语试听", "使用当前朗读模式、缅语发音人和语速。", "播放", () -> SpeechManager.speak(this, "မင်္ဂလာပါ။ ကျွန်မ မြန်မာစကား လေ့ကျင့်နေပါတယ်။")));
        addCustomByteDanceTestCard();
        root.addView(card("离线诊断日志", "最后记录：\n" + SpeechDebugLog.lastLine(this)
                + "\n\n即使字节 Native 子进程崩溃，这份日志仍保留。", "查看 / 复制", this::showByteDanceDebugLog));
        root.addView(card("清空离线诊断日志", "清空后重新输入文字测试，更容易判断字节前端实际收到的内容。", "清空", () -> {
            SpeechDebugLog.clear(this);
            toast("离线诊断日志已清空");
            render();
        }));
        root.addView(card("一句话混读测试", "字节离线包只含中文；遇到缅语时会自动使用在线备用源或系统 TTS。", "播放", () -> SpeechManager.speak(this, "你好，我们开始练习口语。 မင်္ဂလာပါ၊ စကားပြော လေ့ကျင့်ကြမယ်။")));
        root.addView(card("停止播放", "停止当前系统 TTS、在线音频或离线子进程中的合成。", "停止", () -> SpeechManager.get(this).stop()));
    }

    private void addCustomByteDanceTestCard() {
        LinearLayout panel = baseCard();
        panel.addView(text("字节离线自定义测试", 17, Color.rgb(17, 24, 39), true));

        TextView description = text(
                "上框可输入任意汉字或完整带调拼音。点“原样朗读”时不拆分、不替换，直接把输入交给字节 plain text 前端。下框填写对应汉字后，可测试“拼音朗读完，再完整读一次词语”。",
                13,
                Color.rgb(107, 114, 128),
                false
        );
        description.setPadding(0, dp(6), 0, dp(12));
        panel.addView(description);

        TextView inputLabel = text("测试文本（汉字或带调拼音）", 13, Color.rgb(55, 65, 81), true);
        inputLabel.setPadding(0, 0, 0, dp(6));
        panel.addView(inputLabel);
        customSpeechInput = testInput(customSpeechValue, "例如：nǐ hǎo、bà、你好");
        panel.addView(customSpeechInput, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView hanziLabel = text("对应完整汉字（仅“拼音后读汉字”使用）", 13, Color.rgb(55, 65, 81), true);
        hanziLabel.setPadding(0, dp(12), 0, dp(6));
        panel.addView(hanziLabel);
        customHanziInput = testInput(customHanziValue, "例如：你好、爸");
        panel.addView(customHanziInput, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(14), 0, 0);

        TextView rawButton = actionButton("原样朗读", Color.rgb(24, 119, 242));
        LinearLayout.LayoutParams rawParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        rawParams.setMargins(0, 0, dp(6), 0);
        buttons.addView(rawButton, rawParams);

        TextView sequenceButton = actionButton("拼音后读汉字", Color.rgb(16, 96, 184));
        LinearLayout.LayoutParams sequenceParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        sequenceParams.setMargins(dp(6), 0, 0, 0);
        buttons.addView(sequenceButton, sequenceParams);

        rawButton.setOnClickListener(v -> playCustomRawInput());
        sequenceButton.setOnClickListener(v -> playCustomPinyinThenHanzi());
        panel.addView(buttons);
        root.addView(panel);
    }

    private EditText testInput(String value, String hint) {
        EditText input = new EditText(this);
        input.setText(value == null ? "" : value);
        input.setHint(hint);
        input.setTextSize(16);
        input.setTextColor(Color.rgb(17, 24, 39));
        input.setHintTextColor(Color.rgb(156, 163, 175));
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(rounded(Color.rgb(249, 250, 251), dp(12), Color.rgb(209, 213, 219), 1));
        return input;
    }

    private TextView actionButton(String label, int color) {
        TextView button = text(label, 14, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(color, dp(13), Color.TRANSPARENT, 0));
        return button;
    }

    private void playCustomRawInput() {
        if (!prepareByteDanceForTest()) return;
        captureCustomTestInputs();
        String value = PinyinNormalizer.normalizePlainText(customSpeechValue);
        if (value.isEmpty()) {
            toast("请输入要朗读的汉字或带调拼音");
            return;
        }
        SpeechDebugLog.append(this, "ui.custom_raw text=" + value);
        toast("已原样提交：" + value);
        SpeechManager.speak(this, value, "zh-CN", "word");
    }

    private void playCustomPinyinThenHanzi() {
        if (!prepareByteDanceForTest()) return;
        captureCustomTestInputs();
        String pinyin = PinyinNormalizer.normalizeNativePinyin(customSpeechValue);
        String hanzi = PinyinNormalizer.normalizePlainText(customHanziValue);
        if (pinyin.isEmpty()) {
            toast("上框请输入完整带调拼音，例如 nǐ hǎo");
            return;
        }
        if (hanzi.isEmpty()) {
            toast("下框请输入拼音对应的完整汉字");
            return;
        }
        SpeechDebugLog.append(this, "ui.custom_sequence pinyin=" + pinyin + " hanzi=" + hanzi);
        toast("先读拼音，再读完整汉字");
        SpeechManager.speak(this, hanzi, pinyin, "zh-CN", "spelling");
    }

    private boolean prepareByteDanceForTest() {
        if (!prefs.isByteDancePackageReady()) {
            toast("请先导入字节离线语音包");
            return false;
        }
        TtsSource active = prefs.getActiveSource();
        if (active == null || !TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(active.type)) {
            prefs.setActiveSourceId(TtsSource.byteDanceOffline().id);
            toast("已自动切换到字节跳动离线语音");
        }
        return true;
    }

    private void captureCustomTestInputs() {
        if (customSpeechInput != null) {
            customSpeechValue = customSpeechInput.getText().toString();
        }
        if (customHanziInput != null) {
            customHanziValue = customHanziInput.getText().toString();
        }
    }

    private void showByteDanceDebugLog() {
        String log = SpeechDebugLog.read(this);
        EditText view = new EditText(this);
        view.setText(log);
        view.setTextSize(11);
        view.setMinLines(16);
        view.setGravity(Gravity.TOP | Gravity.START);
        view.setSingleLine(false);
        view.setTextIsSelectable(true);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int padding = dp(12);
        view.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(this)
                .setTitle("字节离线语音诊断日志")
                .setView(view)
                .setPositiveButton("复制全部", (dialog, which) ->
                        copyText("bytedance_debug.log", SpeechDebugLog.read(this)))
                .setNeutralButton("刷新", (dialog, which) -> showByteDanceDebugLog())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void addCacheSection() {
        root.addView(section("缓存"));
        root.addView(card("清理语音缓存", "在线 mp3 和离线 wav 都会缓存在本机，重复单词不会反复合成。", "清理", () -> {
            long size = SpeechCache.clear(this);
            toast("已清理缓存 " + (size / 1024) + " KB");
        }));
    }

    private void addAdvancedToggle() {
        root.addView(section("更多"));
        root.addView(card(
                "高级语音源设置",
                showAdvanced
                        ? "已展开。这里包含源 JSON、Endpoint、协议参数和 MultiTTS 导入。"
                        : "普通用户不需要设置。仅在调试 Edge 协议或导入兼容源时打开。",
                showAdvanced ? "收起" : "展开",
                () -> {
                    showAdvanced = !showAdvanced;
                    render();
                }
        ));
    }

    private void chooseSimpleMode() {
        CharSequence[] items = new CharSequence[]{
                "字节跳动离线语音\n本地模型合成，支持指定拼音和声调",
                "在线自然语音\nEdge 主源 → 微软兼容源 → 系统语音",
                "手机系统语音\n完全离线，但音质取决于手机已安装的 TTS",
                "开发者：选择具体语音源"
        };
        new AlertDialog.Builder(this)
                .setTitle("选择朗读模式")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        if (!prefs.isByteDancePackageReady()) {
                            toast("请先导入【离线】字节跳动.zip");
                            openImportPicker();
                            return;
                        }
                        prefs.setActiveSourceId(TtsSource.byteDanceOffline().id);
                        toast("已启用字节跳动离线语音");
                        render();
                    } else if (which == 1) {
                        prefs.setActiveSourceId(TtsSource.edgeWebSocketTemplate().id);
                        toast("已启用在线自然语音");
                        render();
                    } else if (which == 2) {
                        prefs.setActiveSourceId(TtsSource.system().id);
                        toast("已启用手机系统语音");
                        render();
                    } else {
                        showAdvanced = true;
                        chooseSource();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addWarnText() {
        TextView warn = text("说明：字节离线模式不需要 AppID、Token 或联网，但语音包内部仍带有 speech_license.licbag，本地引擎初始化时会读取它。当前提取的运行库只有 arm64-v8a，32 位手机会自动回退在线或系统语音。", 12, Color.rgb(107, 114, 128), false);
        warn.setPadding(dp(4), dp(12), dp(4), 0);
        root.addView(warn);
    }

    private String statusText(TtsSource active) {
        if (active == null) active = TtsSource.system();
        boolean byteDance = TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(active.type);
        String mode = TtsSource.TYPE_SYSTEM.equals(active.type)
                ? "手机系统语音"
                : byteDance ? "字节跳动离线语音" : "在线自然语音（自动备用）";
        String chineseVoice = byteDance ? prefs.getByteDanceVoice() : prefs.getZhVoice();
        return "模式：" + mode
                + "\n当前主源：" + active.name
                + "\n中文：" + prefs.voiceDisplayName(chineseVoice)
                + (byteDance ? "" : " · 缅语：" + prefs.voiceDisplayName(prefs.getMyVoice()))
                + "\n语速：" + signed(prefs.getRatePercent()) + "%"
                + " · 混读：" + (prefs.isMixedReadEnabled() ? "开启" : "关闭");
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
                    if (!TtsSource.TYPE_SYSTEM.equals(source.type)
                            && !TtsSource.TYPE_MS_TRANSLATOR.equals(source.type)
                            && !TtsSource.TYPE_EDGE_WEBSOCKET.equals(source.type)
                            && !TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(source.type)) {
                        toast("该源已保存，但朗读引擎尚未接入，播放时会自动使用推荐在线源或系统 TTS");
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
                .setMessage("会恢复 Edge 在线自然语音、微软翻译兼容源和系统 TTS，并把 Edge 设为默认。不会删除发音人列表。")
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

    private void chooseByteDanceVoice() {
        List<TtsVoice> list = prefs.getByteDanceVoices();
        if (list.isEmpty()) {
            toast("请先导入字节跳动离线语音包");
            openImportPicker();
            return;
        }
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) {
            TtsVoice voice = list.get(i);
            items[i] = voice.displayName() + "\n" + voice.code;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择中文离线发音人")
                .setItems(items, (dialog, which) -> {
                    TtsVoice voice = list.get(which);
                    prefs.setByteDanceVoice(voice.code);
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
            if (ByteDanceOfflinePackageImporter.looksLikePackage(this, uri)) {
                toast("正在导入离线模型，请勿关闭应用");
                new Thread(() -> {
                    try {
                        ByteDanceOfflinePackageImporter.Result result =
                                ByteDanceOfflinePackageImporter.importFromUri(this, uri);
                        runOnUiThread(() -> {
                            toast("离线语音包导入成功：" + result.voiceCount + " 个音色");
                            render();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> toast("离线语音包导入失败：" + error.getMessage()));
                    }
                }, "bytedance-tts-import").start();
                return;
            }
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
