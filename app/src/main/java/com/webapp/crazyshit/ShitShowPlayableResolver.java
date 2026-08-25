package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves one /shitshow/<story> permalink through the site's rendered JavaScript player.
 *
 * The Shit Show index exposes story permalinks but not a stable media URL. Normal CrazyShit
 * media pages can stay on the fast Jsoup path; only these story permalinks need a short hidden
 * WebView pass to expose the actual stream used by the site's player.
 */
final class ShitShowPlayableResolver {
    private static final long TIMEOUT_MS = 10_000L;
    private static final long FALLBACK_AFTER_MS = 3_000L;
    private static final String DIRECT_MARKER = "csdirect=";

    private ShitShowPlayableResolver() {
    }

    static CrazyShitRepository.StreamInfo resolve(Context context, String pageUrl) {
        if (!(context instanceof Activity) || !isStoryUrl(pageUrl)) return null;
        if (Looper.myLooper() == Looper.getMainLooper()) return null;

        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) return null;

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> domMedia = new AtomicReference<>("");
        AtomicReference<String> requestMedia = new AtomicReference<>("");
        AtomicReference<String> requestKind = new AtomicReference<>("");
        AtomicReference<String> title = new AtomicReference<>("Shit Show");
        AtomicReference<WebView> holder = new AtomicReference<>();
        long startedAt = System.currentTimeMillis();
        Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                done.countDown();
                return;
            }

            WebView view = new WebView(activity);
            holder.set(view);
            view.setAlpha(0.01f);
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            view.setFocusable(false);
            view.setFocusableInTouchMode(false);

            ViewGroup host = activity.findViewById(android.R.id.content);
            if (host != null) {
                host.addView(view, 0, new FrameLayout.LayoutParams(dp(activity, 360), dp(activity, 640)));
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

            view.addJavascriptInterface(new Bridge(domMedia, title, done), "CSResolveBridge");
            view.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView web, WebResourceRequest request) {
                    if (request == null || request.getUrl() == null || !request.isForMainFrame()) return false;
                    return !isCrazyShitHost(request.getUrl().toString());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView web, String url) {
                    return !isCrazyShitHost(url);
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView web, WebResourceRequest request) {
                    observeRequest(request, requestMedia, requestKind);
                    return null;
                }

                @Override
                public void onLoadResource(WebView web, String url) {
                    if (isDirectMedia(url) && !looksLikeAdHost(url)) {
                        requestMedia.compareAndSet("", cleanUrl(url));
                        if (requestKind.get().isEmpty()) requestKind.set(kindFromUrl(url));
                    }
                }

                @Override
                public void onPageFinished(WebView web, String url) {
                    probe(web);
                    main.postDelayed(() -> probeIfAlive(holder.get(), web), 250L);
                    main.postDelayed(() -> probeIfAlive(holder.get(), web), 700L);
                    main.postDelayed(() -> probeIfAlive(holder.get(), web), 1_400L);
                    main.postDelayed(() -> probeIfAlive(holder.get(), web), 2_400L);
                    main.postDelayed(() -> {
                        if (holder.get() != web || done.getCount() == 0L) return;
                        String fallback = requestMedia.get();
                        if (isHttp(fallback) && System.currentTimeMillis() - startedAt >= FALLBACK_AFTER_MS) {
                            done.countDown();
                        }
                    }, FALLBACK_AFTER_MS);
                }
            });

            view.loadUrl(pageUrl);
        });

        try {
            done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        String media = cleanUrl(domMedia.get());
        if (!isHttp(media)) media = cleanUrl(requestMedia.get());
        if (isHttp(media) && !isDirectMedia(media)) {
            media = markDirect(media, requestKind.get());
        }
        final WebView old = holder.getAndSet(null);
        main.post(() -> destroy(old));

        if (!isHttp(media) || !isDirectMedia(media)) return null;
        return new CrazyShitRepository.StreamInfo(media, pageUrl, title.get());
    }

    private static void probeIfAlive(WebView current, WebView expected) {
        if (current == expected && expected != null) probe(expected);
    }

    private static void probe(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(
                "(function(){try{" +
                        "var vs=[].slice.call(document.querySelectorAll('video'));" +
                        "for(var i=0;i<vs.length;i++){var v=vs[i],q=v.querySelector('source[src]')," +
                        "s=v.currentSrc||v.src||(q&&(q.src||q.getAttribute('src')))||'';" +
                        "if(/^https?:/i.test(s)){var r=v.closest('article,section,.swiper-slide,.slide,.item')||v.parentElement," +
                        "t=(v.getAttribute('title')||v.getAttribute('aria-label')||(r&&r.textContent)||document.title||'Shit Show')" +
                        ".replace(/\\s+/g,' ').trim().slice(0,120);" +
                        "CSResolveBridge.onMedia(String(s),t);return;}}" +
                        "}catch(e){}})();",
                null
        );
    }

    private static void observeRequest(
            WebResourceRequest request,
            AtomicReference<String> requestMedia,
            AtomicReference<String> requestKind
    ) {
        if (request == null || request.getUrl() == null || !"GET".equalsIgnoreCase(request.getMethod())) return;
        String url = cleanUrl(request.getUrl().toString());
        if (!isHttp(url) || looksLikeAdHost(url)) return;

        String accept = "";
        try {
            Map<String, String> headers = request.getRequestHeaders();
            if (headers != null) {
                accept = headers.get("Accept");
                if (accept == null) accept = headers.get("accept");
                if (accept == null) accept = "";
            }
        } catch (Exception ignored) {
        }

        String lowerAccept = accept.toLowerCase(Locale.US);
        boolean mediaRequest = isDirectMedia(url)
                || lowerAccept.contains("video/")
                || lowerAccept.contains("mpegurl")
                || lowerAccept.contains("dash+xml");
        if (!mediaRequest) return;

        if (requestMedia.compareAndSet("", url)) {
            if (lowerAccept.contains("mpegurl")) requestKind.set("m3u8");
            else if (lowerAccept.contains("dash+xml")) requestKind.set("mpd");
            else {
                String fromUrl = kindFromUrl(url);
                requestKind.set(fromUrl.isEmpty() ? "mp4" : fromUrl);
            }
        }
    }

    private static String markDirect(String value, String kind) {
        String url = cleanUrl(value);
        if (!isHttp(url) || isDirectMedia(url)) return url;
        String normalizedKind = safe(kind).toLowerCase(Locale.US);
        if (!"m3u8".equals(normalizedKind) && !"mpd".equals(normalizedKind)
                && !"webm".equals(normalizedKind) && !"m4v".equals(normalizedKind)) {
            normalizedKind = "mp4";
        }
        String separator = url.contains("#") ? "&" : "#";
        return url + separator + DIRECT_MARKER + "." + normalizedKind;
    }

    private static String kindFromUrl(String value) {
        String lower = safe(value).toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) return "m3u8";
        if (lower.contains(".mpd")) return "mpd";
        if (lower.contains(".webm")) return "webm";
        if (lower.contains(".m4v")) return "m4v";
        if (lower.contains(".mp4")) return "mp4";
        return "";
    }

    private static boolean isStoryUrl(String value) {
        try {
            Uri uri = Uri.parse(cleanUrl(value));
            String host = safe(uri.getHost()).toLowerCase(Locale.US);
            String path = safe(uri.getPath()).toLowerCase(Locale.US);
            return ("crazyshit.com".equals(host) || "www.crazyshit.com".equals(host))
                    && path.startsWith("/shitshow/")
                    && path.length() > "/shitshow/".length();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isCrazyShitHost(String value) {
        try {
            Uri uri = Uri.parse(cleanUrl(value));
            String host = safe(uri.getHost()).toLowerCase(Locale.US);
            return "crazyshit.com".equals(host) || "www.crazyshit.com".equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean looksLikeAdHost(String value) {
        String lower = safe(value).toLowerCase(Locale.US);
        return lower.contains("clickwhole") || lower.contains("trustkiwi")
                || lower.contains("doubleclick") || lower.contains("googlesyndication")
                || lower.contains("google-analytics") || lower.contains("googletagmanager")
                || lower.contains("cloudflareinsights");
    }

    private static boolean isDirectMedia(String value) {
        String lower = safe(value).toLowerCase(Locale.US);
        if (!isHttp(lower)) return false;
        return lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m4v")
                || lower.contains(".m3u8") || lower.contains(".mpd");
    }

    private static boolean isHttp(String value) {
        String lower = safe(value).toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String cleanUrl(String value) {
        if (value == null) return "";
        String url = value.trim().replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&");
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return "https://crazyshit.com" + url;
        return url;
    }

    private static String safe(CharSequence value) {
        return value == null ? "" : value.toString().replaceAll("\\s+", " ").trim();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void destroy(WebView view) {
        if (view == null) return;
        try {
            view.stopLoading();
            view.removeJavascriptInterface("CSResolveBridge");
            view.setWebViewClient(null);
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
            view.destroy();
        } catch (Exception ignored) {
        }
    }

    private static final class Bridge {
        private final AtomicReference<String> media;
        private final AtomicReference<String> title;
        private final CountDownLatch done;

        Bridge(AtomicReference<String> media, AtomicReference<String> title, CountDownLatch done) {
            this.media = media;
            this.title = title;
            this.done = done;
        }

        @JavascriptInterface
        public void onMedia(String value, String videoTitle) {
            String candidate = cleanUrl(value);
            if (!isHttp(candidate)) return;
            media.compareAndSet("", candidate);
            String cleanTitle = safe(videoTitle);
            if (!cleanTitle.isEmpty()) title.set(cleanTitle);
            done.countDown();
        }
    }
}
