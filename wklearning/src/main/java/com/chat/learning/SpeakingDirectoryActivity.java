package com.chat.learning;

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
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Speaking category browser. Categories open the vertical full-screen phrase feed. */
public class SpeakingDirectoryActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PARENT_ID = "parent_id";

    private static final int REQUEST_IMPORT_SPEAKING_PACK = 7203;
    private static final int COLOR_BG = 0xFFF5F6FA;
    private static final int COLOR_TEXT = 0xFF151925;
    private static final int COLOR_SUB = 0xFF747C8E;
    private static final int COLOR_BRAND = 0xFF5E5CE6;
    private static final int COLOR_STROKE = 0xFFE7E9F0;

    private final Map<String, CategoryBinding> bindings = new HashMap<>();
    private LearningCatalogRepository.Catalog catalog;
    private SpeakingProgressStore progressStore;
    private String requestedParentId = "";

    public static void open(Context context, String title, String parentId) {
        Intent intent = new Intent(context, SpeakingDirectoryActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_PARENT_ID, parentId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestedParentId = safe(getIntent().getStringExtra(EXTRA_PARENT_ID));
        catalog = LearningCatalogRepository.load(this, "speaking");
        progressStore = new SpeakingProgressStore(this);

        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        buildLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProgress();
    }

    @Override
    protected void onDestroy() {
        if (progressStore != null) progressStore.close();
        super.onDestroy();
    }

    private void buildLayout() {
        bindings.clear();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text(getString(R.string.speaking_directory_subtitle),
                13.5f, COLOR_SUB, false);
        subtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(4), 0, dp(14));
        content.addView(subtitle, subLp);

        if (catalog == null || catalog.items == null || catalog.items.isEmpty()) {
            content.addView(emptyView(), new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        ArrayList<LearningCatalogRepository.Node> imported = new ArrayList<>();
        for (LearningCatalogRepository.Node rootNode : catalog.items) {
            if (rootNode == null) continue;
            if (rootNode.imported) {
                imported.add(rootNode);
                continue;
            }
            if (rootNode.hasChildren()) {
                content.addView(groupTitle(rootNode.title, rootNode.subtitle), groupTitleLp());
                List<LearningCatalogRepository.Node> leaves = new ArrayList<>();
                collectLeafNodes(rootNode.children, leaves);
                addCategoryGrid(content, leaves);
            } else {
                ArrayList<LearningCatalogRepository.Node> single = new ArrayList<>();
                single.add(rootNode);
                addCategoryGrid(content, single);
            }
        }

        if (!imported.isEmpty()) {
            content.addView(groupTitle(getString(R.string.speaking_imported_group),
                    getString(R.string.speaking_imported_group_subtitle)), groupTitleLp());
            addCategoryGrid(content, imported);
        }
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(resolveTitle(), 27, COLOR_TEXT, true);
        title.setIncludeFontPadding(false);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView favorites = headerButton(getString(R.string.speaking_favorites_action));
        favorites.setOnClickListener(v -> SpeakingFullscreenActivity.openFavorites(this));
        row.addView(favorites, new LinearLayout.LayoutParams(dp(72), dp(40)));

        TextView importView = headerButton(getString(R.string.speaking_import));
        importView.setOnClickListener(v -> showImportMenu());
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(dp(64), dp(40));
        importLp.setMargins(dp(7), 0, 0, 0);
        row.addView(importView, importLp);
        return row;
    }

    private TextView headerButton(String label) {
        TextView view = text(label, 12.5f, COLOR_BRAND, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setBackground(rounded(Color.WHITE, dp(20), COLOR_STROKE, dp(1)));
        return view;
    }

    private void collectLeafNodes(List<LearningCatalogRepository.Node> source,
                                  List<LearningCatalogRepository.Node> output) {
        if (source == null || output == null) return;
        for (LearningCatalogRepository.Node node : source) {
            if (node == null) continue;
            if (node.hasChildren()) collectLeafNodes(node.children, output);
            else output.add(node);
        }
    }

    private void addCategoryGrid(LinearLayout parent,
                                 List<LearningCatalogRepository.Node> nodes) {
        if (nodes == null || nodes.isEmpty()) return;
        int index = 0;
        while (index < nodes.size()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.setMargins(0, 0, 0, dp(9));
            parent.addView(row, rowLp);

            for (int column = 0; column < 2; column++) {
                if (index < nodes.size()) {
                    LearningCatalogRepository.Node node = nodes.get(index++);
                    SpeakingPhraseRepository.Pack pack = SpeakingPhraseRepository.load(
                            this, node.asset, node.id, node.title);
                    row.addView(categoryCard(node, pack),
                            new LinearLayout.LayoutParams(0, dp(104), 1f));
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1f));
                }
                if (column == 0) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
                }
            }
        }
    }

    private View groupTitle(String titleValue, String subtitleValue) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(titleValue, 18, COLOR_TEXT, true);
        title.setIncludeFontPadding(false);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        if (!safe(subtitleValue).isEmpty()) {
            TextView sub = text(subtitleValue, 12.5f, COLOR_SUB, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(4), 0, 0);
            box.addView(sub, lp);
        }
        return box;
    }

    private LinearLayout.LayoutParams groupTitleLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(1), dp(8), dp(1), dp(9));
        return lp;
    }

    private View categoryCard(LearningCatalogRepository.Node node,
                              SpeakingPhraseRepository.Pack pack) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        card.setPadding(dp(12), dp(10), dp(10), dp(9));
        card.setBackground(rounded(Color.WHITE, dp(18), COLOR_STROKE, dp(1)));
        card.setOnClickListener(v -> SpeakingFullscreenActivity.open(
                this, pack.id, pack.title, node.asset, ""));
        if (node.imported) {
            card.setOnLongClickListener(v -> {
                confirmDelete(node);
                return true;
            });
        }

        TextView title = text(node.title, 15.5f, COLOR_TEXT, true);
        title.setIncludeFontPadding(false);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView progress = text("", 11.5f, node.imported ? COLOR_BRAND : COLOR_SUB, false);
        progress.setIncludeFontPadding(false);
        progress.setMaxLines(2);
        progress.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, -2);
        progressLp.setMargins(0, dp(5), 0, 0);
        card.addView(progress, progressLp);

        bindings.put(pack.id, new CategoryBinding(pack, progress, node.imported));
        updateCategory(bindings.get(pack.id));
        return card;
    }

    private void refreshProgress() {
        if (progressStore == null) return;
        for (CategoryBinding binding : bindings.values()) updateCategory(binding);
    }

    private void updateCategory(CategoryBinding binding) {
        if (binding == null) return;
        SpeakingProgressStore.PackStats stats = progressStore.stats(
                binding.pack.id, binding.pack.phrases.size(), System.currentTimeMillis());
        String value = getString(R.string.speaking_progress_learned, stats.learned, stats.total);
        if (binding.imported) value += "  " + getString(R.string.speaking_imported_delete_hint);
        binding.progress.setText(value);
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
                    if (SpeakingImportedPackStore.delete(this, node.id)) {
                        catalog = LearningCatalogRepository.load(this, "speaking");
                        buildLayout();
                        Toast.makeText(this, R.string.speaking_import_delete_success,
                                Toast.LENGTH_SHORT).show();
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
                    catalog = LearningCatalogRepository.load(this, "speaking");
                    buildLayout();
                    Toast.makeText(this, getString(R.string.speaking_import_success,
                            result.title, result.count), Toast.LENGTH_LONG).show();
                    SpeakingFullscreenActivity.open(this, result.packId, result.title,
                            result.asset, "");
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

    private String resolveTitle() {
        String value = safe(getIntent().getStringExtra(EXTRA_TITLE));
        if (value.isEmpty() || !requestedParentId.isEmpty()) {
            return getString(R.string.speaking_directory_title);
        }
        return value;
    }

    private View emptyView() {
        TextView view = text(getString(R.string.speaking_directory_empty),
                14, COLOR_SUB, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(30), dp(20), dp(30));
        view.setBackground(rounded(Color.WHITE, dp(20), COLOR_STROKE, dp(1)));
        return view;
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

    private static final class CategoryBinding {
        final SpeakingPhraseRepository.Pack pack;
        final TextView progress;
        final boolean imported;

        CategoryBinding(SpeakingPhraseRepository.Pack pack, TextView progress, boolean imported) {
            this.pack = pack;
            this.progress = progress;
            this.imported = imported;
        }
    }
}
