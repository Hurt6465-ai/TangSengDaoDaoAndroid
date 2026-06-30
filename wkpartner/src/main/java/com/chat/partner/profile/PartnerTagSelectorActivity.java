package com.chat.partner.profile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
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

    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private LinearLayout groupContainer;
    private TextView countTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected.addAll(PartnerTagLocalizer.toKeyList(split(getIntent().getStringExtra(EXTRA_TAGS))));
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
        for (int groupIndex = 0; groupIndex < PartnerTagLocalizer.TAG_KEYS.length; groupIndex++) {
            TextView title = new TextView(this);
            title.setText(PartnerTagLocalizer.groupTitle(this, groupIndex));
            title.setTextSize(16);
            title.setTextColor(0xFF222222);
            title.setPadding(0, dp(18), 0, dp(8));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            groupContainer.addView(title, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout row = null;
            for (int i = 0; i < PartnerTagLocalizer.TAG_KEYS[groupIndex].length; i++) {
                if (row == null || row.getChildCount() >= 2) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    groupContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
                row.addView(makeChip(groupIndex, PartnerTagLocalizer.TAG_KEYS[groupIndex][i]));
            }
        }
    }

    private TextView makeChip(int groupIndex, String key) {
        TextView tv = new TextView(this);
        tv.setText(PartnerTagLocalizer.tagText(this, key));
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tv.setEllipsize(null);
        tv.setPadding(dp(8), dp(7), dp(8), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        tv.setLayoutParams(lp);
        refreshChip(tv, selected.contains(key));
        tv.setOnClickListener(v -> {
            if (selected.contains(key)) {
                selected.remove(key);
            } else {
                if (selected.size() >= PartnerTagLocalizer.MAX_TAGS) {
                    Toast.makeText(this, R.string.partner_tag_max_tip, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (PartnerTagLocalizer.isSingleGroup(groupIndex)) {
                    removeGroupSelections(groupIndex);
                }
                selected.add(key);
            }
            renderGroups();
            updateCount();
        });
        return tv;
    }

    private void removeGroupSelections(int groupIndex) {
        ArrayList<String> remove = new ArrayList<>();
        for (String key : selected) {
            if (PartnerTagLocalizer.groupIndexOf(key) == groupIndex) remove.add(key);
        }
        selected.removeAll(remove);
    }

    private void refreshChip(TextView tv, boolean checked) {
        tv.setTextColor(checked ? 0xFF5B3FE6 : 0xFF555555);
        tv.setBackgroundResource(checked ? R.drawable.bg_partner_tag_selected : R.drawable.bg_partner_tag_unselected);
    }

    private void updateCount() {
        if (countTv != null) countTv.setText(selected.size() + "/" + PartnerTagLocalizer.MAX_TAGS);
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
            if (!TextUtils.isEmpty(clean) && !out.contains(clean) && out.size() < PartnerTagLocalizer.MAX_TAGS) out.add(clean);
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
