package com.chat.partnerlist;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.LruCache;
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

import com.chat.partner.profile.PartnerTagLocalizer;
import com.chat.partnerlist.databinding.ItemPartnerListBinding;
import com.chat.partnerlist.model.PartnerListUser;
import com.chat.uikit.partner.PartnerPendingStore;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class PartnerListAdapter extends ListAdapter<PartnerListUser, PartnerListAdapter.VH> {
    private static final String PAYLOAD_TIME = "time";
    private static final String PAYLOAD_QUOTA = "quota";
    private static final String PAYLOAD_FRESH = "fresh";
    private static final String PAYLOAD_GREETING = "greeting";
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final int NEW_USER_DAYS = 5;

    public interface Listener {
        void onOpenProfile(PartnerListUser user);
        void onGreeting(PartnerListUser user, int position);
        void onOpenChat(PartnerListUser user);
    }

    private final Listener listener;
    private final Set<String> greetingPending = new HashSet<>();
    private final Set<String> greeted = new HashSet<>();
    private final LruCache<String, List<String>> localizedTagCache = new LruCache<>(160);
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
        int value = Math.max(0, remaining);
        if (greetingRemaining == value) return;
        greetingRemaining = value;
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount(), PAYLOAD_QUOTA);
    }

    public void setRecentlyAdded(List<String> ids) {
        Set<String> next = ids == null ? Collections.emptySet() : new HashSet<>(ids);
        if (recentlyAdded.equals(next)) return;
        recentlyAdded = next;
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
                bindBadges(holder.binding, user);
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
        Context context = b.getRoot().getContext();
        String uid = user.stableId();
        b.cardSurface.setBackgroundResource(cardBackground(uid));

        String flag = !TextUtils.isEmpty(user.country_code) ? user.country_code : user.country;
        if (!TextUtils.equals(holder.boundUid, uid)
                || !TextUtils.equals(holder.boundVercode, user.vercode)
                || !TextUtils.equals(holder.boundFlag, flag)) {
            b.avatarView.setSize(72f);
            b.avatarView.setStrokeWidth(0f);
            b.avatarView.showAvatar(uid, WKChannelType.PERSONAL, user.vercode);
            b.avatarView.showFlag(flag);
            holder.boundUid = uid;
            holder.boundVercode = user.vercode;
            holder.boundFlag = flag;
        }

        bindPresence(b, user);
        bindBadges(b, user);
        b.nameTv.setText(user.displayName());

        String nativeLanguage = PartnerListLanguage.compact(context, user.nativeLanguages());
        String learningLanguage = PartnerListLanguage.compact(context, user.learningLanguages());
        boolean hasNativeLanguage = !TextUtils.isEmpty(nativeLanguage);
        boolean hasLearningLanguage = !TextUtils.isEmpty(learningLanguage);
        b.nativeLanguageTv.setText(nativeLanguage);
        b.learningLanguageTv.setText(learningLanguage);
        b.nativeLanguageTv.setVisibility(hasNativeLanguage ? View.VISIBLE : View.GONE);
        b.learningLanguageTv.setVisibility(hasLearningLanguage ? View.VISIBLE : View.GONE);
        b.languageExchangeTv.setVisibility(hasNativeLanguage && hasLearningLanguage ? View.VISIBLE : View.GONE);
        b.languageRow.setVisibility(hasNativeLanguage || hasLearningLanguage ? View.VISIBLE : View.GONE);

        String intro = normalizeIntro(user.intro);
        if (TextUtils.isEmpty(intro)) intro = normalizeIntro(user.country);
        if (TextUtils.isEmpty(intro)) intro = context.getString(R.string.partnerlist_intro_fallback);
        b.introTv.setText(intro);

        bindTags(holder, user);
        bindGreeting(holder, user);
        b.cardRoot.setOnClickListener(v -> {
            if (listener != null) listener.onOpenProfile(user);
        });
    }

    private void bindPresence(ItemPartnerListBinding b, PartnerListUser user) {
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
        boolean fresh = recentlyAdded.contains(user.stableId());
        String registeredAt = user.registrationTimeRaw();
        boolean joinedRecently = PartnerListTime.isWithinDays(registeredAt, serverTime, NEW_USER_DAYS);
        if (TextUtils.isEmpty(registeredAt)) joinedRecently = user.is_new == 1;
        // 推荐刷新标签优先，避免两个小标签同时挤压名字。
        b.freshBadge.setVisibility(fresh ? View.VISIBLE : View.GONE);
        b.newBadge.setVisibility(!fresh && joinedRecently ? View.VISIBLE : View.GONE);
    }

    private void bindTags(VH holder, PartnerListUser user) {
        ItemPartnerListBinding b = holder.binding;
        Context context = b.getRoot().getContext();
        List<String> labels = localizedTags(context, user.tags());
        for (TextView chip : holder.tagViews) chip.setVisibility(View.GONE);
        if (labels.isEmpty()) {
            b.tagsRow.setVisibility(View.GONE);
            return;
        }

        boolean myanmar = isMyanmarLocale(context) || containsMyanmar(labels);
        int maxVisible = Math.min(labels.size(), myanmar ? 3 : 5);
        int minVisible = Math.min(maxVisible, myanmar ? 2 : 3);
        int available = b.tagsRow.getWidth();
        if (available <= 0) {
            int screen = context.getResources().getDisplayMetrics().widthPixels;
            available = Math.max(dp(context, 150), screen - dp(context, 149));
        }

        int count = maxVisible;
        while (count > minVisible && measuredTagWidth(holder.tagViews[0], labels, count, context) > available) {
            count--;
        }
        int margins = dp(context, 5) * Math.max(0, count - 1);
        int perChipMax = Math.max(dp(context, 44), (available - margins) / Math.max(1, count));
        for (int i = 0; i < count; i++) {
            TextView chip = holder.tagViews[i];
            chip.setText(labels.get(i));
            chip.setMaxWidth(perChipMax);
            chip.setVisibility(View.VISIBLE);
        }
        b.tagsRow.setVisibility(View.VISIBLE);
    }

    private List<String> localizedTags(Context context, List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) return Collections.emptyList();
        String locale = currentLocale(context).toLanguageTag();
        String cacheKey = locale + '|' + TextUtils.join("\u001f", rawTags);
        List<String> cached = localizedTagCache.get(cacheKey);
        if (cached != null) return cached;

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : rawTags) {
            if (TextUtils.isEmpty(raw)) continue;
            String key = PartnerTagLocalizer.toKey(raw);
            String display = PartnerTagLocalizer.tagText(context, key);
            if (TextUtils.isEmpty(display)) continue;
            // 未识别的后端机器 key 不直接暴露给用户。
            if (TextUtils.equals(display, raw.trim()) && isMachineKey(raw.trim())) continue;
            unique.add(display.trim());
        }
        List<String> result = Collections.unmodifiableList(new ArrayList<>(unique));
        localizedTagCache.put(cacheKey, result);
        return result;
    }

    private boolean isMachineKey(String value) {
        if (TextUtils.isEmpty(value) || value.indexOf('_') <= 0) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c == '_' || Character.isDigit(c) || (c >= 'a' && c <= 'z'))) return false;
        }
        return true;
    }

    private int measuredTagWidth(TextView sample, List<String> labels, int count, Context context) {
        float width = 0f;
        int horizontal = dp(context, 14);
        int margin = dp(context, 5);
        for (int i = 0; i < count; i++) {
            width += sample.getPaint().measureText(labels.get(i)) + horizontal;
            if (i > 0) width += margin;
        }
        return Math.round(width);
    }

    private boolean containsMyanmar(List<String> values) {
        for (String value : values) {
            if (value == null) continue;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c >= '\u1000' && c <= '\u109f') return true;
            }
        }
        return false;
    }

    private boolean isMyanmarLocale(Context context) {
        return "my".equalsIgnoreCase(currentLocale(context).getLanguage());
    }

    private Locale currentLocale(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !configuration.getLocales().isEmpty()) {
            return configuration.getLocales().get(0);
        }
        return configuration.locale == null ? Locale.getDefault() : configuration.locale;
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
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        textView.setTypeface(MEDIUM);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) textView.setLetterSpacing(0.015f);
    }

    private void setCompactButtonText(TextView textView, int textRes) {
        textView.setText(textRes);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        textView.setTypeface(MEDIUM);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) textView.setLetterSpacing(0f);
    }

    private int cardBackground(String uid) {
        switch (Math.floorMod(uid == null ? 0 : uid.hashCode(), 8)) {
            case 1: return R.drawable.bg_partnerlist_card_lavender;
            case 2: return R.drawable.bg_partnerlist_card_peach;
            case 3: return R.drawable.bg_partnerlist_card_sky;
            case 4: return R.drawable.bg_partnerlist_card_rose;
            case 5: return R.drawable.bg_partnerlist_card_aqua;
            case 6: return R.drawable.bg_partnerlist_card_lemon;
            case 7: return R.drawable.bg_partnerlist_card_coral;
            default: return R.drawable.bg_partnerlist_card_mint;
        }
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

    private String normalizeIntro(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        while (clean.contains("  ")) clean = clean.replace("  ", " ");
        return clean;
    }

    private void animatePress(View view) {
        view.animate().cancel();
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70L).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(90L).start()).start();
    }

    private int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override public void onViewRecycled(@NonNull VH holder) {
        holder.binding.cardRoot.setOnClickListener(null);
        holder.binding.greetingBtn.setOnClickListener(null);
        holder.binding.greetingBtn.animate().cancel();
        holder.binding.avatarView.showDefaultAvatar("");
        holder.boundUid = null;
        holder.boundVercode = null;
        holder.boundFlag = null;
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
        final TextView[] tagViews;
        String boundUid;
        String boundVercode;
        String boundFlag;

        VH(ItemPartnerListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.tagViews = new TextView[]{
                    binding.tag1Tv, binding.tag2Tv, binding.tag3Tv, binding.tag4Tv, binding.tag5Tv
            };
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
                    && TextUtils.equals(a.vercode, b.vercode)
                    && TextUtils.equals(a.registrationTimeRaw(), b.registrationTimeRaw())
                    && a.online == b.online
                    && a.last_active_at == b.last_active_at
                    && a.is_new == b.is_new;
        }
    };
}
