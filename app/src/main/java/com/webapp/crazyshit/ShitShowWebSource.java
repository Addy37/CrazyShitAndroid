package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dedicated extractor for CrazyShit's JavaScript-driven Shit Show swipe feed.
 *
 * Shit Show is not a normal /cnt/medias/ listing. This source runs the real swipe page in a hidden
 * rendered WebView, watches the page and its media traffic, advances the real player, and hands
 * direct playable streams to native Chaos.
 */
final class ShitShowWebSource {
    private static final String TAG = "ShitShowWebSource";
    private static final String PAGE = CrazyShitRepository.BASE + "shitshow/";
    private static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final int TARGET_CACHE = 24;
    private static final int MAX_CACHE = 80;
    private static final long HARVEST_TIMEOUT_MS = 34_000L;
    private static final long PUMP_MS = 1_350L;
    private static final long FIRST_BATCH_WAIT_MS = 3_000L;

    private static final Pattern MEDIA_IN_PAYLOAD = Pattern.compile(
            "(?i)https?://[^\\s\\\"'<>]+?\\.(?:m3u8|mpd|mp4|webm|m4v)(?:\\?[^\\s\\\"'<>]*)?"
    );
    private static final Pattern PROTOCOL_RELATIVE_MEDIA_IN_PAYLOAD = Pattern.compile(
            "(?i)//[^\\s\\\"'<>]+?\\.(?:m3u8|mpd|mp4|webm|m4v)(?:\\?[^\\s\\\"'<>]*)?"
    );

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final ArrayDeque<NativeContentItem> ready = new ArrayDeque<>();
    private final Set<String> seenMedia = new HashSet<>();

    private boolean warming;
    private WebView webView;
    private long harvestStartedAt;
    private int pumpCount;
    private int observedRequests;
    private int observedPayloads;
    private int observedCandidates;

    void prewarm(Context context) {
        if (cachedCount() < 8) warm(context);
    }

    List<NativeContentItem> takeBatchOrWarm(Context context, int maxItems) {
        int requested = Math.max(0, maxItems);
        prewarm(context);

        ArrayList<NativeContentItem> result = drainReady(requested);
        if (!result.isEmpty() || requested == 0) return result;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            long deadline = SystemClock.uptimeMillis() + FIRST_BATCH_WAIT_MS;
            synchronized (lock) {
                while (ready.isEmpty() && warming) {
                    long remaining = deadline - SystemClock.uptimeMillis();
                    if (remaining <= 0L) break;
                    try {
                        lock.wait(remaining);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                while (requested-- > 0 && !ready.isEmpty()) result.add(ready.removeFirst());
            }
        }
        return result;
    }

    void resetDeck() {
        // Keep seenMedia for this process. Chaos keeps its own session repeat protection too.
    }

    private ArrayList<NativeContentItem> drainReady(int maxItems) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        synchronized (lock) {
            int count = Math.max(0, maxItems);
            while (count-- > 0 && !ready.isEmpty()) result.add(ready.removeFirst());
        }
        return result;
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

    private void startHarvest(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            setWarming(false);
            return;
        }

        destroyWebView();
        harvestStartedAt = System.currentTimeMillis();
        pumpCount = 0;
        observedRequests = 0;
        observedPayloads = 0;
        observedCandidates = 0;

        WebView view = new WebView(activity);
        webView = view;
        view.setAlpha(0.01f);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);

        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    dp(activity, 360), dp(activity, 640)
            );
            host.addView(view, 0, params);
        } else {
            view.layout(0, 0, dp(activity, 360), dp(activity, 640));
        }

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(MOBILE_USER_AGENT);

        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            cookies.setAcceptThirdPartyCookies(view, true);
        } catch (Exception ignored) {
        }

        view.addJavascriptInterface(new Bridge(), "CSShitBridge");
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageCommitVisible(WebView web, String url) {
                installProbe(web);
                schedulePump(web);
            }

            @Override
            public void onPageFinished(WebView web, String url) {
                installProbe(web);
                schedulePump(web);
            }

            @Override
            public void onLoadResource(WebView web, String url) {
                observeResource(url);
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView web,
                    WebResourceRequest request
            ) {
                observeRequest(request);
                return null;
            }
        });
        view.loadUrl(PAGE);

        main.postDelayed(() -> {
            if (webView != view || !isWarming()) return;
            installProbe(view);
            schedulePump(view);
        }, 2_500L);
    }

    private void installProbe(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(PROBE_JS, null);
    }

    private void schedulePump(WebView view) {
        main.removeCallbacksAndMessages(view);
        main.postAtTime(() -> pump(view), view, SystemClock.uptimeMillis() + 250L);
    }

    private void pump(WebView view) {
        if (view == null || webView != view || !isWarming()) return;

        pumpCount++;
        view.evaluateJavascript(SCAN_AND_ADVANCE_JS, null);
        dispatchNativeSwipe(view);
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
        main.postAtTime(() -> pump(view), view, SystemClock.uptimeMillis() + PUMP_MS);
    }

    private void dispatchNativeSwipe(WebView view) {
        try {
            float width = view.getWidth() > 0 ? view.getWidth() : dp(view.getContext(), 360);
            float height = view.getHeight() > 0 ? view.getHeight() : dp(view.getContext(), 640);
            float x = width * 0.50f;
            float startY = height * 0.78f;
            float midY = height * 0.50f;
            float endY = height * 0.22f;
            long downTime = SystemClock.uptimeMillis();

            dispatchTouch(view, downTime, downTime, MotionEvent.ACTION_DOWN, x, startY);
            dispatchTouch(view, downTime, downTime + 28L, MotionEvent.ACTION_MOVE, x, midY);
            dispatchTouch(view, downTime, downTime + 56L, MotionEvent.ACTION_MOVE, x, endY);
            dispatchTouch(view, downTime, downTime + 84L, MotionEvent.ACTION_UP, x, endY);
        } catch (Exception ignored) {
        }
    }

    private static void dispatchTouch(
            WebView view,
            long downTime,
            long eventTime,
            int action,
            float x,
            float y
    ) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private void finishHarvest(WebView view) {
        if (view != null) main.removeCallbacksAndMessages(view);
        Log.d(TAG, "harvest finished: cached=" + cachedCount()
                + " requests=" + observedRequests
                + " payloads=" + observedPayloads
                + " candidates=" + observedCandidates
                + " pumps=" + pumpCount);
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
            ViewParent parent = old.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(old);
            old.destroy();
        } catch (Exception ignored) {
        }
    }

    private void observeRequest(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return;
        observedRequests++;
        String url = request.getUrl().toString();
        if (isDirectMedia(url)) {
            enqueueKnownMedia(url, "", "", "Shit Show", PAGE, false);
            return;
        }

        try {
            Map<String, String> headers = request.getRequestHeaders();
            if (headers == null) return;
            String accept = headers.get("Accept");
            if (accept == null) accept = headers.get("accept");
            if (isMediaMime(accept)) {
                enqueueKnownMedia(url, accept, "", "Shit Show", PAGE, false);
            }
        } catch (Exception ignored) {
        }
    }

    private void observeResource(String url) {
        observedRequests++;
        // Media can live on a CDN outside crazyshit.com. Beta.10 incorrectly discarded it here.
        if (isDirectMedia(url)) enqueueKnownMedia(url, "", "", "Shit Show", PAGE, false);
    }

    private void harvestPayload(String raw) {
        if (raw == null || raw.isEmpty()) return;
        observedPayloads++;
        String text = raw
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");

        Matcher absolute = MEDIA_IN_PAYLOAD.matcher(text);
        while (absolute.find()) {
            enqueueKnownMedia(absolute.group(), "", "", "Shit Show", PAGE, false);
        }

        Matcher protocolRelative = PROTOCOL_RELATIVE_MEDIA_IN_PAYLOAD.matcher(text);
        while (protocolRelative.find()) {
            enqueueKnownMedia(protocolRelative.group(), "", "", "Shit Show", PAGE, false);
        }
    }

    private void enqueueKnownMedia(
            String mediaUrl,
            String mime,
            String posterUrl,
            String title,
            String pageUrl,
            boolean fromMediaElement
    ) {
        String media = normalizePlayableCandidate(mediaUrl, mime, fromMediaElement);
        if (media.isEmpty()) return;
        observedCandidates++;

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
            lock.notifyAll();
        }
    }

    private static String normalizePlayableCandidate(String value, String mime, boolean fromMediaElement) {
        String media = cleanUrl(value);
        if (!isHttpUrl(media)) return "";
        if (isDirectMedia(media)) return media;

        String lowerMime = mime == null ? "" : mime.toLowerCase(Locale.US);
        String suffix = "";
        if (lowerMime.contains("mpegurl")) suffix = ".m3u8";
        else if (lowerMime.contains("dash+xml")) suffix = ".mpd";
        else if (lowerMime.contains("webm")) suffix = ".webm";
        else if (lowerMime.startsWith("video/") || fromMediaElement) suffix = ".mp4";
        if (suffix.isEmpty()) return "";

        // A URL fragment is not sent to the server. It gives Media3 an extension hint for signed
        // or API-style media endpoints whose real path has no file extension.
        int hash = media.indexOf('#');
        if (hash >= 0) media = media.substring(0, hash);
        return media + "#csdirect" + suffix;
    }

    private static boolean isMediaMime(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("video/")
                || lower.contains("mpegurl")
                || lower.contains("dash+xml");
    }

    private static String cleanTitle(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || text.equalsIgnoreCase("Swipe up for next video")) return "Shit Show";
        if (text.length() > 120) text = text.substring(0, 120).trim();
        return text;
    }

    private static String cleanUrl(String value) {
        if (value == null) return "";
        String url = value.trim()
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return "https://crazyshit.com" + url;
        return url;
    }

    private static boolean isHttpUrl(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static boolean isDirectMedia(String value) {
        if (!isHttpUrl(value)) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains(".mp4")
                || lower.contains(".webm")
                || lower.contains(".m4v")
                || lower.contains(".m3u8")
                || lower.contains(".mpd");
    }

    private boolean isWarming() {
        synchronized (lock) {
            return warming;
        }
    }

    private void setWarming(boolean value) {
        synchronized (lock) {
            warming = value;
            lock.notifyAll();
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
                enqueueKnownMedia(
                        json.optString("media", ""),
                        json.optString("mime", ""),
                        json.optString("poster", ""),
                        json.optString("title", "Shit Show"),
                        json.optString("page", PAGE),
                        true
                );
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void onMediaUrl(String raw) {
            enqueueKnownMedia(raw, "", "", "Shit Show", PAGE, false);
        }

        @JavascriptInterface
        public void onMediaCandidate(String raw, String mime) {
            enqueueKnownMedia(raw, mime, "", "Shit Show", PAGE, false);
        }

        @JavascriptInterface
        public void onPayload(String raw) {
            harvestPayload(raw);
        }
    }

    private static final String PROBE_JS =
            "(function(){" +
            "if(window.__csShitShowProbe){window.__csShitShowScan&&window.__csShitShowScan();return;}" +
            "window.__csShitShowProbe=true;" +
            "var mediaRe=/\\.(mp4|webm|m4v|m3u8|mpd)(\\?|#|$)/i;" +
            "var mediaType=/^(video\\/)|mpegurl|dash\\+xml/i;" +
            "var watched=[];" +
            "function abs(v){try{return v?new URL(v,location.href).href:'';}catch(e){return v||'';}}" +
            "function obvious(v){try{var u=abs(v);if(u&&mediaRe.test(u))CSShitBridge.onMediaUrl(u);}catch(e){}}" +
            "function candidate(v,m){try{var u=abs(v);if(/^https?:/i.test(u))CSShitBridge.onMediaCandidate(u,m||'');}catch(e){}}" +
            "function payload(v){try{if(typeof v==='string'&&v.length)CSShitBridge.onPayload(v.slice(0,220000));}catch(e){}}" +
            "function titleFor(v,r){try{" +
            "var t=v.getAttribute('title')||v.getAttribute('aria-label')||v.getAttribute('data-title')||'';" +
            "if(!t&&r){var e=r.querySelector('[data-title],.title,.caption,h1,h2,h3,h4');" +
            "if(e)t=e.getAttribute('data-title')||e.textContent||'';}" +
            "return (t||'').replace(/\\s+/g,' ').trim();}catch(e){return '';}}" +
            "function send(v){try{" +
            "v.muted=true;v.preload='auto';" +
            "var q=v.querySelector('source[src]');" +
            "var s=v.currentSrc||v.src||(q&&(q.src||q.getAttribute('src')))||'';" +
            "var mt=(q&&(q.type||q.getAttribute('type')))||v.getAttribute('type')||'';" +
            "if(s&&/^https?:/i.test(abs(s))){" +
            "var r=v.closest('[data-id],[data-video-id],article,section,.swiper-slide,.slide,.item')||v.parentElement;" +
            "var a=(r&&r.querySelector('a[href]'))||v.closest('a[href]');" +
            "var p=v.poster||v.getAttribute('poster')||'';" +
            "CSShitBridge.onClip(JSON.stringify({media:abs(s),mime:mt,poster:abs(p),title:titleFor(v,r),page:a?abs(a.href):location.href}));}" +
            "try{var pr=v.play();if(pr&&pr.catch)pr.catch(function(){});}catch(e){}" +
            "}catch(e){}}" +
            "function scanAttrs(d){try{" +
            "d.querySelectorAll('[src],[data-src],[data-url],[data-file],[data-video],[data-stream],[data-media]').forEach(function(e){" +
            "var tag=(e.tagName||'').toLowerCase();" +
            "['src','data-src','data-url','data-file','data-video','data-stream','data-media'].forEach(function(k){var v=e.getAttribute&&e.getAttribute(k);if(!v)return;if(tag==='video'||tag==='source')candidate(v,e.getAttribute('type')||'');else obvious(v);});" +
            "});}catch(e){}}" +
            "function scanDoc(d){try{" +
            "d.querySelectorAll('video').forEach(send);" +
            "d.querySelectorAll('source[src]').forEach(function(e){candidate(e.src||e.getAttribute('src'),e.type||e.getAttribute('type')||'');});" +
            "scanAttrs(d);" +
            "d.querySelectorAll('script:not([src])').forEach(function(s){payload(s.textContent||'');});" +
            "d.querySelectorAll('iframe').forEach(function(f){try{if(f.contentDocument){watch(f.contentDocument);scanDoc(f.contentDocument);}}catch(e){}});" +
            "}catch(e){}}" +
            "function watch(d){try{if(!d||watched.indexOf(d)>=0)return;watched.push(d);" +
            "new MutationObserver(function(){scanDoc(d);}).observe(d.documentElement||d,{subtree:true,childList:true,attributes:true," +
            "attributeFilter:['src','poster','data-src','data-url','data-file','data-video','data-stream','data-media']});}catch(e){}}" +
            "function scan(){scanDoc(document);try{performance.getEntriesByType('resource').forEach(function(e){obvious(e.name||'');});}catch(e){}}" +
            "window.__csShitShowScan=scan;watch(document);" +
            "try{new PerformanceObserver(function(list){list.getEntries().forEach(function(e){obvious(e.name||'');});}).observe({entryTypes:['resource']});}catch(e){}" +
            "try{if(window.fetch&&!window.__csShitFetch){window.__csShitFetch=window.fetch;window.fetch=function(){return window.__csShitFetch.apply(this,arguments).then(function(r){" +
            "try{var ct=(r.headers&&r.headers.get&&r.headers.get('content-type'))||'';if(mediaType.test(ct))candidate(r.url||'',ct);else obvious(r.url||'');var c=r.clone();c.text().then(payload).catch(function(){});}catch(e){}return r;});};}}catch(e){}" +
            "try{if(window.XMLHttpRequest&&!XMLHttpRequest.prototype.__csShitWrapped){XMLHttpRequest.prototype.__csShitWrapped=true;var xo=XMLHttpRequest.prototype.open,xs=XMLHttpRequest.prototype.send;" +
            "XMLHttpRequest.prototype.open=function(m,u){try{this.__csShitUrl=abs(u);}catch(e){}return xo.apply(this,arguments);};" +
            "XMLHttpRequest.prototype.send=function(){try{this.addEventListener('load',function(){try{var ct=this.getResponseHeader('content-type')||'';var u=this.responseURL||this.__csShitUrl||'';if(mediaType.test(ct))candidate(u,ct);else obvious(u);if(!this.responseType||this.responseType==='text')payload(this.responseText||'');}catch(e){}});}catch(e){}return xs.apply(this,arguments);};}}catch(e){}" +
            "setInterval(scan,550);scan();" +
            "})();";

    private static final String SCAN_AND_ADVANCE_JS =
            "(function(){try{" +
            "window.__csShitShowScan&&window.__csShitShowScan();" +
            "var w=Math.max(window.innerWidth||0,320),h=Math.max(window.innerHeight||0,600);" +
            "var v=document.querySelector('video');" +
            "var t=v||(document.elementFromPoint&&document.elementFromPoint(w*0.5,h*0.5))||document.body;" +
            "var s=(v&&v.closest('.swiper,.swiper-container,[class*=swiper],[class*=swipe],[data-swiper]'))||t;" +
            "try{if(s&&s.swiper&&typeof s.swiper.slideNext==='function')s.swiper.slideNext();}catch(e){}" +
            "try{if(window.swiper&&typeof window.swiper.slideNext==='function')window.swiper.slideNext();}catch(e){}" +
            "function pe(n,y){try{t.dispatchEvent(new PointerEvent(n,{pointerId:1,pointerType:'touch',clientX:w*0.5,clientY:y,bubbles:true,cancelable:true,isPrimary:true}));}catch(e){}}" +
            "pe('pointerdown',h*0.78);pe('pointermove',h*0.48);pe('pointerup',h*0.20);" +
            "try{var sc=t;while(sc&&sc!==document.body&&!(sc.scrollHeight>sc.clientHeight+80))sc=sc.parentElement;if(sc&&sc!==document.body)sc.scrollBy(0,Math.max(420,h*0.92));else window.scrollBy(0,Math.max(420,h*0.92));}catch(e){}" +
            "try{t.dispatchEvent(new WheelEvent('wheel',{deltaY:h,bubbles:true,cancelable:true}));}catch(e){}" +
            "try{document.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',code:'ArrowDown',bubbles:true,cancelable:true}));}catch(e){}" +
            "try{var bs=document.querySelectorAll('button,[role=button]');for(var i=0;i<bs.length;i++){var q=((bs[i].getAttribute('aria-label')||'')+' '+(bs[i].getAttribute('title')||'')+' '+(bs[i].textContent||'')).trim();if(/\\bnext\\b/i.test(q)){bs[i].click();break;}}}catch(e){}" +
            "document.querySelectorAll('video').forEach(function(x){try{x.muted=true;x.preload='auto';var p=x.play();if(p&&p.catch)p.catch(function(){});}catch(e){}});" +
            "}catch(e){}})();";
}
