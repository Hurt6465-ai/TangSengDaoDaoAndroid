package com.chat.partnerbrowse;

import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.base.net.IRequestResultListener;
import com.chat.partnerbrowse.model.PartnerBrowseBean;
import com.chat.partnerbrowse.model.PartnerBrowseResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for the fullscreen partner browser.
 *
 * Cursor/page are page-session state. PartnerBrowseActivity must call resetPaging() for every fresh
 * first page, otherwise a later open may continue from an old cursor.
 */
public final class PartnerRepository {
    private static final int MAX_CACHE_SIZE = 300;

    private static final LinkedHashMap<String, PartnerBrowseBean> CACHE = new LinkedHashMap<String, PartnerBrowseBean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PartnerBrowseBean> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    private static String cursor = "";
    private static int round = 1;
    private static boolean reachedEnd = false;

    private PartnerRepository() {
    }

    public static synchronized void resetPaging() {
        cursor = "";
        round = 1;
        reachedEnd = false;
    }

    public static synchronized boolean isReachedEnd() {
        return reachedEnd;
    }

    public static synchronized PartnerBrowseBean getPartnerFromCache(String key) {
        if (TextUtils.isEmpty(key)) return null;
        return CACHE.get(key);
    }

    public static synchronized void putOne(PartnerBrowseBean item) {
        if (item == null) return;
        String key = item.getStableKey();
        if (!TextUtils.isEmpty(key)) CACHE.put(key, item);
        if (!TextUtils.isEmpty(item.uid) && !TextUtils.equals(item.uid, key)) CACHE.put(item.uid, item);
        if (!TextUtils.isEmpty(item.id) && !TextUtils.equals(item.id, key)) CACHE.put(item.id, item);
    }

    public static synchronized void putAll(List<PartnerBrowseBean> list) {
        if (list == null) return;
        for (PartnerBrowseBean item : list) putOne(item);
    }

    public static void loadPartners(int page, int limit, Callback callback) {
        final String requestCursor;
        synchronized (PartnerRepository.class) {
            if (reachedEnd) {
                if (callback != null) callback.onResult(new ArrayList<>(), "");
                return;
            }
            requestCursor = cursor;
        }

        if (PartnerBrowseConfig.DEBUG_MOCK) {
            List<PartnerBrowseBean> mock = PartnerBrowseMockData.create();
            rank(mock);
            putAll(mock);
            synchronized (PartnerRepository.class) {
                reachedEnd = true;
            }
            if (callback != null) callback.onResult(mock, "");
            return;
        }

        PartnerBrowseModel.getInstance().listPartners(requestCursor, page, limit, new IRequestResultListener<PartnerBrowseResponse>() {
            @Override
            public void onSuccess(PartnerBrowseResponse result) {
                List<PartnerBrowseBean> list = result == null ? new ArrayList<>() : result.getListSafe();
                synchronized (PartnerRepository.class) {
                    String newCursor = result == null ? "" : result.cursor;
                    boolean hasCursor = !TextUtils.isEmpty(newCursor);
                    if (hasCursor) {
                        boolean cursorAdvanced = !TextUtils.equals(newCursor, cursor);
                        if (cursorAdvanced) cursor = newCursor;
                        if (list.isEmpty() || !cursorAdvanced) reachedEnd = true;
                    } else {
                        // Page-mode backend: no cursor is normal. Use page size to infer the end.
                        if (list.isEmpty() || (limit > 0 && list.size() < limit)) reachedEnd = true;
                    }
                }
                rank(list);
                putAll(list);
                if (callback != null) callback.onResult(list, "");
            }

            @Override
            public void onFail(int code, String msg) {
                if (PartnerBrowseConfig.FALLBACK_MOCK_ON_ERROR) {
                    List<PartnerBrowseBean> mock = PartnerBrowseMockData.create();
                    rank(mock);
                    putAll(mock);
                    synchronized (PartnerRepository.class) {
                        reachedEnd = true;
                    }
                    if (callback != null) callback.onResult(mock, "");
                    return;
                }
                if (callback != null) callback.onResult(new ArrayList<>(), msg);
            }
        });
    }

    public static synchronized void resetRound() {
        round++;
    }

    private static void rank(List<PartnerBrowseBean> list) {
        if (list == null) return;
        String uid = WKConfig.getInstance().getUid();
        final int currentRound;
        synchronized (PartnerRepository.class) {
            currentRound = round;
        }
        for (PartnerBrowseBean item : list) PartnerRecommendScorer.score(item, uid, currentRound);
        Collections.sort(list, new Comparator<PartnerBrowseBean>() {
            @Override
            public int compare(PartnerBrowseBean a, PartnerBrowseBean b) {
                return Double.compare(b == null ? -9999 : b.score, a == null ? -9999 : a.score);
            }
        });
    }

    public interface Callback {
        void onResult(List<PartnerBrowseBean> list, String errorMsg);
    }
}
