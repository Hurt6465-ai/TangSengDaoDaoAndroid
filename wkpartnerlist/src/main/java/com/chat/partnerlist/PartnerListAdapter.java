package com.chat.partnerlist;

import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.partnerlist.databinding.ItemPartnerListBinding;
import com.chat.partnerlist.model.PartnerListUser;
import com.chat.uikit.partner.PartnerPendingStore;
import com.xinbida.wukongim.entity.WKChannelType;

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

        // 与会话列表和全屏语伴共用 AvatarView，国旗会自动在头像上挖出极细间隙，
        // 不再通过白色圆圈伪造边框。
        b.avatarView.setSize(72f);
        b.avatarView.setStrokeWidth(0f);
        b.avatarView.showAvatar(uid, WKChannelType.PERSONAL, user.vercode);
        b.avatarView.showFlag(!TextUtils.isEmpty(user.country_code) ? user.country_code : user.country);
        bindPresence(b, user);
        bindBadges(b, user);

        // 列表语伴只突出名字和语言，不显示性别、年龄。
        b.nameTv.setText(user.displayName());
        String nativeLanguage = PartnerListLanguage.compact(b.getRoot().getContext(), user.nativeLanguages());
        String learningLanguage = PartnerListLanguage.compact(b.getRoot().getContext(), user.learningLanguages());
        boolean hasNativeLanguage = !TextUtils.isEmpty(nativeLanguage);
        boolean hasLearningLanguage = !TextUtils.isEmpty(learningLanguage);
        b.nativeLanguageTv.setText(nativeLanguage);
        b.learningLanguageTv.setText(learningLanguage);
        b.nativeLanguageTv.setVisibility(hasNativeLanguage ? View.VISIBLE : View.GONE);
        b.learningLanguageTv.setVisibility(hasLearningLanguage ? View.VISIBLE : View.GONE);
        b.languageExchangeTv.setVisibility(hasNativeLanguage && hasLearningLanguage ? View.VISIBLE : View.GONE);
        b.languageRow.setVisibility(hasNativeLanguage || hasLearningLanguage ? View.VISIBLE : View.GONE);

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
        // 光环和绿点只表示“当前在线”，避免把“最近活跃”误显示成在线。
        boolean online = user.online == 1;
        b.onlineDot.setVisibility(online ? View.VISIBLE : View.GONE);
        b.avatarRing.setVisibility(online ? View.VISIBLE : View.INVISIBLE);
        b.avatarGap.setVisibility(online ? View.VISIBLE : View.INVISIBLE);
        if (online) b.avatarRing.setBackgroundResource(avatarRingBackground(user.stableId()));
        String activeLabel = PartnerListTime.activeLabel(
                b.getRoot().getContext(), user.online, user.last_active_at, serverTime);
        b.activeTv.setText(activeLabel);
        b.activeTv.setVisibility(TextUtils.isEmpty(activeLabel) ? View.GONE : View.VISIBLE);
        b.activeTv.setTextColor(ContextCompat.getColor(b.getRoot().getContext(),
                online ? R.color.partnerlist_online_text : R.color.partnerlist_active_text));
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
            setCompactButtonText(b.greetingBtn, R.string.partnerlist_sending);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting_disabled);
        } else if (contacted) {
            b.greetingBtn.setEnabled(true);
            b.greetingBtn.setAlpha(1f);
            setCompactButtonText(b.greetingBtn, R.string.partnerlist_go_chat);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting);
        } else if (greetingRemaining <= 0) {
            b.greetingBtn.setEnabled(false);
            b.greetingBtn.setAlpha(0.72f);
            setCompactButtonText(b.greetingBtn, R.string.partnerlist_limit_reached_short);
            b.greetingBtn.setBackgroundResource(R.drawable.bg_partnerlist_greeting_disabled);
        } else {
            b.greetingBtn.setEnabled(true);
            b.greetingBtn.setAlpha(1f);
            setGreetingHi(b.greetingBtn);
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


    private void setGreetingHi(TextView textView) {
        textView.setText("Hi");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) textView.setLetterSpacing(0.025f);
    }

    private void setCompactButtonText(TextView textView, int textRes) {
        textView.setText(textRes);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) textView.setLetterSpacing(0.01f);
    }

    private int cardBackground(String uid) {
        int bucket = Math.floorMod(uid == null ? 0 : uid.hashCode(), 4);
        if (bucket == 1) return R.drawable.bg_partnerlist_card_lavender;
        if (bucket == 2) return R.drawable.bg_partnerlist_card_peach;
        if (bucket == 3) return R.drawable.bg_partnerlist_card_sky;
        return R.drawable.bg_partnerlist_card_mint;
    }

    private int avatarRingBackground(String uid) {
        int bucket = Math.floorMod(uid == null ? 0 : uid.hashCode(), 6);
        if (bucket == 1) return R.drawable.bg_partnerlist_avatar_ring_lavender_rose;
        if (bucket == 2) return R.drawable.bg_partnerlist_avatar_ring_sunset;
        if (bucket == 3) return R.drawable.bg_partnerlist_avatar_ring_sky_violet;
        if (bucket == 4) return R.drawable.bg_partnerlist_avatar_ring_aqua_lime;
        if (bucket == 5) return R.drawable.bg_partnerlist_avatar_ring_coral_gold;
        return R.drawable.bg_partnerlist_avatar_ring_mint_sky;
    }

    private void animatePress(View view) {
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()).start();
    }

    @Override public void onViewRecycled(@NonNull VH holder) {
        holder.binding.cardRoot.setOnClickListener(null);
        holder.binding.greetingBtn.setOnClickListener(null);
        holder.binding.avatarView.showDefaultAvatar("");
        super.onViewRecycled(holder);
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
