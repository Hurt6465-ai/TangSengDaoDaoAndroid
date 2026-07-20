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
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import androidx.recyclerview.widget.DiffUtil;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native article detail screen. */
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
    private boolean refreshCommentsPending;
    private boolean sending;
    private long replyParentId;
    private long replyQuoteId;
    private final ForumApiClient.RequestScope requestScope = new ForumApiClient.RequestScope();

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

        stateView = text(ForumText.get(R.string.forum_loading_article), 14, dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
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
        input.setHint(R.string.forum_comment_hint);
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
        send.setBackground(null);
        send.setTextColor(0xFF1877F2);
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
        ForumApiClient.getInstance().ensureSession(this, requestScope,
                new ForumApiClient.ResultCallback<String>() {
            @Override public void onSuccess(@Nullable String data) { requestArticle(); }
            @Override public void onError(@NonNull String message) { requestArticle(); }
        });
    }

    private void requestArticle() {
        ForumApiClient.getInstance().getArticle(articleId, requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Article>() {
                    @Override public void onSuccess(@Nullable ForumApiClient.Article data) {
                        if (isFinishing() || isDestroyed()) return;
                        if (data == null) {
                            showError(ForumText.get(R.string.forum_article_not_found));
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
        if (loading) {
            if (reset) refreshCommentsPending = true;
            return;
        }
        if (reset) {
            refreshCommentsPending = false;
            cursor = "";
            hasMore = false;
            comments.clear();
            adapter.rebuild();
        }
        loading = true;
        ForumApiClient.getInstance().getArticleComments(articleId, cursor, "asc", requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        loading = false;
                        if (isFinishing() || isDestroyed()) return;
                        if (page != null && page.results != null) appendUnique(page.results);
                        String nextCursor = page == null ? "" : safe(page.cursor);
                        if (!TextUtils.isEmpty(nextCursor)) cursor = nextCursor;
                        hasMore = page != null && page.hasMore && !TextUtils.isEmpty(nextCursor);
                        adapter.rebuild();
                        drainPendingCommentRefresh();
                    }
                    @Override public void onError(@NonNull String message) {
                        loading = false;
                        if (comments.isEmpty()) Toast.makeText(ForumArticleActivity.this,
                                message, Toast.LENGTH_LONG).show();
                        drainPendingCommentRefresh();
                    }
                });
    }

    private void drainPendingCommentRefresh() {
        if (isDead() || loading || !refreshCommentsPending) return;
        refreshCommentsPending = false;
        loadComments(true);
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
        final long targetParentId = replyParentId;
        final long targetQuoteId = replyQuoteId;
        String entityType = targetParentId > 0 ? "comment" : "article";
        String entityId = targetParentId > 0 ? String.valueOf(targetParentId)
                : String.valueOf(articleId);
        ForumApiClient.getInstance().createComment(entityType, entityId, content, targetQuoteId,
                new ArrayList<>(), new ForumApiClient.ResultCallback<ForumApiClient.Comment>() {
                    @Override public void onSuccess(@Nullable ForumApiClient.Comment data) {
                        if (isDead()) return;
                        sending = false;
                        input.setText("");
                        if (replyParentId == targetParentId && replyQuoteId == targetQuoteId) {
                            clearReply();
                        }
                        if (article != null) article.commentCount++;
                        loadComments(true);
                    }
                    @Override public void onError(@NonNull String message) {
                        if (isDead()) return;
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
        replyHint.setText(ForumText.get(R.string.forum_replying_to, userName(comment.user)));
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
        labels.add(ForumText.get(comment.liked ? R.string.forum_unlike : R.string.forum_like));
        actions.add(() -> changeLike(comment));
        labels.add(ForumText.get(R.string.forum_reply));
        actions.add(() -> setReply(comment));
        labels.add(ForumText.get(R.string.forum_share));
        actions.add(() -> share(userName(comment.user) + "：" + comment.content));
        if (canDelete(comment)) {
            labels.add(ForumText.get(R.string.forum_delete));
            actions.add(() -> deleteComment(comment));
        }
        showCompactMenu(labels, actions);
    }

    private void changeLike(ForumApiClient.Comment comment) {
        boolean next = !comment.liked;
        ForumApiClient.getInstance().setCommentLiked(comment.id, next,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override public void onSuccess(@Nullable Void data) {
                        if (isDead()) return;
                        comment.liked = next;
                        comment.likeCount = Math.max(0, comment.likeCount + (next ? 1 : -1));
                        adapter.rebuild();
                    }
                    @Override public void onError(@NonNull String message) {
                        if (isDead()) return;
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
                        if (isDead()) return;
                        comments.remove(comment);
                        if (article != null) {
                            article.commentCount = Math.max(0, article.commentCount - 1);
                        }
                        adapter.rebuild();
                    }
                    @Override public void onError(@NonNull String message) {
                        if (isDead()) return;
                        Toast.makeText(ForumArticleActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showArticleMenu() {
        if (article == null) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        String articleUrl = ForumLinkRouter.articleWebUrl(article.id);
        boolean manager = ForumApiClient.getInstance().isForumManager();
        labels.add(getString(R.string.forum_send_to_contacts));
        actions.add(() -> ForumShareHelper.sendToTalkami(this, article.title,
                article.summary, articleUrl));
        labels.add(getString(R.string.forum_more_share));
        actions.add(() -> share(article.title + "\n" + articleUrl));
        if (manager) {
            labels.add(getString(R.string.forum_copy_article_link));
            actions.add(() -> copyArticleText(getString(R.string.forum_article_link_label), articleUrl));
        }
        if (!TextUtils.isEmpty(article.sourceUrl)) {
            labels.add(getString(R.string.forum_view_source));
            actions.add(() -> ForumLinkRouter.open(this, article.sourceUrl));
        }
        showCompactMenu(labels, actions);
    }

    private void copyArticleText(String label, String value) {
        boolean copied = ForumLinkRouter.copyToClipboard(this, label, value);
        Toast.makeText(this, copied ? R.string.forum_copied : R.string.forum_copy_failed, Toast.LENGTH_SHORT).show();
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
        startActivity(Intent.createChooser(intent, getString(R.string.forum_share)));
    }

    private boolean isDead() {
        return isFinishing() || isDestroyed();
    }

    @Override
    protected void onDestroy() {
        requestScope.cancelAll();
        super.onDestroy();
    }

    private final class ArticleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Row> rows = new ArrayList<>();

        ArticleAdapter() {
            setHasStableIds(true);
        }

        void rebuild() {
            List<Row> next = new ArrayList<>();
            if (article != null) next.add(Row.article(articleContentKey()));
            for (ForumApiClient.Comment comment : comments) {
                if (comment != null) next.add(Row.comment(comment, commentContentKey(comment)));
            }
            if (article != null && comments.isEmpty() && !loading) next.add(Row.empty());
            if (hasMore || loading) next.add(Row.loadMore(loading ? "loading" : "more:" + cursor));

            List<Row> old = new ArrayList<>(rows);
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    return old.get(oldPosition).stableId() == next.get(newPosition).stableId();
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    return TextUtils.equals(old.get(oldPosition).contentKey,
                            next.get(newPosition).contentKey);
                }
            }, false);
            rows.clear();
            rows.addAll(next);
            diff.dispatchUpdatesTo(this);
        }

        @Override public long getItemId(int position) {
            return rows.get(position).stableId();
        }

        @Override public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == Row.TYPE_ARTICLE) return new ArticleHeaderHolder(new ArticleHeaderView(parent.getContext()));
            if (type == Row.TYPE_COMMENT) return new CommentHolder(createCommentView(parent.getContext()));
            TextView view = text("", 14, isDark() ? 0xFF8F949C : 0xFF7A818A, false);
            view.setGravity(Gravity.CENTER);
            view.setPadding(dp(18), dp(22), dp(18), dp(26));
            return new SimpleHolder(view);
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (row.type == Row.TYPE_ARTICLE) {
                ((ArticleHeaderHolder) holder).view.bind(article);
            } else if (row.type == Row.TYPE_COMMENT) {
                bindComment((CommentHolder) holder, row.comment);
            } else {
                TextView text = (TextView) holder.itemView;
                text.setText(row.type == Row.TYPE_EMPTY
                        ? ForumText.get(R.string.forum_no_comments)
                        : loading ? ForumText.get(R.string.forum_loading)
                        : ForumText.get(R.string.forum_load_more));
                text.setOnClickListener(v -> {
                    if (row.type == Row.TYPE_LOAD_MORE && !loading) loadComments(false);
                });
            }
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            if (holder instanceof ArticleHeaderHolder) {
                ((ArticleHeaderHolder) holder).view.recycle();
            } else if (holder instanceof CommentHolder) {
                ((CommentHolder) holder).videoEmbeds.recycle();
            }
            super.onViewRecycled(holder);
        }

        @Override public int getItemCount() {
            return rows.size();
        }

        private String articleContentKey() {
            if (article == null) return "";
            ForumApiClient.ImageInfo cover = article.cover;
            return article.id + "|" + textKey(article.title) + '|' + textKey(article.summary) + '|'
                    + textKey(article.content) + '|' + textKey(article.sourceUrl) + '|'
                    + article.createTime + '|' + article.viewCount + '|' + article.commentCount + '|'
                    + article.likeCount + '|' + article.favorited + '|' + article.status + '|'
                    + userKey(article.user) + '|' + textKey(cover == null ? null : cover.url) + '|'
                    + textKey(cover == null ? null : cover.preview);
        }

        private String commentContentKey(ForumApiClient.Comment comment) {
            if (comment == null) return "";
            return comment.id + "|" + textKey(comment.content) + '|' + comment.likeCount + '|'
                    + comment.liked + '|' + comment.status + '|' + comment.createTime + '|'
                    + userKey(comment.user);
        }

        private String userKey(@Nullable ForumApiClient.User user) {
            if (user == null) return "";
            return safe(user.id) + ':' + safe(user.uid) + ':' + textKey(user.nickname) + ':'
                    + textKey(user.smallAvatar) + ':' + textKey(user.avatar) + ':'
                    + safe(user.countryCode) + ':' + safe(user.country);
        }

        private String textKey(@Nullable String value) {
            if (value == null) return "0:0";
            return value.length() + ":" + value.hashCode();
        }
    }

    private static final class Row {
        static final int TYPE_ARTICLE = 1;
        static final int TYPE_COMMENT = 2;
        static final int TYPE_EMPTY = 3;
        static final int TYPE_LOAD_MORE = 4;

        final int type;
        final ForumApiClient.Comment comment;
        final String contentKey;

        private Row(int type, @Nullable ForumApiClient.Comment comment, @NonNull String contentKey) {
            this.type = type;
            this.comment = comment;
            this.contentKey = contentKey;
        }

        static Row article(String key) { return new Row(TYPE_ARTICLE, null, key); }
        static Row comment(ForumApiClient.Comment comment, String key) {
            return new Row(TYPE_COMMENT, comment, key);
        }
        static Row empty() { return new Row(TYPE_EMPTY, null, "empty"); }
        static Row loadMore(String key) { return new Row(TYPE_LOAD_MORE, null, key); }

        long stableId() {
            if (type == TYPE_ARTICLE) return Long.MIN_VALUE + 1;
            if (type == TYPE_EMPTY) return Long.MIN_VALUE + 3;
            if (type == TYPE_LOAD_MORE) return Long.MIN_VALUE + 4;
            return comment == null ? RecyclerView.NO_ID : comment.id;
        }
    }

    private View createCommentView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(4), dp(9));
        root.setBackgroundColor(isDark() ? 0xFF1A1D21 : 0xFFF7F8FA);
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
        ForumVideoEmbedListView videoEmbeds = new ForumVideoEmbedListView(context);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        videoParams.leftMargin = dp(43);
        videoParams.rightMargin = dp(10);
        root.addView(videoEmbeds, videoParams);
        return root;
    }

    private void bindComment(CommentHolder holder, ForumApiClient.Comment comment) {
        String author = userName(comment.user);
        bindAvatar(holder.avatar, comment.user, author);
        View.OnClickListener openProfile = v -> ForumProfileRouter.open(
                ForumArticleActivity.this, comment.user);
        holder.avatar.setOnClickListener(openProfile);
        holder.name.setOnClickListener(openProfile);
        boolean owner = article != null && article.user != null && comment.user != null
                && TextUtils.equals(article.user.id, comment.user.id);
        holder.name.setText(commentName(author, formatTime(comment.createTime), owner));
        ForumLinkRouter.setLinkedText(holder.body, ForumHtmlCache.parse(
                ForumVideoEmbedListView.stripStandaloneEmbedUrls(comment.content)));
        holder.videoEmbeds.bind(comment.content);
        holder.more.setOnClickListener(v -> showCommentMenu(comment));
        holder.root.setOnClickListener(v -> setReply(comment));
        holder.root.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showCommentMenu(comment); return true;
        });
    }

    private CharSequence commentName(String name, String time, boolean owner) {
        String ownerText = owner ? "  " + ForumText.get(R.string.forum_article_author) : "";
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

    private final class ArticleHeaderView extends LinearLayout {
        private final TextView articleMark;
        private final TextView title;
        private final AvatarView avatar;
        private final TextView author;
        private final ImageView cover;
        private final TextView content;
        private final ForumVideoEmbedListView videoEmbeds;
        private final ForumRemoteImageListView bodyImages;
        private final TextView stats;
        private String boundCoverUrl = "";

        ArticleHeaderView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(18), dp(12), dp(18), dp(14));
            setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setGravity(Gravity.TOP);
            articleMark = text("#", 12, Color.WHITE, true);
            articleMark.setGravity(Gravity.CENTER);
            articleMark.setIncludeFontPadding(false);
            articleMark.setBackground(roundRect(isDark() ? 0xFF7651AA : 0xFF8B63D7, 10));
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            markParams.topMargin = dp(4);
            markParams.rightMargin = dp(7);
            titleRow.addView(articleMark, markParams);
            title = text("", 24, isDark() ? Color.WHITE : 0xFF17191C, true);
            title.setLineSpacing(0, 1.10f);
            title.setMaxLines(Integer.MAX_VALUE);
            titleRow.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView more = text("⋮", 26, isDark() ? 0xFFD8DADE : 0xFF4D535B, false);
            more.setGravity(Gravity.CENTER);
            more.setIncludeFontPadding(false);
            more.setContentDescription(ForumText.get(R.string.forum_article_actions));
            more.setBackground(selectableBackground());
            more.setOnClickListener(v -> showArticleMenu());
            more.setTranslationX(dp(10));
            titleRow.addView(more, new LinearLayout.LayoutParams(dp(34), dp(38)));
            addView(titleRow);

            LinearLayout authorRow = new LinearLayout(context);
            authorRow.setGravity(Gravity.CENTER_VERTICAL);
            authorRow.setPadding(0, dp(12), 0, dp(12));
            avatar = new AvatarView(context);
            avatar.setSize(38);
            authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
            author = text("", 13, isDark() ? 0xFFE4E6E9 : 0xFF30353B, true);
            LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            authorParams.leftMargin = dp(9);
            authorRow.addView(author, authorParams);
            addView(authorRow);

            cover = new ImageView(context);
            cover.setAdjustViewBounds(true);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cover.setBackgroundColor(isDark() ? 0xFF24262B : 0xFFF0F1F3);
            cover.setVisibility(GONE);
            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
            coverParams.bottomMargin = dp(12);
            addView(cover, coverParams);

            content = htmlText("", 17);
            content.setMaxLines(Integer.MAX_VALUE);
            content.setEllipsize(null);
            addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            videoEmbeds = new ForumVideoEmbedListView(context);
            addView(videoEmbeds, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            bodyImages = new ForumRemoteImageListView(context);
            LinearLayout.LayoutParams bodyImageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyImageParams.topMargin = dp(10);
            addView(bodyImages, bodyImageParams);

            stats = text("", 12, isDark() ? 0xFF8F949C : 0xFF90969E, false);
            stats.setPadding(0, dp(12), 0, dp(12));
            addView(stats);
            addView(divider(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f)));
            TextView commentsTitle = text(ForumText.get(R.string.forum_comments), 17,
                    isDark() ? Color.WHITE : 0xFF202328, true);
            commentsTitle.setGravity(Gravity.CENTER_VERTICAL);
            addView(commentsTitle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }

        void bind(@Nullable ForumApiClient.Article value) {
            if (value == null) {
                setVisibility(GONE);
                recycle();
                return;
            }
            setVisibility(VISIBLE);
            title.setText(safe(value.title));
            String authorName = userName(value.user);
            bindAvatar(avatar, value.user, authorName);
            author.setText(ForumText.get(R.string.forum_article_author_time,
                    authorName, formatTime(value.createTime)));
            View.OnClickListener openProfile = v -> ForumProfileRouter.open(
                    ForumArticleActivity.this, value.user);
            avatar.setOnClickListener(openProfile);
            author.setOnClickListener(openProfile);

            String coverUrl = "";
            String coverFullUrl = "";
            if (value.cover != null && !TextUtils.isEmpty(value.cover.url)) {
                coverFullUrl = ForumApiClient.getInstance().resolveUrl(value.cover.url);
                String remote = TextUtils.isEmpty(value.cover.preview)
                        ? value.cover.url : value.cover.preview;
                coverUrl = ForumApiClient.getInstance().resolveUrl(remote);
                if (TextUtils.isEmpty(coverFullUrl)) coverFullUrl = coverUrl;
            }
            cover.setVisibility(TextUtils.isEmpty(coverUrl) ? GONE : VISIBLE);
            if (TextUtils.isEmpty(coverUrl)) {
                Glide.with(cover).clear(cover);
                cover.setImageDrawable(null);
                cover.setOnClickListener(null);
                boundCoverUrl = "";
            } else if (!TextUtils.equals(boundCoverUrl, coverUrl)) {
                Glide.with(cover).clear(cover);
                Glide.with(cover).load(coverUrl).fitCenter().into(cover);
                boundCoverUrl = coverUrl;
            }
            if (!TextUtils.isEmpty(coverUrl)) {
                final String openCoverUrl = TextUtils.isEmpty(coverFullUrl)
                        ? coverUrl : coverFullUrl;
                cover.setOnClickListener(v -> {
                    ArrayList<String> urls = new ArrayList<>();
                    urls.add(openCoverUrl);
                    ForumImageViewerActivity.open(ForumArticleActivity.this, urls, 0);
                });
            }

            String body = TextUtils.isEmpty(value.content) ? value.summary : value.content;
            ForumLinkRouter.setLinkedText(content, ForumHtmlCache.parse(
                    ForumVideoEmbedListView.stripStandaloneEmbedUrls(body)));
            videoEmbeds.bind(body);
            bodyImages.bind(extractArticleImages(body), dp(180), dp(10),
                    isDark() ? 0xFF24262B : 0xFFF0F1F3);
            stats.setText(ForumText.get(R.string.forum_article_stats,
                    Math.max(0, value.viewCount), Math.max(0, value.commentCount)));
        }

        void recycle() {
            Glide.with(cover).clear(cover);
            cover.setImageDrawable(null);
            boundCoverUrl = "";
            cover.setOnClickListener(null);
            videoEmbeds.recycle();
            bodyImages.recycle();
        }
    }

    private List<ForumApiClient.ImageInfo> extractArticleImages(String content) {
        List<ForumApiClient.ImageInfo> result = new ArrayList<>();
        if (TextUtils.isEmpty(content)) return result;
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("<img[^>]+src=[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE),
                Pattern.compile("!\\[[^]]*]\\(([^)\\s]+)")
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find() && result.size() < 2) {
                String url = matcher.group(1);
                if (TextUtils.isEmpty(url)) continue;
                boolean duplicate = false;
                for (ForumApiClient.ImageInfo old : result) {
                    if (old != null && TextUtils.equals(old.url, url)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) result.add(new ForumApiClient.ImageInfo(url));
            }
            if (!result.isEmpty()) break;
        }
        return result;
    }

    private final class ArticleHeaderHolder extends RecyclerView.ViewHolder {
        final ArticleHeaderView view;
        ArticleHeaderHolder(@NonNull ArticleHeaderView itemView) {
            super(itemView);
            view = itemView;
        }
    }

    private static final class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(@NonNull View itemView) { super(itemView); }
    }
    private static final class CommentHolder extends RecyclerView.ViewHolder {
        final LinearLayout root; final AvatarView avatar; final TextView name;
        final TextView more; final TextView body;
        final ForumVideoEmbedListView videoEmbeds;
        CommentHolder(@NonNull View view) {
            super(view); root = (LinearLayout) view;
            LinearLayout header = (LinearLayout) root.getChildAt(0);
            avatar = (AvatarView) header.getChildAt(0);
            name = (TextView) header.getChildAt(1);
            more = (TextView) header.getChildAt(2);
            body = (TextView) root.getChildAt(1);
            videoEmbeds = (ForumVideoEmbedListView) root.getChildAt(2);
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
        ForumLinkRouter.setLinkedText(view, ForumHtmlCache.parse(html));
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
        return user == null || TextUtils.isEmpty(user.nickname)
                ? ForumText.get(R.string.forum_user) : user.nickname;
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static long normalizeTime(long value) { return value > 0 && value < 10_000_000_000L ? value * 1000L : value; }
    private static String formatTime(long value) {
        return ForumText.relativeTime(value);
    }
    private void showError(String message) {
        stateView.setText(TextUtils.isEmpty(message)
                ? ForumText.get(R.string.forum_article_load_failed) : message);
        stateView.setVisibility(View.VISIBLE);
    }
}
