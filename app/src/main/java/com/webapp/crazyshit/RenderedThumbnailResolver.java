package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONTokener;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RenderedThumbnailResolver {
    interface Callback {
        void onResolved(String pageUrl, String thumbnailUrl);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final String THUMB_JS =
            "(() => {" +
            "const abs=u=>{try{return u?new URL(u,document.baseURI).href:''}catch(e){return ''}};" +
            "const good=u=>{u=abs(u);return /^https?:\\/\\//i.test(u)?u:''};" +
            "let v=document.querySelector('video');" +
            "if(v){let x=good(v.poster||v.getAttribute('poster'));if(x)return x;}" +
            "for(const s of ['meta[property=\\\"og:image\\\"]','meta[property=\\\"og:image:secure_url\\\"]','meta[name=\\\"twitter:image\\\"]','meta[name=\\\"twitter:image:src\\\"]']){" +
            "let m=document.querySelector(s);if(m){let x=good(m.content||m.getAttribute('content'));if(x)return x;}}" +
            "for(const sc of document.querySelectorAll('script[type=\\\"application/ld+json\\\"]')){" +
            "try{let o=JSON.parse(sc.textContent||'{}');let q=[o];while(q.length){let n=q.shift();if(!n||typeof n!=='object')continue;" +
            "let t=n.thumbnailUrl||n.thumbnail||n.image;if(Array.isArray(t))t=t[0];if(t&&typeof t==='object')t=t.url||t.contentUrl;let x=good(t);if(x)return x;" +
            "for(const k in n){let z=n[k];if(z&&typeof z==='object')q.push(z);}}}catch(e){}}" +
            "let imgs=[...document.images].sort((a,b)=>(b.naturalWidth*b.naturalHeight)-(a.naturalWidth*a.naturalHeight));" +
            "for(const i of imgs){let x=good(i.currentSrc||i.src||i.getAttribute('data-src')||i.getAttribute('data-original'));if(x)return x;}" +
            "for(const e of document.querySelectorAll('*')){let bg='';try{bg=getComputedStyle(e).backgroundImage||''}catch(err){};let m=bg.match(/url\\([\\\"']?([^\\\"')]+)[\\\"']?\\)/i);if(m){let x=good(m[1]);if(x)return x;}}" +
            "return '';" +
            "})()";

    private final Context context;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final Map<String, Boolean> pending = new HashMap<>();
    private final CrazyShitRepository repository = new CrazyShitRepository();

    private WebView webView;
    private String currentPage;
    private boolean busy;
    private int attempt;

    RenderedThumbnailResolver(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        createWebView();
    }

    void request(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) return;
        synchronized (pending) {
            if (pending.containsKey(pageUrl)) return;
            pending.put(pageUrl, Boolean.TRUE);
        }
        main.post(() -> {
            queue.offer(pageUrl);
            startNext();
        });
    }

    private void createWebView() {
        main.post(() -> {
            if (webView != null) return;
            webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setUserAgentString(USER_AGENT);
            settings.setLoadsImagesAutomatically(true);
            settings.setBlockNetworkImage(false);
            try {
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
            } catch (Exception ignored) {
            }
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (!busy || currentPage == null) return;
                    attempt = 0;
                    main.postDelayed(RenderedThumbnailResolver.this::probeRenderedPage, 900L);
                }
            });
        });
    }

    private void startNext() {
        if (busy || webView == null) return;
        currentPage = queue.poll();
        if (currentPage == null) return;
        busy = true;
        attempt = 0;
        try {
            webView.stopLoading();
            webView.loadUrl(currentPage);
        } catch (Exception e) {
            fallbackToVideo(currentPage);
        }
    }

    private void probeRenderedPage() {
        if (!busy || webView == null || currentPage == null) return;
        final String page = currentPage;
        try {
            webView.evaluateJavascript(THUMB_JS, raw -> {
                String resolved = decodeJsString(raw);
                if (isUsable(resolved)) {
                    finish(page, resolved);
                    return;
                }
                attempt++;
                if (attempt < 3) {
                    main.postDelayed(this::probeRenderedPage, attempt == 1 ? 1000L : 1500L);
                } else {
                    fallbackToVideo(page);
                }
            });
        } catch (Exception e) {
            fallbackToVideo(page);
        }
    }

    private void fallbackToVideo(String page) {
        if (page == null) {
            finish(null, "");
            return;
        }
        io.execute(() -> {
            String result = "";
            try {
                CrazyShitRepository.StreamInfo stream = repository.resolvePlayable(context, page);
                if (stream != null && stream.mediaUrl != null && !stream.mediaUrl.isEmpty()) {
                    result = extractFrame(stream.mediaUrl, page);
                }
            } catch (Exception ignored) {
            }
            final String resolved = result;
            main.post(() -> finish(page, resolved));
        });
    }

    private String extractFrame(String mediaUrl, String pageUrl) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", USER_AGENT);
            headers.put("Referer", pageUrl);
            try {
                String cookies = CookieManager.getInstance().getCookie(mediaUrl);
                if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                    cookies = CookieManager.getInstance().getCookie(pageUrl);
                }
                if (cookies != null && !cookies.isEmpty()) headers.put("Cookie", cookies);
            } catch (Exception ignored) {
            }

            retriever.setDataSource(mediaUrl, headers);
            Bitmap bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bitmap == null) {
                bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            if (bitmap == null) return "";

            File dir = new File(context.getCacheDir(), "native_thumbnails");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, sha256(pageUrl) + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out);
            }
            bitmap.recycle();
            return Uri.fromFile(file).toString();
        } catch (Exception ignored) {
            return "";
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void finish(String page, String result) {
        if (page != null && callback != null && isUsable(result)) {
            callback.onResolved(page, result);
        }
        if (page != null) {
            synchronized (pending) {
                pending.put(page, Boolean.FALSE);
            }
        }
        if (page != null && page.equals(currentPage)) {
            busy = false;
            currentPage = null;
            try {
                if (webView != null) webView.loadUrl("about:blank");
            } catch (Exception ignored) {
            }
            main.post(this::startNext);
        }
    }

    private String decodeJsString(String raw) {
        if (raw == null || raw.equals("null")) return "";
        try {
            Object value = new JSONTokener(raw).nextValue();
            return value instanceof String ? (String) value : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isUsable(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String lower = value.trim().toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://");
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest((value == null ? "" : value).getBytes("UTF-8"));
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
        return out.toString();
    }
}
