package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Reads Fapello through Android's browser engine when its edge rejects a direct HTTP client. */
final class FapelloWebViewFetcher {
    private static final long TIMEOUT_SECONDS = 18L;
    private static final int MAX_EXTRACTION_ATTEMPTS = 9;
    private static final long EXTRACTION_RETRY_MS = 650L;

    static final class Page {
        final Document document;
        final String bodyText;
        final String finalUrl;

        Page(Document document, String bodyText, String finalUrl) {
            this.document = document;
            this.bodyText = bodyText == null ? "" : bodyText;
            this.finalUrl = finalUrl == null ? "" : finalUrl;
        }
    }

    private FapelloWebViewFetcher() {
    }

    static Page fetch(Context context, String url, String referer) throws IOException {
        Activity activity = activity(context);
        if (activity == null) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.BLOCKED,
                    "Fapello blocked the direct request and no active screen could retry it"
            );
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.NETWORK,
                    "Fapello browser fallback cannot block the main thread"
            );
        }

        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Page> result = new AtomicReference<>();
        AtomicReference<IOException> error = new AtomicReference<>();
        AtomicReference<WebView> activeWebView = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> start(
                activity,
                url,
                referer,
                activeWebView,
                completed,
                result,
                error,
                finished
        ));
        try {
            if (!finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                error.compareAndSet(null, new FapelloSourceException(
                        FapelloSourceException.Reason.NETWORK,
                        "Fapello browser fallback timed out"
                ));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.NETWORK,
                    "Fapello browser fallback was interrupted",
                    interrupted
            );
        } finally {
            completed.set(true);
            main.post(() -> destroy(activeWebView.getAndSet(null)));
        }

        Page page = result.get();
        if (page != null) return page;
        IOException failure = error.get();
        throw failure == null
                ? new FapelloSourceException(
                        FapelloSourceException.Reason.MALFORMED,
                        "Fapello browser returned no readable page"
                )
                : failure;
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
            String referer,
            AtomicReference<WebView> activeWebView,
            AtomicBoolean completed,
            AtomicReference<Page> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            complete(
                    completed,
                    error,
                    finished,
                    new FapelloSourceException(
                            FapelloSourceException.Reason.NETWORK,
                            "Fapello screen closed before loading"
                    )
            );
            return;
        }
        try {
            WebView webView = new WebView(activity);
            activeWebView.set(webView);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setVisibility(android.view.View.INVISIBLE);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setBlockNetworkImage(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setUserAgentString(FapelloRepository.browserUserAgent(activity));
            webView.setWebViewClient(new WebViewClient() {
                private boolean extractionStarted;

                @Override
                public void onPageFinished(WebView view, String finalUrl) {
                    if (extractionStarted || completed.get()) return;
                    extractionStarted = true;
                    view.postDelayed(
                            () -> extract(
                                    view,
                                    1,
                                    completed,
                                    result,
                                    error,
                                    finished
                            ),
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
                    int status = response.getStatusCode();
                    if (status < 400) return;
                    FapelloSourceException.Reason reason = status == 429
                            ? FapelloSourceException.Reason.RATE_LIMITED
                            : status == 404
                            ? FapelloSourceException.Reason.NOT_FOUND
                            : status == 403
                            ? FapelloSourceException.Reason.BLOCKED
                            : FapelloSourceException.Reason.HTTP;
                    complete(completed, error, finished, new FapelloSourceException(
                            reason,
                            status,
                            "Fapello browser returned HTTP " + status
                    ));
                }

                @Override
                public void onReceivedError(
                        WebView view,
                        WebResourceRequest request,
                        WebResourceError webError
                ) {
                    if (request != null && request.isForMainFrame()) {
                        complete(completed, error, finished, new FapelloSourceException(
                                FapelloSourceException.Reason.NETWORK,
                                "Fapello browser could not load the page"
                        ));
                    }
                }
            });
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
            params.gravity = Gravity.BOTTOM | Gravity.END;
            activity.addContentView(webView, params);
            java.util.HashMap<String, String> headers = new java.util.HashMap<>();
            headers.put("Accept-Language", "en-US,en;q=0.9");
            if (referer != null && !referer.trim().isEmpty()) headers.put("Referer", referer);
            webView.loadUrl(url, headers);
        } catch (Exception failure) {
            complete(completed, error, finished, new FapelloSourceException(
                    FapelloSourceException.Reason.NETWORK,
                    "Fapello browser fallback could not start",
                    failure
            ));
        }
    }

    private static void extract(
            WebView webView,
            int attempt,
            AtomicBoolean completed,
            AtomicReference<Page> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (completed.get()) return;
        String script = "(() => {"
                + "const attrs=['href','src','data-src','data-original','data-lazy-src',"
                + "'data-url','data-href','data-post','data-permalink','poster','srcset',"
                + "'content','property','name','rel','type','title','aria-label','class'];"
                + "const esc=s=>String(s||'').replace(/[&<>\\\"']/g,c=>({'&':'&amp;','<':'&lt;',"
                + "'>':'&gt;','\\\"':'&quot;',\"'\":'&#39;'}[c]));"
                + "const node=e=>{let a='';for(const k of attrs){const v=e.getAttribute(k);"
                + "if(v!==null&&v!=='')a+=' '+k+'=\\\"'+esc(v)+'\\\"';}"
                + "let inner='';if(e.tagName==='A'){inner=(e.innerHTML||'').slice(0,5000);}"
                + "else if(e.tagName==='SCRIPT'){inner=esc((e.textContent||'').slice(0,500000));}"
                + "return '<'+e.tagName.toLowerCase()+a+'>'+inner+'</'+e.tagName.toLowerCase()+'>';};"
                + "const selectors='a[href],a[data-href],a[data-url],img,video,source,'"
                + "+'meta[property],meta[name],link[rel=next],script[type=\\\"application/ld+json\\\"]';"
                + "const nodes=Array.from(document.querySelectorAll(selectors)).slice(0,2500).map(node);"
                + "return JSON.stringify({url:location.href,title:document.title||'',"
                + "text:(document.body?document.body.innerText:'').slice(0,12000),nodes});})()";
        webView.evaluateJavascript(script, encoded -> {
            try {
                Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
                if (!(decoded instanceof String)) {
                    throw new IOException("Fapello browser returned invalid page data");
                }
                JSONObject payload = new JSONObject((String) decoded);
                String title = payload.optString("title");
                String body = payload.optString("text");
                String finalUrl = payload.optString("url", webView.getUrl());
                String blocked = title + " " + body;
                if (FapelloRepository.looksBlocked(blocked)) {
                    complete(completed, error, finished, new FapelloSourceException(
                            FapelloSourceException.Reason.BLOCKED,
                            403,
                            "Fapello browser reached a Cloudflare verification page"
                    ));
                    return;
                }
                JSONArray nodes = payload.optJSONArray("nodes");
                StringBuilder html = new StringBuilder("<html><head><title>")
                        .append(Entities.escape(title)).append("</title></head><body>")
                        .append("<div id=\"fapello-rendered-text\">")
                        .append(Entities.escape(body)).append("</div>");
                if (nodes != null) {
                    for (int index = 0; index < nodes.length(); index++) {
                        html.append(nodes.optString(index));
                    }
                }
                html.append("</body></html>");
                Document document = Jsoup.parse(html.toString(), finalUrl);
                document.setBaseUri(finalUrl);
                if (ready(document, body) || attempt >= MAX_EXTRACTION_ATTEMPTS) {
                    result.set(new Page(document, body, finalUrl));
                    complete(completed, error, finished, null);
                    return;
                }
                webView.postDelayed(
                        () -> extract(
                                webView,
                                attempt + 1,
                                completed,
                                result,
                                error,
                                finished
                        ),
                        EXTRACTION_RETRY_MS
                );
            } catch (Exception failure) {
                if (attempt < MAX_EXTRACTION_ATTEMPTS) {
                    webView.postDelayed(
                            () -> extract(
                                    webView,
                                    attempt + 1,
                                    completed,
                                    result,
                                    error,
                                    finished
                            ),
                            EXTRACTION_RETRY_MS
                    );
                } else {
                    complete(completed, error, finished, new FapelloSourceException(
                            FapelloSourceException.Reason.MALFORMED,
                            "Fapello browser response could not be read",
                            failure
                    ));
                }
            }
        });
    }

    private static boolean ready(Document document, String body) {
        if (document == null) return false;
        if (!document.select("video[src],video[data-src],source[src],source[data-src],"
                + "meta[property=og:video],meta[property=og:image]").isEmpty()) return true;
        for (org.jsoup.nodes.Element link : document.select("a[href],a[data-href],a[data-url]")) {
            String value = link.hasAttr("href") ? link.attr("href")
                    : link.hasAttr("data-href") ? link.attr("data-href") : link.attr("data-url");
            String url = FapelloRepository.normalizeUrl(value, document.baseUri());
            if (FapelloRepository.isModelUrl(url) || FapelloRepository.isPostUrl(url) ||
                    url.matches("(?i).*\\.(?:jpe?g|png|webp|avif|gif|mp4|webm|m4v|mov|m3u8|mpd)(?:$|[?#]).*")) {
                return true;
            }
        }
        for (org.jsoup.nodes.Element image : document.select("img[data-src],img[data-original],img[data-lazy-src]")) {
            String value = image.hasAttr("data-original") ? image.attr("data-original")
                    : image.hasAttr("data-src") ? image.attr("data-src") : image.attr("data-lazy-src");
            String url = FapelloRepository.normalizeUrl(value, document.baseUri());
            if (url.matches("(?i).*\\.(?:jpe?g|png|webp|avif|gif)(?:$|[?#]).*")) return true;
        }
        String text = body == null ? "" : body.toLowerCase(java.util.Locale.US);
        return text.contains("no results") || text.contains("not found") ||
                text.matches("(?s).*\\b0\\s+media\\b.*") ||
                text.trim().startsWith("{") || text.trim().startsWith("[");
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
