package com.chat.partnerbrowse;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.chat.partnerbrowse.model.PartnerBrowseProfileMe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class PartnerBrowseActivity extends WKBaseActivity<ActivityWkPartnerBrowseBinding> {
    private static final int PAGE_LIMIT = 12;
    private static final int EXPOSURE_DELAY_MS = 700;
    private static final int MIN_SKIP_REPORT_MS = 120;
    private static final int EXPOSURE_BATCH_SIZE = 5;
    private static final int LOCAL_RECYCLE_BATCH_SIZE = 8;
    private static final int LOCAL_RECYCLE_MAX_ITEMS = 72;
    private static final int LOCAL_RECYCLE_MIN_GAP = 20;

    private final ArrayList<PartnerBrowseBean> partners = new ArrayList<>();
    private PartnerOuterAdapter adapter;
    private PartnerBrowseLocationManager locationManager;
    private boolean loading;
    private boolean checkingProfile;
    private boolean profileGatePassed;
    private boolean profileRequired;
    private boolean profileEditOpened;
    private boolean noMore;
    private boolean autoPermissionCheckedThisOpen;
    private int duplicatePageCount;
    private int page = 1;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private final Handler exposureHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Map<String, Object>> pendingExposures = new ArrayList<>();
    private final HashSet<String> exposedKeys = new HashSet<>();
    private int currentExposurePosition = -1;
    private long currentExposureStartMs;
    private boolean currentExposureQualified;

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
            exposedKeys.clear();
            pendingExposures.clear();
            resetExposureTracking();
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
                finishCurrentExposure(false);
                PartnerImagePreloader.preloadNextUser(PartnerBrowseActivity.this, partners, position);
                startExposureTracking(position);
                if (profileGatePassed && position >= partners.size() - 3) {
                    if (noMore) appendLocalCycleIfNeeded();
                    else loadMore(false);
                }
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
        } else if (profileGatePassed && currentExposurePosition < 0 && !partners.isEmpty()) {
            startExposureTracking(wkVBinding.viewPagerOuter.getCurrentItem());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PartnerBrowseLocationManager.REQUEST_LOCATION_PERMISSION && locationManager != null) {
            if (locationManager.hasLocationPermission()) {
                locationManager.markPermissionGranted();
                locationManager.maybeUpdateLocation(true);
            } else {
                locationManager.markPermissionDenied();
                locationManager.suppressPromptTemporarily();
            }
            updateLocationPrompt();
        }
    }

    @Override
    protected void onPause() {
        finishCurrentExposure(true);
        flushExposures();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            finishCurrentExposure(true);
            flushExposures();
            if (wkVBinding != null && pageChangeCallback != null) {
                wkVBinding.viewPagerOuter.unregisterOnPageChangeCallback(pageChangeCallback);
            }
            if (wkVBinding != null) wkVBinding.viewPagerOuter.setAdapter(null);
        } catch (Throwable ignored) {
        }
        pageChangeCallback = null;
        resetExposureTracking();
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
            if (code == HttpResponseCode.success && data != null && data.hasPartnerPhoto() && data.hasPartnerLanguages()) {
                profileGatePassed = true;
                profileRequired = false;
                requestLocationPermissionOnEntryIfAllowed();
                if (locationManager != null && locationManager.hasLocationPermission()) {
                    locationManager.maybeUpdateLocation(false);
                }
                loadMore(true);
                return;
            }
            if (code == HttpResponseCode.success && data != null) {
                showProfileRequiredGate(true, profileRequiredMessage(data));
            } else {
                profileGatePassed = false;
                profileRequired = false;
                showLoading(false, TextUtils.isEmpty(msg) ? getString(R.string.partnerbrowse_profile_check_failed) : msg);
            }
        });
    }

    private String profileRequiredMessage(PartnerBrowseProfileMe data) {
        if (data == null) return localProfileRequiredTip();
        boolean hasPhoto = data.hasPartnerPhoto();
        boolean hasLanguages = data.hasPartnerLanguages();
        if (!hasPhoto && !hasLanguages) return localProfileRequiredTip();
        if (!hasPhoto) return getString(R.string.partnerbrowse_photo_required_tip);
        if (!hasLanguages) return localLanguageRequiredTip();
        return localProfileRequiredTip();
    }

    private String localLanguageRequiredTip() {
        String lang = getCurrentLanguage();
        if ("my".equals(lang) || "myanmar".equals(lang) || "burmese".equals(lang)) {
            return "ဘာသာဖော်စာမျက်နှာ မကြည့်မီ မိခင်ဘာသာစကားနှင့် လေ့လာနေသော ဘာသာစကားကို ရွေးပါ။ Tag မဖြည့်လည်း ရပါတယ်။";
        }
        if ("en".equals(lang)) {
            return "Choose your native language and learning language before browsing. Tags are optional.";
        }
        return "进入语伴前，请先选择母语和正在学习的语言。标签可以不填。";
    }

    private String localProfileRequiredTip() {
        String lang = getCurrentLanguage();
        if ("my".equals(lang) || "myanmar".equals(lang) || "burmese".equals(lang)) {
            return "ဘာသာဖော်စာမျက်နှာ မကြည့်မီ ဓာတ်ပုံ၊ မိခင်ဘာသာစကားနှင့် လေ့လာနေသော ဘာသာစကားကို ဖြည့်ပါ။";
        }
        if ("en".equals(lang)) {
            return "Complete your partner photo, native language and learning language before browsing.";
        }
        return "进入语伴前，请先补全语伴照片、母语和正在学习的语言。";
    }

    private String getCurrentLanguage() {
        try {
            Locale locale;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locale = getResources().getConfiguration().getLocales().get(0);
            } else {
                locale = getResources().getConfiguration().locale;
            }
            return locale == null || locale.getLanguage() == null ? "" : locale.getLanguage().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void showProfileRequiredGate(boolean openEditor, String message) {
        profileGatePassed = false;
        profileRequired = true;
        partners.clear();
        adapter.notifyDataSetChanged();
        wkVBinding.viewPagerOuter.setVisibility(View.GONE);
        wkVBinding.loadingLayout.setVisibility(View.VISIBLE);
        wkVBinding.loadingTv.setText(TextUtils.isEmpty(message) ? localProfileRequiredTip() : message);
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

    /**
     * 语伴页只允许“受控自动弹一次”：
     * 1. 已授权：不弹，后台静默定位。
     * 2. 首次未授权：可以自动弹一次系统权限。
     * 3. 拒绝过或近期问过：冷却期内不再自动弹，只显示顶部弱提示。
     */
    private void requestLocationPermissionOnEntryIfAllowed() {
        if (autoPermissionCheckedThisOpen || locationManager == null) return;
        autoPermissionCheckedThisOpen = true;
        if (locationManager.requestPermissionOnFirstEntry(this)) {
            wkVBinding.locationPrompt.setVisibility(View.GONE);
        } else {
            updateLocationPrompt();
        }
    }

    private void loadMore(boolean first) {
        if (loading || noMore || !profileGatePassed) return;
        loading = true;
        if (first && partners.isEmpty()) showLoading(true, "");
        PartnerRepository.loadPartners(page, PAGE_LIMIT, (newList, errorMsg) -> {
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
                int current = wkVBinding.viewPagerOuter.getCurrentItem();
                if (current >= start && current < partners.size()) {
                    startExposureTracking(current);
                } else if (first && current >= 0 && current < partners.size()) {
                    startExposureTracking(current);
                }
            } else {
                duplicatePageCount++;
                if (duplicatePageCount >= 2) noMore = true;
                if (partners.isEmpty()) {
                    showLoading(false, getString(R.string.partnerbrowse_empty));
                }
            }
        });
    }

    private void startExposureTracking(int position) {
        if (!profileGatePassed || position < 0 || position >= partners.size()) return;
        if (currentExposurePosition == position && currentExposureStartMs > 0) return;
        resetExposureTracking();
        currentExposurePosition = position;
        currentExposureStartMs = System.currentTimeMillis();
        currentExposureQualified = false;
        exposureHandler.postDelayed(() -> {
            if (wkVBinding == null || currentExposurePosition != position) return;
            if (wkVBinding.viewPagerOuter.getCurrentItem() == position) {
                currentExposureQualified = true;
            }
        }, EXPOSURE_DELAY_MS);
    }

    private void resetExposureTracking() {
        currentExposurePosition = -1;
        currentExposureStartMs = 0L;
        currentExposureQualified = false;
        exposureHandler.removeCallbacksAndMessages(null);
    }

    private void finishCurrentExposure(boolean forceFlush) {
        if (currentExposurePosition < 0 || currentExposureStartMs <= 0) {
            if (forceFlush) flushExposures();
            return;
        }
        int position = currentExposurePosition;
        long startMs = currentExposureStartMs;
        boolean qualified = currentExposureQualified;
        resetExposureTracking();
        if (position < 0 || position >= partners.size()) {
            if (forceFlush) flushExposures();
            return;
        }
        PartnerBrowseBean item = partners.get(position);
        if (item == null) {
            if (forceFlush) flushExposures();
            return;
        }
        long duration = Math.max(0L, System.currentTimeMillis() - startMs);
        if (duration < MIN_SKIP_REPORT_MS) {
            if (forceFlush) flushExposures();
            return;
        }
        String eventType = (qualified || duration >= EXPOSURE_DELAY_MS) ? "expose" : "skip";
        String key = item.getStableKey();
        if ("expose".equals(eventType)) {
            if (TextUtils.isEmpty(key) || !exposedKeys.add(key)) {
                if (forceFlush) flushExposures();
                return;
            }
        }
        enqueuePartnerEvent(item, startMs, duration, eventType, 0);
        if (forceFlush || pendingExposures.size() >= EXPOSURE_BATCH_SIZE) flushExposures();
    }

    private void enqueuePartnerEvent(PartnerBrowseBean item, long seenAt, long duration, String eventType, int photoIndex) {
        if (item == null) return;
        String toUID = TextUtils.isEmpty(item.uid) ? item.id : item.uid;
        if (TextUtils.isEmpty(toUID)) return;
        Map<String, Object> exposure = new HashMap<>();
        exposure.put("to_uid", toUID);
        exposure.put("seen_at", seenAt <= 0 ? System.currentTimeMillis() : seenAt);
        exposure.put("duration_ms", Math.max(0L, duration));
        exposure.put("event_type", TextUtils.isEmpty(eventType) ? "expose" : eventType);
        exposure.put("source", "partner_browse");
        exposure.put("photo_index", Math.max(0, photoIndex));
        pendingExposures.add(exposure);
    }

    private void flushExposures() {
        if (pendingExposures.isEmpty()) return;
        ArrayList<Map<String, Object>> copy = new ArrayList<>(pendingExposures);
        pendingExposures.clear();
        PartnerBrowseModel.getInstance().reportExposures(copy, (code, msg, data) -> {
            if (code == HttpResponseCode.success || code == 200 || code == 0) return;
            if (!isFinishing() && !isDestroyed() && pendingExposures.size() < EXPOSURE_BATCH_SIZE * 2) {
                pendingExposures.addAll(0, copy);
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
            if (item == null || !item.hasPartnerPhoto() || !item.hasPartnerLanguages()) continue;
            String key = item.getStableKey();
            if (existKeys.add(key)) partners.add(item);
        }
        return partners.size() - before;
    }

    private void appendLocalCycleIfNeeded() {
        if (partners.isEmpty() || partners.size() >= LOCAL_RECYCLE_MAX_ITEMS) return;
        ArrayList<PartnerBrowseBean> copy = new ArrayList<>(partners);
        Collections.shuffle(copy);
        int current = wkVBinding == null ? partners.size() - 1 : wkVBinding.viewPagerOuter.getCurrentItem();
        int start = partners.size();
        int count = 0;
        for (PartnerBrowseBean item : copy) {
            if (item == null || item.follow == 1 || item.isHelloSent()) continue;
            if (isInRecentWindow(item, current, LOCAL_RECYCLE_MIN_GAP)) continue;
            partners.add(item);
            count++;
            if (count >= LOCAL_RECYCLE_BATCH_SIZE) break;
        }
        if (count == 0) {
            for (PartnerBrowseBean item : copy) {
                if (item == null || item.follow == 1 || item.isHelloSent()) continue;
                partners.add(item);
                count++;
                if (count >= Math.min(3, LOCAL_RECYCLE_BATCH_SIZE)) break;
            }
        }
        if (partners.size() > start) adapter.notifyItemRangeInserted(start, partners.size() - start);
    }

    private boolean isInRecentWindow(PartnerBrowseBean item, int current, int gap) {
        if (item == null || TextUtils.isEmpty(item.getStableKey())) return false;
        int from = Math.max(0, current - gap);
        int to = Math.min(partners.size() - 1, current + gap);
        for (int i = from; i <= to; i++) {
            PartnerBrowseBean old = partners.get(i);
            if (old != null && TextUtils.equals(old.getStableKey(), item.getStableKey())) return true;
        }
        return false;
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
