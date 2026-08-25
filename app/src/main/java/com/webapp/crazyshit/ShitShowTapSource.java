package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
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
import java.util.Set;

/**
 * Harvests the embedded Shit Show story list and returns normal CrazyShit permalinks.
 * The repository resolves those permalinks into playable streams later.
 */
final class ShitShowTapSource {
    private static final String PAGE = CrazyShitRepository.BASE + "shitshow/";
    private static final int MAX_CACHE = 80;
    private static final int TARGET_CACHE = 24;
    private static final long FIRST_BATCH_WAIT_MS = 6_500L;
    private static final long HARVEST_TIMEOUT_MS = 18_000L;
    private static final long PUMP_MS = 850L;
    private static final long DIAGNOSTIC_KEEP_MS = 30_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final ArrayDeque<NativeContentItem> ready = new ArrayDeque<>();
    private final Set<String> seenStoryUrls = new HashSet<>();

    private boolean warming;
    private WebView webView;
    private TextView diagnosticView;
    private long harvestStartedAt;
    private int pumpCount;
    private int storiesSeen;
    private int storiesAccepted;
    private int directStories;
    private int pageStories;
    private int pageFinishedCount;
    private String pageState = "idle";
    private String firstTitle = "";
    private String firstPage = "";
    private String lastError = "";

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
        // Keep process-level Shit Show history. Chaos has its own 500-URL repeat protection too.
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
        view.setImportantForAccessibility(WebView.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
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

        view.addJavascriptInterface(new Bridge(), "CSShitBridge");
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView web, WebResourceRequest request) {
                if (request == null || request.getUrl() == null || !request.isForMainFrame()) return false;
                return blockExternalMainFrame(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView web, String url) {
                return blockExternalMainFrame(url);
            }

            @Override
            public void onPageStarted(WebView web, String url, android.graphics.Bitmap favicon) {
                pageState = "started";
                refreshDiagnosticView();
            }

            @Override
            public void onPageFinished(WebView web, String url) {
                pageFinishedCount++;
                pageState = "loaded";
                harvestStories(web);
                schedulePump(web);
                refreshDiagnosticView();
            }
        });

        pageState = "loading";
        refreshDiagnosticView();
        view.loadUrl(PAGE);

        main.postDelayed(() -> {
            if (webView != view || !isWarming()) return;
            harvestStories(view);
            schedulePump(view);
        }, 1_500L);
    }

    private boolean blockExternalMainFrame(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        if (isCrazyShitPage(value) || "about:blank".equalsIgnoreCase(value.trim())) return false;
        return true;
    }

    private void schedulePump(WebView view) {
        main.removeCallbacksAndMessages(view);
        main.postAtTime(() -> pump(view), view, SystemClock.uptimeMillis() + 250L);
    }

    private void pump(WebView view) {
        if (view == null || webView != view || !isWarming()) return;
        pumpCount++;
        harvestStories(view);

        boolean enough = cachedCount() >= TARGET_CACHE;
        boolean timedOut = System.currentTimeMillis() - harvestStartedAt >= HARVEST_TIMEOUT_MS;
        refreshDiagnosticView();
        if (enough || timedOut) {
            finishHarvest(view);
            return;
        }
        main.postAtTime(() -> pump(view), view, SystemClock.uptimeMillis() + PUMP_MS);
    }

    private void harvestStories(WebView view) {
        if (view != null) view.evaluateJavascript(HARVEST_STORIES_JS, null);
    }

    private void acceptStory(String raw) {
        storiesSeen++;
        try {
            JSONObject json = new JSONObject(raw == null ? "{}" : raw);
            String pageUrl = cleanUrl(json.optString("page", ""));
            String direct = cleanUrl(json.optString("media", ""));
            String title = cleanTitle(json.optString("title", "Shit Show"));
            String poster = cleanUrl(json.optString("poster", ""));
            String views = cleanText(json.optString("views", ""));
            String comments = cleanText(json.optString("comments", ""));

            if (firstPage.isEmpty() && !pageUrl.isEmpty()) firstPage = pageUrl;

            String itemUrl = "";
            boolean directItem = false;
            if (isDirectMedia(direct)) {
                itemUrl = direct;
                directItem = true;
            } else if (isUsableStoryPage(pageUrl)) {
                itemUrl = pageUrl;
            }
            if (itemUrl.isEmpty()) return;

            synchronized (lock) {
                if (!seenStoryUrls.add(itemUrl)) return;
                while (ready.size() >= MAX_CACHE) ready.removeLast();
                ready.addLast(new NativeContentItem(
                        NativeContentItem.KIND_MEDIA,
                        title,
                        itemUrl,
                        poster,
                        views,
                        "Shit Show",
                        comments
                ));
                storiesAccepted++;
                if (directItem) directStories++;
                else pageStories++;
                if (firstTitle.isEmpty()) firstTitle = title;
                lock.notifyAll();
            }
            refreshDiagnosticView();
        } catch (Exception error) {
            lastError = cleanText(error.getClass().getSimpleName() + ": " + error.getMessage());
            refreshDiagnosticView();
        }
    }

    private static boolean isUsableStoryPage(String value) {
        if (!isCrazyShitPage(value)) return false;
        try {
            Uri uri = Uri.parse(cleanUrl(value));
            String path = cleanText(uri.getPath()).toLowerCase(Locale.US);
            if (path.isEmpty() || "/".equals(path)) return false;
            String normalized = path.endsWith("/") ? path : path + "/";
            if ("/shitshow/".equals(normalized)
                    || "/categories/".equals(normalized)
                    || "/trending/".equals(normalized)
                    || "/videos/".equals(normalized)
                    || "/submissions/".equals(normalized)
                    || "/search/".equals(normalized)) {
                return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isCrazyShitPage(String value) {
        try {
            Uri uri = Uri.parse(cleanUrl(value));
            String scheme = cleanText(uri.getScheme()).toLowerCase(Locale.US);
            String host = cleanText(uri.getHost()).toLowerCase(Locale.US);
            return ("http".equals(scheme) || "https".equals(scheme))
                    && ("crazyshit.com".equals(host) || "www.crazyshit.com".equals(host));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isDirectMedia(String value) {
        String lower = cleanText(value).toLowerCase(Locale.US);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) return false;
        return lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m4v")
                || lower.contains(".m3u8") || lower.contains(".mpd");
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

    private static String cleanTitle(String value) {
        String text = cleanText(value);
        if (text.isEmpty() || text.equalsIgnoreCase("Swipe up for next video")) return "Shit Show";
        return text.length() > 120 ? text.substring(0, 120).trim() : text;
    }

    private static String cleanText(CharSequence value) {
        return value == null ? "" : value.toString().replaceAll("\\s+", " ").trim();
    }

    private void resetDiagnostics() {
        pumpCount = 0;
        storiesSeen = 0;
        storiesAccepted = 0;
        directStories = 0;
        pageStories = 0;
        pageFinishedCount = 0;
        pageState = "starting";
        firstTitle = "";
        firstPage = "";
        lastError = "";
    }

    private void attachDiagnosticView(Activity activity) {
        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host == null) return;
        TextView view = new TextView(activity);
        diagnosticView = view;
        view.setTextColor(Color.WHITE);
        view.setTextSize(10.2f);
        view.setGravity(Gravity.START);
        view.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));
        view.setBackgroundColor(Color.argb(220, 0, 0, 0));
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
        text.append("Shit Show B21 ").append(isWarming() ? "RUN" : "STOP").append('\n');
        text.append("page:").append(pageState).append(" f:").append(pageFinishedCount)
                .append(" pump:").append(pumpCount).append('\n');
        text.append("stories:").append(storiesSeen)
                .append(" accepted:").append(storiesAccepted)
                .append(" cache:").append(cachedCount()).append('\n');
        text.append("pageUrls:").append(pageStories).append(" direct:").append(directStories);
        if (!firstTitle.isEmpty()) text.append('\n').append("first:").append(shorten(firstTitle, 56));
        if (!firstPage.isEmpty()) text.append('\n').append("url:").append(shorten(firstPage, 72));
        if (!lastError.isEmpty()) text.append('\n').append("err:").append(shorten(lastError, 72));
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

    private static String shorten(String value, int max) {
        String text = cleanText(value);
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    private final class Bridge {
        @JavascriptInterface
        public void onStory(String raw) {
            acceptStory(raw);
        }

        @JavascriptInterface
        public void onProbeError(String error) {
            lastError = cleanText(error);
            refreshDiagnosticView();
        }
    }

    private static final String HARVEST_STORIES_JS =
            "(function(){try{" +
            "function abs(v){try{return v?new URL(v,location.href).href:'';}catch(e){return String(v||'');}}" +
            "function str(v){return v==null?'':String(v);}" +
            "function first(o,ks){for(var i=0;i<ks.length;i++){var v=o&&o[ks[i]];if(v!=null&&v!=='')return v;}return '';}" +
            "function page(o){var v=first(o,['permalink','link','href','page','page_url','story_url']);if(!v){var u=o&&o.url;if(typeof u==='string'&&!/\\.(mp4|webm|m4v|m3u8|mpd)(\\?|#|$)/i.test(u))v=u;}return abs(v);}" +
            "function media(o){var v=first(o,['video','video_url','media','media_url','stream','stream_url','src','source','file']);if(typeof v==='object'&&v)v=first(v,['url','src','file','mp4','hls']);var s=abs(v);return /\\.(mp4|webm|m4v|m3u8|mpd)(\\?|#|$)/i.test(s)?s:'';}" +
            "function poster(o){var v=first(o,['poster','thumbnail','thumb','image','image_url','thumbnail_url','preview','cover']);if(typeof v==='object'&&v)v=first(v,['url','src','large','medium','small']);return abs(v);}" +
            "function good(a){if(!Array.isArray(a)||!a.length)return false;var hits=0;for(var i=0;i<a.length&&i<10;i++){var o=a[i];if(o&&typeof o==='object'&&(o.permalink||o.id)&&(o.title||o.name||o.permalink))hits++;}return hits>0;}" +
            "function emit(a){if(!good(a))return 0;var n=0;for(var i=0;i<a.length&&i<80;i++){var o=a[i];if(!o||typeof o!=='object')continue;var p=page(o),m=media(o);if(!p&&!m)continue;CSShitBridge.onStory(JSON.stringify({page:p,media:m,title:str(first(o,['title','name','headline','caption'])||'Shit Show'),poster:poster(o),views:str(first(o,['views','view_count','viewCount','views_count'])),comments:str(first(o,['comments','comment_count','commentCount','comments_count'])),id:str(o.id||'')}));n++;}return n;}" +
            "var sent=0,names=['_stories','stories','shitshowStories','shitShowStories','storyList','story_list'];" +
            "for(var i=0;i<names.length;i++){try{sent+=emit(window[names[i]]);}catch(e){}}" +
            "if(!sent){try{var ks=Object.keys(window);for(var j=0;j<ks.length&&j<2500;j++){var v;try{v=window[ks[j]];}catch(e){continue;}if(good(v)){sent+=emit(v);if(sent)break;}}}catch(e){}}" +
            "}catch(e){try{CSShitBridge.onProbeError(String(e&&e.stack||e));}catch(x){}}})();";
}
