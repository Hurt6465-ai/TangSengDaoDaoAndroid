package com.chat.learning;

import android.content.Context;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** 每个学习栏目一个 RecyclerView 页面，外层由 LearningFragment 的 ViewPager2 横向切换。 */
public class LearningPageFragment extends Fragment {
    private static final String ARG_PAGE = "page";
    private static final int COLOR_BG = 0xFFF7F9FC;
    private static final int COLOR_BLUE = 0xFF1877F2;
    private static final int COLOR_TEXT_DARK = 0xFF111827;
    private static final int COLOR_TEXT_GRAY = 0xFF6B7280;
    private static final int COLOR_LINE = 0xFFE8EDF6;

    public static LearningPageFragment newInstance(int page) {
        LearningPageFragment fragment = new LearningPageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setBackgroundColor(COLOR_BG);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        int page = getArguments() != null ? getArguments().getInt(ARG_PAGE, 0) : 0;
        recyclerView.setAdapter(new LearningCardAdapter(buildCards(page)));
        recyclerView.setPadding(0, dp(10), 0, dp(28));
        recyclerView.setClipToPadding(false);
        return recyclerView;
    }

    private List<Card> buildCards(int page) {
        ArrayList<Card> list = new ArrayList<>();
        switch (page) {
            case 0:
                list.add(Card.hero("拼音点读", "声母、韵母、整体认读，适合中文零基础。", "点击卡片可播放发音，后续接 wkspeech。"));
                list.add(Card.normal("单韵母", "a  o  e  i  u  ü", "先听，再跟读，最后做听音选择。"));
                list.add(Card.normal("声母", "b p m f / d t n l / g k h", "按发音部位分组，低端机也能流畅展示。"));
                list.add(Card.normal("易混发音", "zh ch sh r / z c s / j q x", "用对比卡片做强化训练。"));
                break;
            case 1:
                list.add(Card.action("全屏背单词", "抖音式上下切词，左右滑判断会了/不会，点击翻面。", "开始", Card.ACTION_WORD_FULLSCREEN));
                list.add(Card.normal("今日单词", "你好、谢谢、吃饭、工作、学习", "默认 20 个词一组，适合广告或 VIP 以后做权限。"));
                list.add(Card.normal("生词本", "左滑不会的词自动进入复习池", "后续可存 SQLite，本地离线不丢。"));
                list.add(Card.normal("高频生活词", "餐厅、医院、机场、求职、租房", "跟你的语伴场景保持一致。"));
                break;
            case 2:
                list.add(Card.hero("口语场景", "打招呼、点餐、买东西、面试、医院、机场。", "每个场景 10 句短句，适合朗读和跟读。"));
                list.add(Card.normal("打招呼", "你好 / 最近怎么样 / 你会说中文吗", "中文 + 拼音 + 缅语/英语释义。"));
                list.add(Card.normal("点餐买东西", "我要这个 / 多少钱 / 可以便宜点吗", "句子短，适合手机朗读。"));
                list.add(Card.normal("求职面试", "我想找工作 / 我有经验 / 我可以加班", "服务语伴里的求职功能。"));
                break;
            case 3:
                list.add(Card.hero("句型模板", "我想 + 动词 / 我要 + 名词 / 可以...吗？", "用模板套句子，比背长文章更适合初学者。"));
                list.add(Card.normal("我想 + 动词", "我想学习中文 / 我想找工作", "动词替换练习。"));
                list.add(Card.normal("我要 + 名词", "我要水 / 我要米饭 / 我要这个", "购物、点餐场景高频。"));
                list.add(Card.normal("你可以...吗？", "你可以帮我吗 / 你可以慢点说吗", "口语求助必备。"));
                break;
            case 4:
                list.add(Card.hero("语法短卡", "不要长文章，每张卡只讲一个点。", "一句解释 + 三个例句 + 一个互动题。"));
                list.add(Card.normal("了", "表示变化或完成", "我吃饭了 / 天气热了。"));
                list.add(Card.normal("在", "表示正在进行", "我在学习 / 他在工作。"));
                list.add(Card.normal("吗 / 呢", "疑问语气", "你好吗？你呢？"));
                break;
            default:
                list.add(Card.hero("互动题", "选择题、听音选词、看图选词、句子排序、中缅互译。", "第一版先做静态题，后续远程 JSON 更新。"));
                list.add(Card.normal("选择题", "看到中文，选择正确拼音或翻译", "适合快速刷题。"));
                list.add(Card.normal("听音选词", "播放发音，选择听到的词", "以后接原生 TTS。"));
                list.add(Card.normal("句子排序", "把乱序词语拖成一句话", "后续再做拖拽，第一版可点选排序。"));
                list.add(Card.normal("中缅互译", "输入或选择译文", "跟你的翻译功能打通。"));
                break;
        }
        return list;
    }

    private class LearningCardAdapter extends RecyclerView.Adapter<LearningCardAdapter.Holder> {
        private final List<Card> cards;

        LearningCardAdapter(List<Card> cards) {
            this.cards = cards;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(16), dp(15), dp(16), dp(15));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.setMargins(dp(16), 0, dp(16), dp(12));
            root.setLayoutParams(lp);
            return new Holder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(cards.get(position));
        }

        @Override
        public int getItemCount() {
            return cards.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            private final LinearLayout root;
            private final TextView title;
            private final TextView desc;
            private final TextView extra;
            private final TextView action;

            Holder(@NonNull View itemView) {
                super(itemView);
                root = (LinearLayout) itemView;
                title = new TextView(itemView.getContext());
                desc = new TextView(itemView.getContext());
                extra = new TextView(itemView.getContext());
                action = new TextView(itemView.getContext());

                title.setTextColor(COLOR_TEXT_DARK);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setTextSize(19);
                root.addView(title, new LinearLayout.LayoutParams(-1, -2));

                desc.setTextColor(COLOR_TEXT_GRAY);
                desc.setTextSize(14);
                desc.setLineSpacing(dp(2), 1f);
                desc.setPadding(0, dp(7), 0, 0);
                root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

                extra.setTextColor(0xFF334155);
                extra.setTextSize(13);
                extra.setLineSpacing(dp(2), 1f);
                extra.setPadding(0, dp(10), 0, 0);
                root.addView(extra, new LinearLayout.LayoutParams(-1, -2));

                action.setTextSize(14);
                action.setTypeface(Typeface.DEFAULT_BOLD);
                action.setTextColor(Color.WHITE);
                action.setGravity(Gravity.CENTER);
                action.setBackground(rounded(COLOR_BLUE, dp(18), Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(92), dp(38));
                actionLp.setMargins(0, dp(13), 0, 0);
                root.addView(action, actionLp);
            }

            void bind(Card card) {
                boolean hero = card.hero;
                root.setBackground(rounded(hero ? 0xFFFFFFFF : 0xFFFFFFFF, dp(hero ? 22 : 18), COLOR_LINE, 1));
                title.setText(card.title);
                title.setTextSize(hero ? 21 : 18);
                desc.setText(card.desc);
                extra.setText(card.extra);
                if (card.actionType == Card.ACTION_WORD_FULLSCREEN) {
                    action.setVisibility(View.VISIBLE);
                    action.setText(card.actionText);
                    root.setOnClickListener(v -> openWordFullscreen());
                    action.setOnClickListener(v -> openWordFullscreen());
                } else {
                    action.setVisibility(View.GONE);
                    root.setOnClickListener(null);
                    action.setOnClickListener(null);
                }
            }
        }
    }

    private void openWordFullscreen() {
        startActivity(new Intent(requireContext(), WordFullscreenActivity.class));
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

    private static class Card {
        static final int ACTION_NONE = 0;
        static final int ACTION_WORD_FULLSCREEN = 1;

        final String title;
        final String desc;
        final String extra;
        final String actionText;
        final int actionType;
        final boolean hero;

        private Card(String title, String desc, String extra, String actionText, int actionType, boolean hero) {
            this.title = title;
            this.desc = desc;
            this.extra = extra;
            this.actionText = actionText;
            this.actionType = actionType;
            this.hero = hero;
        }

        static Card hero(String title, String desc, String extra) {
            return new Card(title, desc, extra, null, ACTION_NONE, true);
        }

        static Card normal(String title, String desc, String extra) {
            return new Card(title, desc, extra, null, ACTION_NONE, false);
        }

        static Card action(String title, String desc, String actionText, int actionType) {
            return new Card(title, desc, "上滑下一个，下滑上一个；左滑不会，右滑会了；点击卡片翻面。", actionText, actionType, true);
        }
    }
}
