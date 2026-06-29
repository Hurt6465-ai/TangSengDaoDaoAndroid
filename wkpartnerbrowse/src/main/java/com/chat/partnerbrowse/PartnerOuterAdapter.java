package com.chat.partnerbrowse;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.chat.partnerbrowse.model.PartnerBrowseBean;

import java.util.List;

/**
 * Outer vertical pager adapter.
 *
 * Stable ids are allocated per stable key for this adapter lifecycle instead of hashing, so two
 * different partner keys cannot collide inside the same ViewPager2 adapter. The fragment also gets
 * a small snapshot of the bean, so if the Repository LRU cache is missed after process pressure or
 * fast recreation, the page can still render instead of black-screening.
 */
public class PartnerOuterAdapter extends FragmentStateAdapter {
    private final List<PartnerBrowseBean> list;
    public PartnerOuterAdapter(@NonNull FragmentActivity activity, List<PartnerBrowseBean> list) {
        super(activity);
        this.list = list;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        PartnerBrowseBean bean = itemAt(position);
        return PartnerDetailFragment.newInstance(bean == null ? "" : bean.getStableKey(), bean);
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public long getItemId(int position) {
        // Use position ids so the same partner can appear again in a local recycle cycle
        // without FragmentStateAdapter treating the duplicate as the same fragment.
        return position < 0 ? RecyclerView.NO_ID : position + 1L;
    }

    @Override
    public boolean containsItem(long itemId) {
        return itemId != RecyclerView.NO_ID && list != null && itemId >= 1 && itemId <= list.size();
    }

    private PartnerBrowseBean itemAt(int position) {
        if (list == null || position < 0 || position >= list.size()) return null;
        return list.get(position);
    }
}
