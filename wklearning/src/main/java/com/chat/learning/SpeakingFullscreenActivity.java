package com.chat.learning;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.chat.userscript.AiScriptWebActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full-screen vertical speaking feed with one phrase per page. */
public class SpeakingFullscreenActivity extends AppCompatActivity {
    public static final String EXTRA_PACK_ID = "pack_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ASSET = "asset";
    public static final String EXTRA_START_ID = "start_id";
    public static final String EXTRA_FAVORITES_ONLY = "favorites_only";

    private static final int REQUEST_IMPORT_SPEAKING_PACK = 7204;
    private static final int COLOR_BG = 0xFF11141D;
    private static final int COLOR_PANEL = 0xFF1D2230;
    private static final int COLOR_PANEL_SOFT = 0xCC252B3A;
    private static final int COLOR_TEXT = 0xFFF7F8FC;
    private static final int COLOR_SUB = 0xFFADB5C8;
    private static final int COLOR_BRAND = 0xFF8C88FF;
    private static final int COLOR_FAVORITE = 0xFFFFC34D;
    private static final int COLOR_MY = 0xFFE5E7F0;

    private String packId = "";
    private String title = "";
    private String asset = "";
    private String startId = "";
    private boolean favoritesOnly;

    private final ArrayList<SpeakingPhrase> phrases = new ArrayList<>();
    private SpeakingProgressStore progressStore;
    private int currentIndex;

    private TextView titleView;
    private TextView favoriteView;
    private TextView progressView;
    private TextView chineseView;
    private TextView pinyinView;
    private TextView myanmarView;
    private TextView sceneView;
    private TextView sceneSecondaryView;
    private TextView loopLabelView;
    private FrameLayout bodyHost;
    private SwipeFeedHost feedHost;
    private View feedCanvas;
    private TextView emptyView;

    private final Handler autoPlayHandler = new Handler(Looper.getMainLooper());
    private boolean autoPlayEnabled;
    private int autoPlayGeneration;
    private boolean phraseAnimating;

    public static void open(Context context, String packId, String title, String asset,
                            String startId) {
        Intent intent = new Intent(context, SpeakingFullscreenActivity.class);
        intent.putExtra(EXTRA_PACK_ID, packId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_ASSET, asset);
        intent.putExtra(EXTRA_START_ID, startId);
        context.startActivity(intent);
    }

    public static void openFavorites(Context context) {
        Intent intent = new Intent(context, SpeakingFullscreenActivity.class);
        intent.putExtra(EXTRA_FAVORITES_ONLY, true);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packId = safe(getIntent().getStringExtra(EXTRA_PACK_ID));
        title = safe(getIntent().getStringExtra(EXTRA_TITLE));
        asset = safe(getIntent().getStringExtra(EXTRA_ASSET));
        startId = safe(getIntent().getStringExtra(EXTRA_START_ID));
        favoritesOnly = getIntent().getBooleanExtra(EXTRA_FAVORITES_ONLY, false);
        progressStore = new SpeakingProgressStore(this);

        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);

        loadPhrases();
        buildLayout();
        if (phrases.isEmpty()) showEmpty(); else renderCurrent();
    }

    @Override
    protected void onPause() {
        stopAutoPlay();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopAutoPlay();
        if (progressStore != null) progressStore.close();
        super.onDestroy();
    }

    private void loadPhrases() {
        phrases.clear();
        if (favoritesOnly) {
            title = getString(R.string.speaking_favorites_title);
            collectFavoritePhrases();
            currentIndex = 0;
            return;
        }

        SpeakingPhraseRepository.Pack pack = SpeakingPhraseRepository.load(
                this, asset, packId, title);
        if (!pack.id.isEmpty()) packId = pack.id;
        if (!pack.title.isEmpty()) title = pack.title;
        phrases.addAll(pack.phrases);
        currentIndex = 0;
        if (!startId.isEmpty()) {
            for (int i = 0; i < phrases.size(); i++) {
                if (startId.equals(phrases.get(i).progressKey())) {
                    currentIndex = i;
                    break;
                }
            }
        }
    }

    private void collectFavoritePhrases() {
        LearningCatalogRepository.Catalog catalog = LearningCatalogRepository.load(this, "speaking");
        ArrayList<LearningCatalogRepository.Node> leaves = new ArrayList<>();
        if (catalog != null) collectLeafNodes(catalog.items, leaves);
        for (LearningCatalogRepository.Node node : leaves) {
            if (node == null || safe(node.asset).isEmpty()) continue;
            SpeakingPhraseRepository.Pack pack = SpeakingPhraseRepository.load(
                    this, node.asset, node.id, node.title);
            for (SpeakingPhrase phrase : pack.phrases) {
                String sourcePack = phrase.packId.isEmpty() ? pack.id : phrase.packId;
                if (progressStore.isFavorite(sourcePack, phrase.progressKey())) {
                    phrases.add(phrase);
                }
            }
        }
    }

    private void collectLeafNodes(List<LearningCatalogRepository.Node> source,
                                  List<LearningCatalogRepository.Node> output) {
        if (source == null) return;
        for (LearningCatalogRepository.Node node : source) {
            if (node == null) continue;
            if (node.hasChildren()) collectLeafNodes(node.children, output);
            else output.add(node);
        }
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(8), dp(12), dp(10));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(50)));

        bodyHost = new FrameLayout(this);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bodyLp.setMargins(0, dp(7), 0, 0);
        page.addView(bodyHost, bodyLp);

        feedHost = new SwipeFeedHost(this);
        feedCanvas = buildFeedCanvas();
        feedHost.addView(feedCanvas, new FrameLayout.LayoutParams(-1, -1));
        bodyHost.addView(feedHost, new FrameLayout.LayoutParams(-1, -1));
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView close = topButton("<");
        close.setTextSize(24);
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));

        titleView = text(title, 16, COLOR_TEXT, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -1, 1f);
        titleLp.setMargins(dp(7), 0, dp(7), 0);
        bar.addView(titleView, titleLp);

        favoriteView = topButton(getString(R.string.speaking_favorite));
        favoriteView.setOnClickListener(v -> toggleFavorite());
        bar.addView(favoriteView, new LinearLayout.LayoutParams(dp(74), dp(40)));

        TextView importView = topButton(getString(R.string.speaking_import));
        importView.setOnClickListener(v -> showImportMenu());
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(dp(62), dp(40));
        importLp.setMargins(dp(6), 0, 0, 0);
        bar.addView(importView, importLp);
        return bar;
    }

    private TextView topButton(String label) {
        TextView view = text(label, 12.5f, COLOR_TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setBackground(rounded(COLOR_PANEL, dp(20), 0, 0));
        return view;
    }

    private View buildFeedCanvas() {
        FrameLayout canvas = new FrameLayout(this);
        canvas.setBackground(rounded(COLOR_PANEL, dp(28), 0, 0));
        canvas.setClipToOutline(true);

        progressView = text("", 12, COLOR_SUB, false);
        progressView.setGravity(Gravity.CENTER);
        progressView.setPadding(dp(12), dp(5), dp(12), dp(5));
        progressView.setBackground(rounded(COLOR_PANEL_SOFT, dp(14), 0, 0));
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        progressLp.topMargin = dp(18);
        canvas.addView(progressView, progressLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(24), dp(58), dp(82), dp(76));
        canvas.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView sceneLabel = text(getString(R.string.speaking_scene_label),
                12.5f, COLOR_BRAND, true);
        sceneLabel.setPadding(dp(11), dp(5), dp(11), dp(5));
        sceneLabel.setBackground(rounded(0x332E89FF, dp(13), 0, 0));
        content.addView(sceneLabel, new LinearLayout.LayoutParams(-2, -2));

        chineseView = text("", 38, COLOR_TEXT, true);
        chineseView.setIncludeFontPadding(false);
        chineseView.setLineSpacing(dp(7), 1.02f);
        chineseView.setOnClickListener(v -> speakSentence());
        LinearLayout.LayoutParams zhLp = new LinearLayout.LayoutParams(-1, -2);
        zhLp.setMargins(0, dp(20), 0, 0);
        content.addView(chineseView, zhLp);

        pinyinView = text("", 19, COLOR_BRAND, false);
        pinyinView.setLineSpacing(dp(3), 1f);
        pinyinView.setOnClickListener(v -> speakSpelling());
        LinearLayout.LayoutParams pyLp = new LinearLayout.LayoutParams(-1, -2);
        pyLp.setMargins(0, dp(12), 0, 0);
        content.addView(pinyinView, pyLp);

        myanmarView = text("", 20, COLOR_MY, false);
        myanmarView.setIncludeFontPadding(true);
        myanmarView.setLineSpacing(dp(5), 1.12f);
        myanmarView.setOnClickListener(v -> speakMyanmar());
        LinearLayout.LayoutParams myLp = new LinearLayout.LayoutParams(-1, -2);
        myLp.setMargins(0, dp(15), 0, 0);
        content.addView(myanmarView, myLp);

        LinearLayout sceneBox = new LinearLayout(this);
        sceneBox.setOrientation(LinearLayout.VERTICAL);
        sceneBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        sceneBox.setBackground(rounded(0x99272D3C, dp(18), 0, 0));
        LinearLayout.LayoutParams sceneBoxLp = new LinearLayout.LayoutParams(-1, -2);
        sceneBoxLp.setMargins(0, dp(22), 0, 0);
        content.addView(sceneBox, sceneBoxLp);

        sceneView = text("", 14.5f, COLOR_TEXT, false);
        sceneView.setLineSpacing(dp(3), 1.05f);
        sceneBox.addView(sceneView, new LinearLayout.LayoutParams(-1, -2));

        sceneSecondaryView = text("", 13, COLOR_SUB, false);
        sceneSecondaryView.setLineSpacing(dp(2), 1.05f);
        LinearLayout.LayoutParams secondaryLp = new LinearLayout.LayoutParams(-1, -2);
        secondaryLp.setMargins(0, dp(6), 0, 0);
        sceneBox.addView(sceneSecondaryView, secondaryLp);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        rail.addView(actionButton("A", getString(R.string.speaking_action_read),
                v -> speakSentence()), actionLp());
        rail.addView(actionButton("P", getString(R.string.speaking_action_spelling),
                v -> speakSpelling()), actionLp());
        LinearLayout loop = actionButton("L", getString(R.string.speaking_action_loop),
                v -> toggleAutoPlay());
        loopLabelView = (TextView) loop.getChildAt(1);
        rail.addView(loop, actionLp());
        rail.addView(actionButton("R", getString(R.string.speaking_action_pronunciation),
                v -> openPronunciation()), actionLp());
        rail.addView(actionButton("AI", getString(R.string.speaking_action_coach),
                v -> openAiCoach()), actionLp());

        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(dp(70), -2,
                Gravity.END | Gravity.CENTER_VERTICAL);
        railLp.rightMargin = dp(6);
        canvas.addView(rail, railLp);

        TextView hint = text(getString(R.string.speaking_swipe_hint), 12.5f, COLOR_SUB, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(12), dp(6), dp(12), dp(6));
        hint.setBackground(rounded(0x99272D3C, dp(15), 0, 0));
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintLp.bottomMargin = dp(18);
        canvas.addView(hint, hintLp);
        return canvas;
    }

    private LinearLayout.LayoutParams actionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(66));
        lp.setMargins(0, dp(2), 0, dp(2));
        return lp;
    }

    private LinearLayout actionButton(String iconText, String label, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setOnClickListener(click);
        box.setClickable(true);

        TextView icon = text(iconText, "AI".equals(iconText) ? 12 : 16, COLOR_TEXT, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(0xCC303748, dp(22), 0, 0));
        box.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView labelView = text(label, 10.5f, COLOR_SUB, false);
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        box.addView(labelView, new LinearLayout.LayoutParams(-1, dp(20)));
        return box;
    }

    private void renderCurrent() {
        SpeakingPhrase phrase = current();
        if (phrase == null) {
            showEmpty();
            return;
        }
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        feedHost.setVisibility(View.VISIBLE);
        favoriteView.setVisibility(View.VISIBLE);
        titleView.setText(favoritesOnly ? getString(R.string.speaking_favorites_title) : title);
        progressView.setText(getString(R.string.speaking_feed_progress,
                currentIndex + 1, phrases.size()));

        chineseView.setText(phrase.text);
        pinyinView.setText(phrase.pinyin);
        pinyinView.setVisibility(phrase.pinyin.isEmpty() ? View.GONE : View.VISIBLE);
        myanmarView.setText(phrase.meaningMy);
        myanmarView.setVisibility(phrase.meaningMy.isEmpty() ? View.GONE : View.VISIBLE);

        String primary = primaryScene(phrase);
        sceneView.setText(primary.isEmpty() ? getString(R.string.speaking_scene_default) : primary);
        String secondary = secondaryScene(phrase);
        sceneSecondaryView.setText(secondary);
        sceneSecondaryView.setVisibility(secondary.isEmpty() ? View.GONE : View.VISIBLE);

        String sourcePack = sourcePackId(phrase);
        progressStore.markViewed(sourcePack, phrase.progressKey());
        updateFavoriteUi(progressStore.isFavorite(sourcePack, phrase.progressKey()));
        feedCanvas.setAlpha(1f);
        feedCanvas.setTranslationY(0f);
    }

    private void showEmpty() {
        stopAutoPlay();
        if (feedHost != null) feedHost.setVisibility(View.GONE);
        favoriteView.setVisibility(View.GONE);
        if (emptyView == null) {
            emptyView = text("", 15, COLOR_SUB, false);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setLineSpacing(dp(4), 1f);
            emptyView.setPadding(dp(24), dp(24), dp(24), dp(24));
            emptyView.setBackground(rounded(COLOR_PANEL, dp(28), 0, 0));
            bodyHost.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        }
        emptyView.setText(favoritesOnly
                ? getString(R.string.speaking_favorites_empty)
                : getString(R.string.speaking_pack_empty, asset));
        emptyView.setVisibility(View.VISIBLE);
    }

    private void changePhrase(int direction) {
        changePhrase(direction, false);
    }

    private void changePhrase(int direction, boolean fromAutoPlay) {
        if (phrases.size() < 2 || phraseAnimating) return;
        phraseAnimating = true;
        if (!fromAutoPlay) stopAutoPlay();
        float distance = Math.max(feedHost.getHeight(), dp(480));
        float outgoing = direction > 0 ? -distance : distance;
        feedCanvas.animate()
                .translationY(outgoing)
                .alpha(0.15f)
                .setDuration(150)
                .withEndAction(() -> {
                    currentIndex = (currentIndex + direction + phrases.size()) % phrases.size();
                    renderCurrent();
                    feedCanvas.setTranslationY(direction > 0 ? distance : -distance);
                    feedCanvas.setAlpha(0.15f);
                    feedCanvas.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(190)
                            .withEndAction(() -> phraseAnimating = false)
                            .start();
                })
                .start();
    }

    private void toggleFavorite() {
        SpeakingPhrase phrase = current();
        if (phrase == null) return;
        String sourcePack = sourcePackId(phrase);
        boolean favorite = progressStore.toggleFavorite(sourcePack, phrase.progressKey());
        updateFavoriteUi(favorite);
        Toast.makeText(this, favorite ? R.string.speaking_favorite_added
                : R.string.speaking_favorite_removed, Toast.LENGTH_SHORT).show();
        if (!favorite && favoritesOnly) {
            phrases.remove(currentIndex);
            if (phrases.isEmpty()) {
                showEmpty();
            } else {
                currentIndex = Math.min(currentIndex, phrases.size() - 1);
                renderCurrent();
            }
        }
    }

    private void updateFavoriteUi(boolean favorite) {
        favoriteView.setText(favorite ? getString(R.string.speaking_favorited)
                : getString(R.string.speaking_favorite));
        favoriteView.setTextColor(favorite ? COLOR_FAVORITE : COLOR_TEXT);
    }

    private String sourcePackId(SpeakingPhrase phrase) {
        if (phrase != null && !phrase.packId.isEmpty()) return phrase.packId;
        return packId;
    }

    private void showImportMenu() {
        List<LearningCatalogRepository.Node> imported = SpeakingImportedPackStore.nodes(this);
        if (imported.isEmpty()) {
            launchImport();
            return;
        }
        String[] options = new String[]{
                getString(R.string.speaking_import_new),
                getString(R.string.speaking_import_manage)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.speaking_import)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) launchImport();
                    else showImportedManager();
                })
                .show();
    }

    private void launchImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "text/json", "text/plain"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT_SPEAKING_PACK);
        } catch (Throwable error) {
            Toast.makeText(this, R.string.speaking_import_picker_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportedManager() {
        List<LearningCatalogRepository.Node> imported = SpeakingImportedPackStore.nodes(this);
        if (imported.isEmpty()) {
            Toast.makeText(this, R.string.speaking_import_none, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[imported.size()];
        for (int i = 0; i < imported.size(); i++) names[i] = imported.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle(R.string.speaking_import_manage)
                .setItems(names, (dialog, which) -> confirmDelete(imported.get(which)))
                .setNegativeButton(R.string.learning_cancel, null)
                .show();
    }

    private void confirmDelete(LearningCatalogRepository.Node node) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.speaking_import_delete_title)
                .setMessage(getString(R.string.speaking_import_delete_message, node.title))
                .setNegativeButton(R.string.learning_cancel, null)
                .setPositiveButton(R.string.speaking_import_delete, (dialog, which) -> {
                    if (!SpeakingImportedPackStore.delete(this, node.id)) return;
                    Toast.makeText(this, R.string.speaking_import_delete_success,
                            Toast.LENGTH_SHORT).show();
                    if (!favoritesOnly && node.id.equals(packId)) finish();
                    if (favoritesOnly) {
                        loadPhrases();
                        if (phrases.isEmpty()) showEmpty(); else renderCurrent();
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_SPEAKING_PACK || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Toast.makeText(this, R.string.speaking_importing, Toast.LENGTH_SHORT).show();
        LearningRemoteContent.execute(() -> {
            try {
                SpeakingImportedPackStore.ImportResult result =
                        SpeakingImportedPackStore.importFromUri(getApplicationContext(), uri);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, getString(R.string.speaking_import_success,
                            result.title, result.count), Toast.LENGTH_LONG).show();
                    SpeakingFullscreenActivity.open(this, result.packId, result.title,
                            result.asset, "");
                    finish();
                });
            } catch (Throwable error) {
                String message = safe(error.getMessage());
                if (message.isEmpty()) message = getString(R.string.speaking_import_invalid);
                String finalMessage = message;
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.speaking_import_failed, finalMessage),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void speakSentence() {
        SpeakingPhrase phrase = current();
        if (phrase == null || phrase.text.isEmpty()) return;
        progressStore.increment(sourcePackId(phrase), phrase.progressKey(), "listen_count");
        LearningTtsBridge.speak(this, phrase.text, phrase.ttsPinyin,
                LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_EXAMPLE);
    }

    private void speakSpelling() {
        SpeakingPhrase phrase = current();
        if (phrase == null || phrase.text.isEmpty()) return;
        progressStore.increment(sourcePackId(phrase), phrase.progressKey(), "spelling_count");
        LearningTtsBridge.speak(this, phrase.text, phrase.ttsPinyin,
                LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_SPELLING);
    }

    private void speakMyanmar() {
        SpeakingPhrase phrase = current();
        if (phrase == null || phrase.meaningMy.isEmpty()) return;
        LearningTtsBridge.speak(this, phrase.meaningMy, "my-MM", "auto");
    }

    private void toggleAutoPlay() {
        if (autoPlayEnabled) {
            stopAutoPlay();
            return;
        }
        autoPlayEnabled = true;
        updateAutoPlayUi();
        playAutoSequence(++autoPlayGeneration);
    }

    private void playAutoSequence(int generation) {
        if (!autoPlayEnabled || generation != autoPlayGeneration || isFinishing()) return;
        SpeakingPhrase phrase = current();
        if (phrase == null) return;
        speakSentence();
        autoPlayHandler.postDelayed(() -> {
            if (!autoPlayEnabled || generation != autoPlayGeneration || isFinishing()) return;
            speakSpelling();
            autoPlayHandler.postDelayed(() -> {
                if (!autoPlayEnabled || generation != autoPlayGeneration || isFinishing()) return;
                changePhrase(1, true);
                autoPlayHandler.postDelayed(() -> playAutoSequence(generation), 900L);
            }, estimateSpellingDuration(phrase.ttsPinyin));
        }, estimateSentenceDuration(phrase.text));
    }

    private long estimateSentenceDuration(String value) {
        String clean = value == null ? "" : value.replaceAll("[\\s,.!?]", "");
        int count = clean.codePointCount(0, clean.length());
        return Math.max(1800L, count * 600L);
    }

    private long estimateSpellingDuration(String value) {
        String clean = safe(value);
        int syllables = clean.isEmpty() ? 1 : clean.split("\\s+").length;
        return Math.max(2200L, syllables * 760L);
    }

    private void stopAutoPlay() {
        autoPlayEnabled = false;
        autoPlayGeneration++;
        autoPlayHandler.removeCallbacksAndMessages(null);
        updateAutoPlayUi();
    }

    private void updateAutoPlayUi() {
        if (loopLabelView == null) return;
        loopLabelView.setTextColor(autoPlayEnabled ? COLOR_BRAND : COLOR_SUB);
        loopLabelView.setTypeface(autoPlayEnabled ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void openPronunciation() {
        SpeakingPhrase phrase = current();
        if (phrase == null) return;
        progressStore.increment(sourcePackId(phrase), phrase.progressKey(),
                "pronunciation_count");
        Intent intent = new Intent(this, WordPronunciationActivity.class);
        intent.putExtra(WordPronunciationActivity.EXTRA_WORD, phrase.text);
        intent.putExtra(WordPronunciationActivity.EXTRA_PINYIN, phrase.pinyin);
        intent.putExtra(WordPronunciationActivity.EXTRA_SPELLING_TEXT, phrase.ttsPinyin);
        startActivity(intent);
    }

    private void openAiCoach() {
        SpeakingPhrase phrase = current();
        if (phrase == null) return;
        String[] providers = {"DeepSeek", getString(R.string.speaking_provider_qwen)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.speaking_choose_coach)
                .setItems(providers, (dialog, which) ->
                        launchAiCoach(which == 0 ? "deepseek" : "qwen", phrase))
                .show();
    }

    private void launchAiCoach(String provider, SpeakingPhrase phrase) {
        progressStore.increment(sourcePackId(phrase), phrase.progressKey(), "ai_practice_count");
        String url = "deepseek".equals(provider)
                ? "https://chat.deepseek.com/" : "https://chat.qwen.ai/";
        String providerTitle = "deepseek".equals(provider)
                ? getString(R.string.speaking_deepseek_coach_title)
                : getString(R.string.speaking_qwen_coach_title);
        AiScriptWebActivity.open(this, providerTitle, url, buildCoachPrompt(phrase),
                "speaking-coach");
    }

    private String buildCoachPrompt(SpeakingPhrase phrase) {
        String scene = primaryScene(phrase);
        if (scene.isEmpty()) scene = getString(R.string.speaking_scene_default);
        StringBuilder prompt = new StringBuilder();
        prompt.append(getString(R.string.speaking_coach_prompt_intro)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_sentence, phrase.text)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_pinyin, phrase.pinyin)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_meaning, phrase.meaningMy)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_scene, scene)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_hint_language)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_rules));
        return prompt.toString();
    }

    private String primaryScene(SpeakingPhrase phrase) {
        if (phrase == null) return "";
        String language = uiLanguage();
        if ("my".equals(language)) return !phrase.sceneMy.isEmpty() ? phrase.sceneMy : phrase.scene;
        if ("en".equals(language)) return !phrase.sceneEn.isEmpty() ? phrase.sceneEn : phrase.scene;
        return !phrase.scene.isEmpty() ? phrase.scene : phrase.sceneMy;
    }

    private String secondaryScene(SpeakingPhrase phrase) {
        if (phrase == null) return "";
        String language = uiLanguage();
        if ("my".equals(language)) return phrase.scene;
        if ("en".equals(language)) return phrase.sceneMy;
        return phrase.sceneMy;
    }

    private String uiLanguage() {
        Locale locale;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            locale = getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = getResources().getConfiguration().locale;
        }
        String language = locale == null ? "" : locale.getLanguage();
        if ("my".equalsIgnoreCase(language)) return "my";
        if ("en".equalsIgnoreCase(language)) return "en";
        return "zh";
    }

    private SpeakingPhrase current() {
        if (phrases.isEmpty()) return null;
        currentIndex = Math.max(0, Math.min(currentIndex, phrases.size() - 1));
        return phrases.get(currentIndex);
    }

    private final class SwipeFeedHost extends FrameLayout {
        private float downX;
        private float downY;
        private boolean blocked;

        SwipeFeedHost(Context context) {
            super(context);
            setClickable(true);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                blocked = downX > getWidth() - dp(90);
                return false;
            }
            if (action == MotionEvent.ACTION_MOVE && !blocked) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                return Math.abs(dy) > dp(12) && Math.abs(dy) > Math.abs(dx) * 1.2f;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (blocked || phraseAnimating) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                float dy = event.getY() - downY;
                feedCanvas.setTranslationY(dy * 0.72f);
                feedCanvas.setAlpha(Math.max(0.55f, 1f - Math.abs(dy) / Math.max(1f, getHeight())));
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float dy = event.getY() - downY;
                if (action == MotionEvent.ACTION_UP && Math.abs(dy) > dp(68)) {
                    changePhrase(dy < 0 ? 1 : -1);
                } else {
                    feedCanvas.animate().translationY(0f).alpha(1f).setDuration(150).start();
                }
                return true;
            }
            return true;
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
