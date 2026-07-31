package com.chat.dating;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.dating.databinding.ItemWkDatingProfileGridBinding;
import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.List;

public final class DatingProfileGridAdapter extends RecyclerView.Adapter<DatingProfileGridAdapter.Holder> {
    public interface Listener {
        void onClick(DatingProfile profile, int position);
        void onLongClick(DatingProfile profile, int position);
    }

    private final ArrayList<DatingProfile> items = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<DatingProfile> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
    }

    public DatingProfile getItem(int position) {
        return position < 0 || position >= items.size() ? null : items.get(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWkDatingProfileGridBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DatingProfile profile = items.get(position);
        Glide.with(holder.itemView)
                .load(DatingImageSource.resolve(holder.itemView.getContext(), profile.firstPhoto()))
                .thumbnail(0.25f)
                .override(540, 720)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(holder.binding.photoIv);
        holder.binding.nameTv.setText(DatingUi.nameAgeFlag(profile));
        String meta = DatingUi.displayLocation(holder.itemView.getContext(), profile);
        holder.binding.metaTv.setText(meta);
        holder.binding.metaRow.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);
        holder.binding.onlineDot.setVisibility(profile.online == 1 ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> {
            int p = holder.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION && listener != null) listener.onClick(items.get(p), p);
        });
        holder.itemView.setOnLongClickListener(v -> {
            int p = holder.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION && listener != null) listener.onLongClick(items.get(p), p);
            return true;
        });
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.itemView).clear(holder.binding.photoIv);
        holder.binding.photoIv.setImageDrawable(null);
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemWkDatingProfileGridBinding binding;
        Holder(ItemWkDatingProfileGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
