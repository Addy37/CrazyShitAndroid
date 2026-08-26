package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Locale;

/** Posts through the site's own authenticated form so its session and anti-forgery fields remain valid. */
final class NativeCommentPoster {
    interface Callback {
        void onPosted();
        void onLoginRequired();
        void onError(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NativeCommentPoster() {
    }

    static void post(
            Activity activity,
            String pageUrl,
            String message,
            NativeCommentsLoader.Comment replyTo,
            Callback callback
    ) {
        Runnable start = () -> {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                if (callback != null) callback.onError("The video page is no longer open.");
                return;
            }
            new Request(activity, clean(pageUrl), cleanMessage(message), replyTo, callback).start();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) start.run();
        else MAIN.post(start);
    }

    private static final class Request {
        private static final int MAX_PREPARE_ATTEMPTS = 6;
        private static final int MAX_VERIFY_ATTEMPTS = 4;

        final Activity activity;
        final String url;
        final String message;
        final NativeCommentsLoader.Comment replyTo;
        final Callback callback;
        final Runnable hardTimeout = () -> completeError("The comment took too long to submit. Try again.");

        WebView webView;
        ViewGroup host;
        int prepareAttempts;
        int verifyAttempts;
        boolean replyActionTried;
        boolean replyFallback;
        boolean submissionAttempted;
        boolean finished;

        Request(
                Activity activity,
                String url,
                String message,
                NativeCommentsLoader.Comment replyTo,
                Callback callback
        ) {
            this.activity = activity;
            this.url = url;
            this.message = message;
            this.replyTo = replyTo;
            this.callback = callback;
        }

        @SuppressLint("SetJavaScriptEnabled")
        void start() {
            if (url.isEmpty() || message.isEmpty()) {
                completeError("Write a comment before posting.");
                return;
            }
            try {
                webView = new WebView(activity);
                webView.setAlpha(0.01f);
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
                settings.setLoadsImagesAutomatically(false);
                settings.setBlockNetworkImage(true);
                settings.setMediaPlaybackRequiresUserGesture(true);
                settings.setJavaScriptCanOpenWindowsAutomatically(false);
                settings.setSupportMultipleWindows(false);
                settings.setUserAgentString(CommentsActivity.USER_AGENT);

                try {
                    CookieManager cookies = CookieManager.getInstance();
                    cookies.setAcceptCookie(true);
                    cookies.setAcceptThirdPartyCookies(webView, false);
                    cookies.flush();
                } catch (Exception ignored) {
                }

                host = activity.findViewById(android.R.id.content);
                if (host != null) {
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
                    params.gravity = Gravity.BOTTOM | Gravity.END;
                    host.addView(webView, params);
                }

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return !isCrazyShit(request == null ? null : request.getUrl());
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String nextUrl) {
                        return !isCrazyShit(nextUrl == null ? null : Uri.parse(nextUrl));
                    }

                    @Override
                    public void onPageFinished(WebView view, String loadedUrl) {
                        if (finished) return;
                        try {
                            CookieManager.getInstance().flush();
                        } catch (Exception ignored) {
                        }
                        view.postDelayed(
                                submissionAttempted ? Request.this::verifySubmission : Request.this::prepareSubmission,
                                submissionAttempted ? 500L : 600L
                        );
                    }

                    @Override
                    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        completeError("The comment service stopped unexpectedly. Try again.");
                        return true;
                    }
                });

                webView.postDelayed(hardTimeout, 20000L);
                webView.loadUrl(url);
            } catch (Exception e) {
                completeError("Comments couldn't connect to the website.");
            }
        }

        void prepareSubmission() {
            if (finished || submissionAttempted || webView == null) return;
            if (activity.isFinishing() || activity.isDestroyed()) {
                completeError("The video page was closed.");
                return;
            }
            try {
                webView.evaluateJavascript(prepareScript(replyTo, replyActionTried), raw -> {
                    if (finished || submissionAttempted) return;
                    String state = state(raw);
                    if ("ready".equals(state)) {
                        submitNow();
                        return;
                    }
                    if ("login".equals(state)) {
                        completeLoginRequired();
                        return;
                    }
                    if ("reply-clicked".equals(state)) replyActionTried = true;
                    if ("reply-fallback".equals(state)) {
                        replyActionTried = true;
                        replyFallback = true;
                    }
                    prepareAttempts++;
                    if (prepareAttempts < MAX_PREPARE_ATTEMPTS && webView != null) {
                        long delay = "reply-fallback".equals(state)
                                ? 80L
                                : prepareAttempts < 3 ? 650L : 1050L;
                        webView.postDelayed(this::prepareSubmission, delay);
                    } else {
                        completeError(replyTo == null
                                ? "The website didn't expose its comment box."
                                : "The website didn't expose a reply box for this comment.");
                    }
                });
            } catch (Exception e) {
                completeError("The comment box couldn't be prepared.");
            }
        }

        void submitNow() {
            if (finished || submissionAttempted || webView == null) return;
            submissionAttempted = true;
            try {
                webView.evaluateJavascript(submitScript(message, replyTo, replyFallback), raw -> {
                    if (finished) return;
                    String state = state(raw);
                    if ("login".equals(state)) {
                        completeLoginRequired();
                    } else if ("submitted".equals(state)) {
                        if (webView != null) webView.postDelayed(this::verifySubmission, 850L);
                    } else if ("missing".equals(state)) {
                        submissionAttempted = false;
                        completeError("The website rejected the comment form before it was sent.");
                    } else if (webView != null) {
                        // A standard form submit can navigate before evaluateJavascript returns.
                        webView.postDelayed(this::verifySubmission, 900L);
                    }
                });
            } catch (Exception e) {
                // A normal form navigation can destroy the JavaScript context before its callback runs.
                if (webView != null) webView.postDelayed(this::verifySubmission, 900L);
            }
        }

        void verifySubmission() {
            if (finished || !submissionAttempted || webView == null) return;
            try {
                webView.evaluateJavascript(verifyScript(message), raw -> {
                    if (finished) return;
                    JSONObject result = decode(raw);
                    String nextState = result == null ? "" : result.optString("state", "");
                    if ("success".equals(nextState)) {
                        completePosted();
                        return;
                    }
                    if ("login".equals(nextState)) {
                        completeLoginRequired();
                        return;
                    }
                    if ("error".equals(nextState)) {
                        String error = clean(result.optString("message", ""));
                        completeError(error.isEmpty() ? "The website rejected the comment." : error);
                        return;
                    }
                    verifyAttempts++;
                    if (verifyAttempts < MAX_VERIFY_ATTEMPTS && webView != null) {
                        webView.postDelayed(this::verifySubmission, 850L);
                    } else {
                        // Some successful posts are queued for moderation and do not render immediately.
                        completePosted();
                    }
                });
            } catch (Exception e) {
                verifyAttempts++;
                if (verifyAttempts < MAX_VERIFY_ATTEMPTS && webView != null) {
                    webView.postDelayed(this::verifySubmission, 850L);
                } else {
                    completePosted();
                }
            }
        }

        void completePosted() {
            if (!finish()) return;
            if (callback != null) callback.onPosted();
        }

        void completeLoginRequired() {
            if (!finish()) return;
            if (callback != null) callback.onLoginRequired();
        }

        void completeError(String message) {
            if (!finish()) return;
            if (callback != null) callback.onError(message);
        }

        boolean finish() {
            if (finished) return false;
            finished = true;
            cleanup();
            return true;
        }

        void cleanup() {
            if (webView == null) return;
            try {
                webView.removeCallbacks(hardTimeout);
                webView.stopLoading();
                webView.setWebViewClient(null);
                if (webView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) webView.getParent()).removeView(webView);
                }
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
            host = null;
        }
    }

    private static String prepareScript(
            NativeCommentsLoader.Comment replyTo,
            boolean replyActionTried
    ) {
        String author = JSONObject.quote(replyTo == null ? "" : clean(replyTo.author));
        String text = JSONObject.quote(replyTo == null ? "" : clean(replyTo.text));
        return "(() => {" +
                "const author=" + author + ",targetText=" + text + ",replyTried=" + replyActionTried + ";" +
                commonScript() +
                "let root=findRoot(author,targetText);" +
                "if(targetText&&!replyTried){" +
                "let reply=findReply(root);" +
                "if(reply){reply.click();return JSON.stringify({state:'reply-clicked'});}" +
                "return JSON.stringify({state:'reply-fallback'});" +
                "}" +
                "let field=findField(root);" +
                "if(field)return JSON.stringify({state:'ready'});" +
                "return JSON.stringify({state:needsLogin()?'login':'wait'});" +
                "})()";
    }

    private static String submitScript(
            String message,
            NativeCommentsLoader.Comment replyTo,
            boolean replyFallback
    ) {
        String author = JSONObject.quote(replyTo == null ? "" : clean(replyTo.author));
        String text = JSONObject.quote(replyTo == null ? "" : clean(replyTo.text));
        String body = JSONObject.quote(message);
        return "(() => {" +
                "const author=" + author + ",targetText=" + text + ",message=" + body + ";" +
                commonScript() +
                "let root=findRoot(author,targetText),field=findField(root);" +
                "if(!field)return JSON.stringify({state:needsLogin()?'login':'missing'});" +
                "let value=message;" +
                "if(targetText&&" + replyFallback + "&&author){" +
                "let handle=author.startsWith('@')?author:'@'+author;value=handle+' '+message;" +
                "}" +
                "if(field.matches('textarea,input'))field.value=value;else field.textContent=value;" +
                "field.dispatchEvent(new Event('input',{bubbles:true}));" +
                "field.dispatchEvent(new Event('change',{bubbles:true}));" +
                "let form=field.closest('form'),scope=form||(root||field.parentElement||document);" +
                "let submit=[...scope.querySelectorAll('button,input[type=submit],[role=button]')].find(e=>{" +
                "let t=actionText(e);return visible(e)&&(t.includes('post')||t.includes('reply')||t.includes('comment')||t.includes('send')||t.includes('submit'));" +
                "});" +
                "if(submit){submit.click();return JSON.stringify({state:'submitted'});}" +
                "if(form){if(form.requestSubmit)form.requestSubmit();else form.submit();return JSON.stringify({state:'submitted'});}" +
                "return JSON.stringify({state:'missing'});" +
                "})()";
    }

    private static String verifyScript(String message) {
        return "(() => {" +
                "const clean=s=>(s||'').replace(/\\s+/g,' ').trim();" +
                "const visible=e=>!!e&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';" +
                "const body=clean(document.body?document.body.innerText:'');" +
                "const needle=" + JSONObject.quote(message.toLowerCase(Locale.ROOT)) + ";" +
                "let fields=[...document.querySelectorAll('textarea,[contenteditable=true],input[type=text],input:not([type])')].filter(e=>{if(!visible(e))return false;let f=e.closest('form'),s=((e.name||'')+' '+(e.id||'')+' '+(typeof e.className==='string'?e.className:'')+' '+(e.getAttribute('placeholder')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(f?((f.action||'')+' '+(f.id||'')+' '+(typeof f.className==='string'?f.className:'')):'')).toLowerCase();return s.includes('comment')||s.includes('reply');});" +
                "let stillEditing=fields.some(e=>clean(e.matches('textarea,input')?e.value:e.textContent).toLowerCase()===needle);" +
                "let posted=/comment\\s+(?:was\\s+)?(?:posted|submitted|received|queued)|thanks\\s+for\\s+(?:your\\s+)?comment/i.test(body);" +
                "let roots=[...document.querySelectorAll('[data-comment-id],[id*=comment],.comment,.comment-item,.comment_text,.comment-text')].filter(e=>!e.closest('form'));" +
                "let rendered=needle&&roots.some(e=>clean(e.innerText||e.textContent).toLowerCase().includes(needle));" +
                "if(posted||(!stillEditing&&rendered))return JSON.stringify({state:'success'});" +
                "const login=!fields.length&&(/log\\s*in\\s+to\\s+comment|login\\s+to\\s+comment/i.test(body)||!!document.querySelector('form[action*=login] input[type=password]'));" +
                "if(login)return JSON.stringify({state:'login'});" +
                "let errors=[...document.querySelectorAll('.error,.errors,.alert-danger,.validation-error,[class*=error]')].filter(visible).map(e=>clean(e.innerText||e.textContent)).filter(t=>t&&t.length<280);" +
                "if(errors.length)return JSON.stringify({state:'error',message:errors[0]});" +
                "return JSON.stringify({state:'wait'});" +
                "})()";
    }

    private static String commonScript() {
        return "const clean=s=>(s||'').replace(/\\s+/g,' ').trim();" +
                "const visible=e=>!!e&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';" +
                "const actionText=e=>clean(e&&((e.innerText||e.textContent||e.value||e.getAttribute('aria-label')))).toLowerCase();" +
                "const commentish=e=>{let s=((e&&e.id)||'')+' '+((e&&typeof e.className==='string')?e.className:'');return !!(e&&e.getAttribute&&e.getAttribute('data-comment-id'))||s.toLowerCase().includes('comment');};" +
                "const roots=()=>[...document.querySelectorAll('[data-comment-id],[id*=comment],div,li,article,section')].filter(commentish);" +
                "const findRoot=(a,t)=>{if(!t)return null;let needle=t.slice(0,100).toLowerCase(),who=(a||'').toLowerCase(),all=roots();let matches=all.filter(e=>{let v=clean(e.innerText||e.textContent).toLowerCase();return v.includes(needle)&&(!who||v.includes(who));});if(!matches.length)matches=all.filter(e=>clean(e.innerText||e.textContent).toLowerCase().includes(needle));matches.sort((x,y)=>clean(x.innerText||x.textContent).length-clean(y.innerText||y.textContent).length);return matches[0]||null;};" +
                "const findReply=root=>{if(!root)return null;let scope=root.parentElement||root;return [...scope.querySelectorAll('a,button,[role=button],input[type=button]')].find(e=>{let t=actionText(e);return t==='reply'||t.startsWith('reply ');})||null;};" +
                "const findField=root=>{let fields=[...document.querySelectorAll('textarea,[contenteditable=true],input[type=text],input:not([type])')].filter(visible);if(root){let nested=fields.find(e=>root.contains(e));if(nested)return nested;}return fields.find(e=>{let f=e.closest('form'),s=((e.name||'')+' '+(e.id||'')+' '+(typeof e.className==='string'?e.className:'')+' '+(e.getAttribute('placeholder')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(f?((f.action||'')+' '+(f.id||'')+' '+(typeof f.className==='string'?f.className:'')):'')).toLowerCase();return s.includes('comment')||s.includes('reply');})||null;};" +
                "const needsLogin=()=>{let body=clean(document.body?document.body.innerText:'');return /log\\s*in\\s+to\\s+comment|login\\s+to\\s+comment/i.test(body)||!!document.querySelector('form[action*=login] input[type=password]');};";
    }

    private static String state(String raw) {
        JSONObject object = decode(raw);
        return object == null ? "" : object.optString("state", "");
    }

    private static JSONObject decode(String raw) {
        if (raw == null || raw.equals("null")) return null;
        try {
            Object outer = new JSONTokener(raw).nextValue();
            if (!(outer instanceof String)) return null;
            return new JSONObject((String) outer);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isCrazyShit(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        return host != null && (host.equalsIgnoreCase("crazyshit.com")
                || host.toLowerCase(Locale.ROOT).endsWith(".crazyshit.com"));
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String cleanMessage(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').trim();
    }
}
