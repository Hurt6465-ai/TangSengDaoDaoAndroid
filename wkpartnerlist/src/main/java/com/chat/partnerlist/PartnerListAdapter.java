package com.chat.partnerlist;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.chat.base.config.WKApiConfig;
import com.chat.partnerlist.databinding.ItemPartnerListBinding;
import com.chat.partnerlist.model.PartnerListUser;
import com.chat.uikit.partner.PartnerPendingStore;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PartnerListAdapter extends ListAdapter<PartnerListUser, PartnerListAdapter.VH> {
    private static final String PAYLOAD_TIME = "time";
    private static final String PAYLOAD_QUOTA = "quota";
    private static final String PAYLOAD_FRESH = "fresh";
    private static final String PAYLOAD_GREETING = "greeting";

    public interface Listener {
        void onOpenProfile(PartnerListUser user);
        void onGreeting(PartnerListUser user, int position);
        void onOpenChat(PartnerListUser user);
    }

    private final Listener listener;
    private final Set<String> greetingPending = new HashSet<>();
    private final Set<String> greeted = new HashSet<>();
    private Set<String> recentlyAdded = Collections.emptySet();
    private long serverTime;
    private int greetingRemaining = 10;

    public PartnerListAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        PartnerListUser user = getItem(position);
        return fnv1a64(user == null ? "" : user.stableId());
    }

    public void setServerTime(long value) {
        serverTime = value;
    }

    public void setGreetingRemaining(int remaining) {
        greetingRemaining = Math.max(0, remaining);
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount(), PAYLOAD_QUOTA);
    }

    public void setRecentlyAdded(List<String> ids) {
        recentlyAdded = ids == null ? Collections.emptySet() : new HashSet<>(ids);
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount(), PAYLOAD_FRESH);
    }

    public void markGreetingPending(String uid, boolean pending) {
        if (TextUtils.isEmpty(uid)) return;
        if (pending) greetingPending.add(uid); else greetingPending.remove(uid);
        notifyUid(uid, PAYLOAD_GREETING);
    }

    public void markGreeted(String uid) {
        if (TextUtils.isEmpty(uid)) return;
        greetingPending.remove(uid);
        greeted.add(uid);
        notifyUid(uid, PAYLOAD_GREETING);
    }

    public void refreshVisible(int first, int last) {
        if (getItemCount() == 0) return;
        int start = Math.max(0, first);
        int end = Math.min(getItemCount() - 1, Math.max(start, last));
        notifyItemRangeChanged(start, end - start + 1, PAYLOAD_TIME);
    }

    private void notifyUid(String uid, String payload) {
        for (int i = 0; i < getItemCount(); i++) {
            PartnerListUser user = getItem(i);
            if (user != null && TextUtils.equals(uid, user.stableId())) {
                notifyItemChanged(i, payload);
                return;
            }
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemPartnerListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull VH holder, int position) {
        bind(holder, getItem(position));
    }

    @Override public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        PartnerListUser user = getItem(position);
        if (user == null || payloads.isEmpty()) {
            bind(holder, user);
            return;
        }
        boolean handled = true;
        for (Object payload : payloads) {
            if (PAYLOAD_TIME.equals(payload)) {
                bindPresence(holder.binding, user);
            } else if (PAYLOAD_QUOTA.equals(payload) || PAYLOAD_GREETING.equals(payload)) {
                bindGreeting(holder, user);
            } else if (PAYLOAD_FRESH.equals(payload)) {
                bindBadges(holder.binding, user);
            } else {
                handled = false;
                break;
            }
        }
        if (!handled) bind(holder, user);
    }

    private void bind(VH holder, PartnerListUser user) {
        if (user == null) return;
        ItemPartnerListBinding b = holder.binding;
        String uid = user.stableId();
        b.cardSurface.setBackgroundResource(cardBackground(uid));

        int avatarPx = Math.round(70f * b.getRoot().getResources().getDisplayMetrics().density);
        int avatarPlaceholder = ContextCompat.getColor(b.getRoot().getContext(), R.color.partnerlist_avatar_placeholder);
        Glide.with(b.avatarIv)
                .load(showUrl(user.displayAvatar()))
                .apply(RequestOptions.circleCropTransform())
                .override(avatarPx, avatarPx)
                .placeholder(new ColorDrawable(avatarPlaceholder))
                .error(new ColorDrawable(avatarPlaceholder))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .into(b.avatarIv);

        PartnerListFlagResolver.bind(b.flagIv, user.country_code, user.country);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) b.flagIv.setClipToOutline(true);
        bindPresence(b, user);
        bindBadges(b, user);

        // 列表语伴只突出名字和语言，不显示性别、年龄。
        b.nameTv.setText(user.displayName());
        String relation = PartnerListLanguage.relation(b.getRoot().getContext(), user.nativeLanguages(), user.learningLanguages());
        b.languageTv.setText(relation);
        b.languageTv.setVisibility(TextUtils.isEmpty(relation) ? View.GONE : View.VISIBLE);

        String intro = user.intro;
        if (TextUtils.isEmpty(intro) && !user.tags().isEmpty()) intro = user.tags().get(0);
        if (TextUtils.isEmpty(intro)) intro = user.country;
        if (TextUtils.isEmpty(intro)) intro = b.getRoot().getContext().getString(R.string.partnerlist_intro_fallback);
        b.introTv.setText(intro);

        bindGreeting(holder, user);
        b.cardRoot.setOnClickListener(v -> {
            if (listener != null) listener.onOpenProfile(user);
        });
    }

    private void bindPresence(ItemPartnerListBinding b, PartnerListUser user) {
        boolean active = PartnerListTime.isRecentlyActive(user.online, user.last_active_at, serverTime);
        b.onlineDot.setVisibility(active ? View.VISIBLE : View.GONE);
        b.activeTv.setText(PartnerListTime.activeLabel(
                b.getRoot().getContext(), user.online, user.last_active_at, serverTime));
    }

    private void bindBadges(ItemPartnerListBinding b, PartnerListUser user) {
        String uid = user.stableId();
        boolean fresh = recentlyAdded.contains(uid);
        // 两个“新”标签同时出现会挤压名字；刷新推荐标签优先，普通新用户标签作为兜底。
        b.freshBadge.setVisibility(fresh ? View.VISIBLE : View.GONE);
        b.newBadge.setVisibility(!fresh && user.is_new == 1 ? View.VISIBLE : View.GONE);
    }

    private void bindGreeting(VH holder, PartnerListUser user) {
        ItemPartnerListBinding b = holder.binding;
        String uid = user.stableId();
        boolean sending = greetingPending.contains(uid);
        PartnerPendingStore.Entry relationState = PartnerPendingStore.get(uid);
        boolean contacted = greeted.contains(uid) || relationState != null;

        if (sending) {
            b.greetingBtn.setEnabled(false);
            b.greetingBtn.setAlpha(0.72f);
            b.greetingBtn.setText(R.string.partnerlist_sending);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting_disabled);
        } else if (contacted) {
            b.greetingBtn.setEnabled(true);
            b.greetingBtn.setAlpha(1f);
            b.greetingBtn.setText(R.string.partnerlist_go_chat);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting);
        } else if (greetingRemaining <= 0) {
            b.greetingBtn.setEnabled(false);
            b.greetingBtn.setAlpha(0.72f);
            b.greetingBtn.setText(R.string.partnerlist_limit_reached_short);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting_disabled);
        } else {
            b.greetingBtn.setEnabled(true);
            b.greetingBtn.setAlpha(1f);
            b.greetingBtn.setText(R.string.partnerlist_greet);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting);
        }

        b.greetingBtn.setOnClickListener(v -> {
            if (listener == null) return;
            animatePress(v);
            if (contacted) {
                listener.onOpenChat(user);
            } else {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onGreeting(user, pos);
            }
        });
    }

    private int cardBackground(String uid) {
        int bucket = Math.floorMod(uid == null ? 0 : uid.hashCode(), 4);
        if (bucket == 1) return R.drawable.bg_partnerlist_card_lavender;
        if (bucket == 2) return R.drawable.bg_partnerlist_card_peach;
        if (bucket == 3) return R.drawable.bg_partnerlist_card_sky;
        return R.drawable.bg_partnerlist_card_mint;
    }

    private void animatePress(View view) {
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()).start();
    }

    @Override public void onViewRecycled(@NonNull VH holder) {
        holder.binding.cardRoot.setOnClickListener(null);
        holder.binding.greetingBtn.setOnClickListener(null);
        try { Glide.with(holder.binding.avatarIv).clear(holder.binding.avatarIv); } catch (Throwable ignored) {}
        super.onViewRecycled(holder);
    }

    private String showUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        return WKApiConfig.getShowUrl(value);
    }

    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        String safe = value == null ? "" : value;
        for (int i = 0; i < safe.length(); i++) {
            hash ^= safe.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash == RecyclerView.NO_ID ? Long.MIN_VALUE : hash;
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemPartnerListBinding binding;
        VH(ItemPartnerListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<PartnerListUser> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull PartnerListUser oldItem, @NonNull PartnerListUser newItem) {
            return TextUtils.equals(oldItem.stableId(), newItem.stableId());
        }

        @Override public boolean areContentsTheSame(@NonNull PartnerListUser a, @NonNull PartnerListUser b) {
            if (a.profile_version > 0 && b.profile_version > 0 && a.profile_version != b.profile_version) return false;
            return TextUtils.equals(a.displayName(), b.displayName())
                    && TextUtils.equals(a.displayAvatar(), b.displayAvatar())
                    && TextUtils.equals(a.intro, b.intro)
                    && TextUtils.equals(a.country_code, b.country_code)
                    && TextUtils.equals(a.country, b.country)
                    && Objects.equals(a.nativeLanguages(), b.nativeLanguages())
                    && Objects.equals(a.learningLanguages(), b.learningLanguages())
                    && Objects.equals(a.tags(), b.tags())
                    && Objects.equals(a.profile_images, b.profile_images)
                    && TextUtils.equals(a.profile_cover, b.profile_cover)
                    && a.online == b.online
                    && a.last_active_at == b.last_active_at
                    && a.is_new == b.is_new;
        }
    };
}
