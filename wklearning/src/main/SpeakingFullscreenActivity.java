package com.chat.learning;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.chat.userscript.AiScriptWebActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Full-screen speaking phrase card with TTS, spelling, pronunciation, variants and FSRS. */
public class SpeakingFullscreenActivity extends AppCompatActivity {
    public static final String EXTRA_PACK_ID = "pack_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ASSET = "asset";
    public static final String EXTRA_START_ID = "start_id";

    private static final int COLOR_BG = 0xFFF4F5FA;
    private static final int COLOR_CARD = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF151925;
    private static final int COLOR_SUB = 0xFF727A8D;
    private static final int COLOR_BRAND = 0xFF625FE7;
    private static final int COLOR_BRAND_SOFT = 0xFFEFEEFF;
    private static final int COLOR_STROKE = 0xFFE6E8F0;
    private static final int COLOR_MY = 0xFF3E4A60;

    private String packId;
    private String title;
    private String asset;
    private String startId;
    private final ArrayList<SpeakingPhrase> phrases = new ArrayList<>();
    private final Set<String> ratedThisSession = new HashSet<>();
    private final WordFsrsScheduler scheduler = new WordFsrsScheduler();
    private SpeakingProgressStore progressStore;
    private int currentIndex;

    private TextView progressView;
    private TextView stateView;
    private TextView favoriteView;
    private TextView chineseView;
    private TextView pinyinView;
    private TextView myanmarView;
    private TextView sceneView;
    private TextView sceneMyView;
    private TextView sceneTabView;
    private TextView replacementsTabView;
    private TextView alternativesTabView;
    private FrameLayout detailPageHost;
    private View scenePage;
    private View replacementsPage;
    private View alternativesPage;
    private LinearLayout breakdownBox;
    private LinearLayout replacementsBox;
    private LinearLayout alternativesBox;
    private int detailTabIndex;
    private FrameLayout decisionHost;
    private LinearLayout ratingRow;
    private LinearLayout actionRow;
    private ActionIconView loopIconView;
    private TextView loopLabelView;
    private FrameLayout bodyHost;
    private View studyBody;
    private FrameLayout faceHost;
    private View frontFaceView;
    private View backFaceView;
    private TextView frontStateView;
    private TextView frontSceneView;
    private TextView frontSceneSecondaryView;
    private TextView frontMeaningView;
    private boolean frontFace = true;
    private boolean flipAnimating;
    private View completionBody;
    private final EnumMap<WordFsrsScheduler.Rating, TextView> ratingButtons =
            new EnumMap<>(WordFsrsScheduler.Rating.class);

    private String displayText = "";
    private String displayPinyin = "";
    private String displayTtsPinyin = "";
    private String displayMeaningMy = "";
    private float touchDownX;
    private float touchDownY;
    private final Handler autoPlayHandler = new Handler(Looper.getMainLooper());
    private boolean autoPlayEnabled;
    private int autoPlayGeneration;

    public static void open(Context context, String packId, String title, String asset,
                            String startId) {
        Intent intent = new Intent(context, SpeakingFullscreenActivity.class);
        intent.putExtra(EXTRA_PACK_ID, packId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_ASSET, asset);
        intent.putExtra(EXTRA_START_ID, startId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packId = safe(getIntent().getStringExtra(EXTRA_PACK_ID), "speaking");
        title = safe(getIntent().getStringExtra(EXTRA_TITLE),
                getString(R.string.speaking_title_default));
        asset = safe(getIntent().getStringExtra(EXTRA_ASSET),
                "assets/learning/speaking/hello.json");
        startId = safe(getIntent().getStringExtra(EXTRA_START_ID), "");
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
        SpeakingPhraseRepository.Pack pack = SpeakingPhraseRepository.load(
                this, asset, packId, title);
        if (pack.id.length() > 0) packId = pack.id;
        if (pack.title.length() > 0) title = pack.title;
        phrases.clear();
        phrases.addAll(pack.phrases);
        if (phrases.isEmpty()) return;

        if (startId.length() > 0) {
            for (int i = 0; i < phrases.size(); i++) {
                if (startId.equals(phrases.get(i).progressKey())) {
                    currentIndex = i;
                    return;
                }
            }
        }

        Map<String, WordFsrsScheduler.CardState> states = progressStore.loadPack(packId);
        long now = System.currentTimeMillis();
        Collections.sort(phrases, new Comparator<SpeakingPhrase>() {
            @Override
            public int compare(SpeakingPhrase left, SpeakingPhrase right) {
                return Integer.compare(rank(left, states, now), rank(right, states, now));
            }
        });
        currentIndex = 0;
    }

    private int rank(SpeakingPhrase phrase, Map<String, WordFsrsScheduler.CardState> states,
                     long now) {
        WordFsrsScheduler.CardState state = states.get(phrase.progressKey());
        if (state != null && state.reviewCount > 0 && state.dueAt <= now) return 0;
        if (state == null || state.reviewCount <= 0) return 1;
        return 2;
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(10), dp(14), dp(12));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(48)));

        progressView = text("", 12.5f, COLOR_SUB, false);
        progressView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, -2);
        progressLp.setMargins(0, dp(3), 0, dp(8));
        page.addView(progressView, progressLp);

        bodyHost = new FrameLayout(this);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        page.addView(bodyHost, bodyLp);

        studyBody = buildStudyBody();
        bodyHost.addView(studyBody, new FrameLayout.LayoutParams(-1, -1));

        decisionHost = new FrameLayout(this);
        ratingRow = buildRatingRow();
        decisionHost.addView(ratingRow, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams decisionLp = new LinearLayout.LayoutParams(-1, dp(60));
        decisionLp.setMargins(0, dp(8), 0, 0);
        page.addView(decisionHost, decisionLp);

        actionRow = buildActionRow();
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, dp(80));
        actionLp.setMargins(0, dp(5), 0, 0);
        page.addView(actionRow, actionLp);
    }

    private View topBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView close = text("‹", 29, COLOR_TEXT, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(rounded(Color.WHITE, dp(20), COLOR_STROKE, dp(1)));
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView titleView = text(title, 17, COLOR_TEXT, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -1, 1f);
        titleLp.setMargins(dp(8), 0, dp(8), 0);
        top.addView(titleView, titleLp);

        favoriteView = text("☆", 26, COLOR_SUB, false);
        favoriteView.setGravity(Gravity.CENTER);
        favoriteView.setBackground(rounded(Color.WHITE, dp(20), COLOR_STROKE, dp(1)));
        favoriteView.setOnClickListener(v -> toggleFavorite());
        top.addView(favoriteView, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return top;
    }

    private View buildStudyBody() {
        faceHost = new FrameLayout(this);
        faceHost.setBackground(rounded(COLOR_CARD, dp(27), COLOR_STROKE, dp(1)));
        faceHost.setElevation(dp(3));
        faceHost.setCameraDistance(dp(9000));

        frontFaceView = buildFrontFace();
        backFaceView = buildBackFace();
        faceHost.addView(frontFaceView, new FrameLayout.LayoutParams(-1, -1));
        faceHost.addView(backFaceView, new FrameLayout.LayoutParams(-1, -1));
        backFaceView.setVisibility(View.GONE);
        return faceHost;
    }

    private View buildFrontFace() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(20), dp(24), dp(18));
        page.setOnTouchListener(this::handleCardTouch);
        page.setOnClickListener(v -> revealAnswer());

        frontStateView = text("", 12, COLOR_BRAND, true);
        frontStateView.setGravity(Gravity.CENTER);
        frontStateView.setPadding(dp(10), dp(4), dp(10), dp(4));
        frontStateView.setBackground(rounded(COLOR_BRAND_SOFT, dp(12), 0, 0));
        page.addView(frontStateView, new LinearLayout.LayoutParams(-2, -2));

        page.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.38f));

        frontSceneView = text("", 13, COLOR_BRAND, true);
        frontSceneView.setGravity(Gravity.CENTER);
        frontSceneView.setSingleLine(false);
        frontSceneView.setPadding(dp(13), dp(6), dp(13), dp(6));
        frontSceneView.setBackground(rounded(COLOR_BRAND_SOFT, dp(15), 0, 0));
        page.addView(frontSceneView, new LinearLayout.LayoutParams(-2, -2));

        // Kept for compatibility with the existing field structure. The front side intentionally
        // shows no translation or secondary-language hint, so the learner reads Chinese first.
        frontSceneSecondaryView = text("", 1, Color.TRANSPARENT, false);
        frontSceneSecondaryView.setVisibility(View.GONE);
        page.addView(frontSceneSecondaryView, new LinearLayout.LayoutParams(1, 1));

        frontMeaningView = text("", 38, COLOR_TEXT, true);
        frontMeaningView.setGravity(Gravity.CENTER);
        frontMeaningView.setIncludeFontPadding(false);
        frontMeaningView.setLineSpacing(dp(7), 1.03f);
        LinearLayout.LayoutParams sentenceLp = new LinearLayout.LayoutParams(-1, -2);
        sentenceLp.setMargins(0, dp(25), 0, 0);
        page.addView(frontMeaningView, sentenceLp);

        TextView prompt = text(getString(R.string.speaking_front_prompt), 13.5f,
                COLOR_SUB, false);
        prompt.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams promptLp = new LinearLayout.LayoutParams(-1, -2);
        promptLp.setMargins(0, dp(18), 0, 0);
        page.addView(prompt, promptLp);

        TextView tapHint = text(getString(R.string.speaking_front_tap), 12.5f,
                0xFF9AA1B2, false);
        tapHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tapLp = new LinearLayout.LayoutParams(-1, -2);
        tapLp.setMargins(0, dp(11), 0, 0);
        page.addView(tapHint, tapLp);

        page.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.62f));
        return page;
    }

    private View buildBackFace() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(20), dp(18), dp(18));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout answer = new LinearLayout(this);
        answer.setOrientation(LinearLayout.VERTICAL);
        answer.setGravity(Gravity.CENTER_HORIZONTAL);
        answer.setPadding(0, 0, 0, dp(8));
        answer.setOnClickListener(v -> showFrontFace());
        content.addView(answer, new LinearLayout.LayoutParams(-1, -2));

        stateView = text("", 12, COLOR_BRAND, true);
        stateView.setGravity(Gravity.CENTER);
        stateView.setPadding(dp(10), dp(4), dp(10), dp(4));
        stateView.setBackground(rounded(COLOR_BRAND_SOFT, dp(12), 0, 0));
        answer.addView(stateView, new LinearLayout.LayoutParams(-2, -2));

        chineseView = text("", 37, COLOR_TEXT, true);
        chineseView.setGravity(Gravity.CENTER);
        chineseView.setIncludeFontPadding(false);
        chineseView.setLineSpacing(dp(5), 1f);
        chineseView.setOnClickListener(v -> speakSentence());
        LinearLayout.LayoutParams zhLp = new LinearLayout.LayoutParams(-1, -2);
        zhLp.setMargins(0, dp(18), 0, 0);
        answer.addView(chineseView, zhLp);

        pinyinView = text("", 19, COLOR_BRAND, false);
        pinyinView.setGravity(Gravity.CENTER);
        pinyinView.setLineSpacing(dp(3), 1f);
        pinyinView.setOnClickListener(v -> speakSpelling());
        LinearLayout.LayoutParams pyLp = new LinearLayout.LayoutParams(-1, -2);
        pyLp.setMargins(0, dp(10), 0, 0);
        answer.addView(pinyinView, pyLp);

        myanmarView = text("", 19, COLOR_MY, false);
        myanmarView.setGravity(Gravity.CENTER);
        myanmarView.setIncludeFontPadding(true);
        myanmarView.setLineSpacing(dp(4), 1.12f);
        myanmarView.setOnClickListener(v -> speakMyanmar());
        LinearLayout.LayoutParams myLp = new LinearLayout.LayoutParams(-1, -2);
        myLp.setMargins(0, dp(13), 0, 0);
        answer.addView(myanmarView, myLp);

        TextView backHint = text(getString(R.string.speaking_back_tap), 11.5f,
                0xFF9AA1B2, false);
        backHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(11), 0, 0);
        answer.addView(backHint, hintLp);

        content.addView(detailCard(), detailLp());
        return scroll;
    }

    private void revealAnswer() {
        if (!frontFace || flipAnimating || phrases.isEmpty()) return;
        stopAutoPlay();
        flipTo(false);
    }

    private void showFrontFace() {
        if (frontFace || flipAnimating || phrases.isEmpty()) return;
        stopAutoPlay();
        flipTo(true);
    }

    private void flipTo(boolean showFront) {
        if (faceHost == null || flipAnimating || frontFace == showFront) return;
        flipAnimating = true;
        View hide = showFront ? backFaceView : frontFaceView;
        View show = showFront ? frontFaceView : backFaceView;
        faceHost.animate()
                .rotationY(86f)
                .setDuration(105)
                .withEndAction(() -> {
                    hide.setVisibility(View.GONE);
                    show.setVisibility(View.VISIBLE);
                    frontFace = showFront;
                    decisionHost.setVisibility(showFront ? View.INVISIBLE : View.VISIBLE);
                    ratingRow.setVisibility(showFront ? View.GONE : View.VISIBLE);
                    faceHost.setRotationY(-86f);
                    faceHost.animate()
                            .rotationY(0f)
                            .setDuration(115)
                            .withEndAction(() -> flipAnimating = false)
                            .start();
                })
                .start();
    }

    private void revealForAction(Runnable action) {
        if (frontFace) revealAnswer();
        if (action != null) action.run();
    }

    private LinearLayout buildActionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(2), dp(2), dp(2), 0);
        row.setBackground(rounded(Color.WHITE, dp(22), COLOR_STROKE, dp(1)));

        row.addView(actionButton(ActionIconView.TYPE_SPEAKER,
                getString(R.string.speaking_action_read),
                v -> speakSentence()), actionLp());
        row.addView(actionButton(ActionIconView.TYPE_SPELLING,
                getString(R.string.speaking_action_spelling),
                v -> revealForAction(this::speakSpelling)), actionLp());

        LinearLayout loopButton = actionButton(ActionIconView.TYPE_LOOP,
                getString(R.string.speaking_action_loop),
                v -> revealForAction(this::toggleAutoPlay));
        loopIconView = (ActionIconView) ((LinearLayout) loopButton).getChildAt(0);
        loopLabelView = (TextView) ((LinearLayout) loopButton).getChildAt(1);
        row.addView(loopButton, actionLp());

        row.addView(actionButton(ActionIconView.TYPE_MICROPHONE,
                getString(R.string.speaking_action_pronunciation),
                v -> openPronunciation()), actionLp());
        row.addView(actionButton(ActionIconView.TYPE_AI,
                getString(R.string.speaking_action_coach),
                v -> openAiCoach()), actionLp());
        return row;
    }

    private LinearLayout.LayoutParams actionLp() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private LinearLayout actionButton(int type, String label, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setClickable(true);
        box.setOnClickListener(click);

        ActionIconView icon = new ActionIconView(this, type);
        icon.setBackground(rounded(0xFFF1F2F6, dp(24), 0, 0));
        box.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView labelView = text(label, 11.5f, COLOR_SUB, false);
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, dp(23));
        labelLp.setMargins(0, dp(2), 0, 0);
        box.addView(labelView, labelLp);
        return box;
    }

    private View detailCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(14));
        box.setBackground(rounded(Color.WHITE, dp(24), COLOR_STROKE, dp(1)));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(rounded(0xFFF4F4F9, dp(18), 0, 0));

        sceneTabView = detailTab(getString(R.string.speaking_section_scene), 0);
        replacementsTabView = detailTab(getString(R.string.speaking_section_replacements), 1);
        alternativesTabView = detailTab(getString(R.string.speaking_section_alternatives), 2);
        tabs.addView(sceneTabView, new LinearLayout.LayoutParams(0, dp(42), 1f));
        tabs.addView(replacementsTabView, new LinearLayout.LayoutParams(0, dp(42), 1f));
        tabs.addView(alternativesTabView, new LinearLayout.LayoutParams(0, dp(42), 1f));
        box.addView(tabs, new LinearLayout.LayoutParams(-1, -2));

        detailPageHost = new DetailSwipeHost(this);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(-1, -2);
        hostLp.setMargins(dp(7), dp(8), dp(7), 0);
        box.addView(detailPageHost, hostLp);

        scenePage = buildScenePage();
        replacementsPage = buildVariantsPage(true);
        alternativesPage = buildVariantsPage(false);
        detailPageHost.addView(scenePage, new FrameLayout.LayoutParams(-1, -2));
        detailPageHost.addView(replacementsPage, new FrameLayout.LayoutParams(-1, -2));
        detailPageHost.addView(alternativesPage, new FrameLayout.LayoutParams(-1, -2));
        showDetailTab(0);
        return box;
    }

    private TextView detailTab(String label, int index) {
        TextView tab = text(label, 13, COLOR_SUB, false);
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine(true);
        tab.setOnClickListener(v -> showDetailTab(index));
        return tab;
    }

    private View buildScenePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(4), dp(8), dp(4), dp(4));

        sceneView = text("", 15.5f, COLOR_TEXT, false);
        sceneView.setLineSpacing(dp(3), 1f);
        page.addView(sceneView, new LinearLayout.LayoutParams(-1, -2));

        sceneMyView = text("", 13.5f, COLOR_MY, false);
        sceneMyView.setIncludeFontPadding(true);
        sceneMyView.setLineSpacing(dp(2), 1.08f);
        LinearLayout.LayoutParams sceneMyLp = new LinearLayout.LayoutParams(-1, -2);
        sceneMyLp.setMargins(0, dp(5), 0, 0);
        page.addView(sceneMyView, sceneMyLp);

        TextView breakdownTitle = sectionLabelWithMargin(
                getString(R.string.speaking_section_breakdown));
        page.addView(breakdownTitle, new LinearLayout.LayoutParams(-1, -2));

        breakdownBox = new LinearLayout(this);
        breakdownBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(breakdownBox, new LinearLayout.LayoutParams(-1, -2));
        return page;
    }

    private View buildVariantsPage(boolean replacements) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(4), 0, dp(4), dp(4));
        if (replacements) {
            replacementsBox = page;
        } else {
            alternativesBox = page;
        }
        return page;
    }

    private void showDetailTab(int index) {
        detailTabIndex = Math.max(0, Math.min(2, index));
        if (scenePage != null) scenePage.setVisibility(detailTabIndex == 0 ? View.VISIBLE : View.GONE);
        if (replacementsPage != null) {
            replacementsPage.setVisibility(detailTabIndex == 1 ? View.VISIBLE : View.GONE);
        }
        if (alternativesPage != null) {
            alternativesPage.setVisibility(detailTabIndex == 2 ? View.VISIBLE : View.GONE);
        }
        styleDetailTab(sceneTabView, detailTabIndex == 0);
        styleDetailTab(replacementsTabView, detailTabIndex == 1);
        styleDetailTab(alternativesTabView, detailTabIndex == 2);
    }

    private void styleDetailTab(TextView tab, boolean selected) {
        if (tab == null) return;
        tab.setTextColor(selected ? COLOR_BRAND : COLOR_SUB);
        tab.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tab.setBackground(selected
                ? rounded(Color.WHITE, dp(15), COLOR_STROKE, dp(1))
                : rounded(Color.TRANSPARENT, dp(15), 0, 0));
    }

    private LinearLayout.LayoutParams detailLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        return lp;
    }

    private TextView sectionLabel(String label) {
        TextView view = text(label, 13, COLOR_BRAND, true);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView sectionLabelWithMargin(String label) {
        TextView view = sectionLabel(label);
        view.setPadding(0, dp(18), 0, 0);
        return view;
    }

    private LinearLayout buildRatingRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        addRating(row, WordFsrsScheduler.Rating.AGAIN,
                getString(R.string.speaking_rating_again), 0xFFEA6A78);
        addRating(row, WordFsrsScheduler.Rating.HARD,
                getString(R.string.speaking_rating_hard), 0xFFE89A4B);
        addRating(row, WordFsrsScheduler.Rating.GOOD,
                getString(R.string.speaking_rating_good), 0xFF4C9A78);
        addRating(row, WordFsrsScheduler.Rating.EASY,
                getString(R.string.speaking_rating_easy), 0xFF5C70D8);
        return row;
    }

    private void addRating(LinearLayout row, WordFsrsScheduler.Rating rating,
                           String title, int accent) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.WHITE, dp(18), COLOR_STROKE, dp(1)));
        button.setOnClickListener(v -> rate(rating));

        TextView titleView = text(title, 13.5f, accent, true);
        titleView.setGravity(Gravity.CENTER);
        button.addView(titleView, new LinearLayout.LayoutParams(-1, dp(31)));

        TextView interval = text("", 10.5f, COLOR_SUB, false);
        interval.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        button.addView(interval, new LinearLayout.LayoutParams(-1, dp(26)));
        ratingButtons.put(rating, interval);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, lp);
    }

    private void renderCurrent() {
        if (phrases.isEmpty()) return;
        if (completionBody != null) completionBody.setVisibility(View.GONE);
        studyBody.setVisibility(View.VISIBLE);
        actionRow.setVisibility(View.VISIBLE);
        frontFace = true;
        flipAnimating = false;
        if (frontFaceView != null) frontFaceView.setVisibility(View.VISIBLE);
        if (backFaceView != null) backFaceView.setVisibility(View.GONE);
        if (faceHost != null) faceHost.setRotationY(0f);
        decisionHost.setVisibility(View.INVISIBLE);
        ratingRow.setVisibility(View.GONE);

        SpeakingPhrase phrase = current();
        displayBase(phrase);
        progressView.setText(getString(R.string.speaking_session_progress,
                currentIndex + 1, phrases.size(), ratedThisSession.size()));

        WordFsrsScheduler.CardState state = progressStore.load(packId, phrase.progressKey());
        String stateText = state.reviewCount <= 0
                ? getString(R.string.speaking_state_new)
                : (state.dueAt <= System.currentTimeMillis()
                ? getString(R.string.speaking_state_due)
                : getString(R.string.speaking_state_learning));
        stateView.setText(stateText);
        frontStateView.setText(stateText);
        bindFrontPrompt(phrase);
        favoriteView.setText(progressStore.isFavorite(packId, phrase.progressKey()) ? "★" : "☆");
        favoriteView.setTextColor(progressStore.isFavorite(packId, phrase.progressKey())
                ? 0xFFE89A32 : COLOR_SUB);
        renderDetails(phrase);
        renderIntervals(state);
    }

    private void bindFrontPrompt(SpeakingPhrase phrase) {
        String scene = primaryScene(phrase);
        if (scene.length() == 0) scene = getString(R.string.speaking_scene_default);
        frontSceneView.setText(scene);
        frontSceneSecondaryView.setText("");
        frontSceneSecondaryView.setVisibility(View.GONE);
        frontMeaningView.setText(phrase.text);
        frontMeaningView.setVisibility(phrase.text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void displayBase(SpeakingPhrase phrase) {
        displayText = phrase.text;
        displayPinyin = phrase.pinyin;
        displayTtsPinyin = phrase.ttsPinyin;
        displayMeaningMy = phrase.meaningMy;
        applyDisplay();
    }

    private void displayVariant(SpeakingPhrase.Variant variant) {
        displayText = variant.text;
        displayPinyin = variant.pinyin;
        displayTtsPinyin = variant.ttsPinyin;
        displayMeaningMy = variant.meaningMy;
        applyDisplay();
        restartAutoPlayIfNeeded();
        if (!autoPlayEnabled) speakSentence();
    }

    private void applyDisplay() {
        chineseView.setText(displayText);
        pinyinView.setText(displayPinyin);
        pinyinView.setVisibility(displayPinyin.length() == 0 ? View.GONE : View.VISIBLE);
        myanmarView.setText(displayMeaningMy);
        myanmarView.setVisibility(displayMeaningMy.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void renderDetails(SpeakingPhrase phrase) {
        String primaryScene = primaryScene(phrase);
        String secondaryScene = secondaryScene(phrase);
        sceneView.setText(primaryScene.length() > 0
                ? primaryScene : getString(R.string.speaking_scene_default));
        sceneMyView.setText(secondaryScene);
        sceneMyView.setVisibility(secondaryScene.length() == 0 ? View.GONE : View.VISIBLE);
        fillBreakdown(breakdownBox, phrase.breakdown);
        fillVariants(replacementsBox, phrase.replacements,
                getString(R.string.speaking_no_replacements));
        fillVariants(alternativesBox, phrase.alternatives,
                getString(R.string.speaking_no_alternatives));
        showDetailTab(detailTabIndex);
    }

    private void fillBreakdown(LinearLayout parent, List<SpeakingPhrase.Breakdown> parts) {
        parent.removeAllViews();
        if (parts == null || parts.isEmpty()) {
            TextView empty = text(getString(R.string.speaking_no_breakdown),
                    13, COLOR_SUB, false);
            empty.setPadding(0, dp(8), 0, 0);
            parent.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (SpeakingPhrase.Breakdown part : parts) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            row.setBackground(rounded(0xFFF6F6FA, dp(15), 0, 0));
            row.setOnClickListener(v -> LearningTtsBridge.speak(
                    this, part.text, part.pinyin,
                    LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_EXAMPLE));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(head, new LinearLayout.LayoutParams(-1, -2));

            TextView zh = text(part.text, 15, COLOR_TEXT, true);
            head.addView(zh, new LinearLayout.LayoutParams(-2, -2));

            TextView py = text(part.pinyin, 13, COLOR_BRAND, false);
            LinearLayout.LayoutParams pyLp = new LinearLayout.LayoutParams(0, -2, 1f);
            pyLp.setMargins(dp(10), 0, 0, 0);
            head.addView(py, pyLp);

            if (part.meaningMy.length() > 0) {
                TextView my = text(part.meaningMy, 12.5f, COLOR_MY, false);
                my.setIncludeFontPadding(true);
                LinearLayout.LayoutParams myLp = new LinearLayout.LayoutParams(-1, -2);
                myLp.setMargins(0, dp(4), 0, 0);
                row.addView(my, myLp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.setMargins(0, dp(8), 0, 0);
            parent.addView(row, rowLp);
        }
    }

    private void fillVariants(LinearLayout parent, List<SpeakingPhrase.Variant> variants,
                              String emptyText) {
        parent.removeAllViews();
        if (variants == null || variants.isEmpty()) {
            TextView empty = text(emptyText, 13, COLOR_SUB, false);
            empty.setPadding(0, dp(8), 0, 0);
            parent.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (SpeakingPhrase.Variant variant : variants) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(rounded(0xFFF6F6FA, dp(15), 0, 0));
            row.setOnClickListener(v -> displayVariant(variant));

            String title = variant.label.length() > 0
                    ? variant.label + "  ·  " + variant.text : variant.text;
            TextView zh = text(title, 14.5f, COLOR_TEXT, true);
            row.addView(zh, new LinearLayout.LayoutParams(-1, -2));
            if (variant.meaningMy.length() > 0) {
                TextView my = text(variant.meaningMy, 12.5f, COLOR_MY, false);
                my.setIncludeFontPadding(true);
                LinearLayout.LayoutParams myLp = new LinearLayout.LayoutParams(-1, -2);
                myLp.setMargins(0, dp(4), 0, 0);
                row.addView(my, myLp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.setMargins(0, dp(8), 0, 0);
            parent.addView(row, rowLp);
        }
    }

    private void renderIntervals(WordFsrsScheduler.CardState state) {
        Map<WordFsrsScheduler.Rating, WordFsrsScheduler.Result> results =
                scheduler.preview(state, System.currentTimeMillis());
        for (WordFsrsScheduler.Rating rating : WordFsrsScheduler.Rating.values()) {
            TextView view = ratingButtons.get(rating);
            WordFsrsScheduler.Result result = results.get(rating);
            if (view != null && result != null) view.setText(formatInterval(result.intervalMillis));
        }
    }

    private String formatInterval(long millis) {
        long minute = 60_000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (millis < hour) {
            return getString(R.string.speaking_interval_minutes,
                    Math.max(1, Math.round((float) millis / minute)));
        }
        if (millis < day) {
            return getString(R.string.speaking_interval_hours,
                    Math.max(1, Math.round((float) millis / hour)));
        }
        long days = Math.max(1, Math.round((float) millis / day));
        if (days < 30) return getString(R.string.speaking_interval_days, days);
        if (days < 365) {
            return getString(R.string.speaking_interval_months,
                    Math.max(1, Math.round(days / 30f)));
        }
        return getString(R.string.speaking_interval_years, days / 365f);
    }

    private void rate(WordFsrsScheduler.Rating rating) {
        if (frontFace || flipAnimating) {
            revealAnswer();
            return;
        }
        stopAutoPlay();
        SpeakingPhrase phrase = current();
        if (phrase == null) return;
        WordFsrsScheduler.CardState oldState = progressStore.load(packId, phrase.progressKey());
        WordFsrsScheduler.Result result = scheduler.review(oldState, rating,
                System.currentTimeMillis());
        progressStore.save(packId, phrase.progressKey(), result.card);
        ratedThisSession.add(phrase.progressKey());

        int next = findNextUnrated(currentIndex + 1);
        if (next < 0) {
            showCompletion();
        } else {
            currentIndex = next;
            renderCurrent();
        }
    }

    private int findNextUnrated(int start) {
        if (ratedThisSession.size() >= phrases.size()) return -1;
        for (int offset = 0; offset < phrases.size(); offset++) {
            int index = (start + offset) % phrases.size();
            if (!ratedThisSession.contains(phrases.get(index).progressKey())) return index;
        }
        return -1;
    }

    private void showCompletion() {
        stopAutoPlay();
        studyBody.setVisibility(View.GONE);
        decisionHost.setVisibility(View.GONE);
        ratingRow.setVisibility(View.GONE);
        actionRow.setVisibility(View.GONE);
        progressView.setText(getString(R.string.speaking_group_complete));
        if (completionBody == null) {
            completionBody = buildCompletion();
            bodyHost.addView(completionBody, new FrameLayout.LayoutParams(-1, -1));
        }
        completionBody.setVisibility(View.VISIBLE);
    }

    private View buildCompletion() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        box.setBackground(rounded(Color.WHITE, dp(28), COLOR_STROKE, dp(1)));

        TextView mark = text("✓", 48, 0xFF178A64, true);
        mark.setGravity(Gravity.CENTER);
        box.addView(mark, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text(getString(R.string.speaking_complete_title),
                25, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(12), 0, 0);
        box.addView(title, titleLp);

        TextView subtitle = text(getString(R.string.speaking_complete_subtitle), 14,
                COLOR_SUB, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(10), 0, dp(22));
        box.addView(subtitle, subLp);

        TextView restart = actionButton(getString(R.string.speaking_restart), COLOR_BRAND);
        restart.setOnClickListener(v -> {
            ratedThisSession.clear();
            currentIndex = 0;
            completionBody.setVisibility(View.GONE);
            renderCurrent();
        });
        box.addView(restart, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView close = actionButton(getString(R.string.speaking_back_to_categories),
                0xFFEEF0F5);
        close.setTextColor(COLOR_TEXT);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(48));
        closeLp.setMargins(0, dp(10), 0, 0);
        box.addView(close, closeLp);
        return box;
    }

    private TextView actionButton(String label, int background) {
        TextView button = text(label, 15, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(background, dp(18), 0, 0));
        return button;
    }

    private void showEmpty() {
        stopAutoPlay();
        studyBody.setVisibility(View.GONE);
        decisionHost.setVisibility(View.GONE);
        ratingRow.setVisibility(View.GONE);
        actionRow.setVisibility(View.GONE);
        TextView empty = text(getString(R.string.speaking_pack_empty, asset),
                15, COLOR_SUB, false);
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(dp(4), 1f);
        empty.setBackground(rounded(Color.WHITE, dp(26), COLOR_STROKE, dp(1)));
        bodyHost.addView(empty, new FrameLayout.LayoutParams(-1, -1));
    }

    private void speakSentence() {
        if (displayText.length() == 0) return;
        progressStore.increment(packId, current().progressKey(), "listen_count");
        LearningTtsBridge.speak(this, displayText, displayTtsPinyin,
                LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_EXAMPLE);
    }

    private void speakSpelling() {
        if (displayText.length() == 0) return;
        progressStore.increment(packId, current().progressKey(), "spelling_count");
        LearningTtsBridge.speak(this, displayText, displayTtsPinyin,
                LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_SPELLING);
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
        speakSentence();
        long sentenceDelay = estimateSentenceDuration(displayText);
        autoPlayHandler.postDelayed(() -> {
            if (!autoPlayEnabled || generation != autoPlayGeneration || isFinishing()) return;
            speakSpelling();
            long spellingDelay = estimateSpellingDuration(displayTtsPinyin);
            autoPlayHandler.postDelayed(() -> {
                if (!autoPlayEnabled || generation != autoPlayGeneration || isFinishing()) return;
                speakSentence();
                autoPlayHandler.postDelayed(() -> playAutoSequence(generation),
                        estimateSentenceDuration(displayText) + 850L);
            }, spellingDelay);
        }, sentenceDelay + 450L);
    }

    private long estimateSentenceDuration(String value) {
        String clean = value == null ? "" : value.replaceAll("[\\s，。！？、,.!?]", "");
        int count = clean.codePointCount(0, clean.length());
        return Math.max(1800L, count * 620L);
    }

    private long estimateSpellingDuration(String value) {
        String clean = value == null ? "" : value.trim();
        int syllables = clean.length() == 0 ? 1 : clean.split("\\s+").length;
        return Math.max(2500L, syllables * 820L);
    }

    private void stopAutoPlay() {
        autoPlayEnabled = false;
        autoPlayGeneration++;
        autoPlayHandler.removeCallbacksAndMessages(null);
        updateAutoPlayUi();
    }

    private void restartAutoPlayIfNeeded() {
        if (!autoPlayEnabled) return;
        autoPlayGeneration++;
        autoPlayHandler.removeCallbacksAndMessages(null);
        playAutoSequence(autoPlayGeneration);
    }

    private void updateAutoPlayUi() {
        if (loopIconView != null) {
            loopIconView.setIconColor(autoPlayEnabled ? COLOR_BRAND : COLOR_TEXT);
            loopIconView.setBackground(rounded(autoPlayEnabled ? COLOR_BRAND_SOFT : 0xFFF1F2F6,
                    dp(24), 0, 0));
        }
        if (loopLabelView != null) {
            loopLabelView.setTextColor(autoPlayEnabled ? COLOR_BRAND : COLOR_SUB);
            loopLabelView.setTypeface(autoPlayEnabled ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void speakMyanmar() {
        if (displayMeaningMy.length() == 0) return;
        LearningTtsBridge.speak(this, displayMeaningMy, "my-MM", "auto");
    }

    private void openPronunciation() {
        SpeakingPhrase phrase = current();
        if (phrase == null) return;
        progressStore.increment(packId, phrase.progressKey(), "pronunciation_count");
        Intent intent = new Intent(this, WordPronunciationActivity.class);
        intent.putExtra(WordPronunciationActivity.EXTRA_WORD, displayText);
        intent.putExtra(WordPronunciationActivity.EXTRA_PINYIN, displayPinyin);
        intent.putExtra(WordPronunciationActivity.EXTRA_SPELLING_TEXT, displayTtsPinyin);
        startActivity(intent);
    }

    private void openAiCoach() {
        SpeakingPhrase phrase = current();
        if (phrase == null) return;
        String[] providers = {"DeepSeek", getString(R.string.speaking_provider_qwen)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.speaking_choose_coach)
                .setItems(providers, (dialog, which) -> {
                    String provider = which == 0 ? "deepseek" : "qwen";
                    getSharedPreferences("tsdd_speaking", MODE_PRIVATE)
                            .edit().putString("ai_provider", provider).apply();
                    launchAiCoach(provider, phrase);
                })
                .show();
    }

    private void launchAiCoach(String provider, SpeakingPhrase phrase) {
        progressStore.increment(packId, phrase.progressKey(), "ai_practice_count");
        String url = "deepseek".equals(provider)
                ? "https://chat.deepseek.com/" : "https://chat.qwen.ai/";
        String providerTitle = "deepseek".equals(provider)
                ? getString(R.string.speaking_deepseek_coach_title)
                : getString(R.string.speaking_qwen_coach_title);
        AiScriptWebActivity.open(this, providerTitle, url, buildCoachPrompt(phrase),
                "speaking-coach");
    }

    private String buildCoachPrompt(SpeakingPhrase phrase) {
        StringBuilder replacements = new StringBuilder();
        for (SpeakingPhrase.Variant item : phrase.replacements) {
            if (replacements.length() > 0) replacements.append('、');
            replacements.append(item.text);
        }
        StringBuilder alternatives = new StringBuilder();
        for (SpeakingPhrase.Variant item : phrase.alternatives) {
            if (alternatives.length() > 0) alternatives.append('、');
            alternatives.append(item.text);
        }
        String coachText = displayText.length() > 0 ? displayText : phrase.text;
        String coachPinyin = displayPinyin.length() > 0 ? displayPinyin : phrase.pinyin;
        String coachMeaning = displayMeaningMy.length() > 0 ? displayMeaningMy : phrase.meaningMy;
        String coachScene = primaryScene(phrase);
        if (coachScene.length() == 0) coachScene = getString(R.string.speaking_scene_default);
        StringBuilder prompt = new StringBuilder();
        prompt.append(getString(R.string.speaking_coach_prompt_intro)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_sentence, coachText)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_pinyin, coachPinyin)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_meaning, coachMeaning)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_scene, coachScene)).append('\n');
        if (replacements.length() > 0) {
            prompt.append(getString(R.string.speaking_coach_prompt_replacements, replacements))
                    .append('\n');
        }
        if (alternatives.length() > 0) {
            prompt.append(getString(R.string.speaking_coach_prompt_alternatives, alternatives))
                    .append('\n');
        }
        prompt.append(getString(R.string.speaking_coach_prompt_hint_language)).append('\n');
        prompt.append(getString(R.string.speaking_coach_prompt_rules));
        return prompt.toString();
    }


    private String primaryScene(SpeakingPhrase phrase) {
        if (phrase == null) return "";
        String language = uiLanguage();
        if ("my".equals(language)) {
            return phrase.sceneMy.length() > 0 ? phrase.sceneMy : phrase.scene;
        }
        if ("en".equals(language)) {
            return phrase.sceneEn.length() > 0 ? phrase.sceneEn : phrase.scene;
        }
        return phrase.scene.length() > 0 ? phrase.scene : phrase.sceneMy;
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

    private void toggleFavorite() {
        SpeakingPhrase phrase = current();
        if (phrase == null) return;
        boolean favorite = progressStore.toggleFavorite(packId, phrase.progressKey());
        favoriteView.setText(favorite ? "★" : "☆");
        favoriteView.setTextColor(favorite ? 0xFFE89A32 : COLOR_SUB);
    }

    private boolean handleCardTouch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchDownX = event.getX();
            touchDownY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - touchDownX;
            float dy = event.getY() - touchDownY;
            if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                if (dx < 0) moveNext(); else movePrevious();
            } else if (Math.hypot(dx, dy) < dp(18)) {
                revealAnswer();
            }
            return true;
        }
        return true;
    }

    private void moveNext() {
        if (phrases.isEmpty()) return;
        stopAutoPlay();
        currentIndex = (currentIndex + 1) % phrases.size();
        renderCurrent();
    }

    private void movePrevious() {
        if (phrases.isEmpty()) return;
        stopAutoPlay();
        currentIndex = (currentIndex - 1 + phrases.size()) % phrases.size();
        renderCurrent();
    }

    private SpeakingPhrase current() {
        return phrases.isEmpty() ? null : phrases.get(Math.max(0,
                Math.min(currentIndex, phrases.size() - 1)));
    }

    private static final class ActionIconView extends View {
        static final int TYPE_SPEAKER = 1;
        static final int TYPE_SPELLING = 2;
        static final int TYPE_LOOP = 3;
        static final int TYPE_MICROPHONE = 4;
        static final int TYPE_AI = 5;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int type;
        private int iconColor = COLOR_TEXT;

        ActionIconView(Context context, int type) {
            super(context);
            this.type = type;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setClickable(false);
        }

        void setIconColor(int color) {
            iconColor = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setColor(iconColor);
            paint.setStrokeWidth(1.9f * d);
            paint.setStyle(Paint.Style.STROKE);

            if (type == TYPE_SPEAKER) {
                drawSpeaker(canvas, cx, cy, d);
            } else if (type == TYPE_SPELLING) {
                drawSpelling(canvas, cx, cy, d);
            } else if (type == TYPE_LOOP) {
                drawLoop(canvas, cx, cy, d);
            } else if (type == TYPE_MICROPHONE) {
                drawMicrophone(canvas, cx, cy, d);
            } else {
                drawAi(canvas, cx, cy, d);
            }
        }

        private void drawSpeaker(Canvas canvas, float cx, float cy, float d) {
            paint.setStyle(Paint.Style.FILL);
            rect.set(cx - 9f * d, cy - 4f * d, cx - 5f * d, cy + 4f * d);
            canvas.drawRoundRect(rect, 1.2f * d, 1.2f * d, paint);
            android.graphics.Path cone = new android.graphics.Path();
            cone.moveTo(cx - 5f * d, cy - 4f * d);
            cone.lineTo(cx + 0.5f * d, cy - 8f * d);
            cone.lineTo(cx + 0.5f * d, cy + 8f * d);
            cone.lineTo(cx - 5f * d, cy + 4f * d);
            cone.close();
            canvas.drawPath(cone, paint);
            paint.setStyle(Paint.Style.STROKE);
            rect.set(cx - 1f * d, cy - 6f * d, cx + 9f * d, cy + 6f * d);
            canvas.drawArc(rect, -48f, 96f, false, paint);
            rect.set(cx, cy - 8.5f * d, cx + 13f * d, cy + 8.5f * d);
            canvas.drawArc(rect, -45f, 90f, false, paint);
        }

        private void drawSpelling(Canvas canvas, float cx, float cy, float d) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(21f * d);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText("ā", cx, baseline, paint);
        }

        private void drawLoop(Canvas canvas, float cx, float cy, float d) {
            paint.setStyle(Paint.Style.STROKE);
            rect.set(cx - 9f * d, cy - 7f * d, cx + 9f * d, cy + 7f * d);
            canvas.drawArc(rect, 205f, 215f, false, paint);
            canvas.drawArc(rect, 25f, 215f, false, paint);
            paint.setStyle(Paint.Style.FILL);
            android.graphics.Path top = new android.graphics.Path();
            top.moveTo(cx + 8.5f * d, cy - 7f * d);
            top.lineTo(cx + 3.5f * d, cy - 8.5f * d);
            top.lineTo(cx + 6.5f * d, cy - 3.5f * d);
            top.close();
            canvas.drawPath(top, paint);
            android.graphics.Path bottom = new android.graphics.Path();
            bottom.moveTo(cx - 8.5f * d, cy + 7f * d);
            bottom.lineTo(cx - 3.5f * d, cy + 8.5f * d);
            bottom.lineTo(cx - 6.5f * d, cy + 3.5f * d);
            bottom.close();
            canvas.drawPath(bottom, paint);
        }

        private void drawMicrophone(Canvas canvas, float cx, float cy, float d) {
            paint.setStyle(Paint.Style.STROKE);
            rect.set(cx - 4.5f * d, cy - 9f * d, cx + 4.5f * d, cy + 3f * d);
            canvas.drawRoundRect(rect, 4.5f * d, 4.5f * d, paint);
            android.graphics.Path cradle = new android.graphics.Path();
            cradle.moveTo(cx - 8f * d, cy + 0.5f * d);
            cradle.cubicTo(cx - 8f * d, cy + 7f * d,
                    cx + 8f * d, cy + 7f * d, cx + 8f * d, cy + 0.5f * d);
            canvas.drawPath(cradle, paint);
            canvas.drawLine(cx, cy + 7f * d, cx, cy + 11f * d, paint);
            canvas.drawLine(cx - 4f * d, cy + 11f * d, cx + 4f * d, cy + 11f * d, paint);
        }

        private void drawAi(Canvas canvas, float cx, float cy, float d) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(13.5f * d);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText("AI", cx, baseline, paint);
        }
    }

    private final class DetailSwipeHost extends FrameLayout {
        private float downX;
        private float downY;
        private boolean horizontalSwipe;

        DetailSwipeHost(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                horizontalSwipe = false;
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > dp(18) && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                    horizontalSwipe = true;
                    return true;
                }
            }
            return horizontalSwipe;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX;
                if (horizontalSwipe && Math.abs(dx) > dp(52)) {
                    showDetailTab(detailTabIndex + (dx < 0 ? 1 : -1));
                }
                horizontalSwipe = false;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                horizontalSwipe = false;
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

    private String safe(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        return result.length() == 0 ? fallback : result;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
