package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves Search/Library video thumbnails by letting the real site render in WebView and
 * capturing the thumbnail request it makes to media.crazyshit.com. The plain HTML returned by
 * CrazyShit often does not contain the final lazy-loaded thumbnail URL, so DOM-only parsing is
 * intentionally not used here.
 */
final class SearchThumbnailResolver {
    interface Callback {
        void onResolved(String pageUrl, String thumbnailUrl);
    }

    private static final String PREFS = "search_thumbnail_cache_v2";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, String> MEMORY = new ConcurrentHashMap<>();
    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();
    private static final Map<String, List<Callback>> CALLBACKS = new HashMap<>();

    private static WebView webView;
    private static WeakReference<Activity> host = new WeakReference<>(null);
    private static Request current;
    private static Runnable timeout;

    private static final Runnable RELEASE_WEBVIEW = () -> {
        if (current != null || !QUEUE.isEmpty()) return;
        destroyWebView();
    };

    private SearchThumbnailResolver() {
    }

    static void resolve(Context context, String pageUrl, Callback callback) {
        if (context == null || pageUrl == null || pageUrl.trim().isEmpty() || callback == null) return;
        String key = normalizePage(pageUrl);
        if (key.isEmpty()) return;

        Context app = context.getApplicationContext();
        String cached = MEMORY.get(key);
        if (cached == null || cached.isEmpty()) {
            cached = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "");
            if (cached != null && !cached.isEmpty()) MEMORY.put(key, cached);
        }
        if (cached != null && !cached.isEmpty()) {
            String value = cached;
            MAIN.post(() -> callback.onResolved(pageUrl, value));
            return;
        }

        Activity activity = context instanceof Activity ? (Activity) context : null;
        if (activity == null || activity.isFinishing()) {
            MAIN.post(() -> callback.onResolved(pageUrl, ""));
            return;
        }

        MAIN.post(() -> enqueue(activity, pageUrl, key, callback));
    }

    private static void enqueue(Activity activity, String pageUrl, String key, Callback callback) {
        MAIN.removeCallbacks(RELEASE_WEBVIEW);
        List<Callback> callbacks = CALLBACKS.get(key);
        if (callbacks != null) {
            callbacks.add(callback);
            return;
        }

        callbacks = new ArrayList<>();
        callbacks.add(callback);
        CALLBACKS.put(key, callbacks);
        QUEUE.add(new Request(activity, pageUrl, key));
        pump();
    }

    private static void pump() {
        if (current != null) return;

        while (!QUEUE.isEmpty()) {
            Request request = QUEUE.poll();
            Activity activity = request.activity.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                deliver(request, "");
                continue;
            }

            current = request;
            ensureWebView(activity);
            if (webView == null) {
                finishCurrent("");
                return;
            }

            timeout = () -> {
                if (current != request) return;
                finishCurrent(request.fallbackCandidate);
            };
            MAIN.postDelayed(timeout, 7000L);

            try {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Referer", "https://crazyshit.com/");
                webView.loadUrl(request.pageUrl, headers);
            } catch (Exception ignored) {
                finishCurrent("");
            }
            return;
        }

        MAIN.removeCallbacks(RELEASE_WEBVIEW);
        MAIN.postDelayed(RELEASE_WEBVIEW, 1800L);
    }

    private static void ensureWebView(Activity activity) {
        Activity oldHost = host.get();
        if (webView != null && oldHost == activity) return;
        destroyWebView();

        try {
            WebView view = new WebView(activity);
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            view.setAlpha(0.01f);
            view.setClickable(false);
            view.setFocusable(false);
            view.setFocusableInTouchMode(false);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);

            WebSettings settings = view.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadsImagesAutomatically(true);
            settings.setBlockNetworkImage(false);
            settings.setUserAgentString(USER_AGENT);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                cookies.setAcceptThirdPartyCookies(view, true);
            }

            view.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                    if (request != null && request.getUrl() != null) {
                        String url = request.getUrl().toString();
                        if (looksLikeThumbnail(url) || looksLikeMediaImage(url)) {
                            MAIN.post(() -> acceptCandidate(url));
                        }
                    }
                    return super.shouldInterceptRequest(v, request);
                }

                @Override
                public void onLoadResource(WebView v, String url) {
                    if (url != null && (looksLikeThumbnail(url) || looksLikeMediaImage(url))) {
                        acceptCandidate(url);
                    }
                    super.onLoadResource(v, url);
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    super.onPageFinished(v, url);
                    if (current == null || "about:blank".equals(url)) return;
                    primeRenderedPage(v);
                    MAIN.postDelayed(() -> {
                        if (current != null && webView == v) readRenderedCandidates(v);
                    }, 450L);
                    MAIN.postDelayed(() -> {
                        if (current != null && webView == v) {
                            primeRenderedPage(v);
                            readRenderedCandidates(v);
                        }
                    }, 1250L);
                }
            });

            View content = activity.findViewById(android.R.id.content);
            if (content instanceof ViewGroup) {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                ((ViewGroup) content).addView(view, 0, params);
            } else {
                view.destroy();
                return;
            }

            webView = view;
            host = new WeakReference<>(activity);
        } catch (Exception ignored) {
            webView = null;
            host = new WeakReference<>(null);
        }
    }

    private static void primeRenderedPage(WebView view) {
        try {
            view.evaluateJavascript(
                    "(function(){try{" +
                    "var add=function(u){if(!u)return;try{var i=new Image();i.style.cssText='position:absolute;width:2px;height:2px;opacity:.01;pointer-events:none';i.src=u;document.body.appendChild(i);}catch(e){}};" +
                    "document.querySelectorAll('img').forEach(function(i){var u=i.currentSrc||i.src||i.getAttribute('data-src')||i.getAttribute('data-original')||i.getAttribute('data-lazy-src')||i.getAttribute('data-thumb')||i.getAttribute('data-thumbnail');if(u){i.loading='eager';if(!i.src||i.src.indexOf('data:')===0)i.src=u;add(u);}});" +
                    "document.querySelectorAll('video').forEach(function(v){var p=v.poster||v.getAttribute('poster')||v.getAttribute('data-poster')||v.getAttribute('data-thumb');if(p)add(p);});" +
                    "document.querySelectorAll('[data-bg],[data-background],[data-thumb],[data-thumbnail]').forEach(function(e){var u=e.getAttribute('data-bg')||e.getAttribute('data-background')||e.getAttribute('data-thumb')||e.getAttribute('data-thumbnail');if(u)add(u);});" +
                    "window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));setTimeout(function(){window.scrollTo(0,0);},250);return true;}catch(e){return false;}})();",
                    null
            );
        } catch (Exception ignored) {
        }
    }

    private static void readRenderedCandidates(WebView view) {
        try {
            view.evaluateJavascript(
                    "(function(){try{var a=[];var add=function(u){if(u&&typeof u==='string')a.push(u);};" +
                    "document.querySelectorAll('img').forEach(function(i){add(i.currentSrc);add(i.src);add(i.getAttribute('data-src'));add(i.getAttribute('data-original'));add(i.getAttribute('data-lazy-src'));add(i.getAttribute('data-thumb'));add(i.getAttribute('data-thumbnail'));});" +
                    "document.querySelectorAll('video').forEach(function(v){add(v.poster);add(v.getAttribute('poster'));add(v.getAttribute('data-poster'));});" +
                    "performance.getEntriesByType('resource').forEach(function(r){add(r.name);});return a;}catch(e){return [];}})();",
                    value -> {
                        if (current == null || value == null || value.isEmpty()) return;
                        try {
                            JSONArray array = new JSONArray(value);
                            for (int i = 0; i < array.length(); i++) {
                                String candidate = array.optString(i, "");
                                if (!candidate.isEmpty()) acceptCandidate(candidate);
                                if (current == null) break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
            );
        } catch (Exception ignored) {
        }
    }

    private static void acceptCandidate(String raw) {
        Request request = current;
        if (request == null) return;
        String url = cleanUrl(raw);
        if (url.isEmpty()) return;

        if (looksLikeThumbnail(url)) {
            finishCurrent(url);
            return;
        }
        if (request.fallbackCandidate.isEmpty() && looksLikeMediaImage(url)) {
            request.fallbackCandidate = url;
        }
    }

    private static void finishCurrent(String thumbnailUrl) {
        Request request = current;
        if (request == null) return;
        if (timeout != null) MAIN.removeCallbacks(timeout);
        timeout = null;

        String value = cleanUrl(thumbnailUrl);
        if (!value.isEmpty()) {
            MEMORY.put(request.key, value);
            Activity activity = request.activity.get();
            if (activity != null) {
                try {
                    SharedPreferences prefs = activity.getApplicationContext()
                            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    prefs.edit().putString(request.key, value).apply();
                } catch (Exception ignored) {
                }
            }
        }

        try {
            if (webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
            }
        } catch (Exception ignored) {
        }

        current = null;
        deliver(request, value);
        MAIN.postDelayed(SearchThumbnailResolver::pump, 90L);
    }

    private static void deliver(Request request, String value) {
        List<Callback> callbacks = CALLBACKS.remove(request.key);
        if (callbacks == null) return;
        for (Callback callback : callbacks) {
            try {
                callback.onResolved(request.pageUrl, value == null ? "" : value);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean looksLikeThumbnail(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return false;
        return lower.contains("media.crazyshit.com/thumbs/") || lower.contains("/thumbs/");
    }

    private static boolean looksLikeMediaImage(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return false;
        if (!lower.contains("media.crazyshit.com")) return false;
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
                lower.contains(".webp") || lower.contains("/poster/") || lower.contains("/thumb/");
    }

    private static String cleanUrl(String value) {
        if (value == null) return "";
        String url = value.trim()
                .replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("\\u0026", "&");
        if (url.startsWith("//")) url = "https:" + url;
        if (url.startsWith("/")) url = "https://crazyshit.com" + url;
        return url;
    }

    private static String normalizePage(String value) {
        String url = value == null ? "" : value.trim().toLowerCase(Locale.US);
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private static void destroyWebView() {
        WebView view = webView;
        webView = null;
        host = new WeakReference<>(null);
        if (view == null) return;
        try {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        } catch (Exception ignored) {
        }
        try {
            view.stopLoading();
            view.loadUrl("about:blank");
            view.clearHistory();
            view.removeAllViews();
            view.destroy();
        } catch (Exception ignored) {
        }
    }

    private static final class Request {
        final WeakReference<Activity> activity;
        final String pageUrl;
        final String key;
        String fallbackCandidate = "";

        Request(Activity activity, String pageUrl, String key) {
            this.activity = new WeakReference<>(activity);
            this.pageUrl = pageUrl;
            this.key = key;
        }
    }
}
