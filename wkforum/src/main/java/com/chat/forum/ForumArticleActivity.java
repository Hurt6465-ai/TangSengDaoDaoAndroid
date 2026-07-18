package com.chat.forum;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.chat.base.ui.components.AvatarView;
import com.xinbida.wukongim.entity.WKChannelType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Native read-first article screen. Articles are still authored on the web. */
public class ForumArticleActivity extends AppCompatActivity {
    private static final String EXTRA_ARTICLE_ID = "article_id";
    private static final String SEEN_PREF = "forum_article_seen";

    private long articleId;
    private RecyclerView recyclerView;
    private ArticleAdapter adapter;
    private TextView stateView;
    private EditText input;
    private TextView send;
    private TextView replyHint;
    private ForumApiClient.Article article;
    private final List<ForumApiClient.Comment> comments = new ArrayList<>();
    private String cursor = "";
    private boolean hasMore;
    private boolean loading;
    private boolean sending;
    private long replyParentId;
    private long replyQuoteId;

    public static Intent createIntent(Context context, long articleId) {
        return new Intent(context, ForumArticleActivity.class)
                .putExtra(EXTRA_ARTICLE_ID, articleId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        articleId = getIntent() == null ? 0 : getIntent().getLongExtra(EXTRA_ARTICLE_ID, 0);
        if (articleId <= 0) {
            finish();
            return;
        }
        buildView();
        load();
    }

    private void buildView() {
        boolean dark = isDark();
        getWindow().setStatusBarColor(dark ? 0xFF17181B : Color.WHITE);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF111214 : Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                    insets.getSystemWindowInsetBottom());
            return insets;
        });

        FrameLayout body = new FrameLayout(this);
        recyclerView = new RecyclerView(this);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(manager);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(10);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, 0, 0, dp(12));
        adapter = new ArticleAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && hasMore && !loading
                        && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 4) {
                    loadComments(false);
                }
            }
        });
        body.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stateView = text("正在加载文章…", 14, dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
        stateView.setGravity(Gravity.CENTER);
        body.addView(stateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildComposer(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private View buildComposer() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(10), 0, dp(10), dp(7));
        wrapper.setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);
        wrapper.addView(divider(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.6f)));

        replyHint = text("", 12, isDark() ? 0xFFFFC46B : 0xFF8B6500, false);
        replyHint.setPadding(dp(10), dp(5), dp(10), dp(3));
        replyHint.setVisibility(View.GONE);
        replyHint.setOnClickListener(v -> clearReply());
        wrapper.addView(replyHint);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);
        input = new EditText(this);
        input.setHint("友善交流，说点什么…");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTextColor(isDark() ? Color.WHITE : 0xFF202328);
        input.setHintTextColor(isDark() ? 0xFF777B82 : 0xFF9A9FA6);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        input.setPadding(dp(14), dp(9), dp(14), dp(9));
        input.setBackground(roundRect(isDark() ? 0xFF24262B : 0xFFF1F3F5, 21));
        row.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        send = text("➤", 21, Color.WHITE, true);
        send.setGravity(Gravity.CENTER);
        send.setIncludeFontPadding(false);
        send.setPadding(0, 0, 0, 0);
        send.setBackground(roundRect(0xFF1877F2, 23));
        send.setOnClickListener(v -> sendComment());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        sendParams.leftMargin = dp(8);
        row.addView(send, sendParams);
        wrapper.addView(row);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean enabled = !TextUtils.isEmpty(String.valueOf(s).trim()) && !sending;
                send.setAlpha(enabled ? 1f : 0.45f);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        send.setAlpha(0.45f);
        return wrapper;
    }

    private void load() {
        ForumApiClient.getInstance().ensureSession(this, new ForumApiClient.ResultCallback<String>() {
            @Override public void onSuccess(@Nullable String data) { requestArticle(); }
            @Override public void onError(@NonNull String message) { requestArticle(); }
        });
    }

    private void requestArticle() {
        ForumApiClient.getInstance().getArticle(articleId,
                new ForumApiClient.ResultCallback<ForumApiClient.Article>() {
                    @Override public void onSuccess(@Nullable ForumApiClient.Article data) {
                        if (isFinishing() || isDestroyed()) return;
                        if (data == null) {
                            showError("文章不存在或已被删除");
                            return;
                        }
                        article = data;
                        getSharedPreferences(SEEN_PREF, MODE_PRIVATE).edit()
                                .putLong("time_" + articleId, System.currentTimeMillis()).apply();
                        stateView.setVisibility(View.GONE);
                        adapter.rebuild();
                        loadComments(true);
                    }
                    @Override public void onError(@NonNull String message) { showError(message); }
                });
    }

    private void loadComments(boolean reset) {
        if (loading) return;
        if (reset) {
            cursor = "";
            hasMore = false;
            comments.clear();
            adapter.rebuild();
        }
        loading = true;
        ForumApiClient.getInstance().getArticleComments(articleId, cursor, "asc",
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        loading = false;
                        if (isFinishing() || isDestroyed()) return;
                        if (page != null && page.results != null) appendUnique(page.results);
                        cursor = page == null || TextUtils.isEmpty(page.cursor) ? cursor : page.cursor;
                        hasMore = page != null && page.hasMore;
                        adapter.rebuild();
                    }
                    @Override public void onError(@NonNull String message) {
                        loading = false;
                        if (comments.isEmpty()) Toast.makeText(ForumArticleActivity.this,
                                message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void appendUnique(List<ForumApiClient.Comment> incoming) {
        for (ForumApiClient.Comment item : incoming) {
            if (item == null) continue;
            boolean exists = false;
            for (ForumApiClient.Comment old : comments) if (old != null && old.id == item.id) {
                exists = true; break;
            }
            if (!exists) comments.add(item);
        }
    }

    private void sendComment() {
        String content = input.getText().toString().trim();
        if (sending || TextUtils.isEmpty(content)) return;
        sending = true;
        send.setAlpha(0.45f);
        String entityType = replyParentId > 0 ? "comment" : "article";
        String entityId = replyParentId > 0 ? String.valueOf(replyParentId) : String.valueOf(articleId);
        ForumApiClient.getInstance().createComment(entityType, entityId, content, replyQuoteId,
                new ArrayList<>(), new ForumApiClient.ResultCallback<ForumApiClient.Comment>() {
                    @Override public void onSuccess(@Nullable ForumApiClient.Comment data) {
                        sending = false;
                        input.setText("");
                        clearReply();
                        comments.clear(); cursor = ""; hasMore = false;
                        if (article != null) article.commentCount++;
                        loadComments(true);
                    }
                    @Override public void onError(@NonNull String message) {
                        sending = false;
                        send.setAlpha(1f);
                        Toast.makeText(ForumArticleActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setReply(ForumApiClient.Comment comment) {
        if (comment == null || comment.user == null) return;
        replyParentId = comment.entityType != null && comment.entityType.equals("comment")
                ? comment.entityId : comment.id;
        replyQuoteId = comment.id;
        replyHint.setText("正在回复 " + userName(comment.user) + " · 点击取消");
        replyHint.setVisibility(View.VISIBLE);
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    private void clearReply() {
        replyParentId = 0;
        replyQuoteId = 0;
        replyHint.setVisibility(View.GONE);
    }

    private void showCommentMenu(ForumApiClient.Comment comment) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(comment.liked ? "取消点赞" : "点赞");
        actions.add(() -> changeLike(comment));
        labels.add("回复");
        actions.add(() -> setReply(comment));
        labels.add("分享");
        actions.add(() -> share(userName(comment.user) + "：" + comment.content));
        if (canDelete(comment)) {
            labels.add("删除");
            actions.add(() -> deleteComment(comment));
        }
        showCompactMenu(labels, actions);
    }

    private void changeLike(ForumApiClient.Comment comment) {
        boolean next = !comment.liked;
        ForumApiClient.getInstance().setCommentLiked(comment.id, next,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override public void onSuccess(@Nullable Void data) {
                        comment.liked = next;
                        comment.likeCount = Math.max(0, comment.likeCount + (next ? 1 : -1));
                        adapter.rebuild();
                    }
                    @Override public void onError(@NonNull String message) {
                        Toast.makeText(ForumArticleActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean canDelete(ForumApiClient.Comment comment) {
        ForumApiClient client = ForumApiClient.getInstance();
        return client.isForumManager() || client.hasPermission("dashboard.comment.delete")
                || (comment != null && comment.user != null
                && TextUtils.equals(client.getCurrentForumUserId(), comment.user.id));
    }

    private void deleteComment(ForumApiClient.Comment comment) {
        ForumApiClient.getInstance().deleteComment(comment.id,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override public void onSuccess(@Nullable Void data) {
                        comments.remove(comment);
                        adapter.rebuild();
                    }
                    @Override public void onError(@NonNull String message) {
                        Toast.makeText(ForumArticleActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showArticleMenu() {
        if (article == null) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("分享");
        actions.add(() -> share(article.title + "\n" + article.summary));
        if (!TextUtils.isEmpty(article.sourceUrl)) {
            labels.add("查看来源");
            actions.add(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(article.sourceUrl))); }
                catch (Throwable ignored) { }
            });
        }
        showCompactMenu(labels, actions);
    }

    private void showCompactMenu(List<String> labels, List<Runnable> actions) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable bg = roundRect(isDark() ? 0xFF292B30 : Color.WHITE, 13);
        bg.setStroke(dp(0.7f), isDark() ? 0xFF3A3D43 : 0xFFE4E6E9);
        panel.setBackground(bg);
        for (int i = 0; i < labels.size(); i++) {
            int index = i;
            TextView item = text(labels.get(i), 14,
                    isDark() ? 0xFFE6E8EB : 0xFF30343A, false);
            item.setGravity(Gravity.CENTER);
            item.setOnClickListener(v -> { dialog.dismiss(); actions.get(index).run(); });
            panel.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        }
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(dp(126), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        dialog.show();
        if (window != null) window.setLayout(dp(126), WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void share(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "分享"));
    }

    private final class ArticleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Integer> rows = new ArrayList<>();
        ArticleAdapter() { setHasStableIds(true); }
        void rebuild() {
            rows.clear();
            if (article != null) rows.add(-1);
            for (int i = 0; i < comments.size(); i++) rows.add(i);
            if (article != null && comments.isEmpty() && !loading) rows.add(-2);
            if (hasMore || loading) rows.add(-3);
            notifyDataSetChanged();
        }
        @Override public long getItemId(int position) {
            int value = rows.get(position);
            return value >= 0 ? comments.get(value).id : Long.MIN_VALUE - value;
        }
        @Override public int getItemViewType(int position) {
            int value = rows.get(position);
            return value == -1 ? 1 : value >= 0 ? 2 : 3;
        }
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == 1) return new SimpleHolder(new LinearLayout(parent.getContext()));
            if (type == 2) return new CommentHolder(createCommentView(parent.getContext()));
            TextView view = text("", 14, isDark() ? 0xFF8F949C : 0xFF7A818A, false);
            view.setGravity(Gravity.CENTER);
            view.setPadding(dp(18), dp(22), dp(18), dp(26));
            return new SimpleHolder(view);
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int value = rows.get(position);
            if (value == -1) bindArticle((LinearLayout) holder.itemView);
            else if (value >= 0) bindComment((CommentHolder) holder, comments.get(value));
            else {
                TextView text = (TextView) holder.itemView;
                text.setText(value == -2 ? "还没有评论" : loading ? "加载中…" : "加载更多");
                text.setOnClickListener(v -> { if (value == -3 && !loading) loadComments(false); });
            }
        }
        @Override public int getItemCount() { return rows.size(); }
    }

    private void bindArticle(LinearLayout root) {
        root.removeAllViews();
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(14));
        root.setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);
        if (article == null) return;

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.TOP);
        TextView title = text(article.title, 24, isDark() ? Color.WHITE : 0xFF17191C, true);
        title.setLineSpacing(0, 1.10f);
        title.setMaxLines(Integer.MAX_VALUE);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView more = text("⋮", 26, isDark() ? 0xFFD8DADE : 0xFF4D535B, false);
        more.setGravity(Gravity.CENTER);
        more.setIncludeFontPadding(false);
        more.setBackground(selectableBackground());
        more.setOnClickListener(v -> showArticleMenu());
        titleRow.addView(more, new LinearLayout.LayoutParams(dp(38), dp(38)));
        root.addView(titleRow);

        LinearLayout authorRow = new LinearLayout(this);
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        authorRow.setPadding(0, dp(12), 0, dp(12));
        AvatarView avatar = new AvatarView(this);
        avatar.setSize(38);
        bindAvatar(avatar, article.user, userName(article.user));
        authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView name = text(userName(article.user) + "  作者 · " + formatTime(article.createTime),
                13, isDark() ? 0xFFE4E6E9 : 0xFF30353B, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(9);
        authorRow.addView(name, nameParams);
        root.addView(authorRow);

        if (article.cover != null && !TextUtils.isEmpty(article.cover.url)) {
            ImageView cover = new ImageView(this);
            cover.setAdjustViewBounds(true);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(ForumApiClient.getInstance().resolveUrl(article.cover.url)).into(cover);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
            cp.bottomMargin = dp(12);
            root.addView(cover, cp);
        }
        TextView content = htmlText(TextUtils.isEmpty(article.content) ? article.summary : article.content, 17);
        content.setMaxLines(Integer.MAX_VALUE);
        content.setEllipsize(null);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView stats = text("◉ " + Math.max(0, article.viewCount) + "   评论 "
                + Math.max(0, article.commentCount), 12,
                isDark() ? 0xFF8F949C : 0xFF90969E, false);
        stats.setPadding(0, dp(12), 0, dp(12));
        root.addView(stats);
        root.addView(divider(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f)));
        TextView commentsTitle = text("评论", 17, isDark() ? Color.WHITE : 0xFF202328, true);
        commentsTitle.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(commentsTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
    }

    private View createCommentView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(14), 0);
        root.setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        AvatarView avatar = new AvatarView(context);
        avatar.setSize(31);
        header.addView(avatar, new LinearLayout.LayoutParams(dp(35), dp(35)));
        TextView name = text("", 13, isDark() ? 0xFFE4E6E9 : 0xFF30353B, false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        np.leftMargin = dp(8);
        header.addView(name, np);
        TextView more = text("⋮", 20, isDark() ? 0xFF858B93 : 0xFF9AA0A7, false);
        more.setGravity(Gravity.CENTER);
        header.addView(more, new LinearLayout.LayoutParams(dp(28), dp(30)));
        root.addView(header);
        TextView body = htmlText("", 15);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.leftMargin = dp(43); bp.topMargin = dp(5);
        root.addView(body, bp);
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.55f));
        dpv.topMargin = dp(10);
        root.addView(divider(), dpv);
        return root;
    }

    private void bindComment(CommentHolder holder, ForumApiClient.Comment comment) {
        String author = userName(comment.user);
        bindAvatar(holder.avatar, comment.user, author);
        boolean owner = article != null && article.user != null && comment.user != null
                && TextUtils.equals(article.user.id, comment.user.id);
        holder.name.setText(commentName(author, formatTime(comment.createTime), owner));
        holder.body.setText(Html.fromHtml(TextUtils.isEmpty(comment.content) ? "" : comment.content,
                Html.FROM_HTML_MODE_LEGACY));
        holder.more.setOnClickListener(v -> showCommentMenu(comment));
        holder.root.setOnClickListener(v -> setReply(comment));
        holder.root.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showCommentMenu(comment); return true;
        });
    }

    private CharSequence commentName(String name, String time, boolean owner) {
        String ownerText = owner ? "  作者" : "";
        SpannableString value = new SpannableString(name + ownerText + " · " + time);
        value.setSpan(new StyleSpan(Typeface.BOLD), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (owner) value.setSpan(new ForegroundColorSpan(0xFF1877F2), name.length(),
                name.length() + ownerText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int timeStart = name.length() + ownerText.length();
        value.setSpan(new ForegroundColorSpan(isDark() ? 0xFF70767E : 0xFFADB2B9),
                timeStart, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new RelativeSizeSpan(0.82f), timeStart, value.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    private static final class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(@NonNull View itemView) { super(itemView); }
    }
    private static final class CommentHolder extends RecyclerView.ViewHolder {
        final LinearLayout root; final AvatarView avatar; final TextView name;
        final TextView more; final TextView body;
        CommentHolder(@NonNull View view) {
            super(view); root = (LinearLayout) view;
            LinearLayout header = (LinearLayout) root.getChildAt(0);
            avatar = (AvatarView) header.getChildAt(0);
            name = (TextView) header.getChildAt(1);
            more = (TextView) header.getChildAt(2);
            body = (TextView) root.getChildAt(1);
        }
    }

    private void bindAvatar(AvatarView avatar, ForumApiClient.User user, String fallback) {
        try {
            String uid = user == null ? "" : safe(user.uid);
            String id = user == null ? "" : safe(user.id);
            if (!TextUtils.isEmpty(uid)) avatar.showAvatar(uid, WKChannelType.PERSONAL);
            else if (user != null && (!TextUtils.isEmpty(user.smallAvatar) || !TextUtils.isEmpty(user.avatar))) {
                String remote = TextUtils.isEmpty(user.smallAvatar) ? user.avatar : user.smallAvatar;
                avatar.showAvatarUrl(ForumApiClient.getInstance().resolveUrl(remote), id, fallback, id);
            } else avatar.showDefaultAvatar(fallback, TextUtils.isEmpty(uid) ? id : uid);
            if (user != null && (!TextUtils.isEmpty(user.countryCode) || !TextUtils.isEmpty(user.country))) {
                avatar.showFlag(TextUtils.isEmpty(user.countryCode) ? user.country : user.countryCode);
            }
        } catch (Throwable ignored) { avatar.showDefaultAvatar(fallback, fallback); }
    }

    private TextView htmlText(String html, float size) {
        TextView view = text("", size, isDark() ? 0xFFE8E9EB : 0xFF272A2F, false);
        view.setLineSpacing(dp(3), 1.08f);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setText(Html.fromHtml(safe(html).replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY));
        return view;
    }

    private Drawable selectableBackground() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        return AppCompatResources.getDrawable(this, value.resourceId);
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(isDark() ? 0xFF26282D : 0xFFEDEFF2);
        return view;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private GradientDrawable roundRect(int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private boolean isDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }
    private String userName(ForumApiClient.User user) {
        return user == null || TextUtils.isEmpty(user.nickname) ? "用户" : user.nickname;
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static long normalizeTime(long value) { return value > 0 && value < 10_000_000_000L ? value * 1000L : value; }
    private static String formatTime(long value) {
        long time = normalizeTime(value), diff = Math.max(0, System.currentTimeMillis() - time);
        if (diff < 60_000L) return "刚刚";
        if (diff < 3_600_000L) return (diff / 60_000L) + "分钟前";
        if (diff < 86_400_000L) return (diff / 3_600_000L) + "小时前";
        if (diff < 7L * 86_400_000L) return (diff / 86_400_000L) + "天前";
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(time));
    }
    private void showError(String message) {
        stateView.setText(TextUtils.isEmpty(message) ? "文章加载失败" : message);
        stateView.setVisibility(View.VISIBLE);
    }
}
