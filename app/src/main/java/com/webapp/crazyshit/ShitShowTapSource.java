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
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
 * B19 Shit Show diagnostic source.
 *
 * B18 exposed the real static.crazyshit.com index.min.js bundle, but the page could not read that
 * cross-origin script body because of browser CORS. B19 downloads that first-party bundle from
 * Android, mines its code and route strings, and reports real JavaScript/resource errors.
 */
final class ShitShowTapSource {
    private static final String PAGE = CrazyShitRepository.BASE + "shitshow/";
    private static final int TARGET_CACHE = 24;
    private static final int MAX_CACHE = 80;
    private static final long HARVEST_TIMEOUT_MS = 55_000L;
    private static final long PUMP_MS = 1_100L;
    private static final long FIRST_BATCH_WAIT_MS = 7_000L;
    private static final long DIAGNOSTIC_KEEP_MS = 110_000L;
    private static final int MAX_SCRIPT_BYTES = 2_000_000;

    private static final Pattern MEDIA_IN_TEXT = Pattern.compile(
            "(?i)https?://[^\\s\\\"'<>]+?\\.(?:m3u8|mpd|mp4|webm|m4v)(?:\\?[^\\s\\\"'<>]*)?"
    );
    private static final Pattern QUOTED = Pattern.compile("[\\\"'`]([^\\\"'`]{1,180})[\\\"'`]");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final ArrayDeque<NativeContentItem> ready = new ArrayDeque<>();
    private final Set<String> seenMedia = new HashSet<>();
    private final ArrayDeque<String> usefulNetwork = new ArrayDeque<>();
    private final Set<String> seenUsefulNetwork = new HashSet<>();

    private boolean warming;
    private boolean documentStartSupported;
    private boolean scriptFetchStarted;
    private WebView webView;
    private TextView diagnosticView;
    private long harvestStartedAt;

    private int pumpCount;
    private int observedRequests;
    private int observedPayloads;
    private int observedCandidates;
    private int pageCommitCount;
    private int pageFinishedCount;
    private int earlyEvents;
    private int earlyFetches;
    private int earlyXhrs;
    private int earlyBlobs;
    private int earlyMse;
    private int jsVideoCount;
    private int jsHttpVideoCount;
    private int jsBlobVideoCount;
    private int jsSourceCount;
    private int jsScriptCount;
    private int jsIframeCount;
    private int externalBlocks;

    private String pageState = "idle";
    private String lastPageUrl = "";
    private String jsReadyState = "";
    private String lastEarlyKind = "";
    private String lastInterestingRequest = "";
    private String scriptUrlHint = "";
    private String scriptStatus = "idle";
    private String frameHint = "";
    private String codeHint = "";
    private String routeHint = "";
    private String inlineHint = "";
    private String payloadHint = "";
    private String bodyHint = "";
    private String jsErrorHint = "";
    private String blockedUrl = "";

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
        // Keep process-level media history. Chaos has its own repeat protection too.
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
        installDocumentStartHook(view);
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
                lastPageUrl = safe(url);
                pageState = "started";
                if (!documentStartSupported) web.evaluateJavascript(EARLY_HOOK_JS, null);
                refreshDiagnosticView();
            }

            @Override
            public void onPageCommitVisible(WebView web, String url) {
                pageCommitCount++;
                lastPageUrl = safe(url);
                pageState = "commit";
                installProbe(web);
                refreshDiagnosticView();
            }

            @Override
            public void onPageFinished(WebView web, String url) {
                pageFinishedCount++;
                lastPageUrl = safe(url);
                pageState = "loaded";
                installProbe(web);
                schedulePump(web);
                refreshDiagnosticView();
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

        pageState = "loading-shitshow";
        lastPageUrl = PAGE;
        refreshDiagnosticView();
        view.loadUrl(PAGE);

        main.postDelayed(() -> {
            if (webView != view || !isWarming()) return;
            installProbe(view);
            schedulePump(view);
        }, 2_000L);
    }

    private void installDocumentStartHook(WebView view) {
        documentStartSupported = false;
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                Set<String> origins = new HashSet<>();
                origins.add("https://crazyshit.com");
                origins.add("https://www.crazyshit.com");
                WebViewCompat.addDocumentStartJavaScript(view, EARLY_HOOK_JS, origins);
                documentStartSupported = true;
            }
        } catch (Exception ignored) {
            documentStartSupported = false;
        }
    }

    private boolean blockExternalMainFrame(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        if (isSiteHost(value) || "about:blank".equalsIgnoreCase(value.trim())) return false;
        externalBlocks++;
        blockedUrl = safe(value);
        refreshDiagnosticView();
        return true;
    }

    private static boolean isSiteHost(String value) {
        try {
            Uri uri = Uri.parse(safe(value));
            String scheme = safe(uri.getScheme()).toLowerCase(Locale.US);
            String host = safe(uri.getHost()).toLowerCase(Locale.US);
            return ("http".equals(scheme) || "https".equals(scheme))
                    && ("crazyshit.com".equals(host)
                    || "www.crazyshit.com".equals(host)
                    || "static.crazyshit.com".equals(host));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void schedulePump(WebView view) {
        main.removeCallbacksAndMessages(view);
        main.postAtTime(() -> pump(view), view, SystemClock.uptimeMillis() + 300L);
    }

    private void pump(WebView view) {
        if (view == null || webView != view || !isWarming()) return;
        pumpCount++;
        installProbe(view);
        view.evaluateJavascript(SCAN_AND_ADVANCE_JS, null);

        boolean enough = cachedCount() >= TARGET_CACHE;
        boolean timedOut = System.currentTimeMillis() - harvestStartedAt >= HARVEST_TIMEOUT_MS;
        refreshDiagnosticView();
        if (enough || timedOut) {
            finishHarvest(view);
            return;
        }
        main.postAtTime(() -> pump(view), view, SystemClock.uptimeMillis() + PUMP_MS);
    }

    private void installProbe(WebView view) {
        if (view != null) view.evaluateJavascript(PROBE_JS, null);
    }

    private void observeRequest(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return;
        observedRequests++;
        String url = request.getUrl().toString();
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
        if (isInterestingRequest(url, accept)) lastInterestingRequest = safe(url);
        if (isDirectMedia(url)) enqueue(url, accept, "", "Shit Show", false);
        else if (isMediaMime(accept)) enqueue(url, accept, "", "Shit Show", false);
        refreshDiagnosticView();
    }

    private void observeResource(String url) {
        observedRequests++;
        if (isInterestingRequest(url, "")) lastInterestingRequest = safe(url);
        if (isDirectMedia(url)) enqueue(url, "", "", "Shit Show", false);
    }

    private static boolean isInterestingRequest(String url, String accept) {
        String lower = safe(url).toLowerCase(Locale.US);
        String a = safe(accept).toLowerCase(Locale.US);
        if (a.contains("json") || a.contains("video") || a.contains("mpegurl")) return true;
        return lower.contains("shitshow") || lower.contains("/api/") || lower.contains("ajax")
                || lower.contains("feed") || lower.contains("clip") || lower.contains("video")
                || lower.contains("media") || lower.contains("json");
    }

    private void rememberUsefulNetwork(String value) {
        String url = safe(value);
        if (url.isEmpty()) return;
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("google.com/g/collect") || lower.contains("google-analytics")
                || lower.contains("googletagmanager") || lower.contains("doubleclick")
                || lower.contains("trustkiwi") || lower.contains("clickwhole")) return;
        synchronized (lock) {
            if (!seenUsefulNetwork.add(url)) return;
            while (usefulNetwork.size() >= 5) usefulNetwork.removeFirst();
            usefulNetwork.addLast(url);
        }
    }

    private String networkHint(int offset) {
        synchronized (lock) {
            if (offset < 0 || offset >= usefulNetwork.size()) return "";
            int i = 0;
            for (String value : usefulNetwork) {
                if (i++ == offset) return value;
            }
        }
        return "";
    }

    private void fetchScriptOutsideCors(String scriptUrl) {
        String candidate = safe(scriptUrl);
        if (!isSiteHost(candidate)) return;
        synchronized (lock) {
            if (scriptFetchStarted) return;
            scriptFetchStarted = true;
            scriptStatus = "fetching";
        }
        refreshDiagnosticView();

        String userAgent = "";
        WebView current = webView;
        if (current != null) {
            try {
                userAgent = safe(current.getSettings().getUserAgentString());
            } catch (Exception ignored) {
            }
        }
        String cookie = "";
        try {
            cookie = safe(CookieManager.getInstance().getCookie(PAGE));
        } catch (Exception ignored) {
        }
        final String ua = userAgent;
        final String cookieHeader = cookie;

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(candidate).openConnection();
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Referer", PAGE);
                if (!ua.isEmpty()) connection.setRequestProperty("User-Agent", ua);
                if (!cookieHeader.isEmpty()) connection.setRequestProperty("Cookie", cookieHeader);

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                if (stream == null) throw new IllegalStateException("HTTP " + status + " no body");

                StringBuilder text = new StringBuilder();
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    char[] buffer = new char[8192];
                    int count;
                    while ((count = reader.read(buffer)) >= 0 && text.length() < MAX_SCRIPT_BYTES) {
                        int room = MAX_SCRIPT_BYTES - text.length();
                        text.append(buffer, 0, Math.min(count, room));
                        if (room <= count) break;
                    }
                }

                inspectScriptText(candidate, text.toString());
                scriptStatus = "ok:" + text.length();
            } catch (Exception error) {
                scriptStatus = "ERR " + safe(error.getClass().getSimpleName() + ": " + error.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
                main.post(this::refreshDiagnosticView);
            }
        }, "ShitShowScriptB19").start();
    }

    private void inspectScriptText(String sourceUrl, String script) {
        if (script == null || script.isEmpty()) return;
        String lower = script.toLowerCase(Locale.US);
        String[] keys = {"shitshow", "shit-show", "swipe", "/api/", "fetch(", "$.ajax", "ajax", "video", "next"};
        int best = -1;
        for (String key : keys) {
            int at = lower.indexOf(key);
            if (at >= 0 && (best < 0 || at < best)) best = at;
        }
        if (best >= 0) {
            int start = Math.max(0, best - 220);
            int end = Math.min(script.length(), best + 520);
            codeHint = safe(script.substring(start, end));
        }

        ArrayList<String> routes = new ArrayList<>();
        Matcher strings = QUOTED.matcher(script);
        while (strings.find() && routes.size() < 8) {
            String value = safe(strings.group(1));
            String l = value.toLowerCase(Locale.US);
            if (l.contains("shitshow") || l.contains("/api/") || l.contains("ajax")
                    || l.contains("video") || l.contains("feed") || l.contains("swipe")
                    || l.contains("next") || l.contains("random")) {
                if (!routes.contains(value)) routes.add(value);
            }
        }
        if (!routes.isEmpty()) routeHint = String.join(" | ", routes);

        Matcher media = MEDIA_IN_TEXT.matcher(script);
        while (media.find()) enqueue(media.group(), "", "", "Shit Show", false);

        scriptUrlHint = sourceUrl;
    }

    private void harvestPayload(String sourceUrl, String mime, String raw) {
        if (raw == null || raw.isEmpty()) return;
        observedPayloads++;
        String text = raw.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&");
        payloadHint = safe(text);
        if (payloadHint.length() > 100) payloadHint = payloadHint.substring(0, 100);
        Matcher media = MEDIA_IN_TEXT.matcher(text);
        while (media.find()) enqueue(media.group(), mime, "", "Shit Show", false);
        refreshDiagnosticView();
    }

    private void enqueue(String mediaUrl, String mime, String posterUrl, String title, boolean mediaElement) {
        String media = normalizeCandidate(mediaUrl, mime, mediaElement);
        if (media.isEmpty()) return;
        observedCandidates++;
        synchronized (lock) {
            if (!seenMedia.add(media)) return;
            while (ready.size() >= MAX_CACHE) ready.removeLast();
            ready.addLast(new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    cleanTitle(title),
                    media,
                    cleanUrl(posterUrl),
                    "",
                    "Shit Show",
                    ""
            ));
            lock.notifyAll();
        }
        refreshDiagnosticView();
    }

    private static String normalizeCandidate(String value, String mime, boolean mediaElement) {
        String media = cleanUrl(value);
        if (!isHttpUrl(media)) return "";
        if (isDirectMedia(media)) return media;
        String lowerMime = safe(mime).toLowerCase(Locale.US);
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
        String lower = safe(value).toLowerCase(Locale.US);
        return lower.contains("video/") || lower.contains("mpegurl") || lower.contains("dash+xml");
    }

    private static boolean isDirectMedia(String value) {
        if (!isHttpUrl(value)) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m4v")
                || lower.contains(".m3u8") || lower.contains(".mpd");
    }

    private static boolean isHttpUrl(String value) {
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
        earlyEvents = 0;
        earlyFetches = 0;
        earlyXhrs = 0;
        earlyBlobs = 0;
        earlyMse = 0;
        jsVideoCount = 0;
        jsHttpVideoCount = 0;
        jsBlobVideoCount = 0;
        jsSourceCount = 0;
        jsScriptCount = 0;
        jsIframeCount = 0;
        externalBlocks = 0;
        documentStartSupported = false;
        scriptFetchStarted = false;
        pageState = "starting";
        lastPageUrl = PAGE;
        jsReadyState = "";
        lastEarlyKind = "";
        lastInterestingRequest = "";
        scriptUrlHint = "";
        scriptStatus = "idle";
        frameHint = "";
        codeHint = "";
        routeHint = "";
        inlineHint = "";
        payloadHint = "";
        bodyHint = "";
        jsErrorHint = "";
        blockedUrl = "";
        synchronized (lock) {
            usefulNetwork.clear();
            seenUsefulNetwork.clear();
        }
    }

    private void attachDiagnosticView(Activity activity) {
        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host == null) return;
        TextView view = new TextView(activity);
        diagnosticView = view;
        view.setTextColor(Color.WHITE);
        view.setTextSize(9.4f);
        view.setGravity(Gravity.START);
        view.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));
        view.setBackgroundColor(Color.argb(225, 0, 0, 0));
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
        text.append("Shit Show debug B19  ").append(isWarming() ? "RUN" : "STOP").append('\n');
        text.append("page:").append(pageState)
                .append(" c").append(pageCommitCount).append("/f").append(pageFinishedCount)
                .append(" DOM:").append(jsReadyState.isEmpty() ? "?" : jsReadyState)
                .append(" early:").append(documentStartSupported ? "ON" : "OFF").append('\n');
        text.append("ev:").append(earlyEvents).append(" f:").append(earlyFetches)
                .append(" x:").append(earlyXhrs).append(" blob:").append(earlyBlobs)
                .append(" mse:").append(earlyMse).append(" last:").append(shorten(lastEarlyKind, 12)).append('\n');
        text.append("video:").append(jsVideoCount).append(" http:").append(jsHttpVideoCount)
                .append(" blobv:").append(jsBlobVideoCount).append(" src:").append(jsSourceCount)
                .append(" js:").append(jsScriptCount).append(" fr:").append(jsIframeCount).append('\n');
        text.append("req:").append(observedRequests).append(" payload:").append(observedPayloads)
                .append(" cand:").append(observedCandidates).append(" cache:").append(cachedCount())
                .append(" pump:").append(pumpCount).append(" ext:").append(externalBlocks).append('\n');
        text.append("script:").append(shorten(scriptStatus, 28)).append('\n');
        String net1 = networkHint(0);
        String net2 = networkHint(1);
        if (!net1.isEmpty()) text.append("net1:").append(shorten(net1, 72)).append('\n');
        if (!net2.isEmpty()) text.append("net2:").append(shorten(net2, 72)).append('\n');
        if (!scriptUrlHint.isEmpty()) text.append("js*:").append(shorten(scriptUrlHint, 78)).append('\n');
        if (!routeHint.isEmpty()) text.append("route*:").append(shorten(routeHint, 88)).append('\n');
        if (!codeHint.isEmpty()) text.append("code:").append(shorten(codeHint, 100)).append('\n');
        if (!inlineHint.isEmpty()) text.append("inline:").append(shorten(inlineHint, 88)).append('\n');
        if (!jsErrorHint.isEmpty()) text.append("err:").append(shorten(jsErrorHint, 94)).append('\n');
        if (!frameHint.isEmpty()) text.append("frame:").append(shorten(frameHint, 74)).append('\n');
        if (net1.isEmpty() && net2.isEmpty() && !lastInterestingRequest.isEmpty()) {
            text.append("req*:").append(shorten(lastInterestingRequest, 74)).append('\n');
        }
        if (codeHint.isEmpty() && !payloadHint.isEmpty()) text.append("body*:").append(shorten(payloadHint, 86)).append('\n');
        else if (codeHint.isEmpty() && !bodyHint.isEmpty()) text.append(shorten(bodyHint, 86)).append('\n');
        if (!blockedUrl.isEmpty()) text.append("blocked:").append(shorten(blockedUrl, 70));
        view.setText(text.toString().trim());
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
        public void onEarly(String kind, String url, String meta) {
            earlyEvents++;
            lastEarlyKind = safe(kind);
            if ("fetch".equals(kind)) earlyFetches++;
            else if ("xhr".equals(kind)) earlyXhrs++;
            else if ("blob".equals(kind)) earlyBlobs++;
            else if ("mse".equals(kind)) earlyMse++;
            else if ("js-error".equals(kind) || "promise-error".equals(kind)) jsErrorHint = safe(meta);
            if (("fetch".equals(kind) || "fetch-res".equals(kind)
                    || "xhr".equals(kind) || "xhr-res".equals(kind)) && url != null && !url.isEmpty()) {
                rememberUsefulNetwork(url);
            }
            refreshDiagnosticView();
        }

        @JavascriptInterface
        public void onPayload(String url, String mime, String raw) {
            harvestPayload(url, mime, raw);
        }

        @JavascriptInterface
        public void onState(String raw) {
            try {
                JSONObject json = new JSONObject(raw == null ? "{}" : raw);
                lastPageUrl = safe(json.optString("url", lastPageUrl));
                jsReadyState = safe(json.optString("ready", jsReadyState));
                bodyHint = safe(json.optString("body", bodyHint));
                frameHint = safe(json.optString("frame", frameHint));
                inlineHint = safe(json.optString("inline", inlineHint));
                String candidateScript = safe(json.optString("scriptUrl", ""));
                if (!candidateScript.isEmpty()) {
                    scriptUrlHint = candidateScript;
                    fetchScriptOutsideCors(candidateScript);
                }
                jsVideoCount = json.optInt("videos", jsVideoCount);
                jsHttpVideoCount = json.optInt("httpVideos", jsHttpVideoCount);
                jsBlobVideoCount = json.optInt("blobVideos", jsBlobVideoCount);
                jsSourceCount = json.optInt("sources", jsSourceCount);
                jsScriptCount = json.optInt("scripts", jsScriptCount);
                jsIframeCount = json.optInt("frames", jsIframeCount);
                refreshDiagnosticView();
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void onClip(String raw) {
            try {
                JSONObject json = new JSONObject(raw == null ? "{}" : raw);
                enqueue(
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
            enqueue(url, mime, "", "Shit Show", false);
        }
    }

    private static final String EARLY_HOOK_JS =
            "(function(){try{" +
            "if(window.__csEarlyB19)return;window.__csEarlyB19=true;" +
            "function abs(v){try{return v?new URL(v,location.href).href:'';}catch(e){return String(v||'');}}" +
            "function ev(k,u,m){try{CSShitBridge.onEarly(k,abs(u||''),String(m||''));}catch(e){}}" +
            "function pay(u,m,t){try{if(typeof t==='string'&&t.length)CSShitBridge.onPayload(abs(u||''),String(m||''),t.slice(0,300000));}catch(e){}}" +
            "try{addEventListener('error',function(e){var m='';try{if(e&&e.message)m=e.message+' @ '+(e.filename||'')+':'+(e.lineno||0);else{var t=e&&e.target;m='resource '+((t&&t.tagName)||'?')+' '+((t&&(t.src||t.href))||'');}}catch(x){}ev('js-error','',m||'error');},true);addEventListener('unhandledrejection',function(e){var r=e&&e.reason;ev('promise-error','',r&&(r.stack||r.message||String(r))||'rejection');});}catch(e){}" +
            "try{var of=window.fetch;if(of){window.fetch=function(input){var u='';try{u=typeof input==='string'?input:(input&&input.url)||'';}catch(e){}ev('fetch',u,'');return of.apply(this,arguments).then(function(r){try{var ct=(r.headers&&r.headers.get&&r.headers.get('content-type'))||'';ev('fetch-res',r.url||u,ct);r.clone().text().then(function(t){pay(r.url||u,ct,t);}).catch(function(){});}catch(e){}return r;});};}}catch(e){}" +
            "try{var xo=XMLHttpRequest.prototype.open,xs=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u){this.__csUrl=abs(u);ev('xhr',u,m);return xo.apply(this,arguments);};XMLHttpRequest.prototype.send=function(){try{this.addEventListener('load',function(){try{var ct=this.getResponseHeader('content-type')||'',u=this.responseURL||this.__csUrl||'';ev('xhr-res',u,ct);if(!this.responseType||this.responseType==='text')pay(u,ct,this.responseText||'');}catch(e){}});}catch(e){}return xs.apply(this,arguments);};}catch(e){}" +
            "try{var oc=URL.createObjectURL;if(oc){URL.createObjectURL=function(o){var r=oc.apply(this,arguments);ev('blob',r,Object.prototype.toString.call(o));return r;};}}catch(e){}" +
            "try{if(window.MediaSource&&MediaSource.prototype.addSourceBuffer){var asb=MediaSource.prototype.addSourceBuffer;MediaSource.prototype.addSourceBuffer=function(t){ev('mse','',t);return asb.apply(this,arguments);};}}catch(e){}" +
            "try{var ow=window.WebSocket;if(ow){window.WebSocket=function(u,p){ev('ws',u,'');return p===undefined?new ow(u):new ow(u,p);};window.WebSocket.prototype=ow.prototype;}}catch(e){}" +
            "try{var oe=window.EventSource;if(oe){window.EventSource=function(u,c){ev('eventsource',u,'');return new oe(u,c);};window.EventSource.prototype=oe.prototype;}}catch(e){}" +
            "try{var sa=Element.prototype.setAttribute;Element.prototype.setAttribute=function(n,v){try{var tag=(this.tagName||'').toLowerCase();if((tag==='video'||tag==='source')&&String(n).toLowerCase()==='src')ev('media-src',v,tag);}catch(e){}return sa.apply(this,arguments);};}catch(e){}" +
            "}catch(e){}})();";

    private static final String PROBE_JS =
            "(function(){try{" +
            "function abs(v){try{return v?new URL(v,location.href).href:'';}catch(e){return String(v||'');}}" +
            "function cand(v,m){try{var u=abs(v);if(/^https?:/i.test(u))CSShitBridge.onMediaCandidate(u,String(m||''));}catch(e){}}" +
            "var videos=0,httpVideos=0,blobVideos=0,sources=0;" +
            "document.querySelectorAll('video').forEach(function(v){videos++;var q=v.querySelector('source[src]'),s=v.currentSrc||v.src||(q&&(q.src||q.getAttribute('src')))||'',m=(q&&(q.type||q.getAttribute('type')))||v.getAttribute('type')||'';if(/^https?:/i.test(s))httpVideos++;if(/^blob:/i.test(s))blobVideos++;if(/^https?:/i.test(abs(s))){var r=v.closest('article,section,.swiper-slide,.slide,.item')||v.parentElement,p=v.poster||v.getAttribute('poster')||'',t=(v.getAttribute('title')||v.getAttribute('aria-label')||(r&&r.textContent)||'Shit Show').replace(/\\s+/g,' ').trim().slice(0,120);CSShitBridge.onClip(JSON.stringify({media:abs(s),mime:m,poster:abs(p),title:t}));}try{v.muted=true;v.preload='auto';var pr=v.play();if(pr&&pr.catch)pr.catch(function(){});}catch(e){}});" +
            "document.querySelectorAll('source[src]').forEach(function(s){sources++;cand(s.src||s.getAttribute('src'),s.type||s.getAttribute('type')||'');});" +
            "try{performance.getEntriesByType('resource').forEach(function(e){var n=e.name||'';if(/\\.(mp4|webm|m4v|m3u8|mpd)(\\?|#|$)/i.test(n))cand(n,'');});}catch(e){}" +
            "var scripts=[].slice.call(document.scripts||[]),picked='',inline='';for(var i=0;i<scripts.length;i++){var su=abs(scripts[i].src||'');if(/index\\.min\\.js/i.test(su)){picked=su;break;}}for(var j=0;j<scripts.length&&!inline;j++){if(scripts[j].src)continue;var tx=scripts[j].textContent||'';if(/shitshow|swipe|video|ajax|fetch\\(/i.test(tx))inline=tx.replace(/\\s+/g,' ').trim().slice(0,220);}" +
            "var fr='',fi=document.querySelector('iframe[src]');if(fi)fr=abs(fi.getAttribute('src')||fi.src||'');" +
            "var body=(document.body&&document.body.innerText||'').replace(/\\s+/g,' ').trim();" +
            "CSShitBridge.onState(JSON.stringify({url:location.href,ready:document.readyState,body:body.slice(0,90),scriptUrl:picked,frame:fr,inline:inline,videos:videos,httpVideos:httpVideos,blobVideos:blobVideos,sources:sources,scripts:scripts.length,frames:document.querySelectorAll('iframe').length}));" +
            "}catch(e){}})();";

    private static final String SCAN_AND_ADVANCE_JS =
            "(function(){try{" +
            "var v=document.querySelector('video'),h=Math.max(window.innerHeight||0,600);" +
            "try{var s=v&&v.closest('.swiper,.swiper-container,[class*=swiper],[class*=swipe],[data-swiper]');if(s&&s.swiper&&typeof s.swiper.slideNext==='function')s.swiper.slideNext();}catch(e){}" +
            "try{window.dispatchEvent(new WheelEvent('wheel',{deltaY:Math.max(420,h*.9),bubbles:true,cancelable:true}));}catch(e){}" +
            "try{window.scrollBy(0,Math.max(420,h*.92));}catch(e){}" +
            "}catch(e){}})();";
}
