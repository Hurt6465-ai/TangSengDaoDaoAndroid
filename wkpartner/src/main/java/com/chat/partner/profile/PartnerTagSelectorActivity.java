package com.chat.partner.profile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.partner.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class PartnerTagSelectorActivity extends Activity {
    public static final String EXTRA_TAGS = "tags";
    private static final int MAX_TAGS = 20;

    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private LinearLayout groupContainer;
    private TextView countTv;

    private final PartnerTagLocalizer.Group[] groups = PartnerTagLocalizer.groups();



    private boolean isTagInGroup(String tag, PartnerTagLocalizer.Group group) {
        String key = PartnerTagLocalizer.normalizeKey(tag);
        return group != null && group.containsKey(key);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected.addAll(PartnerTagLocalizer.normalizeKeys(split(getIntent().getStringExtra(EXTRA_TAGS))));
        buildContentView();
        renderGroups();
        updateCount();
    }

    private void buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF7F7F7);
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(8), dp(28), dp(16), 0);
        root.addView(titleBar, new LinearLayout.LayoutParams(-1, dp(78)));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setGravity(Gravity.CENTER);
        back.setTextSize(36);
        back.setTextColor(0xFF222222);
        titleBar.addView(back, new LinearLayout.LayoutParams(dp(48), -1));
        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText(R.string.partner_tag_selector);
        title.setTextSize(18);
        title.setTextColor(0xFF222222);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

        countTv = new TextView(this);
        countTv.setTextSize(13);
        countTv.setTextColor(0xFF777777);
        titleBar.addView(countTv, new LinearLayout.LayoutParams(-2, -1));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        groupContainer = new LinearLayout(this);
        groupContainer.setOrientation(LinearLayout.VERTICAL);
        groupContainer.setPadding(dp(16), dp(6), dp(16), dp(20));
        scrollView.addView(groupContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView saveBtn = new TextView(this);
        saveBtn.setText(R.string.partner_save);
        saveBtn.setTextSize(16);
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setBackgroundResource(R.drawable.bg_partner_hello_button);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(48));
        saveLp.setMargins(dp(16), dp(10), dp(16), dp(18));
        root.addView(saveBtn, saveLp);
        saveBtn.setOnClickListener(v -> saveAndFinish());

        setContentView(root);
    }

    private void renderGroups() {
        if (groupContainer == null) return;
        groupContainer.removeAllViews();
        for (PartnerTagLocalizer.Group group : groups) {
            TextView title = new TextView(this);
            title.setText(group.title(this) + (group.singleChoice ? getString(R.string.partner_single_choice_suffix) : getString(R.string.partner_multi_choice_suffix)));
            title.setTextSize(16);
            title.setTextColor(0xFF222222);
            title.setPadding(0, dp(18), 0, dp(8));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            groupContainer.addView(title, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout row = null;
            for (String key : group.keys) {
                if (row == null || row.getChildCount() >= 3) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    groupContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
                row.addView(makeChip(key, group));
            }
        }
    }

    private TextView makeChip(String key, PartnerTagLocalizer.Group group) {
        TextView tv = new TextView(this);
        tv.setText(PartnerTagLocalizer.label(this, key));
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(8), dp(9), dp(8), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        tv.setLayoutParams(lp);
        refreshChip(tv, selected.contains(key));
        tv.setOnClickListener(v -> {
            if (selected.contains(key)) {
                selected.remove(key);
            } else {
                if (group.singleChoice) {
                    ArrayList<String> removeList = new ArrayList<>();
                    for (String item : selected) {
                        if (isTagInGroup(item, group)) removeList.add(item);
                    }
                    selected.removeAll(removeList);
                }
                if (selected.size() >= MAX_TAGS) {
                    Toast.makeText(this, R.string.partner_tag_max_tip, Toast.LENGTH_SHORT).show();
                    return;
                }
                selected.add(key);
            }
            renderGroups();
            updateCount();
        });
        return tv;
    }

    private void refreshChip(TextView tv, boolean checked) {
        tv.setTextColor(checked ? 0xFF5F48D9 : 0xFF555566);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(checked ? 0xE8FFFFFF : 0xBFFFFFFF);
        bg.setStroke(dp(1), checked ? 0x998E78FF : 0x55FFFFFF);
        tv.setBackground(bg);
        tv.setTypeface(checked ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void updateCount() {
        if (countTv != null) countTv.setText(selected.size() + "/" + MAX_TAGS);
    }

    private void saveAndFinish() {
        Intent data = new Intent();
        data.putExtra(EXTRA_TAGS, joinSelected());
        setResult(RESULT_OK, data);
        finish();
    }

    private ArrayList<String> split(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        String[] parts = text.replace('，', ' ').replace(',', ' ').replace('/', ' ').trim().split("\\s+");
        for (String item : parts) {
            String clean = item == null ? "" : item.trim();
            if (!TextUtils.isEmpty(clean) && !out.contains(clean) && out.size() < MAX_TAGS) out.add(clean);
        }
        return out;
    }

    private String joinSelected() {
        StringBuilder sb = new StringBuilder();
        for (String item : selected) {
            if (TextUtils.isEmpty(item)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(item.trim());
        }
        return sb.toString();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
