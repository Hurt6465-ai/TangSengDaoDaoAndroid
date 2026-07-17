package com.chat.partnerlist;

import android.content.Context;
import android.content.res.Configuration;
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
    private static final int NEW_USER_DAYS = 5;

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
        String uid = user.stableId();
        b.cardSurface.setBackgroundResource(cardBackground(uid));

        // 与会话列表和全屏语伴共用 AvatarView；国旗镶嵌留缝由组件统一绘制。
        b.avatarView.setSize(71f);
        b.avatarView.setStrokeWidth(0f);
        b.avatarView.showAvatar(uid, WKChannelType.PERSONAL, user.vercode);
        b.avatarView.showFlag(!TextUtils.isEmpty(user.country_code) ? user.country_code : user.country);
        bindPresence(b, user);
        bindBadges(b, user);

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

        // 简介保持在语言行下方。仅清理换行和重复空格，不按字符硬截断；布局最多显示两行。
        String intro = normalizeIntro(user.intro);
        if (TextUtils.isEmpty(intro)) intro = normalizeIntro(user.country);
        if (TextUtils.isEmpty(intro)) intro = b.getRoot().getContext().getString(R.string.partnerlist_intro_fallback);
        b.introTv.setText(intro);

        bindTags(b, user);
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
        boolean freshRecommendation = recentlyAdded.contains(user.stableId());
        boolean joinedRecently = user.joinedWithinDays(serverTime, NEW_USER_DAYS);
        b.freshBadge.setVisibility(freshRecommendation ? View.VISIBLE : View.GONE);
        b.newBadge.setVisibility(joinedRecently ? View.VISIBLE : View.GONE);
    }

    private void bindTags(ItemPartnerListBinding b, PartnerListUser user) {
        TextView[] chips = new TextView[]{
                b.tagOneTv, b.tagTwoTv, b.tagThreeTv, b.tagFourTv, b.tagFiveTv
        };
        for (TextView chip : chips) {
            chip.setText("");
            chip.setVisibility(View.GONE);
        }

        List<String> tags = visibleTags(b.getRoot().getContext(), user.tags());
        b.tagRow.setVisibility(tags.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < tags.size() && i < chips.length; i++) {
            TextView chip = chips[i];
            chip.setText(tags.get(i));
            chip.setBackgroundResource(tagBackground(user.stableId(), i));
            chip.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 标签保持用户原始顺序，不在 RecyclerView 复用时随机变化。
     * 短标签显示 5 个，中等长度显示 4 个，长标签显示 3 个；缅文因字形更宽且上下留白更大，最多 3 个。
     */
    private List<String> visibleTags(Context context, List<String> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        boolean hasMyanmar = isMyanmarLocale(context);
        for (String value : source) {
            String tag = normalizeOneLine(value);
            if (TextUtils.isEmpty(tag)) continue;
            unique.add(tag);
            hasMyanmar |= containsMyanmar(tag);
            if (unique.size() >= 5) break;
        }
        if (unique.isEmpty()) return Collections.emptyList();

        ArrayList<String> candidates = new ArrayList<>(unique);
        int cap;
        if (hasMyanmar) {
            cap = 3;
        } else if (candidates.size() >= 5 && estimatedUnits(candidates, 5) <= 38f) {
            cap = 5;
        } else if (candidates.size() >= 4 && estimatedUnits(candidates, 4) <= 34f) {
            cap = 4;
        } else {
            cap = 3;
        }
        cap = Math.min(cap, candidates.size());
        return new ArrayList<>(candidates.subList(0, cap));
    }

    private float estimatedUnits(List<String> tags, int count) {
        float total = 0f;
        for (int i = 0; i < count && i < tags.size(); i++) {
            total += 2.4f; // 胶囊左右内边距的近似成本。
            String value = tags.get(i);
            for (int offset = 0; offset < value.length();) {
                int cp = value.codePointAt(offset);
                offset += Character.charCount(cp);
                if (isMyanmarCodePoint(cp)) total += 1.8f;
                else if (isCjkCodePoint(cp)) total += 1.45f;
                else if (Character.isWhitespace(cp)) total += 0.35f;
                else if (Character.isUpperCase(cp)) total += 0.9f;
                else if (Character.isLetterOrDigit(cp)) total += 0.7f;
                else if (cp > 0xFFFF) total += 1.7f;
                else total += 1f;
            }
        }
        return total;
    }

    private boolean isMyanmarLocale(Context context) {
        if (context == null) return false;
        Configuration configuration = context.getResources().getConfiguration();
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = configuration.getLocales().isEmpty() ? Locale.getDefault() : configuration.getLocales().get(0);
        } else {
            locale = configuration.locale;
        }
        return locale != null && "my".equalsIgnoreCase(locale.getLanguage());
    }

    private boolean containsMyanmar(String value) {
        if (TextUtils.isEmpty(value)) return false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (isMyanmarCodePoint(cp)) return true;
        }
        return false;
    }

    private boolean isMyanmarCodePoint(int cp) {
        return (cp >= 0x1000 && cp <= 0x109F)
                || (cp >= 0xAA60 && cp <= 0xAA7F)
                || (cp >= 0xA9E0 && cp <= 0xA9FF);
    }

    private boolean isCjkCodePoint(int cp) {
        return (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0xAC00 && cp <= 0xD7AF);
    }

    private String normalizeIntro(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return value.trim()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll(" {2,}", " ");
    }

    private String normalizeOneLine(String value) {
        return normalizeIntro(value);
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
        int bucket = Math.floorMod(uid == null ? 0 : uid.hashCode(), 8);
        if (bucket == 1) return R.drawable.bg_partnerlist_card_lavender;
        if (bucket == 2) return R.drawable.bg_partnerlist_card_peach;
        if (bucket == 3) return R.drawable.bg_partnerlist_card_sky;
        if (bucket == 4) return R.drawable.bg_partnerlist_card_rose;
        if (bucket == 5) return R.drawable.bg_partnerlist_card_aqua;
        if (bucket == 6) return R.drawable.bg_partnerlist_card_lemon;
        if (bucket == 7) return R.drawable.bg_partnerlist_card_lilac;
        return R.drawable.bg_partnerlist_card_mint;
    }

    private int tagBackground(String uid, int index) {
        int bucket = Math.floorMod((uid == null ? 0 : uid.hashCode()) + index * 3, 5);
        if (bucket == 1) return R.drawable.bg_partnerlist_tag_blue;
        if (bucket == 2) return R.drawable.bg_partnerlist_tag_rose;
        if (bucket == 3) return R.drawable.bg_partnerlist_tag_lavender;
        if (bucket == 4) return R.drawable.bg_partnerlist_tag_peach;
        return R.drawable.bg_partnerlist_tag_mint;
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
                    && TextUtils.equals(a.created_at, b.created_at)
                    && TextUtils.equals(a.joined_at, b.joined_at)
                    && TextUtils.equals(a.registered_at, b.registered_at)
                    && TextUtils.equals(a.join_time, b.join_time)
                    && a.created_at_ts == b.created_at_ts
                    && a.joined_at_ts == b.joined_at_ts
                    && a.online == b.online
                    && a.last_active_at == b.last_active_at
                    && a.is_new == b.is_new;
        }
    };
}
