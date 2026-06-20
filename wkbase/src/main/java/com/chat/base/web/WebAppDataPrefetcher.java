package com.chat.base.web;

import android.os.Build;
import android.text.TextUtils;
import android.webkit.WebView;

/**
 * Page-side prefetch bootstrap for NodeBB WebView tabs.
 *
 * It runs inside the page origin, so fetch() carries the WebView's NodeBB
 * cookies. The fetched data is saved to window.__PEIPE_NATIVE_PREFETCH__ and
 * sessionStorage, where the NodeBB plugin can consume it before issuing its own
 * first feed request.
 */
public class WebAppDataPrefetcher {
    private WebAppDataPrefetcher() {
    }

    public static void inject(WebView webView) {
        if (webView == null) return;
        String js = "(function(){" +
                "if(window.__PeipeNativePrefetchInstalled)return;" +
                "window.__PeipeNativePrefetchInstalled=true;" +
                "window.__PEIPE_NATIVE_PREFETCH__=window.__PEIPE_NATIVE_PREFETCH__||{};" +
                "function fire(key,data){try{window.dispatchEvent(new CustomEvent('PeipeNativePrefetch',{detail:{key:key,data:data}}));}catch(e){}}" +
                "function save(key,data){var entry={time:Date.now(),data:data};window.__PEIPE_NATIVE_PREFETCH__[key]=entry;try{sessionStorage.setItem('__PEIPE_NATIVE_PREFETCH__:'+key,JSON.stringify(entry));}catch(e){}fire(key,data);}" +
                "function prefetch(url,key){try{fetch(url,{credentials:'same-origin',headers:{accept:'application/json','x-requested-with':'XMLHttpRequest'}}).then(function(res){return res.json().catch(function(){return{};}).then(function(json){return json&&json.response?json.response:json;});}).then(function(json){if(json&&json.ok!==false)save(key,json);}).catch(function(){});}catch(e){}}" +
                "var path=String(location.pathname||'');var mode=/nearby/i.test(path)?'nearby':'recommend';" +
                "if(/\\/partners\\/swipe/i.test(path)){" +
                "prefetch('/api/peipe-partners/swipe/feed?mode='+encodeURIComponent(mode)+'&limit=36','peipePartnersFeed:'+mode);" +
                "prefetch('/api/peipe-swipe/swipe/feed?mode='+encodeURIComponent(mode)+'&limit=36','peipeSwipeFeed:'+mode);" +
                "prefetch('/api/peipe-partners/swipe/me','peipePartnersMe');" +
                "}" +
                "if(/\\/video/i.test(path)){" +
                "prefetch('/api/peipe-video/feed?limit=24','peipeVideoFeed');" +
                "prefetch('/api/peipe-video/list?limit=24','peipeVideoList');" +
                "}" +
                "})();";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(js, null);
        } else if (!TextUtils.isEmpty(js)) {
            webView.loadUrl("javascript:" + js);
        }
    }
}
