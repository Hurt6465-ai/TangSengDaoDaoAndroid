package com.chat.dating;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.dating.databinding.ItemWkDatingPhotoPageBinding;

import java.util.ArrayList;
import java.util.List;

/** 详情页使用主图，保留 1080/1440 清晰度，不复用推荐页 720 派生图。 */
public final class DatingPhotoPagerAdapter extends RecyclerView.Adapter<DatingPhotoPagerAdapter.Holder> {
    private final ArrayList<String> photos = new ArrayList<>();

    public void setPhotos(List<String> values) {
        photos.clear();
        if (values != null) photos.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWkDatingPhotoPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Glide.with(holder.itemView)
                .load(DatingImageSource.resolve(holder.itemView.getContext(), photos.get(position)))
                .thumbnail(0.12f)
                .override(1080, 1440)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(holder.binding.photoIv);
    }

    @Override public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.itemView).clear(holder.binding.photoIv);
        holder.binding.photoIv.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    @Override public int getItemCount() { return photos.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemWkDatingPhotoPageBinding binding;
        Holder(ItemWkDatingPhotoPageBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
