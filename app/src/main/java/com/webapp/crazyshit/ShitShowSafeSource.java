package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

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
 * Strict rendered source for CrazyShit's JavaScript-driven Shit Show player.
 *
 * The homepage is loaded first so the site's own runtime/cookies are initialized. Main-frame
 * navigation is then locked to CrazyShit itself and activation is limited to the exact same-origin
 * /shitshow route. This prevents ad overlays and deceptive links from stealing the hidden WebView.
 */
final class ShitShowSafeSource {
    private static final String HOME = CrazyShitRepository.BASE;
    private static final String PAGE = CrazyShitRepository.BASE + "shitshow/";

    private static final int TARGET_CACHE = 24;
    private static final int MAX_CACHE = 80;
    private static final long HARVEST_TIMEOUT_MS = 45_000L;
    private static final long PUMP_MS = 1_350L;
    private static final long FIRST_BATCH_WAIT_MS = 6_000L;
    private static final long DIAGNOSTIC_KEEP_MS = 90_000L;

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
    private TextView diagnosticView;
    private long harvestStartedAt;

    private int pumpCount;
    private int observedRequests;
    private int observedPayloads;
    private int observedCandidates;
    private int pageCommitCount;
    private int pageFinishedCount;
    private int activationAttempts;
    private int exactLinks;
    private int jsSwipeHints;
    private int jsVideoCount;
    private int jsHttpVideoCount;
    private int jsBlobCount;
    private int jsSourceCount;
    private int jsResourceCount;

    private String pageState = "idle";
    private String lastPageUrl = "";
    private String jsReadyState = "";
    private String activationState = "idle";
    private String activationHref = "";
    private String blockedUrl = "";
    private String lastError = "";
    private String bodyHint = "";

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
        // Keep harvested URL history for this process. Chaos has its own session repeat protection.
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
        resetDiagnostics();
        harvestStartedAt = System.currentTimeMillis();
        attachDiagnosticView(activity);

        WebView view = new WebView(activity);
        webView = view;
        view.setAlpha(0.01f);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);

        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(activity, 360), dp(activity, 640));
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
        try {
            settings.setUserAgentString(WebSettings.getDefaultUserAgent(activity));
        } catch (Exception ignored) {
        }

        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            cookies.setAcceptThirdPartyCookies(view, true);
        } catch (Exception ignored) {
        }

        pageState = "loading-home";
        lastPageUrl = HOME;
        refreshDiagnosticView();

        view.addJavascriptInterface(new Bridge(), "CSShitBridge");
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView web, WebResourceRequest request) {
                if (request == null || request.getUrl() == null || !request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (isAllowedMainFrame(url)) return false;
                blockedUrl = safe(url);
                activationState = "blocked-external";
                refreshDiagnosticView();
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView web, String url) {
                if (isAllowedMainFrame(url)) return false;
                blockedUrl = safe(url);
                activationState = "blocked-external";
                refreshDiagnosticView();
                return true;
            }

            @Override
            public void onPageCommitVisible(WebView web, String url) {
                pageCommitCount++;
                pageState = isShitShowPage(url) ? "shitshow-commit" : "commit";
                lastPageUrl = safe(url);
                lastError = "";
                refreshDiagnosticView();
                installSafetyAndProbe(web);
            }

            @Override
            public void onPageFinished(WebView web, String url) {
                pageFinishedCount++;
                pageState = isShitShowPage(url) ? "shitshow-loaded" : "home-loaded";
                lastPageUrl = safe(url);
                lastError = "";
                refreshDiagnosticView();
                installSafetyAndProbe(web);
                if (!isShitShowPage(url)) maybeActivate(web);
                schedulePump(web);
            }

            @Override
            public void onReceivedError(WebView web, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    lastError = "load " + (error == null ? "error" : safe(error.getDescription()));
                    pageState = "error";
                    refreshDiagnosticView();
                }
            }

            @Override
            public void onReceivedHttpError(WebView web, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null) {
                    lastError = "HTTP " + response.getStatusCode();
                    pageState = "http-error";
                    refreshDiagnosticView();
                }
            }

            @Override
            public void onLoadResource(WebView web, String url) {
                observeResource(url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView web, WebResourceRequest request) {
                observeRequest(request);
                return null;
            }
        });

        view.loadUrl(HOME);

        main.postDelayed(() -> {
            if (webView != view || !isWarming()) return;
            installSafetyAndProbe(view);
            maybeActivate(view);
            schedulePump(view);
        }, 2_500L);
    }

    private static boolean isAllowedMainFrame(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String url = value.trim();
        if ("about:blank".equalsIgnoreCase(url)) return true;
        try {
            Uri uri = Uri.parse(url);
            String scheme = safe(uri.getScheme()).toLowerCase(Locale.US);
            String host = safe(uri.getHost()).toLowerCase(Locale.US);
            return ("http".equals(scheme) || "https".equals(scheme))
                    && ("crazyshit.com".equals(host) || "www.crazyshit.com".equals(host));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isShitShowPage(String value) {
        if (!isAllowedMainFrame(value)) return false;
        try {
            Uri uri = Uri.parse(value);
            String path = safe(uri.getPath()).replaceAll("/+$", "");
            return "/shitshow".equalsIgnoreCase(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void maybeActivate(WebView view) {
        if (view == null || webView != view || !isWarming()) return;
        if (isShitShowPage(lastPageUrl) || jsVideoCount > 0 || jsSwipeHints > 0) return;
        if (activationAttempts >= 3) return;
        activationAttempts++;
        activationState = "locating-exact";
        refreshDiagnosticView();
        view.evaluateJavascript(ACTIVATE_EXACT_JS, null);
    }

    private void installSafetyAndProbe(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(SAFETY_JS, null);
        view.evaluateJavascript(PROBE_JS, null);
    }

    private void schedulePump(WebView view) {
        main.removeCallbacksAndMessages(view);
        main.postAtTime(() -> pump(view), view, SystemClock.uptimeMillis() + 300L);
    }

    private void pump(WebView view) {
        if (view == null || webView != view || !isWarming()) return;
        pumpCount++;
        installSafetyAndProbe(view);

        boolean playerActive = isShitShowPage(lastPageUrl) || jsVideoCount > 0 || jsSwipeHints > 0;
        if (playerActive) {
            view.evaluateJavascript(SCAN_AND_ADVANCE_JS, null);
            dispatchNativeSwipe(view);
        } else if (pumpCount % 3 == 0) {
            maybeActivate(view);
        }

        refreshDiagnosticView();
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

    private static void dispatchTouch(WebView view, long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private void observeRequest(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return;
        observedRequests++;
        String url = request.getUrl().toString();
        if (isDirectMedia(url)) {
            enqueueKnownMedia(url, "", "", "Shit Show", false);
        } else {
            try {
                Map<String, String> headers = request.getRequestHeaders();
                String accept = headers == null ? "" : headers.get("Accept");
                if (accept == null && headers != null) accept = headers.get("accept");
                if (isMediaMime(accept)) enqueueKnownMedia(url, accept, "", "Shit Show", false);
            } catch (Exception ignored) {
            }
        }
        refreshDiagnosticView();
    }

    private void observeResource(String url) {
        observedRequests++;
        if (isDirectMedia(url)) enqueueKnownMedia(url, "", "", "Shit Show", false);
    }

    private void harvestPayload(String raw) {
        if (raw == null || raw.isEmpty()) return;
        observedPayloads++;
        String text = raw.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&");

        Matcher absolute = MEDIA_IN_PAYLOAD.matcher(text);
        while (absolute.find()) enqueueKnownMedia(absolute.group(), "", "", "Shit Show", false);

        Matcher protocolRelative = PROTOCOL_RELATIVE_MEDIA_IN_PAYLOAD.matcher(text);
        while (protocolRelative.find()) enqueueKnownMedia(protocolRelative.group(), "", "", "Shit Show", false);
        refreshDiagnosticView();
    }

    private void enqueueKnownMedia(String mediaUrl, String mime, String posterUrl, String title, boolean mediaElement) {
        String media = normalizePlayableCandidate(mediaUrl, mime, mediaElement);
        if (media.isEmpty()) return;
        observedCandidates++;
        String poster = cleanUrl(posterUrl);
        String label = cleanTitle(title);

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
        refreshDiagnosticView();
    }

    private static String normalizePlayableCandidate(String value, String mime, boolean mediaElement) {
        String media = cleanUrl(value);
        if (!isHttpUrl(media)) return "";
        if (isDirectMedia(media)) return media;

        String lowerMime = mime == null ? "" : mime.toLowerCase(Locale.US);
        String suffix = "";
        if (lowerMime.contains("mpegurl")) suffix = ".m3u8";
        else if (lowerMime.contains("dash+xml")) suffix = ".mpd";
        else if (lowerMime.contains("webm")) suffix = ".webm";
        else if (lowerMime.startsWith("video/") || mediaElement) suffix = ".mp4";
        if (suffix.isEmpty()) return "";

        int hash = media.indexOf('#');
        if (hash >= 0) media = media.substring(0, hash);
        return media + "#csdirect" + suffix;
    }

    private static boolean isMediaMime(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("video/") || lower.contains("mpegurl") || lower.contains("dash+xml");
    }

    private static boolean isDirectMedia(String value) {
        if (!isHttpUrl(value)) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m4v")
                || lower.contains(".m3u8") || lower.contains(".mpd");
    }

    private static boolean isHttpUrl(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String cleanUrl(String value) {
        if (value == null) return "";
        String url = value.trim().replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&");
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return "https://crazyshit.com" + url;
        return url;
    }

    private static String cleanTitle(String value) {
        String text = safe(value);
        if (text.isEmpty() || text.equalsIgnoreCase("Swipe up for next video")) return "Shit Show";
        return text.length() > 120 ? text.substring(0, 120).trim() : text;
    }

    private void resetDiagnostics() {
        pumpCount = 0;
        observedRequests = 0;
        observedPayloads = 0;
        observedCandidates = 0;
        pageCommitCount = 0;
        pageFinishedCount = 0;
        activationAttempts = 0;
        exactLinks = 0;
        jsSwipeHints = 0;
        jsVideoCount = 0;
        jsHttpVideoCount = 0;
        jsBlobCount = 0;
        jsSourceCount = 0;
        jsResourceCount = 0;
        pageState = "starting";
        lastPageUrl = HOME;
        jsReadyState = "";
        activationState = "idle";
        activationHref = "";
        blockedUrl = "";
        lastError = "";
        bodyHint = "";
    }

    private void attachDiagnosticView(Activity activity) {
        if (diagnosticView != null && diagnosticView.getParent() != null) {
            diagnosticView.setVisibility(View.VISIBLE);
            refreshDiagnosticView();
            return;
        }
        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host == null) return;

        TextView view = new TextView(activity);
        diagnosticView = view;
        view.setTextColor(Color.WHITE);
        view.setTextSize(10.5f);
        view.setGravity(Gravity.START);
        view.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));
        view.setBackgroundColor(Color.argb(215, 0, 0, 0));
        view.setElevation(dp(activity, 32));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = dp(activity, 8);
        params.topMargin = dp(activity, 8);
        params.rightMargin = dp(activity, 8);
        host.addView(view, params);
        refreshDiagnosticView();
    }

    private void refreshDiagnosticView() {
        TextView view = diagnosticView;
        if (view == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::refreshDiagnosticView);
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("Shit Show debug B14  ").append(isWarming() ? "RUN" : "STOP").append('\n');
        text.append("page:").append(pageState)
                .append(" c").append(pageCommitCount).append("/f").append(pageFinishedCount)
                .append(" DOM:").append(jsReadyState.isEmpty() ? "?" : jsReadyState).append('\n');
        text.append("act:").append(activationAttempts).append(' ').append(shorten(activationState, 24))
                .append(" exact:").append(exactLinks).append(" swipe:").append(jsSwipeHints).append('\n');
        text.append("video:").append(jsVideoCount).append(" http:").append(jsHttpVideoCount)
                .append(" blob:").append(jsBlobCount).append(" src:").append(jsSourceCount).append('\n');
        text.append("req:").append(observedRequests).append(" payload:").append(observedPayloads)
                .append(" cand:").append(observedCandidates).append(" cache:").append(cachedCount())
                .append(" pump:").append(pumpCount).append('\n');
        text.append("url:").append(shorten(lastPageUrl, 72));
        if (!activationHref.isEmpty()) text.append('\n').append("target:").append(shorten(activationHref, 68));
        if (!blockedUrl.isEmpty()) text.append('\n').append("blocked:").append(shorten(blockedUrl, 66));
        String hint = !lastError.isEmpty() ? "ERR " + lastError : bodyHint;
        if (!hint.isEmpty()) text.append('\n').append(shorten(hint, 82));
        view.setText(text.toString());
    }

    private void finishHarvest(WebView view) {
        if (view != null) main.removeCallbacksAndMessages(view);
        pageState = cachedCount() > 0 ? "captured" : "stopped";
        refreshDiagnosticView();
        if (webView == view) destroyWebView();
        setWarming(false);
        refreshDiagnosticView();
        main.postDelayed(() -> {
            if (isWarming()) return;
            TextView old = diagnosticView;
            diagnosticView = null;
            if (old == null) return;
            ViewParent parent = old.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(old);
        }, DIAGNOSTIC_KEEP_MS);
    }

    private void destroyWebView() {
        WebView old = webView;
        webView = null;
        if (old == null) return;
        try {
            main.removeCallbacksAndMessages(old);
            old.stopLoading();
            old.removeJavascriptInterface("CSShitBridge");
            old.setWebViewClient(null);
            ViewParent parent = old.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(old);
            old.destroy();
        } catch (Exception ignored) {
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
            lock.notifyAll();
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String safe(CharSequence value) {
        return value == null ? "" : value.toString().replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String value, int max) {
        String text = safe(value);
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    private final class Bridge {
        @JavascriptInterface
        public void onActivation(String raw) {
            try {
                JSONObject json = new JSONObject(raw == null ? "{}" : raw);
                activationState = safe(json.optString("state", activationState));
                activationHref = safe(json.optString("href", activationHref));
                exactLinks = json.optInt("exact", exactLinks);
                refreshDiagnosticView();
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void onState(String raw) {
            try {
                JSONObject json = new JSONObject(raw == null ? "{}" : raw);
                lastPageUrl = safe(json.optString("url", lastPageUrl));
                jsReadyState = safe(json.optString("ready", jsReadyState));
                bodyHint = safe(json.optString("body", bodyHint));
                exactLinks = json.optInt("exact", exactLinks);
                jsSwipeHints = json.optInt("swipe", jsSwipeHints);
                jsVideoCount = json.optInt("videos", jsVideoCount);
                jsHttpVideoCount = json.optInt("httpVideos", jsHttpVideoCount);
                jsBlobCount = json.optInt("blobVideos", jsBlobCount);
                jsSourceCount = json.optInt("sources", jsSourceCount);
                jsResourceCount = json.optInt("resources", jsResourceCount);
                refreshDiagnosticView();
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void onClip(String raw) {
            try {
                JSONObject json = new JSONObject(raw == null ? "{}" : raw);
                enqueueKnownMedia(
                        json.optString("media", ""),
                        json.optString("mime", ""),
                        json.optString("poster", ""),
                        json.optString("title", "Shit Show"),
                        true
                );
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void onMediaCandidate(String url, String mime) {
            enqueueKnownMedia(url, mime, "", "Shit Show", false);
        }

        @JavascriptInterface
        public void onPayload(String raw) {
            harvestPayload(raw);
        }
    }

    private static final String SAFETY_JS =
            "(function(){try{" +
            "if(window.__csSafeNav)return;window.__csSafeNav=true;" +
            "function ok(v){try{var u=new URL(v,location.href);return u.origin===location.origin;}catch(e){return false;}}" +
            "window.open=function(u){try{if(ok(u))location.href=new URL(u,location.href).href;}catch(e){}return null;};" +
            "document.addEventListener('click',function(e){try{var a=e.target&&e.target.closest&&e.target.closest('a[href]');if(!a)return;var h=a.getAttribute('href')||'';if(h&&!ok(h)){e.preventDefault();e.stopImmediatePropagation();}}catch(x){}},true);" +
            "}catch(e){}})();";

    private static final String ACTIVATE_EXACT_JS =
            "(function(){try{" +
            "var all=[].slice.call(document.querySelectorAll('a[href]')),hits=[];" +
            "for(var i=0;i<all.length;i++){try{var u=new URL(all[i].getAttribute('href'),location.href);var p=u.pathname.replace(/\\/+$/,'');if(u.origin===location.origin&&p==='/shitshow')hits.push(all[i]);}catch(e){}}" +
            "if(!hits.length){CSShitBridge.onActivation(JSON.stringify({state:'exact-no-link',href:'',exact:0}));return;}" +
            "var el=hits[0],u=new URL(el.getAttribute('href'),location.href);" +
            "CSShitBridge.onActivation(JSON.stringify({state:'exact-click',href:u.href,exact:hits.length}));" +
            "try{el.click();}catch(e){}" +
            "setTimeout(function(){try{var p=location.pathname.replace(/\\/+$/,'');if(p!=='/shitshow')location.assign(u.href);}catch(e){}},500);" +
            "}catch(e){CSShitBridge.onActivation(JSON.stringify({state:'activate-error',href:String(e),exact:0}));}})();";

    private static final String PROBE_JS =
            "(function(){try{" +
            "if(!window.__csSafeProbe){window.__csSafeProbe=true;" +
            "var mediaType=/^(video\\/)|mpegurl|dash\\+xml/i;" +
            "function abs(v){try{return v?new URL(v,location.href).href:'';}catch(e){return v||'';}}" +
            "function candidate(v,m){try{var u=abs(v);if(/^https?:/i.test(u))CSShitBridge.onMediaCandidate(u,m||'');}catch(e){}}" +
            "function payload(v){try{if(typeof v==='string'&&v.length)CSShitBridge.onPayload(v.slice(0,220000));}catch(e){}}" +
            "try{if(window.fetch&&!window.__csSafeFetch){window.__csSafeFetch=window.fetch;window.fetch=function(){return window.__csSafeFetch.apply(this,arguments).then(function(r){try{var ct=(r.headers&&r.headers.get&&r.headers.get('content-type'))||'';if(mediaType.test(ct))candidate(r.url||'',ct);var c=r.clone();c.text().then(payload).catch(function(){});}catch(e){}return r;});};}}catch(e){}" +
            "try{if(window.XMLHttpRequest&&!XMLHttpRequest.prototype.__csSafeWrapped){XMLHttpRequest.prototype.__csSafeWrapped=true;var xo=XMLHttpRequest.prototype.open,xs=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u){try{this.__csUrl=abs(u);}catch(e){}return xo.apply(this,arguments);};XMLHttpRequest.prototype.send=function(){try{this.addEventListener('load',function(){try{var ct=this.getResponseHeader('content-type')||'';var u=this.responseURL||this.__csUrl||'';if(mediaType.test(ct))candidate(u,ct);if(!this.responseType||this.responseType==='text')payload(this.responseText||'');}catch(e){}});}catch(e){}return xs.apply(this,arguments);};}}catch(e){}" +
            "}" +
            "function scan(){try{" +
            "var videos=0,httpVideos=0,blobVideos=0,sources=0,swipe=0,exact=0;" +
            "document.querySelectorAll('video').forEach(function(v){videos++;var q=v.querySelector('source[src]');var s=v.currentSrc||v.src||(q&&(q.src||q.getAttribute('src')))||'';var mt=(q&&(q.type||q.getAttribute('type')))||v.getAttribute('type')||'';if(/^https?:/i.test(s))httpVideos++;if(/^blob:/i.test(s))blobVideos++;if(/^https?:/i.test(abs(s))){var r=v.closest('article,section,.swiper-slide,.slide,.item')||v.parentElement;var p=v.poster||v.getAttribute('poster')||'';var t=(v.getAttribute('title')||v.getAttribute('aria-label')||(r&&r.textContent)||'Shit Show').replace(/\\s+/g,' ').trim().slice(0,120);CSShitBridge.onClip(JSON.stringify({media:abs(s),mime:mt,poster:abs(p),title:t}));}try{v.muted=true;v.preload='auto';var pr=v.play();if(pr&&pr.catch)pr.catch(function(){});}catch(e){}});" +
            "document.querySelectorAll('source[src]').forEach(function(s){sources++;candidate(s.src||s.getAttribute('src'),s.type||s.getAttribute('type')||'');});" +
            "document.querySelectorAll('a[href]').forEach(function(a){try{var u=new URL(a.getAttribute('href'),location.href),p=u.pathname.replace(/\\/+$/,'');if(u.origin===location.origin&&p==='/shitshow')exact++;}catch(e){}});" +
            "var body=(document.body&&document.body.innerText||'').replace(/\\s+/g,' ').trim();if(/swipe\\s+up\\s+for\\s+next\\s+video/i.test(body))swipe++;" +
            "try{performance.getEntriesByType('resource').forEach(function(e){var n=e.name||'';if(/\\.(mp4|webm|m4v|m3u8|mpd)(\\?|#|$)/i.test(n))candidate(n,'');});}catch(e){}" +
            "CSShitBridge.onState(JSON.stringify({url:location.href,ready:document.readyState,body:body.slice(0,90),exact:exact,swipe:swipe,videos:videos,httpVideos:httpVideos,blobVideos:blobVideos,sources:sources,resources:(performance.getEntriesByType('resource')||[]).length}));" +
            "}catch(e){}}" +
            "window.__csSafeScan=scan;scan();" +
            "}catch(e){}})();";

    private static final String SCAN_AND_ADVANCE_JS =
            "(function(){try{" +
            "window.__csSafeScan&&window.__csSafeScan();" +
            "var w=Math.max(window.innerWidth||0,320),h=Math.max(window.innerHeight||0,600);" +
            "var v=document.querySelector('video'),t=v||document.body;" +
            "try{var s=v&&v.closest('.swiper,.swiper-container,[class*=swiper],[class*=swipe],[data-swiper]');if(s&&s.swiper&&typeof s.swiper.slideNext==='function')s.swiper.slideNext();}catch(e){}" +
            "function pe(n,y){try{t.dispatchEvent(new PointerEvent(n,{pointerId:1,pointerType:'touch',clientX:w*.5,clientY:y,bubbles:true,cancelable:true,isPrimary:true}));}catch(e){}}" +
            "pe('pointerdown',h*.78);pe('pointermove',h*.48);pe('pointerup',h*.20);" +
            "try{window.scrollBy(0,Math.max(420,h*.92));}catch(e){}" +
            "}catch(e){}})();";
}
