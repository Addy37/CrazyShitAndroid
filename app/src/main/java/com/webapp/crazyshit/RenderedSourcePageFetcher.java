package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Browser-engine fallback for source pages that do not expose their feed to a direct HTTP client. */
final class RenderedSourcePageFetcher {
    private static final long TIMEOUT_SECONDS = 20L;
    private static final int MAX_EXTRACTION_ATTEMPTS = 9;
    private static final long EXTRACTION_RETRY_MS = 650L;

    private RenderedSourcePageFetcher() {
    }

    static Document fetch(
            Context context,
            String url,
            String userAgent,
            Map<String, String> headers,
            String referer,
            String readyHrefFragment
    ) throws IOException {
        return fetchInternal(context, url, userAgent, headers, referer, readyHrefFragment, false);
    }

    static Document fetchMedia(
            Context context,
            String url,
            String userAgent,
            Map<String, String> headers,
            String referer
    ) throws IOException {
        return fetchInternal(context, url, userAgent, headers, referer, "", true);
    }

    private static Document fetchInternal(
            Context context,
            String url,
            String userAgent,
            Map<String, String> headers,
            String referer,
            String readyHrefFragment,
            boolean requireMedia
    ) throws IOException {
        Activity activity = activity(context);
        if (activity == null) throw new IOException("No active screen was available for browser fallback");
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IOException("Browser fallback cannot block the main thread");
        }

        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Document> result = new AtomicReference<>();
        AtomicReference<IOException> error = new AtomicReference<>();
        AtomicReference<WebView> activeWebView = new AtomicReference<>();
        AtomicReference<String> capturedMedia = new AtomicReference<>("");
        AtomicBoolean completed = new AtomicBoolean();
        Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> start(
                activity,
                url,
                userAgent,
                headers,
                referer,
                readyHrefFragment,
                requireMedia,
                capturedMedia,
                activeWebView,
                completed,
                result,
                error,
                finished
        ));
        try {
            if (!finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                error.compareAndSet(null, new IOException("Browser fallback timed out"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Browser fallback was interrupted", interrupted);
        } finally {
            completed.set(true);
            main.post(() -> destroy(activeWebView.getAndSet(null)));
        }

        Document document = result.get();
        if (document != null) return document;
        IOException failure = error.get();
        throw failure == null ? new IOException("Browser fallback returned no readable page") : failure;
    }

    private static Activity activity(Context context) {
        Context current = context;
        while (current instanceof android.content.ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context next = ((android.content.ContextWrapper) current).getBaseContext();
            if (next == current) break;
            current = next;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void start(
            Activity activity,
            String url,
            String userAgent,
            Map<String, String> headers,
            String referer,
            String readyHrefFragment,
            boolean requireMedia,
            AtomicReference<String> capturedMedia,
            AtomicReference<WebView> activeWebView,
            AtomicBoolean completed,
            AtomicReference<Document> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            complete(completed, error, finished, new IOException("Screen closed before browser fallback loaded"));
            return;
        }
        try {
            WebView webView = new WebView(activity);
            activeWebView.set(webView);
            webView.setVisibility(android.view.View.INVISIBLE);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setBlockNetworkImage(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setUserAgentString(userAgent);
            webView.setWebViewClient(new WebViewClient() {
                private boolean extractionStarted;

                @Override
                public WebResourceResponse shouldInterceptRequest(
                        WebView view,
                        WebResourceRequest request
                ) {
                    if (requireMedia && request != null && request.getUrl() != null) {
                        String requestUrl = request.getUrl().toString();
                        if (isDirectMediaUrl(requestUrl)) capturedMedia.compareAndSet("", requestUrl);
                    }
                    return super.shouldInterceptRequest(view, request);
                }

                @Override
                public void onPageFinished(WebView view, String finalUrl) {
                    if (extractionStarted || completed.get()) return;
                    extractionStarted = true;
                    view.postDelayed(
                            () -> extract(view, 1, readyHrefFragment, requireMedia, capturedMedia,
                                    completed, result, error, finished),
                            EXTRACTION_RETRY_MS
                    );
                }

                @Override
                public void onReceivedHttpError(
                        WebView view,
                        WebResourceRequest request,
                        WebResourceResponse response
                ) {
                    if (request == null || !request.isForMainFrame() || response == null) return;
                    if (response.getStatusCode() >= 400) {
                        complete(completed, error, finished,
                                new IOException("Browser fallback returned HTTP " + response.getStatusCode()));
                    }
                }

                @Override
                public void onReceivedError(
                        WebView view,
                        WebResourceRequest request,
                        WebResourceError webError
                ) {
                    if (request != null && request.isForMainFrame()) {
                        complete(completed, error, finished,
                                new IOException("Browser fallback could not load the page"));
                    }
                }
            });

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
            params.gravity = Gravity.BOTTOM | Gravity.END;
            activity.addContentView(webView, params);

            HashMap<String, String> requestHeaders = new HashMap<>();
            if (headers != null) requestHeaders.putAll(headers);
            if (referer != null && !referer.trim().isEmpty()) requestHeaders.put("Referer", referer);
            webView.loadUrl(url, requestHeaders);
        } catch (Exception failure) {
            complete(completed, error, finished,
                    new IOException("Browser fallback could not start", failure));
        }
    }

    private static void extract(
            WebView webView,
            int attempt,
            String readyHrefFragment,
            boolean requireMedia,
            AtomicReference<String> capturedMedia,
            AtomicBoolean completed,
            AtomicReference<Document> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (completed.get()) return;
        String script = "(() => {"
                + "const attrs=['href','src','data-src','data-original','data-lazy-src','poster','srcset',"
                + "'content','property','name','title','aria-label','class'];"
                + "const esc=s=>String(s||'').replace(/[&<>\\\"']/g,c=>({'&':'&amp;','<':'&lt;',"
                + "'>':'&gt;','\\\"':'&quot;',\"'\":'&#39;'}[c]));"
                + "const node=e=>{let a='';for(const k of attrs){const v=e.getAttribute(k);"
                + "if(v!==null&&v!=='')a+=' '+k+'=\\\"'+esc(v)+'\\\"';}"
                + "if(e.tagName==='VIDEO'&&e.currentSrc&&/^https?:/i.test(e.currentSrc))"
                + "a+=' data-current-src=\\\"'+esc(e.currentSrc)+'\\\"';"
                + "let inner='';if(e.tagName==='A'){inner=(e.innerHTML||'').slice(0,8000);}"
                + "else if(e.tagName==='SCRIPT'){inner=esc((e.textContent||'').slice(0,500000));}"
                + "else if(/^H[1-6]$/.test(e.tagName)){inner=esc(e.textContent||'');}"
                + "return '<'+e.tagName.toLowerCase()+a+'>'+inner+'</'+e.tagName.toLowerCase()+'>';};"
                + "const selectors='a[href],img,video,source,iframe[src],meta[property],meta[name],"
                + "h1,h2,h3,h4,h5,h6,script';"
                + "const nodes=Array.from(document.querySelectorAll(selectors)).slice(0,3000).map(node);"
                + "const resources=(performance&&performance.getEntriesByType"
                + "?performance.getEntriesByType('resource').map(e=>e.name).filter(u=>"
                + "/^https?:/i.test(u)&&/\\.(m3u8|mpd|mp4|webm|m4v)(?:[?#]|$)/i.test(u)).slice(-40):[]);"
                + "return JSON.stringify({url:location.href,title:document.title||'',"
                + "text:(document.body?document.body.innerText:'').slice(0,16000),nodes,resources});})()";

        webView.evaluateJavascript(script, encoded -> {
            try {
                Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
                if (!(decoded instanceof String)) throw new IOException("Browser returned invalid page data");
                JSONObject payload = new JSONObject((String) decoded);
                String finalUrl = payload.optString("url", webView.getUrl());
                String title = payload.optString("title");
                String body = payload.optString("text");
                JSONArray nodes = payload.optJSONArray("nodes");
                JSONArray resources = payload.optJSONArray("resources");
                StringBuilder html = new StringBuilder("<html><head><title>")
                        .append(Entities.escape(title)).append("</title></head><body>")
                        .append("<div id=\"rendered-source-text\">")
                        .append(Entities.escape(body)).append("</div>");
                if (nodes != null) {
                    for (int index = 0; index < nodes.length(); index++) html.append(nodes.optString(index));
                }
                if (resources != null) {
                    for (int index = 0; index < resources.length(); index++) {
                        String resource = resources.optString(index);
                        if (isDirectMediaUrl(resource)) {
                            html.append("<source src=\"").append(Entities.escape(resource)).append("\">");
                        }
                    }
                }
                String intercepted = capturedMedia.get();
                if (isDirectMediaUrl(intercepted)) {
                    html.append("<source src=\"").append(Entities.escape(intercepted)).append("\">");
                }
                html.append("</body></html>");
                Document document = Jsoup.parse(html.toString(), finalUrl);
                document.setBaseUri(finalUrl);

                boolean pageReady = requireMedia
                        ? readyMedia(document, capturedMedia.get())
                        : ready(document, readyHrefFragment);
                if (pageReady || attempt >= MAX_EXTRACTION_ATTEMPTS) {
                    result.set(document);
                    complete(completed, error, finished, null);
                    return;
                }
                webView.postDelayed(
                        () -> extract(webView, attempt + 1, readyHrefFragment, requireMedia,
                                capturedMedia, completed, result, error, finished),
                        EXTRACTION_RETRY_MS
                );
            } catch (Exception failure) {
                if (attempt < MAX_EXTRACTION_ATTEMPTS) {
                    webView.postDelayed(
                            () -> extract(webView, attempt + 1, readyHrefFragment, requireMedia,
                                    capturedMedia, completed, result, error, finished),
                            EXTRACTION_RETRY_MS
                    );
                } else {
                    complete(completed, error, finished,
                            new IOException("Browser response could not be read", failure));
                }
            }
        });
    }

    private static boolean readyMedia(Document document, String capturedMedia) {
        if (isDirectMediaUrl(capturedMedia)) return true;
        if (document == null) return false;
        for (org.jsoup.nodes.Element media : document.select(
                "video[src],video[data-current-src],video source[src],source[src],"
                        + "meta[property=og:video][content],meta[property=og:video:url][content],"
                        + "meta[property=og:video:secure_url][content],meta[name=twitter:player:stream][content]"
        )) {
            String value = media.hasAttr("data-current-src")
                    ? media.attr("data-current-src")
                    : media.hasAttr("src") ? media.absUrl("src") : media.absUrl("content");
            if (value.isEmpty()) {
                value = media.hasAttr("src") ? media.attr("src") : media.attr("content");
            }
            if (isDirectMediaUrl(value)) return true;
        }
        return false;
    }

    private static boolean isDirectMediaUrl(String value) {
        if (value == null) return false;
        return value.trim().matches(
                "(?i)^https?://[^\\s]+\\.(?:m3u8|mpd|mp4|webm|m4v)(?:[?#].*)?$"
        );
    }

    private static boolean ready(Document document, String hrefFragment) {
        if (document == null) return false;
        if (!document.select("video[src],video source[src],source[src],meta[property=og:video]").isEmpty()) {
            return true;
        }
        String marker = hrefFragment == null ? "" : hrefFragment.trim().toLowerCase(java.util.Locale.US);
        for (org.jsoup.nodes.Element link : document.select("a[href]")) {
            String href = link.absUrl("href").toLowerCase(java.util.Locale.US);
            if (marker.isEmpty() ? !href.isEmpty() : href.contains(marker)) return true;
        }
        return false;
    }

    private static void complete(
            AtomicBoolean completed,
            AtomicReference<IOException> error,
            CountDownLatch finished,
            IOException failure
    ) {
        if (!completed.compareAndSet(false, true)) return;
        if (failure != null) error.compareAndSet(null, failure);
        finished.countDown();
    }

    private static void destroy(WebView webView) {
        if (webView == null) return;
        try {
            webView.stopLoading();
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(webView);
            webView.destroy();
        } catch (Exception ignored) {
        }
    }
}
