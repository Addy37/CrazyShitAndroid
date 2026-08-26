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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
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
        private static final int MAX_VERIFY_ATTEMPTS = 16;

        final Activity activity;
        final String url;
        final String message;
        final NativeCommentsLoader.Comment replyTo;
        final Callback callback;
        final Runnable hardTimeout = () -> completeError(
                "CrazyShit did not finish the comment request. Your text was kept. [CS14 timeout]"
        );

        WebView webView;
        ViewGroup host;
        int prepareAttempts;
        int verifyAttempts;
        boolean replyActionTried;
        boolean submissionAttempted;
        boolean verifyScheduled;
        boolean confirmationReloaded;
        boolean finished;
        String lastDiagnostic = "";
        String lastNetworkDiagnostic = "";

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
                webView.setImportantForAccessibility(
                        android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                );
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
                settings.setLoadsImagesAutomatically(true);
                settings.setBlockNetworkImage(false);
                settings.setMediaPlaybackRequiresUserGesture(true);
                settings.setJavaScriptCanOpenWindowsAutomatically(false);
                settings.setSupportMultipleWindows(false);
                settings.setUserAgentString(CommentsActivity.USER_AGENT);

                try {
                    CookieManager cookies = CookieManager.getInstance();
                    cookies.setAcceptCookie(true);
                    cookies.setAcceptThirdPartyCookies(webView, true);
                    cookies.flush();
                } catch (Exception ignored) {
                }

                host = activity.findViewById(android.R.id.content);
                if (host != null) {
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1);
                    params.gravity = Gravity.BOTTOM | Gravity.END;
                    host.addView(webView, params);
                    int screenWidth = Math.max(
                            1,
                            activity.getResources().getDisplayMetrics().widthPixels
                    );
                    webView.setTranslationX(screenWidth * 2f);
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
                        if (submissionAttempted) scheduleVerify(500L);
                        else view.postDelayed(Request.this::prepareSubmission, 600L);
                    }

                    @Override
                    public void onReceivedError(
                            WebView view,
                            WebResourceRequest request,
                            WebResourceError error
                    ) {
                        if (!submissionAttempted || request == null || error == null) return;
                        if (!request.isForMainFrame()
                                && "GET".equalsIgnoreCase(request.getMethod())) return;
                        noteNetworkDiagnostic(
                                "CS14 WebView " + error.getErrorCode() + " "
                                        + safeTarget(request.getUrl())
                        );
                    }

                    @Override
                    public void onReceivedHttpError(
                            WebView view,
                            WebResourceRequest request,
                            WebResourceResponse response
                    ) {
                        if (!submissionAttempted || request == null || response == null) return;
                        if (!request.isForMainFrame()
                                && "GET".equalsIgnoreCase(request.getMethod())) return;
                        noteNetworkDiagnostic(
                                "CS14 HTTP " + response.getStatusCode() + " "
                                        + safeTarget(request.getUrl())
                        );
                    }

                    @Override
                    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        completeError("The comment service stopped unexpectedly. Try again.");
                        return true;
                    }
                });

                webView.postDelayed(hardTimeout, 35000L);
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
            verifyAttempts = 0;
            scheduleVerify(650L);
            try {
                webView.evaluateJavascript(submitScript(message, replyTo), raw -> {
                    if (finished) return;
                    JSONObject result = decode(raw);
                    String nextState = result == null ? "" : result.optString("state", "");
                    String diagnostic = result == null
                            ? ""
                            : clean(result.optString("diagnostic", ""));
                    noteDiagnostic(diagnostic);
                    if ("login".equals(nextState)) {
                        completeLoginRequired();
                    } else if ("missing".equals(nextState)) {
                        submissionAttempted = false;
                        String error = result == null ? "" : clean(result.optString("message", ""));
                        completeError(error.isEmpty()
                                ? "CrazyShit did not expose a posting control. Your text was kept. [CS14 no-control]"
                                : error);
                    }
                });
            } catch (Exception e) {
                // A normal form submit can replace the JavaScript page before this callback returns.
                lastDiagnostic = "CS14 page navigation";
            }
        }

        void verifySubmission() {
            verifyScheduled = false;
            if (finished || !submissionAttempted || webView == null) return;
            try {
                webView.evaluateJavascript(verifyScript(message), raw -> {
                    if (finished) return;
                    JSONObject result = decode(raw);
                    String nextState = result == null ? "" : result.optString("state", "");
                    String diagnostic = result == null
                            ? ""
                            : clean(result.optString("diagnostic", ""));
                    noteDiagnostic(diagnostic);
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
                        scheduleVerify(650L);
                    } else if (!confirmationReloaded && webView != null) {
                        confirmationReloaded = true;
                        verifyAttempts = 0;
                        lastDiagnostic = "CS14 final page recheck";
                        try {
                            webView.loadUrl(url);
                        } catch (Exception e) {
                            completeError(unconfirmedMessage());
                        }
                    } else {
                        completeError(unconfirmedMessage());
                    }
                });
            } catch (Exception e) {
                verifyAttempts++;
                if (verifyAttempts < MAX_VERIFY_ATTEMPTS && webView != null) {
                    scheduleVerify(650L);
                } else if (!confirmationReloaded && webView != null) {
                    confirmationReloaded = true;
                    verifyAttempts = 0;
                    lastDiagnostic = "CS14 final page recheck";
                    try {
                        webView.loadUrl(url);
                    } catch (Exception ignored) {
                        completeError(unconfirmedMessage());
                    }
                } else {
                    completeError(unconfirmedMessage());
                }
            }
        }

        void scheduleVerify(long delayMs) {
            if (finished || !submissionAttempted || webView == null || verifyScheduled) return;
            verifyScheduled = true;
            webView.postDelayed(this::verifySubmission, delayMs);
        }

        void noteDiagnostic(String diagnostic) {
            diagnostic = clean(diagnostic);
            if (diagnostic.isEmpty()) return;
            lastDiagnostic = diagnostic;
            if (diagnostic.contains(" HTTP ")
                    || diagnostic.contains(" xhr ")
                    || diagnostic.contains(" fetch ")
                    || diagnostic.contains("WebView")) {
                lastNetworkDiagnostic = diagnostic;
            }
        }

        void noteNetworkDiagnostic(String diagnostic) {
            noteDiagnostic(diagnostic);
        }

        String unconfirmedMessage() {
            String diagnostic = clean(lastNetworkDiagnostic);
            if (diagnostic.isEmpty()) diagnostic = clean(lastDiagnostic);
            if (diagnostic.isEmpty()) diagnostic = "CS14 no response";
            return "CrazyShit did not confirm the comment. Your text was kept. [" + diagnostic + "]";
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
                "if(!field)return JSON.stringify({state:needsLogin()?'login':'missing',message:'CrazyShit did not expose its comment field. Your text was kept. [CS14 no-field]'});" +
                "let form=field.form||field.closest('form'),needle=message.toLowerCase();" +
                "const safeTarget=raw=>{try{let u=new URL(raw||location.href,location.href),path=(u.pathname||'/').slice(0,72);return u.protocol+'//'+u.host+path;}catch(ignore){return 'unknown';}};" +
                "const upgradeTarget=raw=>{try{let u=new URL(raw||location.href,location.href),host=u.hostname.toLowerCase();if(u.protocol==='http:'&&(host==='crazyshit.com'||host.endsWith('.crazyshit.com')))u.protocol='https:';return u.href;}catch(ignore){return raw;}};" +
                "if(field.matches('textarea,input')){" +
                "let proto=field.matches('textarea')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;" +
                "let setter=Object.getOwnPropertyDescriptor(proto,'value');" +
                "if(setter&&setter.set)setter.set.call(field,message);else field.value=message;" +
                "}else field.textContent=message;" +
                "try{field.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:message}));}" +
                "catch(ignore){field.dispatchEvent(new Event('input',{bubbles:true}));}" +
                "field.dispatchEvent(new Event('change',{bubbles:true}));" +
                "try{field.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Unidentified'}));}catch(ignore){}" +
                "let scope=form||(root||field.parentElement||document);" +
                "let controls=[...scope.querySelectorAll('button,input[type=submit],input[type=button],[role=button],a')].filter(e=>e!==field&&!e.disabled);" +
                "let submit=controls.find(e=>{let t=actionText(e);return replying?(t==='reply'||t.includes('post reply')||t.includes('send reply')):(t.includes('post')||t.includes('add comment')||t.includes('send')||t.includes('submit'));});" +
                "if(!submit&&form)submit=controls.find(e=>e.matches('button[type=submit],button:not([type]),input[type=submit]'))||null;" +
                "if(!submit&&!form)return JSON.stringify({state:'missing',message:'CrazyShit did not expose its posting control. Your text was kept. [CS14 no-control]'});" +
                "window.__csNativePostResult={state:'sending',armed:true,submitSeen:false,requestSeen:false,diagnostic:'CS14 control found'};" +
                "try{sessionStorage.setItem('__csNativePostAttempt',String(Date.now()));}catch(ignore){}" +
                "const setResult=(state,diagnostic,error)=>{let active=window.__csNativePostResult||{};active.state=state;active.diagnostic=diagnostic;if(error)active.message=error;window.__csNativePostResult=active;};" +
                "const inspectResponse=(raw,status,url,type)=>{" +
                "let active=window.__csNativePostResult;if(!active||!active.armed)return;active.requestSeen=true;let target=safeTarget(url);" +
                "let doc=null;try{doc=new DOMParser().parseFromString(raw||'','text/html');}catch(ignore){}" +
                "let responseText=clean(doc&&doc.body?doc.body.innerText:(raw||''));" +
                "let login=(url||'').toLowerCase().includes('/login')||/log\\s*in\\s+to\\s+comment|login\\s+to\\s+comment/i.test(responseText);" +
                "let errors=[];if(doc)errors=[...doc.querySelectorAll('.error,.errors,.alert-danger,.validation-error,[role=alert],[aria-invalid=true]')].map(e=>clean(e.innerText||e.textContent)).filter(t=>t&&t.length<260);" +
                "let rejected=/comment\\s+(?:could\\s+not|was\\s+not|failed)|unable\\s+to\\s+(?:post|submit)|invalid\\s+comment|comment\\s+is\\s+required|something\\s+went\\s+wrong/i.test(responseText);" +
                "let explicit=/comment\\s+(?:was\\s+)?(?:posted|submitted|received|queued|added)|thanks\\s+for\\s+(?:your\\s+)?comment|awaiting\\s+moderation|pending\\s+(?:approval|moderation)/i.test(responseText)||/\"(?:success|status)\"\\s*:\\s*(?:true|\"success\"|\"ok\")/i.test(raw||'');" +
                "let rendered=false;if(doc&&needle){let rows=[...doc.querySelectorAll('[data-comment-id],[id*=comment],.comment,.comment-item,.comment_text,.comment-text')].filter(e=>!e.closest('form'));rendered=rows.some(e=>clean(e.innerText||e.textContent).toLowerCase().includes(needle));}" +
                "let jsonEcho=(type||'').toLowerCase().includes('json')&&needle&&(raw||'').toLowerCase().includes(needle);" +
                "if(login){setResult('login','CS14 login response');return;}" +
                "if(status===0){active.state='response';active.diagnostic='CS14 HTTP 0 '+target;return;}" +
                "if(status>=400||rejected||errors.length){let reason=errors[0]||(status>=400?'CrazyShit returned HTTP '+status+'. Your text was kept.':'CrazyShit rejected the comment. Your text was kept.');setResult('error','CS14 rejected '+status+' '+target,reason);return;}" +
                "if((status>=200&&status<400)&&(explicit||rendered||jsonEcho)){setResult('success','CS14 response accepted '+target);return;}" +
                "active.state='response';active.diagnostic='CS14 HTTP '+status+' '+target+' unconfirmed';" +
                "};" +
                "const inspectDom=()=>{let active=window.__csNativePostResult;if(!active||!active.armed||active.state==='success'||active.state==='error'||active.state==='login')return;" +
                "let page=clean(document.body?document.body.innerText:''),posted=/comment\\s+(?:was\\s+)?(?:posted|submitted|received|queued|added)|thanks\\s+for\\s+(?:your\\s+)?comment|awaiting\\s+moderation|pending\\s+(?:approval|moderation)/i.test(page);" +
                "let rows=[...document.querySelectorAll('[data-comment-id],[id*=comment],.comment,.comment-item,.comment_text,.comment-text')].filter(e=>!e.closest('form')),rendered=needle&&rows.some(e=>clean(e.innerText||e.textContent).toLowerCase().includes(needle));" +
                "if(posted||rendered){setResult('success','CS14 page confirmed');return;}" +
                "if(needsLogin()){setResult('login','CS14 login page');return;}" +
                "if(active.submitSeen||active.requestSeen){let errors=[...document.querySelectorAll('.error,.errors,.alert-danger,.validation-error,[role=alert]')].filter(visible).map(e=>clean(e.innerText||e.textContent)).filter(t=>t&&t.length<260);if(errors.length)setResult('error','CS14 page error',errors[0]);}" +
                "};" +
                "if(window.fetch&&!window.__csNativeFetchWrapped){window.__csNativeFetchWrapped=true;let originalFetch=window.fetch;window.fetch=function(input,init){let method=((init&&init.method)||(input&&input.method)||'GET').toUpperCase(),requestUrl=(typeof input==='string'?input:((input&&input.url)||'')),upgradedUrl=upgradeTarget(requestUrl),actualInput=input;try{if(typeof input==='string')actualInput=upgradedUrl;else if(window.URL&&input instanceof URL)actualInput=new URL(upgradedUrl);else if(input&&input.url&&upgradedUrl!==input.url)actualInput=new Request(upgradedUrl,input);}catch(ignore){}let promise=originalFetch.call(this,actualInput,init);return promise.then(response=>{let active=window.__csNativePostResult;if(active&&active.armed&&method!=='GET'){active.requestSeen=true;active.diagnostic='CS14 fetch '+method+' '+safeTarget(upgradedUrl);response.clone().text().then(raw=>inspectResponse(raw,response.status,response.url||upgradedUrl,response.headers.get('content-type')||'')).catch(()=>{});}return response;},error=>{let active=window.__csNativePostResult;if(active&&active.armed&&method!=='GET'){active.requestSeen=true;active.state='response';active.diagnostic='CS14 fetch failed '+safeTarget(upgradedUrl);}throw error;});};}" +
                "if(window.XMLHttpRequest&&!window.__csNativeXhrWrapped){window.__csNativeXhrWrapped=true;let originalOpen=XMLHttpRequest.prototype.open,originalSend=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(method,url){this.__csMethod=(method||'GET').toUpperCase();this.__csUrl=upgradeTarget(url||'');let args=[...arguments];args[1]=this.__csUrl;return originalOpen.apply(this,args);};XMLHttpRequest.prototype.send=function(){let xhr=this,active=window.__csNativePostResult;if(active&&active.armed&&xhr.__csMethod!=='GET'){active.requestSeen=true;active.diagnostic='CS14 xhr '+xhr.__csMethod+' '+safeTarget(xhr.__csUrl);xhr.addEventListener('error',()=>{xhr.__csFailure='network';},{once:true});xhr.addEventListener('abort',()=>{xhr.__csFailure='aborted';},{once:true});xhr.addEventListener('timeout',()=>{xhr.__csFailure='timeout';},{once:true});xhr.addEventListener('loadend',()=>{let raw='';try{raw=typeof xhr.responseText==='string'?xhr.responseText:'';}catch(ignore){}let type='';try{type=xhr.getResponseHeader('content-type')||'';}catch(ignore){}inspectResponse(raw,xhr.status,xhr.responseURL||xhr.__csUrl,type);if(xhr.status===0&&xhr.__csFailure){let result=window.__csNativePostResult;if(result)result.diagnostic='CS14 xhr '+xhr.__csFailure+' '+safeTarget(xhr.__csUrl);}},{once:true});}return originalSend.apply(this,arguments);};}" +
                "if(form)form.addEventListener('submit',()=>{let active=window.__csNativePostResult;if(active){active.submitSeen=true;active.diagnostic='CS14 submit event';}},{capture:true,once:true});" +
                "try{let observer=new MutationObserver(inspectDom);observer.observe(document.body,{subtree:true,childList:true,characterData:true});}catch(ignore){}" +
                "field.focus();" +
                "try{if(submit)submit.click();else if(form&&form.requestSubmit)form.requestSubmit();else if(form)form.submit();else throw new Error('no control');}" +
                "catch(error){return JSON.stringify({state:'missing',message:'CrazyShit could not activate its posting control. Your text was kept. [CS14 click-failed]'});}" +
                "if(window.__csNativePostResult&&!window.__csNativePostResult.submitSeen&&!window.__csNativePostResult.requestSeen)window.__csNativePostResult.diagnostic='CS14 site control clicked';" +
                "setTimeout(inspectDom,120);setTimeout(inspectDom,600);setTimeout(inspectDom,1500);" +
                "return JSON.stringify({state:'submitted',diagnostic:'CS14 site control clicked'});" +
                "})()";
    }

    private static String verifyScript(String message) {
        String needle = JSONObject.quote(message.toLowerCase(Locale.ROOT));
        return "(() => {" +
                "const clean=s=>(s||'').replace(/\\s+/g,' ').trim(),needle=" + needle + ";" +
                "const visible=e=>!!e&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';" +
                "let result=window.__csNativePostResult||null,page=clean(document.body?document.body.innerText:'');" +
                "let rows=[...document.querySelectorAll('[data-comment-id],[id*=comment],.comment,.comment-item,.comment_text,.comment-text')].filter(e=>!e.closest('form'));" +
                "let rendered=needle&&rows.some(e=>clean(e.innerText||e.textContent).toLowerCase().includes(needle));" +
                "let posted=/comment\\s+(?:was\\s+)?(?:posted|submitted|received|queued|added)|thanks\\s+for\\s+(?:your\\s+)?comment|awaiting\\s+moderation|pending\\s+(?:approval|moderation)/i.test(page);" +
                "if(posted||rendered){try{sessionStorage.removeItem('__csNativePostAttempt');}catch(ignore){}return JSON.stringify({state:'success',diagnostic:'CS14 page confirmed'});}" +
                "let login=/log\\s*in\\s+to\\s+comment|login\\s+to\\s+comment/i.test(page)||!!document.querySelector('form[action*=login] input[type=password]');" +
                "if(login)return JSON.stringify({state:'login',diagnostic:'CS14 login page'});" +
                "if(result&&(result.state==='success'||result.state==='login'||result.state==='error'))return JSON.stringify(result);" +
                "let attempted=false;try{attempted=!!sessionStorage.getItem('__csNativePostAttempt');}catch(ignore){}" +
                "if(attempted||result){let errors=[...document.querySelectorAll('.error,.errors,.alert-danger,.validation-error,[role=alert]')].filter(visible).map(e=>clean(e.innerText||e.textContent)).filter(t=>t&&t.length<260);if(errors.length)return JSON.stringify({state:'error',message:errors[0],diagnostic:'CS14 page error'});}" +
                "if(result)return JSON.stringify(result);" +
                "return JSON.stringify({state:'wait',diagnostic:attempted?'CS14 navigated, no confirmation':'CS14 no response'});" +
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

    private static String safeTarget(Uri uri) {
        if (uri == null) return "unknown";
        String scheme = clean(uri.getScheme());
        String host = clean(uri.getHost());
        String path = clean(uri.getPath());
        if (scheme.isEmpty() || host.isEmpty()) return "unknown";
        if (path.length() > 72) path = path.substring(0, 72);
        return scheme + "://" + host + path;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String cleanMessage(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').trim();
    }
}
