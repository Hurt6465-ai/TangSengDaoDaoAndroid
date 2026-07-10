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

/** 5 图编辑网格：点击空位添加、删除后自动前移、长按拖动排序。 */
public final class DatingPhotoGridAdapter extends RecyclerView.Adapter<DatingPhotoGridAdapter.Holder> {
    public interface Listener {
        void onAddPhoto();
        void onDeletePhoto(int position, String url);
        void onPreviewPhoto(int position, String url);
        void onStartDrag(RecyclerView.ViewHolder holder);
    }

    private final ArrayList<String> photos = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPhotos(List<String> values) {
        photos.clear();
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value) && photos.size() < DatingPhotoPolicy.MAX_PHOTO_COUNT) photos.add(value.trim());
            }
        }
        notifyDataSetChanged();
    }

    public ArrayList<String> getPhotos() {
        return new ArrayList<>(photos);
    }

    public int photoCount() {
        return photos.size();
    }

    public void appendPhotos(List<String> values) {
        if (values == null || values.isEmpty()) return;
        int old = photos.size();
        for (String value : values) {
            if (photos.size() >= DatingPhotoPolicy.MAX_PHOTO_COUNT) break;
            if (TextUtils.isEmpty(value) || photos.contains(value.trim())) continue;
            photos.add(value.trim());
        }
        if (photos.size() != old) notifyDataSetChanged();
    }

    public void removePhoto(int position) {
        if (position < 0 || position >= photos.size()) return;
        photos.remove(position);
        notifyDataSetChanged();
    }

    public boolean movePhoto(int from, int to) {
        if (from < 0 || to < 0 || from >= photos.size() || to >= photos.size()) return false;
        if (from == to) return true;
        Collections.swap(photos, from, to);
        notifyItemMoved(from, to);
        return true;
    }

    public boolean isRealPhotoPosition(int position) {
        return position >= 0 && position < photos.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWkDatingPhotoSlotBinding binding = ItemWkDatingPhotoSlotBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ViewGroup.LayoutParams rawParams = holder.itemView.getLayoutParams();
        if (rawParams != null) {
            // 借鉴 DragRankSquare：第一张主图更高，右侧两张自动叠放，其余照片继续向下排列。
            rawParams.height = dp(holder.itemView, position == 0 ? 280 : 135);
            holder.itemView.setLayoutParams(rawParams);
        }
        boolean occupied = position < photos.size();
        String url = occupied ? photos.get(position) : "";
        holder.binding.photoIv.setVisibility(occupied ? View.VISIBLE : View.GONE);
        holder.binding.addIcon.setVisibility(occupied ? View.GONE : View.VISIBLE);
        holder.binding.deleteBtn.setVisibility(occupied ? View.VISIBLE : View.GONE);
        holder.binding.mainLabel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        if (occupied) {
            Glide.with(holder.itemView)
                    .load(DatingImageSource.resolve(holder.itemView.getContext(), url))
                    .centerCrop()
                    .into(holder.binding.photoIv);
        } else {
            Glide.with(holder.itemView).clear(holder.binding.photoIv);
        }
        holder.itemView.setOnClickListener(v -> {
            if (occupied) {
                if (listener != null) listener.onPreviewPhoto(position, url);
            } else if (position == photos.size() && listener != null) {
                listener.onAddPhoto();
            }
        });
        holder.binding.deleteBtn.setOnClickListener(v -> {
            if (occupied && listener != null) listener.onDeletePhoto(position, url);
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

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

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


    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemWkDatingPhotoSlotBinding binding;

        Holder(ItemWkDatingPhotoSlotBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
