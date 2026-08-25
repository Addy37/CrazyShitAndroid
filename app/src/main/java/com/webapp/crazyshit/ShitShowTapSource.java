package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
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
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shit Show story source.
 *
 * B19 proved the rendered /shitshow/ document already contains an embedded stories array with
 * CrazyShit story IDs, titles and permalinks. We do not need to reverse-engineer the site's video
 * player or sniff its final MP4. A story permalink can flow through the same resolvePlayable()
 * path used by every other CrazyShit media page.
 */
final class ShitShowTapSource {
    private static final String PAGE = CrazyShitRepository.BASE + "shitshow/";
    private static final int MAX_CACHE = 80;
    private static final int TARGET_CACHE = 24;
    private static final long FIRST_BATCH_WAIT_MS = 6_500L;
    private static final long HARVEST_TIMEOUT_MS = 22_000L;
    private static final long PUMP_MS = 850L;
    private static final long DIAGNOSTIC_KEEP_MS = 35_000L;

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
        // Preserve process-level Shit Show history. Chaos also tracks 500 watched URLs.
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
        if (view == null) return;
        view.evaluateJavascript(HARVEST_STORIES_JS, null);
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
        String lower = value.toLowerCase(Locale.US);
        return !lower.endsWith("/shitshow/") && !lower.endsWith("/shitshow")
                && (lower.contains("/cnt/medias/") || lower.contains("/media/")
                || lower.contains("/video/") || lower.contains("/videos/")
                || lower.contains("/story/") || lower.contains("/stories/"));
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
        String url = value.trim().replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&");
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
        text.append("Shit Show B20 ").append(isWarming() ? "RUN" : "STOP").append('\n');
        text.append("page:").append(pageState).append(" f:").append(pageFinishedCount)
                .append(" pump:").append(pumpCount).append('\n');
        text.append("stories:").append(storiesSeen)
                .append(" accepted:").append(storiesAccepted)
                .append(" cache:").append(cachedCount()).append('\n');
        text.append("pageUrls:").append(pageStories).append(" direct:").append(directStories);
        if (!firstTitle.isEmpty()) text.append('\n').append("first:").append(shorten(firstTitle, 64));
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
            "function media(o){var out='',seen=[];function walk(v,k,d){if(out||v==null||d>4)return;if(typeof v==='string'){var s=abs(v);if(/\\.(mp4|webm|m4v|m3u8|mpd)(\\?|#|$)/i.test(s))out=s;return;}if(typeof v!=='object')return;if(seen.indexOf(v)>=0)return;seen.push(v);if(Array.isArray(v)){for(var i=0;i<v.length&&!out&&i<20;i++)walk(v[i],k,d+1);return;}var keys=Object.keys(v);for(var j=0;j<keys.length&&!out&&j<80;j++){var q=keys[j],l=q.toLowerCase();if(/video|media|file|stream|source|src|url|mp4|hls/.test(l)||d<2)walk(v[q],q,d+1);}}walk(o,'',0);return out;}" +
            "function poster(o){var v=first(o,['poster','thumbnail','thumb','image','image_url','thumbnail_url','preview','cover']);if(typeof v==='object'&&v){v=first(v,['url','src','large','medium','small']);}return abs(v);}" +
            "function page(o){var v=first(o,['permalink','link','href','page','page_url','story_url']);if(!v){var u=o&&o.url;if(typeof u==='string'&&!/\\.(mp4|webm|m4v|m3u8|mpd)(\\?|#|$)/i.test(u))v=u;}return abs(v);}" +
            "function good(a){if(!Array.isArray(a)||!a.length)return false;var hits=0;for(var i=0;i<a.length&&i<8;i++){var o=a[i];if(o&&typeof o==='object'&&(o.permalink||o.id)&&(o.title||o.name||o.permalink))hits++;}return hits>0;}" +
            "function emit(a){if(!good(a))return 0;var n=0;for(var i=0;i<a.length&&i<120;i++){var o=a[i];if(!o||typeof o!=='object')continue;var p=page(o),m=media(o);if(!p&&!m)continue;var title=str(first(o,['title','name','headline','caption'])||'Shit Show');var views=str(first(o,['views','view_count','viewCount','views_count']));var comments=str(first(o,['comments','comment_count','commentCount','comments_count']));CSShitBridge.onStory(JSON.stringify({page:p,media:m,title:title,poster:poster(o),views:views,comments:comments,id:str(o.id||'')}));n++;}return n;}" +
            "var sent=0,names=['_stories','stories','shitshowStories','shitShowStories','storyList','story_list'];for(var i=0;i<names.length;i++){try{sent+=emit(window[names[i]]);}catch(e){}}" +
            "if(!sent){try{var ks=Object.keys(window);for(var j=0;j<ks.length&&j<2500;j++){var v;try{v=window[ks[j]];}catch(e){continue;}if(good(v)){sent+=emit(v);if(sent)break;}}}catch(e){}}" +
            "if(!sent){try{var ss=[].slice.call(document.scripts||[]);for(var q=0;q<ss.length&&!sent;q++){if(ss[q].src)continue;var t=ss[q].textContent||'';if(!/\\b_?stories\\b/.test(t))continue;var re=/(?:var|let|const)?\\s*(_?stories)\\s*=\\s*\\[/g,mx;while((mx=re.exec(t))&&!sent){var st=t.indexOf('[',mx.index),dep=0,quote='',esc=false,end=-1;for(var z=st;z<t.length;z++){var c=t.charAt(z);if(quote){if(esc){esc=false;continue;}if(c==='\\\\'){esc=true;continue;}if(c===quote)quote='';continue;}if(c==='\"'||c===\"'\"||c==='`'){quote=c;continue;}if(c==='[')dep++;else if(c===']'){dep--;if(dep===0){end=z;break;}}}if(end>st){var raw=t.slice(st,end+1),arr=null;try{arr=JSON.parse(raw);}catch(e){try{arr=(new Function('return ('+raw+')'))();}catch(x){}}sent+=emit(arr);}}}}catch(e){}}" +
            "}catch(e){try{CSShitBridge.onProbeError(String(e&&e.stack||e));}catch(x){}}})();";
}
