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

import com.chat.partner.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class PartnerTagSelectorActivity extends Activity {
    public static final String EXTRA_TAGS = "tags";

    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private LinearLayout groupContainer;
    private TextView saveBtn;

    private final String[][] groups = new String[][]{
            {"语言能力", "母语者", "流利交流", "中高级", "初学者", "零基础"},
            {"学习目标", "找语伴", "工作需要", "兴趣爱好", "交朋友", "文化交流", "准备留学", "准备旅行", "职场提升", "日常练习", "找对象"},
            {"感情状况", "保密", "单身", "在交往", "已婚", "离异"},
            {"性格标签", "有耐心", "外向", "安静", "内向", "幽默", "温柔", "认真", "慢热", "高情商", "好相处"},
            {"宠物兴趣", "狗", "猫", "兔子", "鸟", "鱼", "仓鼠", "爬宠", "喜欢动物"},
            {"运动兴趣", "跑步", "篮球", "足球", "羽毛球", "健身", "瑜伽", "游泳", "骑行", "徒步", "滑板"},
            {"影视兴趣", "电影", "喜剧片", "爱情片", "动作片", "悬疑片", "纪录片", "电视剧", "动漫", "综艺", "短视频"},
            {"职业状态", "保密", "在校生", "普通职工", "服务员", "老师", "警察", "司机", "销售", "老板", "自由职业", "待业中", "其他"},
            {"学历", "保密", "初中及以下", "高中", "本科", "硕士及以上", "其他"},
            {"安全边界", "礼貌聊天", "拒绝骚扰", "不加联系方式", "平台内沟通", "互相尊重"},
            {"学习方式", "自学", "线下学", "线上学"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected.addAll(split(getIntent().getStringExtra(EXTRA_TAGS)));
        buildContentView();
        renderGroups();
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

        ScrollView scrollView = new ScrollView(this);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        groupContainer = new LinearLayout(this);
        groupContainer.setOrientation(LinearLayout.VERTICAL);
        groupContainer.setPadding(dp(16), dp(6), dp(16), dp(20));
        scrollView.addView(groupContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        saveBtn = new TextView(this);
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
        for (String[] group : groups) {
            TextView title = new TextView(this);
            title.setText(group[0]);
            title.setTextSize(16);
            title.setTextColor(0xFF222222);
            title.setPadding(0, dp(18), 0, dp(8));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            groupContainer.addView(title, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout row = null;
            for (int i = 1; i < group.length; i++) {
                if (row == null || row.getChildCount() >= 3) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    groupContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
                row.addView(makeChip(group[i]));
            }
        }
    }

    private TextView makeChip(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(8), dp(9), dp(8), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        tv.setLayoutParams(lp);
        refreshChip(tv, selected.contains(text));
        tv.setOnClickListener(v -> {
            if (selected.contains(text)) selected.remove(text);
            else selected.add(text);
            refreshChip(tv, selected.contains(text));
        });
        return tv;
    }

    private void refreshChip(TextView tv, boolean checked) {
        tv.setTextColor(checked ? 0xFFFFFFFF : 0xFF555555);
        tv.setBackgroundResource(checked ? R.drawable.bg_partner_tag_selected : R.drawable.bg_partner_tag_unselected);
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
            if (!TextUtils.isEmpty(clean) && !out.contains(clean)) out.add(clean);
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
