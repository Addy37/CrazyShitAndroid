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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Uses Android's browser engine when Fapello does not return creator cards to Jsoup. */
final class FapelloWebViewFetcher {
    private static final long TIMEOUT_SECONDS = 20L;
    private static final int MAX_EXTRACTION_ATTEMPTS = 10;
    private static final long EXTRACTION_RETRY_MS = 750L;

    private FapelloWebViewFetcher() {
    }

    static List<FapelloRepository.Model> fetchModelListing(
            Context context,
            String listing,
            int page
    ) throws IOException {
        if (!(context instanceof Activity)) {
            throw new IOException("Fapello browser fallback needs an active screen");
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IOException("Fapello browser fallback cannot block the main thread");
        }
        String endpoint = FapelloRepository.listingUrl(listing, page);
        Activity activity = (Activity) context;
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<List<FapelloRepository.Model>> result = new AtomicReference<>();
        AtomicReference<IOException> error = new AtomicReference<>();
        AtomicReference<WebView> activeWebView = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> start(activity, endpoint, activeWebView, completed, result, error, finished));
        try {
            if (!finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                error.compareAndSet(null, new IOException("Fapello browser fallback timed out"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Fapello browser fallback was interrupted", interrupted);
        } finally {
            completed.set(true);
            main.post(() -> destroy(activeWebView.getAndSet(null)));
        }

        List<FapelloRepository.Model> models = result.get();
        if (models != null && !models.isEmpty()) return models;
        IOException failure = error.get();
        throw failure == null
                ? new IOException("Fapello browser returned no creator cards")
                : failure;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void start(
            Activity activity,
            String endpoint,
            AtomicReference<WebView> activeWebView,
            AtomicBoolean completed,
            AtomicReference<List<FapelloRepository.Model>> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            complete(completed, error, finished,
                    new IOException("Fapello screen closed before loading"));
            return;
        }
        try {
            WebView webView = new WebView(activity);
            activeWebView.set(webView);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setVisibility(android.view.View.INVISIBLE);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setBlockNetworkImage(true);
            webView.setWebViewClient(new WebViewClient() {
                private boolean extractionStarted;

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (extractionStarted || completed.get()) return;
                    extractionStarted = true;
                    view.postDelayed(
                            () -> extract(
                                    view,
                                    endpoint,
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
                public void onReceivedError(
                        WebView view,
                        WebResourceRequest request,
                        WebResourceError webError
                ) {
                    if (request != null && request.isForMainFrame()) {
                        complete(completed, error, finished,
                                new IOException("Fapello browser could not load the creator list"));
                    }
                }
            });
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
            params.gravity = Gravity.BOTTOM | Gravity.END;
            activity.addContentView(webView, params);
            webView.loadUrl(endpoint);
        } catch (Exception failure) {
            complete(completed, error, finished,
                    new IOException("Fapello browser fallback could not start", failure));
        }
    }

    private static void extract(
            WebView webView,
            String endpoint,
            int attempt,
            AtomicBoolean completed,
            AtomicReference<List<FapelloRepository.Model>> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (completed.get()) return;
        String script = "(() => JSON.stringify(Array.from(document.querySelectorAll('a[href]')).map(a => {"
                + "const i=a.querySelector('img');return {href:a.href,text:(a.innerText||a.textContent||'').trim(),"
                + "title:a.getAttribute('title')||'',label:a.getAttribute('aria-label')||'',"
                + "image:i?(i.getAttribute('data-src')||i.getAttribute('data-original')||"
                + "i.getAttribute('data-lazy-src')||i.currentSrc||i.src||''):''};})))()";
        webView.evaluateJavascript(script, value -> {
            try {
                Object decoded = new JSONTokener(value == null ? "null" : value).nextValue();
                if (!(decoded instanceof String)) {
                    throw new IOException("Fapello browser returned an invalid creator list");
                }
                JSONArray links = new JSONArray((String) decoded);
                Document document = Jsoup.parse("<main></main>", endpoint);
                Element main = document.selectFirst("main");
                if (main == null) {
                    throw new IOException("Fapello browser response could not be prepared");
                }
                for (int index = 0; index < links.length(); index++) {
                    JSONObject item = links.optJSONObject(index);
                    if (item == null) continue;
                    Element link = main.appendElement("a")
                            .attr("href", item.optString("href"))
                            .attr("title", item.optString("title"))
                            .attr("aria-label", item.optString("label"))
                            .text(item.optString("text"));
                    String image = item.optString("image");
                    if (!image.isEmpty()) link.appendElement("img").attr("data-src", image);
                }
                List<FapelloRepository.Model> models =
                        new FapelloRepository().parseModelListing(document, endpoint);
                if (models.isEmpty()) {
                    retryOrFinish(
                            webView,
                            endpoint,
                            attempt,
                            completed,
                            result,
                            error,
                            finished,
                            new IOException("Fapello browser returned no creator cards")
                    );
                    return;
                }
                result.set(new ArrayList<>(models));
                complete(completed, error, finished, null);
            } catch (Exception failure) {
                retryOrFinish(
                        webView,
                        endpoint,
                        attempt,
                        completed,
                        result,
                        error,
                        finished,
                        failure instanceof IOException
                                ? (IOException) failure
                                : new IOException(
                                        "Fapello browser response could not be read",
                                        failure
                                )
                );
            }
        });
    }

    private static void retryOrFinish(
            WebView webView,
            String endpoint,
            int attempt,
            AtomicBoolean completed,
            AtomicReference<List<FapelloRepository.Model>> result,
            AtomicReference<IOException> error,
            CountDownLatch finished,
            IOException failure
    ) {
        if (completed.get()) return;
        if (attempt >= MAX_EXTRACTION_ATTEMPTS) {
            complete(completed, error, finished, failure);
            return;
        }
        webView.postDelayed(
                () -> extract(
                        webView,
                        endpoint,
                        attempt + 1,
                        completed,
                        result,
                        error,
                        finished
                ),
                EXTRACTION_RETRY_MS
        );
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
