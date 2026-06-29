package com.chat.partnerbrowse;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.partnerbrowse.R;
import com.chat.partnerbrowse.databinding.ActivityWkPartnerBrowseBinding;
import com.chat.partnerbrowse.model.PartnerBrowseBean;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PartnerBrowseActivity extends WKBaseActivity<ActivityWkPartnerBrowseBinding> {
    private final ArrayList<PartnerBrowseBean> partners = new ArrayList<>();
    private PartnerOuterAdapter adapter;
    private PartnerBrowseLocationManager locationManager;
    private boolean loading;
    private boolean checkingProfile;
    private boolean profileGatePassed;
    private boolean profileRequired;
    private boolean profileEditOpened;
    private boolean noMore;
    private int duplicatePageCount;
    private int page = 1;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;

    @Override
    protected ActivityWkPartnerBrowseBinding getViewBinding() {
        return ActivityWkPartnerBrowseBinding.inflate(getLayoutInflater());
    }

    @Override
    public boolean supportSlideBack() {
        return false;
    }

    @Override
    protected void setTitle(TextView titleTv) {
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(0x00000000);
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView() {
        locationManager = new PartnerBrowseLocationManager(this);
        PartnerRepository.resetPaging();
        adapter = new PartnerOuterAdapter(this, partners);
        wkVBinding.viewPagerOuter.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        wkVBinding.viewPagerOuter.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wkVBinding.viewPagerOuter.setOffscreenPageLimit(1);
        wkVBinding.viewPagerOuter.setSaveEnabled(false);
        wkVBinding.viewPagerOuter.setAdapter(adapter);

        View inner = wkVBinding.viewPagerOuter.getChildCount() > 0 ? wkVBinding.viewPagerOuter.getChildAt(0) : null;
        if (inner instanceof RecyclerView) {
            ((RecyclerView) inner).setItemViewCacheSize(2);
            inner.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        showLoading(true, "");
        updateLocationPrompt();
    }

    @Override
    protected void initListener() {
        wkVBinding.retryBtn.setOnClickListener(v -> {
            if (profileRequired) {
                openProfileEditOnce(true);
                return;
            }
            page = 1;
            noMore = false;
            duplicatePageCount = 0;
            profileGatePassed = false;
            profileRequired = false;
            profileEditOpened = false;
            PartnerRepository.resetPaging();
            partners.clear();
            adapter.notifyDataSetChanged();
            ensureProfileThenLoad();
        });
        wkVBinding.locationPrompt.setOnClickListener(v -> {
            if (locationManager != null) locationManager.requestPermission(this);
        });
        wkVBinding.locationPromptClose.setOnClickListener(v -> {
            if (locationManager != null) locationManager.suppressPromptTemporarily();
            wkVBinding.locationPrompt.setVisibility(View.GONE);
        });
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                PartnerImagePreloader.preloadNextUser(PartnerBrowseActivity.this, partners, position);
                if (profileGatePassed && !noMore && position >= partners.size() - 3) loadMore(false);
            }
        };
        wkVBinding.viewPagerOuter.registerOnPageChangeCallback(pageChangeCallback);
    }

    @Override
    protected void initData() {
        ensureProfileThenLoad();
        if (locationManager != null) locationManager.maybeUpdateLocation(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLocationPrompt();
        if (locationManager != null && locationManager.hasLocationPermission()) {
            locationManager.maybeUpdateLocation(false);
        }
        if (!profileGatePassed && !checkingProfile && partners.isEmpty()) {
            ensureProfileThenLoad();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PartnerBrowseLocationManager.REQUEST_LOCATION_PERMISSION && locationManager != null) {
            updateLocationPrompt();
            if (locationManager.hasLocationPermission()) {
                locationManager.maybeUpdateLocation(true);
            } else {
                locationManager.suppressPromptTemporarily();
                wkVBinding.locationPrompt.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (wkVBinding != null && pageChangeCallback != null) {
                wkVBinding.viewPagerOuter.unregisterOnPageChangeCallback(pageChangeCallback);
            }
            if (wkVBinding != null) wkVBinding.viewPagerOuter.setAdapter(null);
        } catch (Throwable ignored) {
        }
        pageChangeCallback = null;
        super.onDestroy();
    }

    private void ensureProfileThenLoad() {
        if (checkingProfile || profileGatePassed) {
            if (profileGatePassed && partners.isEmpty()) loadMore(true);
            return;
        }
        checkingProfile = true;
        profileRequired = false;
        showLoading(true, getString(R.string.partnerbrowse_checking_profile));
        PartnerBrowseModel.getInstance().getPartnerProfileMe((code, msg, data) -> {
            if (isFinishing() || isDestroyed()) return;
            checkingProfile = false;
            if (code == HttpResponseCode.success && data != null && data.hasPartnerPhoto()) {
                profileGatePassed = true;
                profileRequired = false;
                loadMore(true);
                return;
            }
            if (code == HttpResponseCode.success && data != null) {
                showProfileRequiredGate(true);
            } else {
                profileGatePassed = false;
                profileRequired = false;
                showLoading(false, TextUtils.isEmpty(msg) ? getString(R.string.partnerbrowse_profile_check_failed) : msg);
            }
        });
    }

    private void showProfileRequiredGate(boolean openEditor) {
        profileGatePassed = false;
        profileRequired = true;
        partners.clear();
        adapter.notifyDataSetChanged();
        wkVBinding.viewPagerOuter.setVisibility(View.GONE);
        wkVBinding.loadingLayout.setVisibility(View.VISIBLE);
        wkVBinding.loadingTv.setText(R.string.partnerbrowse_photo_required_tip);
        wkVBinding.retryBtn.setText(R.string.partnerbrowse_go_upload_photo);
        wkVBinding.retryBtn.setVisibility(View.VISIBLE);
        if (openEditor) openProfileEditOnce(false);
    }

    private void openProfileEditOnce(boolean force) {
        if (!force && profileEditOpened) return;
        profileEditOpened = true;
        PartnerBrowseHostBridge.openProfileEdit(this);
    }

    private void updateLocationPrompt() {
        if (wkVBinding == null || locationManager == null) return;
        wkVBinding.locationPrompt.setVisibility(locationManager.shouldShowSoftPrompt() ? View.VISIBLE : View.GONE);
    }

    private void loadMore(boolean first) {
        if (loading || noMore || !profileGatePassed) return;
        loading = true;
        if (first && partners.isEmpty()) showLoading(true, "");
        PartnerRepository.loadPartners(page, 18, (newList, errorMsg) -> {
            if (isFinishing() || isDestroyed()) return;
            loading = false;
            noMore = PartnerRepository.isReachedEnd();
            if (newList == null || newList.isEmpty()) {
                if (partners.isEmpty()) {
                    showLoading(false, TextUtils.isEmpty(errorMsg) ? getString(R.string.partnerbrowse_empty) : errorMsg);
                }
                return;
            }
            int start = partners.size();
            int inserted = appendUnique(newList);
            page++;
            if (inserted > 0) {
                duplicatePageCount = 0;
                adapter.notifyItemRangeInserted(start, inserted);
                showContent();
            } else {
                duplicatePageCount++;
                if (duplicatePageCount >= 2) noMore = true;
                if (partners.isEmpty()) {
                    showLoading(false, getString(R.string.partnerbrowse_empty));
                }
            }
        });
    }

    private int appendUnique(List<PartnerBrowseBean> list) {
        int before = partners.size();
        HashSet<String> existKeys = new HashSet<>(partners.size() * 2 + 8);
        for (PartnerBrowseBean old : partners) {
            if (old != null) existKeys.add(old.getStableKey());
        }
        for (PartnerBrowseBean item : list) {
            if (item == null || !item.hasPartnerPhoto()) continue;
            String key = item.getStableKey();
            if (existKeys.add(key)) partners.add(item);
        }
        return partners.size() - before;
    }

    private void showContent() {
        wkVBinding.loadingLayout.setVisibility(View.GONE);
        wkVBinding.viewPagerOuter.setVisibility(View.VISIBLE);
        updateLocationPrompt();
    }

    private void showLoading(boolean loading, String msg) {
        wkVBinding.viewPagerOuter.setVisibility(View.GONE);
        wkVBinding.loadingLayout.setVisibility(View.VISIBLE);
        if (TextUtils.isEmpty(msg)) msg = loading ? getString(R.string.partnerbrowse_loading) : "";
        wkVBinding.loadingTv.setText(msg);
        wkVBinding.retryBtn.setText(R.string.partnerbrowse_retry);
        wkVBinding.retryBtn.setVisibility(loading ? View.GONE : View.VISIBLE);
    }
}
