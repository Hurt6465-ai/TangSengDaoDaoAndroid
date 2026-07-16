package com.chat.feedlist;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.chat.feedlist.databinding.ItemFeedTimelineBinding;
import com.chat.feedlist.model.FeedListItem;
import com.chat.feedlist.model.FeedListMedia;
import com.chat.feedlist.model.FeedListUser;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class FeedTimelineAdapter extends ListAdapter<FeedListItem, FeedTimelineAdapter.Holder> {
    private static final String PAYLOAD_INTERACTION = "interaction";

    public interface Listener {
        void onProfile(FeedListItem item);
        void onMore(FeedListItem item);
        void onFollow(FeedListItem item);
        void onLike(FeedListItem item, int position);
        void onComment(FeedListItem item, int position);
        void onShare(FeedListItem item);
        void onImages(FeedListItem item, int index, List<FeedListMedia> media);
        void onTikTok(FeedListItem item, FeedListMedia media);
        void onOpenTikTok(FeedListItem item, FeedListMedia media);
        void onTikTokMetadataNeeded(FeedListItem item, FeedListMedia media);
        void onTikTokCoverLoadFailed(FeedListItem item, FeedListMedia media);
    }

    private final Listener listener;
    private final Set<String> expanded = new HashSet<>();
    private long serverTime;
    private long serverTimeLocalAt;

    public FeedTimelineAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) { return getItem(position).stableId(); }

    public FeedListItem getItemAt(int position) {
        return position >= 0 && position < getItemCount() ? getItem(position) : null;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
        this.serverTimeLocalAt = System.currentTimeMillis();
    }

    public void notifyInteractionChanged(int position) {
        if (position >= 0 && position < getItemCount()) notifyItemChanged(position, PAYLOAD_INTERACTION);
    }

    public void notifyUserChanged(String uid) {
        if (TextUtils.isEmpty(uid)) return;
        for (int i = 0; i < getItemCount(); i++) {
            FeedListItem item = getItem(i);
            if (item != null && TextUtils.equals(uid, item.authorUid())) notifyItemChanged(i);
        }
    }

    public void preloadTikTokCovers(@NonNull Context context, int startPosition, int count) {
        if (getItemCount() == 0 || count <= 0) return;
        int start = Math.max(0, startPosition);
        int end = Math.min(getItemCount(), start + count);
        for (int i = start; i < end; i++) {
            FeedListItem item = getItem(i);
            FeedListMedia media = item == null ? null : item.firstMedia();
            if (media == null || !media.isTikTok()) continue;
            String coverUrl = media.tiktokCoverUrl();
            if (TextUtils.isEmpty(coverUrl) || media.isTikTokCoverProbablyExpired(System.currentTimeMillis())) {
                listener.onTikTokMetadataNeeded(item, media);
                continue;
            }
            Glide.with(context)
                    .load(coverUrl)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .preload();
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemFeedTimelineBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        bind(holder, getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_INTERACTION)) {
            bindActions(holder, getItem(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bind(Holder holder, FeedListItem item) {
        ItemFeedTimelineBinding b = holder.binding;
        FeedListUser user = item.user;
        String uid = item.authorUid();
        String name = item.userName();

        b.avatarView.setSize(46);
        String avatarCacheKey = avatarCacheKey(user);
        if (user != null && !TextUtils.isEmpty(user.avatar)) {
            b.avatarView.showAvatarUrl(user.avatar, avatarCacheKey, name, uid);
        } else if (!TextUtils.isEmpty(uid)) {
            b.avatarView.showAvatar(uid, com.xinbida.wukongim.entity.WKChannelType.PERSONAL, avatarCacheKey);
        } else {
            b.avatarView.showDefaultAvatar(name, uid);
        }
        b.avatarView.showFlag(user == null ? "" : user.country_code);

        b.nameTv.setText(name);
        String language = languageText(user);
        b.languageTv.setText(language);
        b.languageTv.setVisibility(TextUtils.isEmpty(language) ? View.GONE : View.VISIBLE);
        b.timeTv.setText(relativeTime(b.getRoot().getContext(), item.created_at, serverTime));
        b.userArea.setOnClickListener(v -> listener.onProfile(item));
        b.avatarView.setOnClickListener(v -> listener.onProfile(item));
        b.moreBtn.setOnClickListener(v -> listener.onMore(item));

        boolean mine = TextUtils.equals(uid, com.chat.base.config.WKConfig.getInstance().getUid());
        boolean followed = user != null && user.follow == 1;
        b.followBtn.setVisibility(!mine && !TextUtils.isEmpty(uid) ? View.VISIBLE : View.GONE);
        String followLabel = b.getRoot().getContext().getString(followed ? R.string.feedlist_followed : R.string.feedlist_follow);
        b.followBtn.setText("\u00B7 " + followLabel);
        b.followBtn.setTextColor(ContextCompat.getColor(
                b.getRoot().getContext(),
                followed ? R.color.feedlist_muted : R.color.feedlist_like
        ));
        b.followBtn.setEnabled(!TextUtils.isEmpty(uid));
        b.followBtn.setOnClickListener(v -> listener.onFollow(item));

        bindContent(holder, item);
        bindMedia(holder, item);
        bindActions(holder, item);
    }

    private void bindContent(Holder holder, FeedListItem item) {
        ItemFeedTimelineBinding b = holder.binding;
        String content = item.displayTitle();
        boolean hasText = !TextUtils.isEmpty(content);
        b.contentTv.setVisibility(hasText ? View.VISIBLE : View.GONE);
        b.expandTv.setVisibility(View.GONE);
        if (!hasText) {
            b.contentTv.setTag(null);
            b.contentTv.setText("");
            return;
        }

        String key = item.stableKey();
        boolean isExpanded = expanded.contains(key);
        b.contentTv.setTag(key);
        b.contentTv.setText(content);
        b.contentTv.setMaxLines(isExpanded ? Integer.MAX_VALUE : 4);
        b.expandTv.setOnClickListener(v -> {
            expanded.add(key);
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) notifyItemChanged(adapterPosition);
        });
        if (!isExpanded) {
            b.contentTv.post(() -> {
                if (!TextUtils.equals(key, String.valueOf(b.contentTv.getTag()))) return;
                android.text.Layout layout = b.contentTv.getLayout();
                boolean truncated = layout != null && layout.getLineCount() > 0
                        && layout.getLineEnd(layout.getLineCount() - 1) < b.contentTv.length();
                b.expandTv.setVisibility(truncated ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void bindMedia(Holder holder, FeedListItem item) {
        ItemFeedTimelineBinding b = holder.binding;
        FeedListMedia first = item.firstMedia();
        boolean tiktok = first != null && first.isTikTok();
        b.tiktokBox.setVisibility(tiktok ? View.VISIBLE : View.GONE);
        b.mediaGrid.setVisibility(tiktok ? View.GONE : View.VISIBLE);

        if (tiktok) {
            b.mediaGrid.bind(null);
            applyTikTokCoverSize(b);
            // TikTok cover links are temporary. Refresh metadata once per activity cooldown even
            // when an old URL still exists, so an expired CDN URL is replaced before Glide needs it.
            listener.onTikTokMetadataNeeded(item, first);

            String coverUrl = first.tiktokCoverUrl();
            boolean expired = first.isTikTokCoverProbablyExpired(System.currentTimeMillis());
            if (TextUtils.isEmpty(coverUrl) || expired) {
                Glide.with(b.tiktokCoverIv).clear(b.tiktokCoverIv);
                b.tiktokCoverIv.setImageResource(R.color.feedlist_media_placeholder);
                if (expired) listener.onTikTokCoverLoadFailed(item, first);
                else listener.onTikTokMetadataNeeded(item, first);
            } else {
                Glide.with(b.tiktokCoverIv)
                        .load(coverUrl)
                        .placeholder(R.color.feedlist_media_placeholder)
                        .error(R.color.feedlist_media_placeholder)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontAnimate()
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                listener.onTikTokCoverLoadFailed(item, first);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(b.tiktokCoverIv);
            }

            // Keep the stable, dedicated full-screen playback path.
            b.tiktokBox.setOnClickListener(v -> listener.onTikTok(item, first));
        } else {
            Glide.with(b.tiktokCoverIv).clear(b.tiktokCoverIv);
            b.tiktokBox.setOnClickListener(null);
            b.mediaGrid.setListener((index, media) -> listener.onImages(item, index, media));
            b.mediaGrid.bind(item.safeMedia());
        }
    }

    private void applyTikTokCoverSize(ItemFeedTimelineBinding binding) {
        Context context = binding.getRoot().getContext();
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int targetHeight = Math.round(screenWidth * 1.25f);
        targetHeight = Math.min(targetHeight, Math.round(screenHeight * 0.72f));
        ViewGroup.LayoutParams params = binding.tiktokBox.getLayoutParams();
        if (params.height != targetHeight) {
            params.height = targetHeight;
            binding.tiktokBox.setLayoutParams(params);
        }
    }

    private void bindActions(Holder holder, FeedListItem item) {
        ItemFeedTimelineBinding b = holder.binding;
        Context context = b.getRoot().getContext();
        boolean liked = item.liked == 1;
        b.likeIcon.setImageDrawable(AppCompatResources.getDrawable(
                context,
                liked ? R.drawable.ic_feedlist_heart_filled : R.drawable.ic_feedlist_heart_outline
        ));
        int likeColor = ContextCompat.getColor(context, liked ? R.color.feedlist_like : R.color.feedlist_action);
        b.likeIcon.setColorFilter(likeColor);
        b.likeCountTv.setTextColor(likeColor);
        b.likeCountTv.setText(countLabel(item.like_count, context.getString(R.string.feedlist_like)));
        b.commentCountTv.setText(countLabel(item.comment_count, context.getString(R.string.feedlist_comment)));
        b.shareCountTv.setVisibility(item.share_count > 0 ? View.VISIBLE : View.GONE);
        b.shareCountTv.setText(item.share_count > 0 ? String.valueOf(item.share_count) : "");

        b.likeAction.setOnClickListener(v -> listener.onLike(item, holder.getBindingAdapterPosition()));
        b.commentAction.setOnClickListener(v -> listener.onComment(item, holder.getBindingAdapterPosition()));
        b.shareAction.setOnClickListener(v -> listener.onShare(item));
    }

    @Override
    public void onViewAttachedToWindow(@NonNull Holder holder) {
        super.onViewAttachedToWindow(holder);
        holder.binding.mediaGrid.reloadImages();
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        holder.binding.contentTv.setTag(null);
        holder.binding.mediaGrid.clearImages();
        Glide.with(holder.binding.tiktokCoverIv).clear(holder.binding.tiktokCoverIv);
        super.onViewRecycled(holder);
    }

    private String countLabel(int count, String fallback) {
        return count > 0 ? String.valueOf(count) : fallback;
    }

    private String avatarCacheKey(FeedListUser user) {
        if (user == null) return "";
        if (!TextUtils.isEmpty(user.avatar_cache_key)) return user.avatar_cache_key;
        return user.vercode == null ? "" : user.vercode;
    }

    private static final DiffUtil.ItemCallback<FeedListItem> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull FeedListItem oldItem, @NonNull FeedListItem newItem) {
            return TextUtils.equals(oldItem.stableKey(), newItem.stableKey());
        }

        @Override
        public boolean areContentsTheSame(@NonNull FeedListItem oldItem, @NonNull FeedListItem newItem) {
            return sameInteraction(oldItem, newItem)
                    && TextUtils.equals(oldItem.displayTitle(), newItem.displayTitle())
                    && oldItem.created_at == newItem.created_at
                    && sameUser(oldItem.user, newItem.user)
                    && sameMedia(oldItem.safeMedia(), newItem.safeMedia());
        }

        @Override
        public Object getChangePayload(@NonNull FeedListItem oldItem, @NonNull FeedListItem newItem) {
            boolean onlyInteractionChanged = !sameInteraction(oldItem, newItem)
                    && TextUtils.equals(oldItem.displayTitle(), newItem.displayTitle())
                    && oldItem.created_at == newItem.created_at
                    && sameUser(oldItem.user, newItem.user)
                    && sameMedia(oldItem.safeMedia(), newItem.safeMedia());
            return onlyInteractionChanged ? PAYLOAD_INTERACTION : null;
        }
    };

    private static boolean sameInteraction(FeedListItem a, FeedListItem b) {
        return a.liked == b.liked
                && a.like_count == b.like_count
                && a.comment_count == b.comment_count
                && a.share_count == b.share_count;
    }

    private static boolean sameUser(FeedListUser a, FeedListUser b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.uid, b.uid)
                && Objects.equals(a.name, b.name)
                && Objects.equals(a.username, b.username)
                && Objects.equals(a.avatar, b.avatar)
                && Objects.equals(a.avatar_cache_key, b.avatar_cache_key)
                && Objects.equals(a.country_code, b.country_code)
                && a.follow == b.follow
                && Objects.equals(a.nativeLanguageList(), b.nativeLanguageList())
                && Objects.equals(a.learningLanguageList(), b.learningLanguageList());
    }

    private static boolean sameMedia(List<FeedListMedia> a, List<FeedListMedia> b) {
        List<FeedListMedia> left = a == null ? Collections.emptyList() : a;
        List<FeedListMedia> right = b == null ? Collections.emptyList() : b;
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            FeedListMedia x = left.get(i);
            FeedListMedia y = right.get(i);
            if (x == y) continue;
            if (x == null || y == null) return false;
            if (!Objects.equals(x.type, y.type)
                    || !Objects.equals(x.thumb_url, y.thumb_url)
                    || !Objects.equals(x.display_url, y.display_url)
                    || !Objects.equals(x.origin_url, y.origin_url)
                    || !Objects.equals(x.cover_url, y.cover_url)
                    || !Objects.equals(x.external_id, y.external_id)
                    || !Objects.equals(x.external_url, y.external_url)
                    || !Objects.equals(x.external_title, y.external_title)
                    || !Objects.equals(x.external_author, y.external_author)
                    || x.width != y.width
                    || x.height != y.height) {
                return false;
            }
        }
        return true;
    }
}
