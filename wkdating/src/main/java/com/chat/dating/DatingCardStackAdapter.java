package com.chat.dating;

import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatingCardStackAdapter extends RecyclerView.Adapter<DatingCardStackAdapter.CardHolder> {
    public interface OnCardTapListener {
        void onPreviousPhoto(DatingProfile profile, int position, int photoIndex);
        void onNextPhoto(DatingProfile profile, int position, int photoIndex);
        void onOpenProfile(DatingProfile profile, int position, int photoIndex);
    }

    private final ArrayList<DatingProfile> profiles = new ArrayList<>();
    private final Map<String, Integer> photoPositions = new HashMap<>();
    private OnCardTapListener tapListener;

    public void setOnCardTapListener(OnCardTapListener listener) {
        this.tapListener = listener;
    }

    public void setProfiles(List<DatingProfile> data) {
        profiles.clear();
        photoPositions.clear();
        if (data != null) profiles.addAll(data);
        notifyDataSetChanged();
    }

    public void appendProfiles(List<DatingProfile> data) {
        if (data == null || data.isEmpty()) return;
        int start = profiles.size();
        profiles.addAll(data);
        notifyItemRangeInserted(start, data.size());
    }

    public DatingProfile getProfile(int position) {
        if (position < 0 || position >= profiles.size()) return null;
        return profiles.get(position);
    }

    public int getPhotoIndex(int position) {
        DatingProfile profile = getProfile(position);
        if (profile == null) return 0;
        Integer index = photoPositions.get(profile.safeUid());
        return index == null ? 0 : Math.max(0, index);
    }

    private void savePhotoIndex(DatingProfile profile, int index) {
        if (profile == null || TextUtils.isEmpty(profile.safeUid())) return;
        photoPositions.put(profile.safeUid(), Math.max(0, index));
    }

    @NonNull
    @Override
    public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DatingCardView card = new DatingCardView(parent.getContext());
        card.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return new CardHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull CardHolder holder, int position) {
        DatingProfile profile = profiles.get(position);
        holder.card.bind(profile, getPhotoIndex(position));
        holder.card.setOnTouchListener(new TapRouter(holder.card, profile, position));
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    public static class CardHolder extends RecyclerView.ViewHolder {
        public final DatingCardView card;

        public CardHolder(@NonNull DatingCardView itemView) {
            super(itemView);
            this.card = itemView;
        }
    }

    private final class TapRouter implements View.OnTouchListener {
        private final DatingCardView card;
        private final DatingProfile profile;
        private final int position;
        private float downX;
        private float downY;
        private long downAt;
        private boolean moved;

        TapRouter(DatingCardView card, DatingProfile profile, int position) {
            this.card = card;
            this.profile = profile;
            this.position = position;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downAt = System.currentTimeMillis();
                    moved = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.hypot(dx, dy) > v.getResources().getDisplayMetrics().density * 12f) moved = true;
                    return false;
                case MotionEvent.ACTION_UP:
                    if (!moved && System.currentTimeMillis() - downAt < 260) {
                        float width = Math.max(1f, v.getWidth());
                        float height = Math.max(1f, v.getHeight());
                        if (event.getX() > width * 0.74f && event.getY() > height * 0.58f) {
                            if (tapListener != null) tapListener.onOpenProfile(profile, position, card.getPhotoIndex());
                        } else if (event.getX() < width * 0.42f) {
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
                default:
                    return false;
            }
        }
    }
}
