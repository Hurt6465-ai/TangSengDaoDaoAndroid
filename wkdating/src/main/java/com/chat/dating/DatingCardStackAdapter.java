package com.chat.dating;

import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DatingCardStackAdapter extends RecyclerView.Adapter<DatingCardStackAdapter.CardHolder> {
    public interface OnCardTapListener {
        void onPreviousPhoto(DatingProfile profile, int position, int photoIndex);
        void onNextPhoto(DatingProfile profile, int position, int photoIndex);
        void onOpenProfile(DatingProfile profile, int position, int photoIndex);
    }

    private final ArrayList<DatingProfile> profiles = new ArrayList<>();
    private final Map<String, Integer> photoPositions = new HashMap<>();
    private OnCardTapListener tapListener;

    public DatingCardStackAdapter() { setHasStableIds(true); }
    public void setOnCardTapListener(OnCardTapListener listener) { this.tapListener = listener; }

    public void submitProfiles(List<DatingProfile> data) {
        ArrayList<DatingProfile> next = new ArrayList<>();
        if (data != null) next.addAll(data);
        ArrayList<DatingProfile> old = new ArrayList<>(profiles);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return TextUtils.equals(old.get(oldItemPosition).safeUid(), next.get(newItemPosition).safeUid());
            }
            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return contentSignature(old.get(oldItemPosition)).equals(contentSignature(next.get(newItemPosition)));
            }
        }, false);
        profiles.clear();
        profiles.addAll(next);
        prunePhotoPositions(next);
        result.dispatchUpdatesTo(this);
    }

    public void appendProfiles(List<DatingProfile> data) {
        if (data == null || data.isEmpty()) return;
        int start = profiles.size();
        profiles.addAll(data);
        notifyItemRangeInserted(start, data.size());
    }

    public DatingProfile getProfile(int position) {
        return position < 0 || position >= profiles.size() ? null : profiles.get(position);
    }

    public int getPhotoIndex(int position) {
        DatingProfile profile = getProfile(position);
        if (profile == null) return 0;
        Integer index = photoPositions.get(profile.safeUid());
        return index == null ? 0 : Math.max(0, index);
    }

    @Override public long getItemId(int position) {
        DatingProfile profile = getProfile(position);
        return profile == null ? RecyclerView.NO_ID : fnv1a64(profile.safeUid());
    }

    @NonNull @Override
    public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DatingCardView card = new DatingCardView(parent.getContext());
        card.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new CardHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull CardHolder holder, int position) {
        DatingProfile profile = profiles.get(position);
        holder.card.bind(profile, getPhotoIndex(position));
        holder.card.setOnTouchListener(new TapRouter(holder.card, profile, holder));
        holder.card.getProfileArrowView().setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        holder.card.getProfileArrowView().setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION && tapListener != null) {
                DatingProfile currentProfile = getProfile(current);
                if (currentProfile != null) tapListener.onOpenProfile(currentProfile, current, holder.card.getPhotoIndex());
            }
        });
    }

    @Override public void onViewRecycled(@NonNull CardHolder holder) {
        holder.card.recycle();
        super.onViewRecycled(holder);
    }

    @Override public int getItemCount() { return profiles.size(); }

    private void savePhotoIndex(DatingProfile profile, int index) {
        if (profile == null || TextUtils.isEmpty(profile.safeUid())) return;
        photoPositions.put(profile.safeUid(), Math.max(0, index));
    }

    private void prunePhotoPositions(List<DatingProfile> values) {
        ArrayList<String> valid = new ArrayList<>();
        for (DatingProfile value : values) if (value != null) valid.add(value.safeUid());
        photoPositions.keySet().retainAll(valid);
    }

    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        String source = value == null ? "" : value;
        for (int i = 0; i < source.length(); i++) {
            hash ^= source.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash == RecyclerView.NO_ID ? hash + 1 : hash;
    }

    private static String contentSignature(DatingProfile p) {
        if (p == null) return "";
        return Objects.toString(p.safeName(), "") + '|' + p.age + '|' + p.normalizedSex() + '|'
                + Objects.toString(p.displayLocation(), "") + '|' + p.online + '|'
                + Objects.toString(p.safeIntro(), "") + '|' + Objects.toString(p.safeRelationshipGoal(), "") + '|'
                + Objects.toString(p.job, "") + '|' + Objects.toString(p.education, "") + '|'
                + Objects.toString(p.relationship_status, "") + '|' + Objects.toString(p.sexual_orientation, "") + '|'
                + p.height_cm + '|' + p.weight_kg + '|' + p.profile_score + '|'
                + p.safePhotos().toString() + '|' + p.safeCardPhotos().toString() + '|' + p.safeTags().toString();
    }

    public static class CardHolder extends RecyclerView.ViewHolder {
        public final DatingCardView card;
        CardHolder(@NonNull DatingCardView itemView) { super(itemView); card = itemView; }
    }

    private final class TapRouter implements View.OnTouchListener {
        private final DatingCardView card;
        private final DatingProfile profile;
        private final CardHolder holder;
        private float downX;
        private float downY;
        private long downAt;
        private boolean moved;

        TapRouter(DatingCardView card, DatingProfile profile, CardHolder holder) {
            this.card = card;
            this.profile = profile;
            this.holder = holder;
        }

        @Override public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX(); downY = event.getY(); downAt = System.currentTimeMillis(); moved = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(event.getX() - downX, event.getY() - downY)
                            > v.getResources().getDisplayMetrics().density * 12f) moved = true;
                    return false;
                case MotionEvent.ACTION_UP:
                    if (!moved && System.currentTimeMillis() - downAt < 260) {
                        int position = holder.getBindingAdapterPosition();
                        if (position == RecyclerView.NO_POSITION) return false;
                        float width = Math.max(1f, v.getWidth());
                        if (event.getX() < width * 0.42f) {
                            card.showPreviousPhoto();
                            savePhotoIndex(profile, card.getPhotoIndex());
                            if (tapListener != null) tapListener.onPreviousPhoto(profile, position, card.getPhotoIndex());
                        } else if (event.getX() > width * 0.58f) {
                            card.showNextPhoto();
                            savePhotoIndex(profile, card.getPhotoIndex());
                            if (tapListener != null) tapListener.onNextPhoto(profile, position, card.getPhotoIndex());
                        }
                    }
                    return false;
                default: return false;
            }
        }
    }
}
