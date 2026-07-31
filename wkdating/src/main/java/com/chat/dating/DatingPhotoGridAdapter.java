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

/** 5 图编辑网格：所有页面只使用同一套照片 URL。 */
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
                if (TextUtils.isEmpty(value)) continue;
                String clean = value.trim();
                if (!photos.contains(clean) && photos.size() < DatingPhotoPolicy.MAX_PHOTO_COUNT) {
                    photos.add(clean);
                }
            }
        }
        notifyDataSetChanged();
    }

    /** 兼容旧调用，cards 已停止使用。 */
    public void setPhotos(List<String> values, List<String> ignoredCards) {
        setPhotos(values);
    }

    public ArrayList<String> getPhotos() {
        return new ArrayList<>(photos);
    }

    /** 兼容旧后端字段，返回与 photos 相同的 URL，不再上传第二份文件。 */
    public ArrayList<String> getCardPhotos() {
        return new ArrayList<>(photos);
    }

    public int photoCount() {
        return photos.size();
    }

    public void appendPhotos(List<String> values) {
        if (values == null || values.isEmpty()) return;
        int old = photos.size();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            String clean = value.trim();
            if (!photos.contains(clean) && photos.size() < DatingPhotoPolicy.MAX_PHOTO_COUNT) photos.add(clean);
        }
        if (photos.size() != old) notifyDataSetChanged();
    }

    /** 兼容旧调用。 */
    public void appendPhotos(List<String> values, List<String> ignoredCards) {
        appendPhotos(values);
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
        return new Holder(ItemWkDatingPhotoSlotBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        // 3 列瀑布流：第 1 张纵向占满两行，其余 4 张组成右侧 2x2，正好 5 个位置。
        ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
        int targetHeight = dp(holder.itemView, position == 0 ? 218 : 104);
        if (params != null && params.height != targetHeight) {
            params.height = targetHeight;
            holder.itemView.setLayoutParams(params);
        }
        boolean occupied = position < photos.size();
        String preview = occupied ? photos.get(position) : "";
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
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION) return;
            if (current < photos.size()) {
                if (listener != null) listener.onPreviewPhoto(current, photos.get(current));
            } else if (listener != null) {
                listener.onAddPhoto();
            }
        });
        holder.binding.deleteBtn.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION && current < photos.size() && listener != null) {
                listener.onDeletePhoto(current, photos.get(current));
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION && current < photos.size() && listener != null) {
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
