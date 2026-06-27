package com.chat.partnerbrowse;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.chat.partnerbrowse.model.PartnerBrowseBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<String, Long> keyToId = new HashMap<>();
    private long nextId = 1L;

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
        PartnerBrowseBean bean = itemAt(position);
        if (bean == null) return RecyclerView.NO_ID;
        String key = bean.getStableKey();
        if (TextUtils.isEmpty(key)) return RecyclerView.NO_ID;
        return idForKey(key);
    }

    @Override
    public boolean containsItem(long itemId) {
        if (itemId == RecyclerView.NO_ID || list == null) return false;
        for (PartnerBrowseBean item : list) {
            if (item == null) continue;
            String key = item.getStableKey();
            if (!TextUtils.isEmpty(key) && idForKey(key) == itemId) return true;
        }
        return false;
    }

    private PartnerBrowseBean itemAt(int position) {
        if (list == null || position < 0 || position >= list.size()) return null;
        return list.get(position);
    }

    private long idForKey(String key) {
        Long old = keyToId.get(key);
        if (old != null) return old;
        long id = nextId++;
        if (id == RecyclerView.NO_ID) id = nextId++;
        keyToId.put(key, id);
        return id;
    }
}
