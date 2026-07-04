package com.chat.learning;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class LearningFragment extends Fragment {
    private final List<PromptItem> promptItems = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        loadPrompts();

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 249, 252));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(requireContext());
        title.setText(getString(R.string.learning_title));
        title.setTextSize(28);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText(getString(R.string.learning_subtitle));
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(107, 114, 128));
        sub.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, dp(18));
        root.addView(sub, subLp);

        root.addView(card(getString(R.string.learning_deepseek_title), getString(R.string.learning_deepseek_desc), getString(R.string.learning_enter), () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));
        root.addView(card(getString(R.string.learning_qianwen_title), getString(R.string.learning_qianwen_desc), getString(R.string.learning_enter), () -> AiScriptWebActivity.open(requireContext(), "千问", "https://www.qianwen.com/")));
        root.addView(card(getString(R.string.learning_qwen_title), getString(R.string.learning_qwen_desc), getString(R.string.learning_enter), () -> AiScriptWebActivity.open(requireContext(), "Qwen", "https://chat.qwen.ai/")));
        root.addView(card(getString(R.string.learning_scene_title), getString(R.string.learning_scene_desc), getString(R.string.learning_select), this::showSceneDialog));
        root.addView(card(getString(R.string.learning_script_title), getString(R.string.learning_script_desc), getString(R.string.learning_manage), () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        TextView warn = new TextView(requireContext());
        warn.setText(getString(R.string.learning_warning));
        warn.setTextSize(12);
        warn.setTextColor(Color.rgb(107, 114, 128));
        warn.setPadding(dp(4), dp(14), dp(4), 0);
        root.addView(warn, new LinearLayout.LayoutParams(-1, -2));
        return scrollView;
    }

    private View card(String title, String desc, String action, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(rounded(Color.WHITE, dp(22), Color.rgb(232, 237, 246), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(lp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(19);
        titleView.setTextColor(Color.rgb(17, 24, 39));
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView descView = new TextView(requireContext());
        descView.setText(desc);
        descView.setTextSize(14);
        descView.setTextColor(Color.rgb(107, 114, 128));
        descView.setPadding(0, dp(7), 0, dp(13));
        card.addView(descView, new LinearLayout.LayoutParams(-1, -2));

        TextView actionView = new TextView(requireContext());
        actionView.setText(action);
        actionView.setTextSize(15);
        actionView.setTypeface(Typeface.DEFAULT_BOLD);
        actionView.setTextColor(Color.WHITE);
        actionView.setGravity(Gravity.CENTER);
        actionView.setBackground(rounded(Color.rgb(24, 119, 242), dp(16), Color.TRANSPARENT, 0));
        card.addView(actionView, new LinearLayout.LayoutParams(-1, dp(46)));
        card.setOnClickListener(v -> click.run());
        actionView.setOnClickListener(v -> click.run());
        return card;
    }

    private void showSceneDialog() {
        if (promptItems.isEmpty()) {
            Toast.makeText(requireContext(), "No prompts", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[promptItems.size()];
        for (int i = 0; i < promptItems.size(); i++) titles[i] = promptItems.get(i).title;
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.learning_choose_scene))
                .setItems(titles, (dialog, which) -> showTargetDialog(promptItems.get(which)))
                .setNegativeButton(getString(R.string.learning_cancel), null)
                .show();
    }

    private void showTargetDialog(PromptItem item) {
        String[] targets = new String[]{getString(R.string.learning_deepseek_title), getString(R.string.learning_qianwen_title), getString(R.string.learning_qwen_title)};
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.learning_choose_target))
                .setItems(targets, (dialog, which) -> {
                    if (which == 0) {
                        AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/", item.prompt);
                    } else if (which == 1) {
                        AiScriptWebActivity.open(requireContext(), "千问", "https:/886.best/", item.prompt);
                    } else {
                        AiScriptWebActivity.open(requireContext(), "Qwen", "https://chat.qwen.ai/", item.prompt);
                    }
                })
                .setNegativeButton(getString(R.string.learning_cancel), null)
                .show();
    }

    private void loadPrompts() {
        promptItems.clear();
        try (InputStream in = requireContext().getAssets().open("learning_prompts/official_prompts.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            JSONArray array = new JSONArray(out.toString("UTF-8"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                PromptItem item = new PromptItem();
                item.id = object.optString("id", "prompt_" + i);
                item.title = object.optString("title", "Prompt");
                item.description = object.optString("description", "");
                item.prompt = object.optString("prompt", "");
                if (item.prompt.length() > 0) promptItems.add(item);
            }
        } catch (Exception ignored) {
        }
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

    private static class PromptItem {
        String id;
        String title;
        String description;
        String prompt;
    }
}
