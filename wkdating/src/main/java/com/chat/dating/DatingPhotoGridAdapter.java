package com.chat.dating;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.chat.dating.databinding.ItemWkDatingPhotoSlotBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 5 图编辑网格：主图与推荐卡派生图始终按同一索引移动、删除。 */
public final class DatingPhotoGridAdapter extends RecyclerView.Adapter<DatingPhotoGridAdapter.Holder> {
    public interface Listener {
        void onAddPhoto();
        void onDeletePhoto(int position, String url);
        void onPreviewPhoto(int position, String url);
        void onStartDrag(RecyclerView.ViewHolder holder);
    }

    private final ArrayList<String> photos = new ArrayList<>();
    private final ArrayList<String> cardPhotos = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPhotos(List<String> masters, List<String> cards) {
        photos.clear();
        cardPhotos.clear();
        if (masters != null) {
            for (int i = 0; i < masters.size() && photos.size() < DatingPhotoPolicy.MAX_PHOTO_COUNT; i++) {
                String master = masters.get(i);
                if (TextUtils.isEmpty(master)) continue;
                String cleanMaster = master.trim();
                String card = cards != null && i < cards.size() ? cards.get(i) : "";
                photos.add(cleanMaster);
                cardPhotos.add(TextUtils.isEmpty(card) ? cleanMaster : card.trim());
            }
        }
        notifyDataSetChanged();
    }

    public ArrayList<String> getPhotos() {
        return new ArrayList<>(photos);
    }

    public ArrayList<String> getCardPhotos() {
        return new ArrayList<>(cardPhotos);
    }

    public int photoCount() {
        return photos.size();
    }

    public void appendPhotos(List<String> masters, List<String> cards) {
        if (masters == null || masters.isEmpty()) return;
        int old = photos.size();
        for (int i = 0; i < masters.size() && photos.size() < DatingPhotoPolicy.MAX_PHOTO_COUNT; i++) {
            String master = masters.get(i);
            if (TextUtils.isEmpty(master) || photos.contains(master.trim())) continue;
            String cleanMaster = master.trim();
            String card = cards != null && i < cards.size() ? cards.get(i) : "";
            photos.add(cleanMaster);
            cardPhotos.add(TextUtils.isEmpty(card) ? cleanMaster : card.trim());
        }
        if (photos.size() != old) notifyDataSetChanged();
    }

    public void removePhoto(int position) {
        if (position < 0 || position >= photos.size()) return;
        photos.remove(position);
        if (position < cardPhotos.size()) cardPhotos.remove(position);
        notifyDataSetChanged();
    }

    public boolean movePhoto(int from, int to) {
        if (from < 0 || to < 0 || from >= photos.size() || to >= photos.size()) return false;
        if (from == to) return true;
        Collections.swap(photos, from, to);
        if (from < cardPhotos.size() && to < cardPhotos.size()) Collections.swap(cardPhotos, from, to);
        notifyItemMoved(from, to);
        return true;
    }

    public boolean isRealPhotoPosition(int position) {
        return position >= 0 && position < photos.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWkDatingPhotoSlotBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        boolean occupied = position < photos.size();
        String master = occupied ? photos.get(position) : "";
        String preview = occupied && position < cardPhotos.size() ? cardPhotos.get(position) : master;
        holder.binding.photoIv.setVisibility(occupied ? View.VISIBLE : View.GONE);
        holder.binding.addIcon.setVisibility(occupied ? View.GONE : View.VISIBLE);
        holder.binding.deleteBtn.setVisibility(occupied ? View.VISIBLE : View.GONE);
        holder.binding.mainLabel.setVisibility(position == 0 && occupied ? View.VISIBLE : View.GONE);
        if (occupied) {
            Glide.with(holder.itemView)
                    .load(DatingImageSource.resolve(holder.itemView.getContext(), preview))
                    .override(360, 640)
                    .centerCrop()
                    .into(holder.binding.photoIv);
        } else {
            Glide.with(holder.itemView).clear(holder.binding.photoIv);
        }
        holder.itemView.setOnClickListener(v -> {
            if (occupied) {
                if (listener != null) listener.onPreviewPhoto(position, master);
            } else if (listener != null) {
                listener.onAddPhoto();
            }
        });
        holder.binding.deleteBtn.setOnClickListener(v -> {
            if (occupied && listener != null) listener.onDeletePhoto(position, master);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (occupied && listener != null) {
                listener.onStartDrag(holder);
                return true;
            }
            return false;
        });
        holder.itemView.setOnTouchListener((v, event) -> {
            if (occupied && event.getActionMasked() == MotionEvent.ACTION_DOWN) v.setPressed(true);
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) v.setPressed(false);
            return false;
        });
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.itemView).clear(holder.binding.photoIv);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return DatingPhotoPolicy.MAX_PHOTO_COUNT;
    }

    public static ItemTouchHelper.Callback touchCallback(DatingPhotoGridAdapter adapter) {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return adapter.movePhoto(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
            @Override public boolean isLongPressDragEnabled() { return false; }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getBindingAdapterPosition();
                if (!adapter.isRealPhotoPosition(position)) return makeMovementFlags(0, 0);
                return super.getMovementFlags(recyclerView, viewHolder);
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.animate().scaleX(1.05f).scaleY(1.05f).alpha(0.92f).setDuration(100).start();
                    viewHolder.itemView.setElevation(viewHolder.itemView.getResources().getDisplayMetrics().density * 12f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
                viewHolder.itemView.setElevation(0f);
            }
        };
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemWkDatingPhotoSlotBinding binding;
        Holder(ItemWkDatingPhotoSlotBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
