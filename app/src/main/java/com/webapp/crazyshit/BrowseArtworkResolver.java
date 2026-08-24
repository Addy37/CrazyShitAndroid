package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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

/** Resolves rendered Series/Categories artwork from CrazyShit's browser UI. */
final class BrowseArtworkResolver {
    interface Callback {
        void onResolved(String pageUrl, Map<String, String> artwork);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final String PRIME_JS =
            "(()=>{" +
            "try{for(const i of document.querySelectorAll('img')){i.loading='eager';try{i.decoding='sync'}catch(e){}}}catch(e){};" +
            "const attrs=['data-src','data-original','data-lazy-src','data-url','data-image','data-img','data-poster','data-thumb','data-thumbnail','data-bg','data-background','data-background-image','data-srcset'];" +
            "for(const n of document.querySelectorAll('img,source')){" +
            "for(const a of attrs){let v=n.getAttribute&&n.getAttribute(a);if(!v)continue;" +
            "if(a.includes('srcset')||n.tagName==='SOURCE'){if(!n.getAttribute('srcset'))n.setAttribute('srcset',v);}" +
            "else{let s=n.getAttribute('src')||'';if(!s||/^data:|^about:blank$|^#$/i.test(s))n.setAttribute('src',v);}}}" +
            "for(const n of document.querySelectorAll('[data-bg],[data-background],[data-background-image],[data-image],[data-img],[data-thumb],[data-thumbnail]')){" +
            "let v=n.getAttribute('data-bg')||n.getAttribute('data-background')||n.getAttribute('data-background-image')||n.getAttribute('data-image')||n.getAttribute('data-img')||n.getAttribute('data-thumb')||n.getAttribute('data-thumbnail')||'';" +
            "if(v&&!/^data:/i.test(v)){try{n.style.backgroundImage='url(\\\"'+v.replace(/\\\"/g,'')+'\\\")'}catch(e){}}}" +
            "let y=0,step=Math.max(420,Math.floor((window.innerHeight||800)*0.72));" +
            "let max=Math.max(document.body?document.body.scrollHeight:0,document.documentElement?document.documentElement.scrollHeight:0);" +
            "if(window.__csBrowseSweep)clearInterval(window.__csBrowseSweep);" +
            "window.__csBrowseSweep=setInterval(()=>{try{window.scrollTo(0,y);window.dispatchEvent(new Event('scroll'));window.dispatchEvent(new Event('resize'));}catch(e){}" +
            "y+=step;if(y>max+step){clearInterval(window.__csBrowseSweep);window.__csBrowseSweep=null;setTimeout(()=>{try{window.scrollTo(0,0)}catch(e){}},120);}},110);" +
            "return true;})()";

    private final Context context;
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Request> queue = new ArrayDeque<>();

    private WebView webView;
    private ViewGroup webViewHost;
    private Request current;
    private int attempt;
    private boolean closed;

    BrowseArtworkResolver(Context context) {
        this.activity = context instanceof Activity ? (Activity) context : null;
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
                    if (webViewHost != null && webView.getParent() == webViewHost) {
                        webViewHost.removeView(webView);
                    }
                    webView.destroy();
                } catch (Exception ignored) {
                }
                webView = null;
                webViewHost = null;
            }
        });
    }

    private void createWebView() {
        main.post(() -> {
            if (closed || webView != null) return;
            Context webContext = activity != null ? activity : context;
            webView = new WebView(webContext);
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

            attachRendererToActivity();

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (closed || current == null || url == null || "about:blank".equals(url)) return;
                    attempt = 0;
                    primePage();
                    main.postDelayed(BrowseArtworkResolver.this::probe, 2600L);
                }
            });
            startNext();
        });
    }

    private void attachRendererToActivity() {
        if (activity == null || webView == null) return;
        try {
            View content = activity.findViewById(android.R.id.content);
            if (!(content instanceof ViewGroup)) return;
            webViewHost = (ViewGroup) content;
            // Keep the WebView fully rendered. It sits behind the opaque native app UI.
            webView.setAlpha(1f);
            webView.setVisibility(View.VISIBLE);
            webView.setFocusable(false);
            webView.setFocusableInTouchMode(false);
            webView.setClickable(false);
            webView.setLongClickable(false);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            webViewHost.addView(webView, 0, params);
        } catch (Exception ignored) {
            webViewHost = null;
        }
    }

    private void primePage() {
        if (closed || webView == null || current == null) return;
        try {
            webView.evaluateJavascript(PRIME_JS, null);
        } catch (Exception ignored) {
        }
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
                    primePage();
                    long delay = attempt == 1 ? 1200L : attempt == 2 ? 1800L : 2400L;
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
                "const thumb=u=>{u=abs(u);return /^https?:\\/\\/media\\.crazyshit\\.com\\/thumbs\\//i.test(u)?u:''};" +
                "const cssThumb=s=>{if(!s||s==='none')return '';let re=/url\\([\\\"']?([^\\\"')]+)[\\\"']?\\)/ig,m;" +
                "while((m=re.exec(s))){let x=thumb(m[1]);if(x)return x;}return ''};" +
                "const fromNode=n=>{if(!n)return '';" +
                "for(const k of ['data-src','data-original','data-lazy-src','data-url','data-image','data-img','data-poster','data-thumb','data-thumbnail','data-bg','data-background','data-background-image','poster','src']){" +
                "let x=thumb(n.getAttribute&&n.getAttribute(k));if(x)return x;}" +
                "for(const k of ['data-srcset','srcset']){let s=n.getAttribute&&n.getAttribute(k);if(!s)continue;for(const p of s.split(',')){let x=thumb(p.trim().split(/\\s+/)[0]);if(x)return x;}}" +
                "let x=thumb(n.currentSrc||n.src);if(x)return x;" +
                "if(n.attributes){for(const a of n.attributes){if(!/(?:src|image|img|thumb|poster|background|bg)/i.test(a.name||''))continue;" +
                "x=thumb(a.value||'');if(x)return x;x=cssThumb(a.value||'');if(x)return x;}}" +
                "try{let s=getComputedStyle(n);x=cssThumb(s.backgroundImage||'');if(x)return x;" +
                "for(let i=0;i<s.length;i++){let v=s.getPropertyValue(s[i]);if(v&&v.includes('url(')){x=cssThumb(v);if(x)return x;}}}catch(e){}" +
                "return ''};" +
                "const rect=n=>{try{let r=n.getBoundingClientRect();return {l:r.left,t:r.top,w:r.width,h:r.height,cx:r.left+r.width/2,cy:r.top+r.height/2}}catch(e){return null}};" +
                "const links=[];for(const a of document.querySelectorAll('a[href]')){" +
                "let href=abs(a.getAttribute('href')||a.href);if(!href||!href.includes(marker))continue;let r=rect(a);if(!r)continue;links.push({n:a,href:href,r:r});}" +
                "let out={},scores={};" +
                "const assign=(href,url,score)=>{if(!href||!url)return;if(scores[href]===undefined||score<scores[href]){scores[href]=score;out[href]=url;}};" +
                "for(const l of links){let n=l.n;for(let d=0;d<10&&n;d++,n=n.parentElement){let x=fromNode(n);if(x){assign(l.href,x,d);break;}" +
                "if(n.querySelectorAll){for(const q of Array.from(n.querySelectorAll('*')).slice(0,320)){x=fromNode(q);if(x){assign(l.href,x,d+0.2);break;}}if(out[l.href])break;}}}" +
                "const visuals=[];const seen=new Set();" +
                "for(const n of document.querySelectorAll('img,[style],[data-src],[data-original],[data-lazy-src],[data-image],[data-img],[data-thumb],[data-thumbnail],[data-bg],[data-background],[data-background-image]')){" +
                "let x=fromNode(n);if(!x||seen.has(x))continue;let r=rect(n);if(!r||r.w<20||r.h<20)continue;seen.add(x);visuals.push({n:n,url:x,r:r});}" +
                "for(const v of visuals){let best=null,bestScore=1e18;for(const l of links){" +
                "let dx=v.r.cx-l.r.cx,dy=v.r.cy-l.r.cy;let score=dx*dx+dy*dy;" +
                "try{let p=l.n;for(let d=0;d<8&&p;d++,p=p.parentElement){if(p===v.n||p.contains(v.n)){score*=0.01;break;}}}catch(e){}" +
                "if(score<bestScore){bestScore=score;best=l;}}if(best)assign(best.href,v.url,bestScore+20);}" +
                "if(Object.keys(out).length===0){try{let resources=[];for(const e of performance.getEntriesByType('resource')){let x=thumb(e.name);if(x&&!resources.includes(x))resources.push(x);}" +
                "let ordered=links.slice().sort((a,b)=>a.r.t===b.r.t?a.r.l-b.r.l:a.r.t-b.r.t);let used=new Set();let clean=[];for(const l of ordered){if(used.has(l.href))continue;used.add(l.href);clean.push(l);}" +
                "for(let i=0;i<Math.min(clean.length,resources.length);i++)assign(clean[i].href,resources[i],1e15+i);}catch(e){}}" +
                "return JSON.stringify(out);})(" + marker + ")";
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
        main.postDelayed(this::startNext, 80L);
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
