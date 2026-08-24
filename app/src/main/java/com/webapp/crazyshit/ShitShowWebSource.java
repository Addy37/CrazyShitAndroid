package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dedicated extractor for CrazyShit's JavaScript-driven Shit Show swipe feed.
 *
 * Shit Show is not a normal /cnt/medias/ listing, so the Jsoup feed parser intentionally does not
 * try to understand it. This source briefly boots the real page in an invisible WebView, observes
 * the rendered video elements and media requests, then hands direct playable URLs to native Chaos.
 * The WebView is destroyed after each harvest and is never shown to the user.
 */
final class ShitShowWebSource {
    private static final String PAGE = CrazyShitRepository.BASE + "shitshow/";
    private static final int TARGET_CACHE = 24;
    private static final int MAX_CACHE = 80;
    private static final long HARVEST_TIMEOUT_MS = 24_000L;
    private static final long PUMP_MS = 1_250L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final ArrayDeque<NativeContentItem> ready = new ArrayDeque<>();
    private final Set<String> seenMedia = new HashSet<>();

    private boolean warming;
    private WebView webView;
    private long harvestStartedAt;

    List<NativeContentItem> takeBatchOrWarm(Context context, int maxItems) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        synchronized (lock) {
            int count = Math.max(0, maxItems);
            while (count-- > 0 && !ready.isEmpty()) result.add(ready.removeFirst());
        }
        if (cachedCount() < 8) warm(context);
        return result;
    }

    void resetDeck() {
        // Do not forget harvested URLs here. Chaos refresh already keeps its session URL set, and
        // retaining this source history prevents the hidden page from immediately dealing the same
        // Shit Show clips back into the mixer.
        warmIfPossible();
    }

    private int cachedCount() {
        synchronized (lock) {
            return ready.size();
        }
    }

    private void warm(Context context) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        synchronized (lock) {
            if (warming) return;
            warming = true;
        }
        main.post(() -> startHarvest(activity));
    }

    private void warmIfPossible() {
        WebView current = webView;
        if (current == null) return;
        Context context = current.getContext();
        if (context instanceof Activity) warm(context);
    }

    private void startHarvest(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            setWarming(false);
            return;
        }

        destroyWebView();
        harvestStartedAt = System.currentTimeMillis();

        WebView view = new WebView(activity);
        webView = view;
        view.setAlpha(0f);
        view.layout(0, 0, dp(activity, 360), dp(activity, 640));

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        try {
            settings.setUserAgentString(WebSettings.getDefaultUserAgent(activity));
        } catch (Exception ignored) {
        }

        view.addJavascriptInterface(new Bridge(), "CSShitBridge");
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView web, String url) {
                installProbe(web);
                schedulePump(web);
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView web,
                    WebResourceRequest request
            ) {
                if (request != null && request.getUrl() != null) {
                    observeSameSiteResource(request.getUrl().toString());
                }
                return null;
            }
        });
        view.loadUrl(PAGE);

        // A page-load callback can be delayed by ad/network resources. Start a fallback pump too.
        main.postDelayed(() -> {
            if (webView != view || !isWarming()) return;
            installProbe(view);
            schedulePump(view);
        }, 3_500L);
    }

    private void installProbe(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(PROBE_JS, null);
    }

    private void schedulePump(WebView view) {
        main.removeCallbacksAndMessages(view);
        main.postAtTime(() -> pump(view), view, android.os.SystemClock.uptimeMillis() + 300L);
    }

    private void pump(WebView view) {
        if (view == null || webView != view || !isWarming()) return;

        view.evaluateJavascript(SCAN_AND_ADVANCE_JS, null);
        try {
            view.pageDown(false);
        } catch (Exception ignored) {
        }

        boolean enough = cachedCount() >= TARGET_CACHE;
        boolean timedOut = System.currentTimeMillis() - harvestStartedAt >= HARVEST_TIMEOUT_MS;
        if (enough || timedOut) {
            finishHarvest(view);
            return;
        }
        main.postAtTime(() -> pump(view), view,
                android.os.SystemClock.uptimeMillis() + PUMP_MS);
    }

    private void finishHarvest(WebView view) {
        if (view != null) main.removeCallbacksAndMessages(view);
        if (webView == view) destroyWebView();
        setWarming(false);
    }

    private void destroyWebView() {
        WebView old = webView;
        webView = null;
        if (old == null) return;
        try {
            main.removeCallbacksAndMessages(old);
            old.stopLoading();
            old.loadUrl("about:blank");
            old.removeJavascriptInterface("CSShitBridge");
            old.setWebViewClient(null);
            old.destroy();
        } catch (Exception ignored) {
        }
    }

    private void observeSameSiteResource(String url) {
        if (!isDirectMedia(url) || !isCrazyShitHost(url)) return;
        enqueue(url, "", "Shit Show", PAGE);
    }

    private void enqueue(String mediaUrl, String posterUrl, String title, String pageUrl) {
        String media = cleanUrl(mediaUrl);
        if (!isDirectMedia(media)) return;

        String poster = cleanUrl(posterUrl);
        String label = cleanTitle(title);
        String page = cleanUrl(pageUrl);
        if (page.isEmpty()) page = PAGE;

        synchronized (lock) {
            if (!seenMedia.add(media)) return;
            while (ready.size() >= MAX_CACHE) ready.removeLast();
            ready.addLast(new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    label,
                    media,
                    poster,
                    "",
                    "Shit Show",
                    ""
            ));
        }
    }

    private static String cleanTitle(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || text.equalsIgnoreCase("Swipe up for next video")) return "Shit Show";
        if (text.length() > 120) text = text.substring(0, 120).trim();
        return text;
    }

    private static String cleanUrl(String value) {
        if (value == null) return "";
        String url = value.trim().replace("\\/", "/");
        if (url.startsWith("//")) return "https:" + url;
        return url;
    }

    private static boolean isDirectMedia(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) return false;
        return lower.contains(".mp4")
                || lower.contains(".webm")
                || lower.contains(".m4v")
                || lower.contains(".m3u8")
                || lower.contains(".mpd");
    }

    private static boolean isCrazyShitHost(String value) {
        try {
            String host = Uri.parse(value).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            return host.equals("crazyshit.com") || host.endsWith(".crazyshit.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isWarming() {
        synchronized (lock) {
            return warming;
        }
    }

    private void setWarming(boolean value) {
        synchronized (lock) {
            warming = value;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class Bridge {
        @JavascriptInterface
        public void onClip(String raw) {
            try {
                JSONObject json = new JSONObject(raw == null ? "{}" : raw);
                enqueue(
                        json.optString("media", ""),
                        json.optString("poster", ""),
                        json.optString("title", "Shit Show"),
                        json.optString("page", PAGE)
                );
            } catch (Exception ignored) {
            }
        }
    }

    private static final String PROBE_JS =
            "(function(){" +
            "if(window.__csShitShowProbe){window.__csShitShowScan&&window.__csShitShowScan();return;}" +
            "window.__csShitShowProbe=true;" +
            "function abs(v){try{return v?new URL(v,location.href).href:'';}catch(e){return v||'';}}" +
            "function titleFor(v,r){try{" +
            "var t=v.getAttribute('title')||v.getAttribute('aria-label')||v.getAttribute('data-title')||'';" +
            "if(!t&&r){var e=r.querySelector('[data-title],.title,.caption,h1,h2,h3,h4');" +
            "if(e)t=e.getAttribute('data-title')||e.textContent||'';}" +
            "return (t||'').replace(/\\s+/g,' ').trim();}catch(e){return '';}}" +
            "function send(v){try{" +
            "v.muted=true;v.preload='auto';" +
            "var s=v.currentSrc||v.src||'';if(!s){var q=v.querySelector('source[src]');if(q)s=q.src||q.getAttribute('src')||'';}" +
            "if(!s)return;" +
            "var r=v.closest('[data-id],[data-video-id],article,section,.swiper-slide,.slide,.item')||v.parentElement;" +
            "var a=(r&&r.querySelector('a[href]'))||v.closest('a[href]');" +
            "var p=v.poster||v.getAttribute('poster')||'';" +
            "CSShitBridge.onClip(JSON.stringify({media:abs(s),poster:abs(p),title:titleFor(v,r),page:a?abs(a.href):location.href}));" +
            "try{var pr=v.play();if(pr&&pr.catch)pr.catch(function(){});}catch(e){}" +
            "}catch(e){}}" +
            "function scan(){try{document.querySelectorAll('video').forEach(send);" +
            "performance.getEntriesByType('resource').forEach(function(e){var u=e.name||'';" +
            "if(/\\.(mp4|webm|m4v|m3u8|mpd)(\\?|$)/i.test(u))CSShitBridge.onClip(JSON.stringify({media:u,title:'Shit Show',page:location.href}));});" +
            "}catch(e){}}" +
            "window.__csShitShowScan=scan;" +
            "new MutationObserver(scan).observe(document.documentElement||document,{subtree:true,childList:true,attributes:true,attributeFilter:['src','poster']});" +
            "setInterval(scan,700);scan();" +
            "})();";

    private static final String SCAN_AND_ADVANCE_JS =
            "(function(){try{" +
            "window.__csShitShowScan&&window.__csShitShowScan();" +
            "var d=Math.max(window.innerHeight||0,600);" +
            "window.dispatchEvent(new WheelEvent('wheel',{deltaY:d,bubbles:true,cancelable:true}));" +
            "document.dispatchEvent(new WheelEvent('wheel',{deltaY:d,bubbles:true,cancelable:true}));" +
            "window.scrollBy(0,Math.max(420,d*0.92));" +
            "document.querySelectorAll('video').forEach(function(v){try{v.muted=true;v.preload='auto';}catch(e){}});" +
            "}catch(e){}})();";
}
