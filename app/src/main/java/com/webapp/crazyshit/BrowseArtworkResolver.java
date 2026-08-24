package com.webapp.crazyshit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the artwork used by CrazyShit's rendered Series/Categories listing cards.
 *
 * The site applies some browse artwork through rendered/lazy CSS rather than exposing a directly
 * usable image URL in the raw HTML. This resolver loads only the listing page, reads each
 * collection card after rendering, and returns a URL -> artwork map to the native adapter.
 */
final class BrowseArtworkResolver {
    interface Callback {
        void onResolved(String pageUrl, Map<String, String> artwork);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Request> queue = new ArrayDeque<>();

    private WebView webView;
    private Request current;
    private int attempt;
    private boolean closed;

    BrowseArtworkResolver(Context context) {
        this.context = context.getApplicationContext();
        createWebView();
    }

    void request(String pageUrl, String pathMarker, Callback callback) {
        if (closed || pageUrl == null || pageUrl.trim().isEmpty() ||
                pathMarker == null || pathMarker.trim().isEmpty()) return;
        main.post(() -> {
            if (closed) return;
            queue.offer(new Request(pageUrl, pathMarker, callback));
            startNext();
        });
    }

    void close() {
        closed = true;
        main.post(() -> {
            queue.clear();
            current = null;
            if (webView != null) {
                try {
                    webView.stopLoading();
                    webView.loadUrl("about:blank");
                    webView.destroy();
                } catch (Exception ignored) {
                }
                webView = null;
            }
        });
    }

    private void createWebView() {
        main.post(() -> {
            if (closed || webView != null) return;
            webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadsImagesAutomatically(true);
            settings.setBlockNetworkImage(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setUserAgentString(USER_AGENT);
            try {
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
            } catch (Exception ignored) {
            }
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (closed || current == null || url == null || "about:blank".equals(url)) return;
                    attempt = 0;
                    main.postDelayed(BrowseArtworkResolver.this::probe, 350L);
                }
            });
            startNext();
        });
    }

    private void startNext() {
        if (closed || webView == null || current != null) return;
        current = queue.poll();
        if (current == null) return;
        attempt = 0;
        try {
            webView.stopLoading();
            webView.loadUrl(current.pageUrl);
        } catch (Exception e) {
            finish(Collections.emptyMap());
        }
    }

    private void probe() {
        if (closed || webView == null || current == null) return;
        final Request request = current;
        try {
            webView.evaluateJavascript(buildScript(request.pathMarker), raw -> {
                if (closed || current != request) return;
                Map<String, String> result = decodeMap(raw);
                if (!result.isEmpty()) {
                    finish(result);
                    return;
                }
                attempt++;
                if (attempt < 4) {
                    long delay = attempt == 1 ? 500L : attempt == 2 ? 900L : 1400L;
                    main.postDelayed(this::probe, delay);
                } else {
                    finish(Collections.emptyMap());
                }
            });
        } catch (Exception e) {
            finish(Collections.emptyMap());
        }
    }

    private String buildScript(String pathMarker) {
        String marker = JSONObject.quote(pathMarker);
        return "((marker)=>{" +
                "const abs=u=>{try{return u?new URL(u,document.baseURI).href:''}catch(e){return ''}};" +
                "const good=u=>{u=abs(u);if(!/^https?:\\/\\//i.test(u))return '';" +
                "const l=u.toLowerCase();return /(?:logo|sprite|avatar|blank\\.gif|spacer|placeholder)/.test(l)?'':u};" +
                "const cssUrl=s=>{if(!s||s==='none')return '';" +
                "let re=/url\\([\\\"']?([^\\\"')]+)[\\\"']?\\)/ig,m;while((m=re.exec(s))){let x=good(m[1]);if(x)return x;}return ''};" +
                "const fromStyle=(n,pseudo)=>{try{let s=getComputedStyle(n,pseudo||null);if(!s)return '';" +
                "for(const k of ['backgroundImage','content','maskImage','webkitMaskImage','borderImageSource','listStyleImage']){" +
                "let x=cssUrl(s[k]||'');if(x)return x;}" +
                "for(let i=0;i<s.length;i++){let v=s.getPropertyValue(s[i]);if(v&&v.includes('url(')){let x=cssUrl(v);if(x)return x;}}" +
                "}catch(e){}return ''};" +
                "const fromNode=n=>{if(!n)return '';" +
                "for(const k of ['data-src','data-original','data-lazy-src','data-image','data-poster','data-thumb','data-thumbnail','data-bg','data-background','data-background-image','poster','src']){" +
                "let x=good(n.getAttribute&&n.getAttribute(k));if(x)return x;}" +
                "for(const k of ['data-srcset','srcset']){let s=n.getAttribute&&n.getAttribute(k);if(s){let a=s.split(',');for(let i=a.length-1;i>=0;i--){let x=good(a[i].trim().split(/\\s+/)[0]);if(x)return x;}}}" +
                "let x=good(n.currentSrc||n.src);if(x)return x;" +
                "x=fromStyle(n,null);if(x)return x;x=fromStyle(n,'::before');if(x)return x;x=fromStyle(n,'::after');if(x)return x;return ''};" +
                "const pick=a=>{let n=a;for(let d=0;d<8&&n;d++,n=n.parentElement){" +
                "let x=fromNode(n);if(x)return x;" +
                "let nodes=n.querySelectorAll?Array.from(n.querySelectorAll('*')).slice(0,180):[];" +
                "for(const q of nodes){x=fromNode(q);if(x)return x;}}return ''};" +
                "let out={};for(const a of document.querySelectorAll('a[href]')){" +
                "let href=abs(a.getAttribute('href')||a.href);if(!href||!href.includes(marker))continue;" +
                "let x=pick(a);if(x&&!out[href])out[href]=x;}return JSON.stringify(out);" +
                "})(" + marker + ")";
    }

    private Map<String, String> decodeMap(String raw) {
        if (raw == null || raw.equals("null")) return Collections.emptyMap();
        try {
            Object decoded = new JSONTokener(raw).nextValue();
            String json = decoded instanceof String ? (String) decoded : raw;
            JSONObject object = new JSONObject(json);
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String url = keys.next();
                String image = object.optString(url, "");
                if (usable(url) && usable(image)) result.put(normalizeKey(url), image.trim());
            }
            return result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private void finish(Map<String, String> result) {
        Request done = current;
        current = null;
        if (done != null && done.callback != null && !closed) {
            done.callback.onResolved(done.pageUrl, result == null ? Collections.emptyMap() : result);
        }
        if (webView != null) {
            try {
                webView.loadUrl("about:blank");
            } catch (Exception ignored) {
            }
        }
        main.postDelayed(this::startNext, 40L);
    }

    static String normalizeKey(String value) {
        if (value == null) return "";
        String url = value.trim();
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        while (url.endsWith("/") && url.length() > "https://a.b/".length()) {
            url = url.substring(0, url.length() - 1);
        }
        return url.toLowerCase(Locale.US);
    }

    private boolean usable(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String lower = value.trim().toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static final class Request {
        final String pageUrl;
        final String pathMarker;
        final Callback callback;

        Request(String pageUrl, String pathMarker, Callback callback) {
            this.pageUrl = pageUrl;
            this.pathMarker = pathMarker;
            this.callback = callback;
        }
    }
}
