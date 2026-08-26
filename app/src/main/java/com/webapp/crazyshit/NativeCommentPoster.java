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
        private static final int MAX_PREPARE_ATTEMPTS = 8;
        private static final int MAX_VERIFY_ATTEMPTS = 12;

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

                webView.postDelayed(hardTimeout, 25000L);
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
                    if ("reply-missing".equals(state)) {
                        completeError("CrazyShit didn't expose a reply form for this comment.");
                        return;
                    }
                    prepareAttempts++;
                    if (prepareAttempts < MAX_PREPARE_ATTEMPTS && webView != null) {
                        long delay = "reply-clicked".equals(state)
                                ? 250L
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
                webView.evaluateJavascript(submitScript(message, replyTo), raw -> {
                    if (finished) return;
                    String state = state(raw);
                    if ("login".equals(state)) {
                        completeLoginRequired();
                    } else if ("submitted".equals(state)) {
                        if (webView != null) webView.postDelayed(this::verifySubmission, 350L);
                    } else if ("missing".equals(state)) {
                        submissionAttempted = false;
                        completeError("CrazyShit didn't expose a valid comment form. Your text was kept.");
                    } else {
                        submissionAttempted = false;
                        completeError("CrazyShit didn't start the comment request. Your text was kept.");
                    }
                });
            } catch (Exception e) {
                submissionAttempted = false;
                completeError("The comment request couldn't start. Your text was kept.");
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
                        completeError(error.isEmpty()
                                ? "CrazyShit rejected the comment. Your text was kept."
                                : error);
                        return;
                    }
                    verifyAttempts++;
                    if (verifyAttempts < MAX_VERIFY_ATTEMPTS && webView != null) {
                        webView.postDelayed(this::verifySubmission, 650L);
                    } else {
                        completeError("CrazyShit didn't confirm the comment. Your text was kept so you can retry.");
                    }
                });
            } catch (Exception e) {
                verifyAttempts++;
                if (verifyAttempts < MAX_VERIFY_ATTEMPTS && webView != null) {
                    webView.postDelayed(this::verifySubmission, 650L);
                } else {
                    completeError("CrazyShit didn't confirm the comment. Your text was kept so you can retry.");
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
                "let root=findRoot(author,targetText),replying=!!targetText;" +
                "if(targetText&&!replyTried){" +
                "let reply=findReply(root);" +
                "if(reply){reply.click();return JSON.stringify({state:'reply-clicked'});}" +
                "return JSON.stringify({state:'reply-missing'});" +
                "}" +
                "let field=findField(root,replying);" +
                "if(field)return JSON.stringify({state:'ready'});" +
                "return JSON.stringify({state:needsLogin()?'login':'wait'});" +
                "})()";
    }

    private static String submitScript(
            String message,
            NativeCommentsLoader.Comment replyTo
    ) {
        String author = JSONObject.quote(replyTo == null ? "" : clean(replyTo.author));
        String text = JSONObject.quote(replyTo == null ? "" : clean(replyTo.text));
        String body = JSONObject.quote(message);
        return "(() => {" +
                "const author=" + author + ",targetText=" + text + ",message=" + body + ";" +
                commonScript() +
                "let root=findRoot(author,targetText),replying=!!targetText,field=findField(root,replying);" +
                "if(!field)return JSON.stringify({state:needsLogin()?'login':'missing'});" +
                "let form=field.form||field.closest('form');" +
                "if(!form)return JSON.stringify({state:'missing'});" +
                "if(field.matches('textarea,input')){" +
                "let proto=field.matches('textarea')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;" +
                "let setter=Object.getOwnPropertyDescriptor(proto,'value');" +
                "if(setter&&setter.set)setter.set.call(field,message);else field.value=message;" +
                "}else field.textContent=message;" +
                "try{field.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:message}));}" +
                "catch(ignore){field.dispatchEvent(new Event('input',{bubbles:true}));}" +
                "field.dispatchEvent(new Event('change',{bubbles:true}));" +
                "let buttons=[...form.querySelectorAll('button,input[type=submit]')].filter(e=>!e.disabled);" +
                "let submit=buttons.find(e=>{let t=actionText(e);return t.includes(replying?'reply':'post')||t.includes('comment')||t.includes('send')||t.includes('submit');})||buttons[0]||null;" +
                "let data=new FormData(form);" +
                "if(field.name&&!data.has(field.name))data.append(field.name,message);" +
                "if(submit&&submit.name&&!data.has(submit.name))data.append(submit.name,submit.value||clean(submit.textContent)||'submit');" +
                "let action=(submit&&submit.getAttribute('formaction'))||form.action||location.href;" +
                "let method=((submit&&submit.getAttribute('formmethod'))||form.method||'post').toUpperCase();" +
                "let target;try{target=new URL(action,location.href);}catch(ignore){return JSON.stringify({state:'missing'});}" +
                "if(target.origin!==location.origin)return JSON.stringify({state:'missing'});" +
                "window.__csNativePostResult={state:'sending'};" +
                "(async()=>{try{" +
                "let options={method:method,credentials:'include',redirect:'follow',referrer:location.href};" +
                "if(method==='GET'){for(let pair of data.entries()){if(typeof pair[1]==='string')target.searchParams.append(pair[0],pair[1]);}}" +
                "else{let encoding=(form.enctype||'').toLowerCase();" +
                "if(encoding.includes('application/x-www-form-urlencoded')){let encoded=new URLSearchParams();for(let pair of data.entries()){if(typeof pair[1]==='string')encoded.append(pair[0],pair[1]);}options.body=encoded;options.headers={'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'};}" +
                "else options.body=data;}" +
                "let response=await fetch(target.toString(),options),raw=await response.text();" +
                "let doc;try{doc=new DOMParser().parseFromString(raw,'text/html');}catch(ignore){doc=null;}" +
                "let responseText=clean(doc&&doc.body?doc.body.innerText:raw),lower=responseText.toLowerCase(),needle=message.toLowerCase();" +
                "let login=(response.url||'').toLowerCase().includes('/login')||/log\\s*in\\s+to\\s+comment|login\\s+to\\s+comment/i.test(responseText);" +
                "let error='';if(doc){let nodes=[...doc.querySelectorAll('.error,.errors,.alert-danger,.validation-error,[aria-invalid=true]')];error=nodes.map(e=>clean(e.innerText||e.textContent)).find(t=>t&&t.length<280)||'';}" +
                "let rejected=/comment\\s+(?:could\\s+not|was\\s+not|failed)|unable\\s+to\\s+(?:post|submit)|invalid\\s+comment|comment\\s+is\\s+required/i.test(responseText);" +
                "let explicit=/comment\\s+(?:was\\s+)?(?:posted|submitted|received|queued|added)|thanks\\s+for\\s+(?:your\\s+)?comment/i.test(responseText)||/\"(?:success|status)\"\\s*:\\s*(?:true|\"success\"|\"ok\")/i.test(raw);" +
                "let rendered=false;if(doc&&needle){let commentRoots=[...doc.querySelectorAll('[data-comment-id],[id*=comment],.comment,.comment-item,.comment_text,.comment-text')].filter(e=>!e.closest('form'));rendered=commentRoots.some(e=>clean(e.innerText||e.textContent).toLowerCase().includes(needle));}" +
                "let accepted=response.ok&&!login&&!error&&!rejected&&(explicit||rendered||response.redirected||response.status===201||response.status===204);" +
                "if(accepted){window.__csNativePostResult={state:'success'};return;}" +
                "if(login){window.__csNativePostResult={state:'login'};return;}" +
                "let reason=error||(response.ok?'CrazyShit did not confirm the comment. Your text was kept so you can retry.':'CrazyShit returned HTTP '+response.status+'. Your text was kept.');" +
                "window.__csNativePostResult={state:'error',message:reason};" +
                "}catch(error){window.__csNativePostResult={state:'error',message:'The comment request failed. Your text was kept so you can retry.'};}})();" +
                "return JSON.stringify({state:'submitted'});" +
                "})()";
    }

    private static String verifyScript(String message) {
        return "(() => {" +
                "let result=window.__csNativePostResult;" +
                "if(!result)return JSON.stringify({state:'wait'});" +
                "return JSON.stringify(result);" +
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
                "const fieldMeta=e=>{let f=e&&e.closest('form');return (((e&&e.name)||'')+' '+((e&&e.id)||'')+' '+((e&&typeof e.className==='string')?e.className:'')+' '+((e&&e.getAttribute('placeholder'))||'')+' '+((e&&e.getAttribute('aria-label'))||'')+' '+(f?((f.action||'')+' '+(f.id||'')+' '+(typeof f.className==='string'?f.className:'')):'')).toLowerCase();};" +
                "const findField=(root,replying)=>{let fields=[...document.querySelectorAll('textarea,[contenteditable=true],input[type=text],input:not([type])')].filter(visible);if(root){let nested=fields.filter(e=>root.contains(e));let exact=nested.find(e=>fieldMeta(e).includes(replying?'reply':'comment'));if(exact)return exact;if(replying&&nested.length)return nested[0];}let tagged=fields.filter(e=>{let s=fieldMeta(e);return s.includes(replying?'reply':'comment');});if(tagged.length)return tagged[tagged.length-1];return replying?null:(fields.length===1?fields[0]:null);};" +
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
