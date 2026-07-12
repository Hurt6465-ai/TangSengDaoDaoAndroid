package com.chat.partnerlist;

import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.chat.base.config.WKApiConfig;
import com.chat.partnerlist.databinding.ItemPartnerListBinding;
import com.chat.partnerlist.model.PartnerListUser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PartnerListAdapter extends ListAdapter<PartnerListUser, PartnerListAdapter.VH> {
    public interface Listener {
        void onOpenProfile(PartnerListUser user);
        void onGreeting(PartnerListUser user, int position);
    }

    private final Listener listener;
    private final Set<String> greetingPending = new HashSet<>();
    private final Set<String> greeted = new HashSet<>();
    private long serverTime;
    private int greetingRemaining = 10;

    public PartnerListAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        PartnerListUser user = getItem(position);
        String id = user == null ? "" : user.stableId();
        return id.hashCode();
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
        notifyItemRangeChanged(0, getItemCount(), "time");
    }

    public void setGreetingRemaining(int remaining) {
        greetingRemaining = Math.max(0, remaining);
        notifyItemRangeChanged(0, getItemCount(), "quota");
    }

    public void markGreetingPending(String uid, boolean pending) {
        if (TextUtils.isEmpty(uid)) return;
        if (pending) greetingPending.add(uid); else greetingPending.remove(uid);
        notifyUid(uid);
    }

    public void markGreeted(String uid) {
        if (TextUtils.isEmpty(uid)) return;
        greetingPending.remove(uid);
        greeted.add(uid);
        notifyUid(uid);
    }

    private void notifyUid(String uid) {
        for (int i = 0; i < getItemCount(); i++) {
            PartnerListUser user = getItem(i);
            if (user != null && TextUtils.equals(uid, user.stableId())) {
                notifyItemChanged(i);
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
        if (payloads.isEmpty()) bind(holder, getItem(position)); else bind(holder, getItem(position));
    }

    private void bind(VH holder, PartnerListUser user) {
        if (user == null) return;
        ItemPartnerListBinding b = holder.binding;
        String uid = user.stableId();
        String avatar = showUrl(user.displayAvatar());
        int avatarPx = Math.round(72f * b.getRoot().getResources().getDisplayMetrics().density);
        Glide.with(b.avatarIv)
                .load(avatar)
                .apply(RequestOptions.circleCropTransform())
                .override(avatarPx, avatarPx)
                .placeholder(new ColorDrawable(0xFFE9ECF4))
                .error(new ColorDrawable(0xFFE9ECF4))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .into(b.avatarIv);

        PartnerListFlagResolver.bind(b.flagIv, user.country_code);
        b.onlineDot.setVisibility(user.online == 1 ? View.VISIBLE : View.GONE);
        b.newBadge.setVisibility(user.is_new == 1 ? View.VISIBLE : View.GONE);

        int age = user.age();
        b.nameTv.setText(age > 0 ? b.getRoot().getContext().getString(R.string.partnerlist_name_age, user.displayName(), age) : user.displayName());
        String relation = PartnerListLanguage.relation(b.getRoot().getContext(), user.nativeLanguages(), user.learningLanguages());
        b.languageTv.setText(relation);
        b.languageTv.setVisibility(TextUtils.isEmpty(relation) ? View.GONE : View.VISIBLE);

        String intro = user.intro;
        if (TextUtils.isEmpty(intro) && !user.tags().isEmpty()) intro = user.tags().get(0);
        if (TextUtils.isEmpty(intro)) intro = user.country;
        if (TextUtils.isEmpty(intro)) intro = b.getRoot().getContext().getString(R.string.partnerlist_intro_fallback);
        b.introTv.setText(intro);
        b.activeTv.setText(PartnerListTime.activeLabel(b.getRoot().getContext(), user.online, user.last_active_at, serverTime));

        boolean isPending = greetingPending.contains(uid);
        boolean isGreeted = greeted.contains(uid);
        b.greetingBtn.setEnabled(!isPending && !isGreeted && greetingRemaining > 0);
        if (isPending) {
            b.greetingBtn.setText(R.string.partnerlist_sending);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting_disabled);
        } else if (isGreeted) {
            b.greetingBtn.setText(R.string.partnerlist_greeted);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting_disabled);
        } else if (greetingRemaining <= 0) {
            b.greetingBtn.setText(R.string.partnerlist_limit_reached_short);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting_disabled);
        } else {
            b.greetingBtn.setText(R.string.partnerlist_greet);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting);
        }

        b.cardRoot.setOnClickListener(v -> {
            if (listener != null) listener.onOpenProfile(user);
        });
        b.greetingBtn.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || listener == null) return;
            animatePress(v);
            listener.onGreeting(user, adapterPosition);
        });
    }

    private void animatePress(View view) {
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()).start();
    }

    @Override public void onViewRecycled(@NonNull VH holder) {
        try { Glide.with(holder.binding.avatarIv).clear(holder.binding.avatarIv); } catch (Throwable ignored) {}
        super.onViewRecycled(holder);
    }

    private String showUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        return WKApiConfig.getShowUrl(value);
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

        @Override public boolean areContentsTheSame(@NonNull PartnerListUser oldItem, @NonNull PartnerListUser newItem) {
            return TextUtils.equals(oldItem.displayName(), newItem.displayName())
                    && TextUtils.equals(oldItem.displayAvatar(), newItem.displayAvatar())
                    && TextUtils.equals(oldItem.intro, newItem.intro)
                    && TextUtils.equals(oldItem.country_code, newItem.country_code)
                    && oldItem.online == newItem.online
                    && oldItem.last_active_at == newItem.last_active_at
                    && oldItem.is_new == newItem.is_new;
        }
    };
}
