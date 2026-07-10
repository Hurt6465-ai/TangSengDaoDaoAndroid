package com.chat.dating;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 轻量筛选弹窗。筛选值仍保存在 DatingFilter，后端未识别的字段由客户端二次过滤。 */
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

        country.setOnClickListener(v -> {
            if (DatingFilter.COUNTRY_SMART.equals(draft.countryMode)) draft.countryMode = DatingFilter.COUNTRY_SAME;
            else if (DatingFilter.COUNTRY_SAME.equals(draft.countryMode)) draft.countryMode = DatingFilter.COUNTRY_FOREIGN;
            else draft.countryMode = DatingFilter.COUNTRY_SMART;
            country.setText(countryText(draft.countryMode));
        });
        gender.setOnClickListener(v -> {
            if ("all".equals(draft.gender)) draft.gender = "female";
            else if ("female".equals(draft.gender)) draft.gender = "male";
            else draft.gender = "all";
            gender.setText(genderText(draft.gender));
        });
        age.setOnClickListener(v -> {
            if (draft.ageMin == 18 && draft.ageMax == 35) {
                draft.ageMin = 22; draft.ageMax = 35;
            } else if (draft.ageMin == 22 && draft.ageMax == 35) {
                draft.ageMin = 18; draft.ageMax = 45;
            } else if (draft.ageMin == 18 && draft.ageMax == 45) {
                draft.ageMin = 30; draft.ageMax = 50;
            } else {
                draft.ageMin = 18; draft.ageMax = 35;
            }
            age.setText(ageText(draft.ageMin, draft.ageMax));
        });
        goal.setOnClickListener(v -> {
            if ("love".equals(draft.goal)) draft.goal = "marriage";
            else if ("marriage".equals(draft.goal)) draft.goal = "all";
            else draft.goal = "love";
            goal.setText(goalText(draft.goal));
        });

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
}
