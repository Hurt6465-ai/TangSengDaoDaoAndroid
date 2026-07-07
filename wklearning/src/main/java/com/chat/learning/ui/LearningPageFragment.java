package com.chat.learning.ui;

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

import com.chat.learning.data.WordRepository;
import com.chat.learning.fullscreen.WordFullscreenActivity;
import com.chat.learning.model.LearningCategory;
import com.chat.learning.review.LearningReviewStore;

import java.util.ArrayList;
import java.util.List;

/** 外层 ViewPager2 的页面。每个大类内部用小卡片，不直接全屏。 */
public class LearningPageFragment extends Fragment {
    private static final String ARG_PAGE = "page";
    private static final int COLOR_BG = 0xFFF6F8FC;
    private static final int COLOR_BLUE = 0xFF1877F2;
    private static final int COLOR_TEXT_DARK = 0xFF111827;
    private static final int COLOR_TEXT_GRAY = 0xFF6B7280;
    private static final int COLOR_LINE = 0xFFE8EDF6;

    public static LearningPageFragment newInstance(int page) {
        LearningPageFragment f = new LearningPageFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_PAGE, page);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setBackgroundColor(COLOR_BG);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setPadding(0, dp(12), 0, dp(30));
        rv.setClipToPadding(false);
        int page = getArguments() == null ? 0 : getArguments().getInt(ARG_PAGE, 0);
        rv.setAdapter(new CardAdapter(buildCards(page), page));
        return rv;
    }

    private List<Card> buildCards(int page) {
        ArrayList<Card> cards = new ArrayList<>();
        if (page == 0) {
            cards.add(Card.header("拼音点读", "先按分类小卡片进入：声母、韵母、声调、拼读练习。拼音音频量少，可以内置 APK。"));
            cards.add(Card.normal("声母", "b p m f / d t n l / g k h", "按发音部位分组，适合零基础。", "学习", "initials"));
            cards.add(Card.normal("韵母", "a o e i u ü / ai ei ui", "发音、口型、例字和跟读。", "学习", "finals"));
            cards.add(Card.normal("声调", "一声、二声、三声、四声、轻声", "用最少例字做声调感知。", "学习", "tones"));
            cards.add(Card.normal("拼读练习", "ba bo bi / ma mo mi", "声母 + 韵母组合训练。", "练习", "practice"));
        } else if (page == 1) {
            cards.add(Card.header("单词", "分类小卡片进入词库；点击分类后进入全屏背单词。复习记录使用 Room + SM-2。"));
            cards.add(Card.review("今日复习", "优先复习 nextReviewAt 已到期的词，再补新词。", "查看", "review_due"));
            WordRepository repo = new WordRepository(requireContext());
            for (LearningCategory c : repo.loadCategories()) {
                cards.add(Card.normal(c.title, c.subtitle, c.count > 0 ? c.count + " 个词 · 支持全屏背单词" : "内容可远程下发", c.action == null || c.action.length() == 0 ? "背单词" : c.action, c.id));
            }
        } else if (page == 2) {
            cards.add(Card.header("口语场景", "先占位，后续用 speaking/ 下的 JSON 下发内容。建议不要第一版就做太重。"));
            cards.add(Card.normal("打招呼", "你好、最近怎么样、你会说中文吗", "中文 + 拼音 + 缅语/英语释义。", "即将上线", "daily"));
            cards.add(Card.normal("点餐买东西", "我要这个、多少钱、可以便宜点吗", "句子短，适合手机朗读。", "即将上线", "shopping"));
            cards.add(Card.normal("求职面试", "我想找工作、我有经验、我可以加班", "服务语伴里的求职功能。", "即将上线", "job"));
        } else if (page == 3) {
            cards.add(Card.header("句型模板", "用模板套句子，不背长文章。"));
            cards.add(Card.normal("我想 + 动词", "我想学习中文 / 我想找工作", "动词替换练习。", "学习", "want_to"));
            cards.add(Card.normal("我要 + 名词", "我要水 / 我要米饭 / 我要这个", "购物、点餐场景高频。", "学习", "need_noun"));
            cards.add(Card.normal("你可以...吗？", "你可以帮我吗 / 你可以慢点说吗", "口语求助必备。", "学习", "can_you"));
        } else if (page == 4) {
            cards.add(Card.header("语法短卡", "不要长文章：一句解释 + 三个例句 + 一个互动题。"));
            cards.add(Card.normal("了", "表示变化或完成", "我吃饭了 / 天气热了。", "学习", "le"));
            cards.add(Card.normal("在", "表示正在进行", "我在学习 / 他在工作。", "学习", "zai"));
            cards.add(Card.normal("吗 / 呢", "疑问语气", "你好吗？你呢？", "学习", "ma_ne"));
        } else {
            cards.add(Card.header("互动题", "第一版先做静态题，后续再接远程 JSON。"));
            cards.add(Card.normal("选择题", "看到中文，选择正确拼音或翻译", "适合快速刷题。", "练习", "choice"));
            cards.add(Card.normal("听音选词", "播放发音，选择听到的词", "以后接原生 TTS。", "练习", "listening_choice"));
            cards.add(Card.normal("句子排序", "把乱序词语排成一句话", "第一版可点选排序，后续再拖拽。", "练习", "sentence_order"));
        }
        return cards;
    }

    private class CardAdapter extends RecyclerView.Adapter<CardAdapter.Holder> {
        private final List<Card> cards;
        private final int page;

        CardAdapter(List<Card> cards, int page) {
            this.cards = cards;
            this.page = page;
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
            final LinearLayout root;
            final TextView title;
            final TextView desc;
            final TextView extra;
            final TextView action;

            Holder(@NonNull View itemView) {
                super(itemView);
                root = (LinearLayout) itemView;
                title = text(19, COLOR_TEXT_DARK, true);
                desc = text(14, COLOR_TEXT_GRAY, false);
                extra = text(13, 0xFF334155, false);
                action = text(13, Color.WHITE, true);
                action.setGravity(Gravity.CENTER);
                action.setBackground(roundRect(COLOR_BLUE, dp(17), 0, 0));

                root.addView(title, new LinearLayout.LayoutParams(-1, -2));
                desc.setPadding(0, dp(7), 0, 0);
                root.addView(desc, new LinearLayout.LayoutParams(-1, -2));
                extra.setPadding(0, dp(9), 0, 0);
                root.addView(extra, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(dp(92), dp(36));
                aLp.setMargins(0, dp(12), 0, 0);
                root.addView(action, aLp);
            }

            void bind(Card card) {
                title.setText(card.title);
                desc.setText(card.desc);
                extra.setText(card.extra);
                action.setText(card.action);
                boolean header = card.header;
                title.setTextSize(header ? 21 : 18);
                action.setVisibility(header ? View.GONE : View.VISIBLE);
                root.setBackground(header ? gradientCard() : roundRect(Color.WHITE, dp(18), COLOR_LINE, 1));
                View.OnClickListener click = v -> handleClick(card);
                root.setOnClickListener(click);
                action.setOnClickListener(click);
            }
        }

        private void handleClick(Card card) {
            if (page == 1 && !card.header) {
                Intent intent = new Intent(requireContext(), WordFullscreenActivity.class);
                intent.putExtra(WordFullscreenActivity.EXTRA_CATEGORY_ID, "review_due".equals(card.id) ? "greeting" : card.id);
                intent.putExtra(WordFullscreenActivity.EXTRA_CATEGORY_TITLE, card.title);
                startActivity(intent);
            }
        }
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView tv = new TextView(requireContext());
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(2), 1f);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable gradientCard() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFFFFF, 0xFFEFF6FF});
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(1, COLOR_LINE);
        return drawable;
    }

    private GradientDrawable roundRect(int color, float radius, int strokeColor, int strokeWidth) {
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
        final String title, desc, extra, action, id;
        final boolean header;

        private Card(String title, String desc, String extra, String action, String id, boolean header) {
            this.title = title;
            this.desc = desc;
            this.extra = extra;
            this.action = action;
            this.id = id;
            this.header = header;
        }

        static Card header(String title, String desc) {
            return new Card(title, desc, "", "", "", true);
        }

        static Card normal(String title, String desc, String extra, String action, String id) {
            return new Card(title, desc, extra, action, id, false);
        }

        static Card review(String title, String desc, String action, String id) {
            return new Card(title, desc, "按 nextReviewAt 到期优先，队列空时补新词。", action, id, false);
        }
    }
}
