package com.chat.dating;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 筛选弹窗。每个选项都用单选弹窗，不再通过连续点击轮换值。 */
public final class DatingFilterDialog {
    public interface Callback {
        void onApplied(DatingFilter filter);
    }

    private DatingFilterDialog() {}

    public static void show(Context context, DatingFilter original, Callback callback) {
        DatingFilter draft = new DatingFilter();
        if (original != null) {
            draft.countryMode = original.countryMode;
            draft.gender = original.gender;
            draft.ageMin = original.ageMin;
            draft.ageMax = original.ageMax;
            draft.goal = original.goal;
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 22), dp(context, 16), dp(context, 22), dp(context, 8));

        TextView tip = text(context, "如果任意一方选择只接受本国，系统不会推荐异国用户。", 13, false);
        tip.setTextColor(Color.rgb(120, 120, 128));
        root.addView(tip);

        TextView country = row(context, countryText(draft.countryMode));
        TextView gender = row(context, genderText(draft.gender));
        TextView age = row(context, ageText(draft.ageMin, draft.ageMax));
        TextView goal = row(context, goalText(draft.goal));
        root.addView(country);
        root.addView(gender);
        root.addView(age);
        root.addView(goal);

        country.setOnClickListener(v -> choose(context, "国家/地区", new String[]{"智能推荐", "只看本国恋", "只看可异国恋"}, countryText(draft.countryMode).replace("国家：", ""), value -> {
            if ("只看本国恋".equals(value)) draft.countryMode = DatingFilter.COUNTRY_SAME;
            else if ("只看可异国恋".equals(value)) draft.countryMode = DatingFilter.COUNTRY_FOREIGN;
            else draft.countryMode = DatingFilter.COUNTRY_SMART;
            country.setText(countryText(draft.countryMode));
        }));

        gender.setOnClickListener(v -> choose(context, "性别", new String[]{"不限", "女生", "男生"}, genderText(draft.gender).replace("性别：", ""), value -> {
            if ("女生".equals(value)) draft.gender = "female";
            else if ("男生".equals(value)) draft.gender = "male";
            else draft.gender = "all";
            gender.setText(genderText(draft.gender));
        }));

        age.setOnClickListener(v -> choose(context, "年龄", new String[]{"18-28 岁", "18-35 岁", "22-35 岁", "25-40 岁", "30-50 岁", "18-60 岁"}, ageText(draft.ageMin, draft.ageMax).replace("年龄：", ""), value -> {
            String raw = value.replace(" 岁", "");
            String[] pair = raw.split("-");
            if (pair.length == 2) {
                try {
                    draft.ageMin = Integer.parseInt(pair[0]);
                    draft.ageMax = Integer.parseInt(pair[1]);
                } catch (Exception ignored) {}
            }
            age.setText(ageText(draft.ageMin, draft.ageMax));
        }));

        goal.setOnClickListener(v -> choose(context, "恋爱意向", new String[]{"认真恋爱", "奔结婚", "不限"}, goalText(draft.goal).replace("意向：", ""), value -> {
            if ("奔结婚".equals(value)) draft.goal = "marriage";
            else if ("不限".equals(value)) draft.goal = "all";
            else draft.goal = "love";
            goal.setText(goalText(draft.goal));
        }));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("筛选偏好")
                .setView(root)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            draft.save(context);
            dialog.dismiss();
            if (callback != null) callback.onApplied(draft);
        }));
        dialog.show();
    }

    private static void choose(Context context, String title, String[] items, String current, ValueCallback callback) {
        int checked = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (callback != null) callback.onValue(items[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static TextView row(Context context, String value) {
        TextView view = text(context, value, 15, true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(247, 247, 249));
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), Color.rgb(230, 230, 235));
        view.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 50));
        lp.setMargins(0, dp(context, 12), 0, 0);
        view.setLayoutParams(lp);
        return view;
    }

    private static TextView text(Context context, String value, int sp, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(45, 45, 52));
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private static String countryText(String mode) {
        if (DatingFilter.COUNTRY_SAME.equals(mode)) return "国家：只看本国恋";
        if (DatingFilter.COUNTRY_FOREIGN.equals(mode)) return "国家：只看可异国恋";
        return "国家：智能推荐";
    }

    private static String genderText(String value) {
        if ("female".equals(value)) return "性别：女生";
        if ("male".equals(value)) return "性别：男生";
        return "性别：不限";
    }

    private static String ageText(int min, int max) {
        return "年龄：" + min + "-" + max + " 岁";
    }

    private static String goalText(String value) {
        if ("marriage".equals(value)) return "意向：奔结婚";
        if ("all".equals(value)) return "意向：不限";
        return "意向：认真恋爱";
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface ValueCallback {
        void onValue(String value);
    }
}
