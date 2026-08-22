package com.webapp.crazyshit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PictureInPictureParams;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Rational;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@UnstableApi
public class MainActivity extends Activity {
    private static final String HOME = "https://crazyshit.com/";
    private static final String RELEASE_API = "https://api.github.com/repos/Addy37/CrazyShitAndroid/releases/latest";
    private static final String RELEASE_PAGE = "https://github.com/Addy37/CrazyShitAndroid/releases/latest";
    private static final long UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final int PICK_FILE = 1001;
    private static final int STORAGE_PERMISSION = 1002;
    private static final int NATIVE_PLAYER = 2001;
    private static final int FAVORITES = 2002;

    private static final String[] BLOCKED_AD_HOSTS = {
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "exoclick.com",
            "exosrv.com",
            "trafficjunky.net",
            "juicyads.com",
            "adsterra.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "hilltopads.net",
            "clickadu.com",
            "onclicka.com",
            "ero-advertising.com",
            "tsyndicate.com",
            "adcash.com",
            "adnxs.com"
    };

    private FrameLayout root;
    private FrameLayout webFrame;
    private FrameLayout videoFrame;
    private SwipeRefreshLayout swipeRefresh;
    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorView;
    private TextView updateBanner;
    private TextView menuButton;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> fileCallback;
    private PendingDownload pendingDownload;
    private OnBackInvokedCallback backCallback;
    private boolean backCallbackRegistered;
    private volatile boolean probingNativeVideo;
    private volatile String lastDetectedMediaUrl;
    private boolean initialPageShown;

    private MaterialCardView miniPlayerCard;
    private PlayerView miniPlayerView;
    private TextView miniTitleView;
    private ExoPlayer miniPlayer;
    private String miniMediaUrl;
    private String miniPageUrl;
    private String miniTitle;
    private String miniUserAgent;
    private String miniCookies;
    private boolean miniResumePlayback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);

        buildUi();
        configureWebView();
        configureBackHandling();
        restoreKnownUpdate();
        checkForUpdates(false);

        String requestedStart = getIntent().getStringExtra("start_url");
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("age_warning_accepted", false)) {
            showAgeWarning();
        } else if (state == null || webView.restoreState(state) == null) {
            webView.loadUrl(requestedStart == null || requestedStart.isEmpty() ? HOME : requestedStart);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 13, 15));

        webFrame = new FrameLayout(this);
        webFrame.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        root.addView(webFrame, match());

        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setOnRefreshListener(() -> {
            errorView.setVisibility(View.GONE);
            probingNativeVideo = false;
            lastDetectedMediaUrl = null;
            if (webView.getUrl() == null) webView.loadUrl(HOME); else webView.reload();
        });
        webFrame.addView(swipeRefresh, match());

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 13, 15));
        webView.setAlpha(1f);
        swipeRefresh.addView(webView, match());
        swipeRefresh.setOnChildScrollUpCallback((parent, child) -> webView.canScrollVertically(-1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.getProgressDrawable().setTint(Color.WHITE);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1, dp(3));
        progressParams.gravity = Gravity.TOP;
        webFrame.addView(progress, progressParams);

        errorView = makeErrorView();
        errorView.setVisibility(View.GONE);
        webFrame.addView(errorView, match());

        updateBanner = new TextView(this);
        updateBanner.setTextColor(Color.WHITE);
        updateBanner.setTextSize(14);
        updateBanner.setGravity(Gravity.CENTER);
        updateBanner.setPadding(dp(12), dp(10), dp(12), dp(10));
        updateBanner.setBackground(rounded(Color.rgb(35, 35, 40), dp(16)));
        updateBanner.setVisibility(View.GONE);
        updateBanner.setElevation(dp(5));
        FrameLayout.LayoutParams updateParams = new FrameLayout.LayoutParams(-1, -2);
        updateParams.gravity = Gravity.BOTTOM;
        updateParams.setMargins(dp(12), 0, dp(12), dp(68));
        webFrame.addView(updateBanner, updateParams);

        menuButton = new TextView(this);
        menuButton.setText("⋮");
        menuButton.setTextColor(Color.WHITE);
        menuButton.setTextSize(28);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setBackground(rounded(Color.argb(238, 30, 30, 34), dp(24)));
        menuButton.setElevation(dp(8));
        menuButton.setContentDescription("App menu");
        menuButton.setOnClickListener(v -> showAppMenu());
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(50), dp(50));
        menuParams.gravity = Gravity.BOTTOM | Gravity.END;
        menuParams.setMargins(0, 0, dp(12), dp(12));
        webFrame.addView(menuButton, menuParams);

        // Create the Media3 mini-player lazily only after a video is minimized.

        videoFrame = new FrameLayout(this);
        videoFrame.setBackgroundColor(Color.BLACK);
        videoFrame.setVisibility(View.GONE);
        root.addView(videoFrame, match());

        setContentView(root);
    }

    private void buildMiniPlayerUi() {
        miniPlayerCard = new MaterialCardView(this);
        miniPlayerCard.setCardBackgroundColor(Color.rgb(24, 24, 28));
        miniPlayerCard.setRadius(dp(20));
        miniPlayerCard.setCardElevation(dp(8));
        miniPlayerCard.setStrokeWidth(1);
        miniPlayerCard.setStrokeColor(Color.rgb(55, 55, 62));
        miniPlayerCard.setVisibility(View.GONE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));

        miniPlayerView = new PlayerView(this);
        miniPlayerView.setUseController(false);
        miniPlayerView.setBackgroundColor(Color.BLACK);
        miniPlayerView.setOnClickListener(v -> reopenMiniPlayer());
        row.addView(miniPlayerView, new LinearLayout.LayoutParams(dp(132), dp(76)));

        miniTitleView = new TextView(this);
        miniTitleView.setTextColor(Color.WHITE);
        miniTitleView.setTextSize(14);
        miniTitleView.setTypeface(null, android.graphics.Typeface.BOLD);
        miniTitleView.setMaxLines(2);
        miniTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniTitleView.setPadding(dp(12), 0, dp(8), 0);
        miniTitleView.setOnClickListener(v -> reopenMiniPlayer());
        row.addView(miniTitleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView expand = new TextView(this);
        expand.setText("↗");
        expand.setTextColor(Color.WHITE);
        expand.setTextSize(22);
        expand.setGravity(Gravity.CENTER);
        expand.setContentDescription("Open full player");
        expand.setOnClickListener(v -> reopenMiniPlayer());
        row.addView(expand, new LinearLayout.LayoutParams(dp(44), dp(56)));

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextColor(Color.WHITE);
        close.setTextSize(24);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close mini player");
        close.setOnClickListener(v -> {
            haptic(v);
            stopMiniPlayer();
            restoreKnownUpdate();
        });
        row.addView(close, new LinearLayout.LayoutParams(dp(44), dp(56)));

        miniPlayerCard.addView(row);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(88));
        params.gravity = Gravity.BOTTOM;
        params.setMargins(dp(12), 0, dp(12), dp(68));
        webFrame.addView(miniPlayerCard, params);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private LinearLayout makeErrorView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        box.setBackgroundColor(Color.rgb(13, 13, 15));

        TextView title = new TextView(this);
        title.setText("Couldn't load the site");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);

        TextView body = new TextView(this);
        body.setText("Check your connection and try again.");
        body.setTextColor(Color.LTGRAY);
        body.setTextSize(15);
        body.setGravity(Gravity.CENTER);

        Button retry = new Button(this);
        retry.setText("Retry");
        retry.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            if (webView.getUrl() == null) webView.loadUrl(HOME); else webView.reload();
        });

        Button browser = new Button(this);
        browser.setText("Open in browser");
        browser.setOnClickListener(v -> openExternal(Uri.parse(currentUrl())));

        box.addView(title);
        box.addView(body);
        box.addView(retry);
        box.addView(browser);
        return box;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return route(request.getUrl(), request.hasGesture());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return route(Uri.parse(url), true);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                errorView.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefresh.setRefreshing(false);
                updateBackCallback();
                installPopupGuard();
                if (probingNativeVideo && nativePlayerEnabled() && isSameSite(Uri.parse(url))) {
                    probeNativeVideo(url, 0, false);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri requestUri = request.getUrl();
                if (adBlockingEnabled() && isBlockedAdHost(requestUri)) {
                    return emptyWebResponse();
                }
                if (probingNativeVideo && isDirectMedia(requestUri)) {
                    lastDetectedMediaUrl = requestUri.toString();
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                updateBackCallback();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 500) showError();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
                if (value >= 100) swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                videoFrame.addView(view, match());
                videoFrame.setVisibility(View.VISIBLE);
                webFrame.setVisibility(View.GONE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                setFullscreenUi(true);
                configurePip(true);
                updateBackCallback();
            }

            @Override
            public void onHideCustomView() {
                leaveVideo();
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, disposition, mime, length) ->
                startDownload(new PendingDownload(url, userAgent, disposition, mime)));
        webView.setOnLongClickListener(v -> handleLongPress());
    }

    private boolean handleLongPress() {
        WebView.HitTestResult hit = webView.getHitTestResult();
        if (hit == null) return false;
        String url = hit.getExtra();
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) return false;

        int type = hit.getType();
        boolean image = type == WebView.HitTestResult.IMAGE_TYPE ||
                type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
        showLongPressMenu(url, image);
        return true;
    }

    private void showLongPressMenu(String url, boolean image) {
        String[] items = image
                ? new String[]{"Open", "Share", "Copy link", "Save image"}
                : new String[]{"Open", "Share", "Copy link", "Save for later"};

        new AlertDialog.Builder(this)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        Uri uri = Uri.parse(url);
                        if (nativePlayerEnabled() && isDirectMedia(uri)) {
                            openNativePlayer(url, currentUrl(), "Video");
                        } else if (isSameSite(uri)) {
                            webView.loadUrl(url);
                        } else {
                            openExternal(uri);
                        }
                    } else if (which == 1) {
                        shareText(url);
                    } else if (which == 2) {
                        copyText(url);
                    } else if (image && which == 3) {
                        startDownload(new PendingDownload(
                                url,
                                webView.getSettings().getUserAgentString(),
                                null,
                                null
                        ));
                    } else if (!image && which == 3) {
                        String pageTitle = webView.getTitle();
                        FavoriteStore.add(this,
                                pageTitle == null ? "Saved video" : pageTitle,
                                url);
                        Toast.makeText(this, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
                        haptic(webView);
                    }
                })
                .show();
    }

    private boolean route(Uri uri, boolean fromUserGesture) {
        if (uri == null || uri.getScheme() == null) return false;
        String scheme = uri.getScheme();

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (nativePlayerEnabled() && fromUserGesture && isDirectMedia(uri)) {
                openNativePlayer(uri.toString(), currentUrl(), "Video");
                return true;
            }

            if (isSameSite(uri)) {
                if (nativePlayerEnabled() && fromUserGesture) {
                    probingNativeVideo = true;
                    lastDetectedMediaUrl = null;
                }
                return false;
            }

            if (adBlockingEnabled()) {
                if (fromUserGesture) {
                    Toast.makeText(this,
                            "Blocked an external ad/pop-up. Long-press a real external link to open it.",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            openExternal(uri);
            return true;
        }

        if ("about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)) return false;

        if ("intent".equalsIgnoreCase(scheme)) {
            try {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            } catch (URISyntaxException ignored) {
            }
            return true;
        }

        openSystemIntent(uri);
        return true;
    }

    private boolean isSameSite(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        return host != null &&
                (host.equalsIgnoreCase("crazyshit.com") || host.toLowerCase().endsWith(".crazyshit.com"));
    }

    private boolean isDirectMedia(Uri uri) {
        if (uri == null) return false;
        String value = uri.toString().toLowerCase();
        return value.contains(".m3u8") ||
                value.contains(".mpd") ||
                value.contains(".mp4") ||
                value.contains(".webm") ||
                value.contains(".m4v");
    }

    private boolean nativePlayerEnabled() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("native_player_enabled", true);
    }

    private boolean adBlockingEnabled() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("ad_blocking_enabled", true);
    }

    private boolean isBlockedAdHost(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null || isSameSite(uri)) return false;
        host = host.toLowerCase();
        for (String blocked : BLOCKED_AD_HOSTS) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
        }
        return false;
    }

    private WebResourceResponse emptyWebResponse() {
        return new WebResourceResponse(
                "text/plain",
                "utf-8",
                new ByteArrayInputStream(new byte[0])
        );
    }

    private void installPopupGuard() {
        if (!adBlockingEnabled() || webView == null) return;

        String js = "(function(){" +
                "if(window.__csPopupGuard)return;window.__csPopupGuard=true;" +
                "function same(u){try{var x=new URL(u,location.href),h=x.hostname.toLowerCase();return h==='crazyshit.com'||h.endsWith('.crazyshit.com');}catch(e){return false;}}" +
                "window.open=function(u){if(u&&same(u)){location.href=new URL(u,location.href).href;}return null;};" +
                "function fix(a){if(a&&a.tagName==='A'&&a.hasAttribute('target'))a.removeAttribute('target');}" +
                "document.querySelectorAll('a[target]').forEach(fix);" +
                "new MutationObserver(function(ms){ms.forEach(function(m){if(m.type==='attributes')fix(m.target);m.addedNodes.forEach(function(n){if(n.nodeType===1){fix(n);n.querySelectorAll&&n.querySelectorAll('a[target]').forEach(fix);}});});}).observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['target']});" +
                "})()";
        webView.evaluateJavascript(js, null);
    }

    private void probeNativeVideo(String pageUrl, int attempt, boolean userInitiated) {
        if (webView == null || pageUrl == null || pageUrl.isEmpty()) return;

        String js = "(function(){" +
                "function ok(u){return typeof u==='string' && /^https?:\\/\\//i.test(u);}" +
                "var c=[];" +
                "var v=document.querySelector('video');" +
                "if(v){if(ok(v.currentSrc))c.push(v.currentSrc);if(ok(v.src))c.push(v.src);" +
                "v.querySelectorAll('source[src]').forEach(function(s){if(ok(s.src))c.push(s.src);});}" +
                "document.querySelectorAll('video source[src],source[type*=video][src]').forEach(function(s){if(ok(s.src))c.push(s.src);});" +
                "['meta[property=\\\"og:video\\\"]','meta[property=\\\"og:video:url\\\"]','meta[property=\\\"og:video:secure_url\\\"]','meta[name=\\\"twitter:player:stream\\\"]'].forEach(function(q){var m=document.querySelector(q);if(m&&ok(m.content))c.push(m.content);});" +
                "c=c.filter(function(u,i,a){return a.indexOf(u)===i;});" +
                "c.sort(function(a,b){function s(u){u=u.toLowerCase();if(u.indexOf('.m3u8')>=0)return 0;if(u.indexOf('.mpd')>=0)return 1;if(u.indexOf('.mp4')>=0)return 2;if(u.indexOf('.webm')>=0)return 3;return 4;}return s(a)-s(b);});" +
                "if(!c.length)return null;" +
                "return JSON.stringify({src:c[0],page:location.href,title:document.title||'Video'});" +
                "})()";

        webView.evaluateJavascript(js, raw -> {
            String mediaUrl = null;
            String actualPage = pageUrl;
            String pageTitle = "Video";

            try {
                if (raw != null && !"null".equals(raw)) {
                    Object decoded = new JSONTokener(raw).nextValue();
                    if (decoded instanceof String) {
                        JSONObject result = new JSONObject((String) decoded);
                        mediaUrl = result.optString("src", "");
                        actualPage = result.optString("page", pageUrl);
                        pageTitle = result.optString("title", "Video");
                    }
                }
            } catch (Exception ignored) {
            }

            if (mediaUrl != null && !mediaUrl.isEmpty()) {
                probingNativeVideo = false;
                lastDetectedMediaUrl = null;
                openNativePlayer(mediaUrl, actualPage, pageTitle);
                if (webView.canGoBack()) webView.goBack();
                return;
            }

            if (attempt < 2 && currentUrl().equals(pageUrl)) {
                int nextAttempt = attempt + 1;
                webView.postDelayed(
                        () -> probeNativeVideo(pageUrl, nextAttempt, userInitiated),
                        650L * nextAttempt
                );
                return;
            }

            String networkMedia = lastDetectedMediaUrl;
            probingNativeVideo = false;
            lastDetectedMediaUrl = null;
            if (networkMedia != null && !networkMedia.isEmpty()) {
                openNativePlayer(networkMedia,
                        pageUrl,
                        webView.getTitle() == null ? "Video" : webView.getTitle());
                if (webView.canGoBack()) webView.goBack();
            } else if (userInitiated) {
                Toast.makeText(this,
                        "No direct video stream found on this page.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openNativePlayer(String mediaUrl, String pageUrl, String title) {
        stopMiniPlayer();
        try {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, mediaUrl);
            intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, pageUrl);
            intent.putExtra(PlayerActivity.EXTRA_TITLE, title);
            intent.putExtra(PlayerActivity.EXTRA_USER_AGENT,
                    webView.getSettings().getUserAgentString());

            String cookies = CookieManager.getInstance().getCookie(mediaUrl);
            if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null) intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
            startActivityForResult(intent, NATIVE_PLAYER);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Couldn't open the native player.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startMiniPlayer(Intent data) {
        stopMiniPlayer();
        if (miniPlayerCard == null) buildMiniPlayerUi();
        miniMediaUrl = data.getStringExtra(PlayerActivity.EXTRA_MEDIA_URL);
        miniPageUrl = data.getStringExtra(PlayerActivity.EXTRA_PAGE_URL);
        miniTitle = data.getStringExtra(PlayerActivity.EXTRA_TITLE);
        miniUserAgent = data.getStringExtra(PlayerActivity.EXTRA_USER_AGENT);
        miniCookies = data.getStringExtra(PlayerActivity.EXTRA_COOKIES);
        long startPosition = data.getLongExtra(PlayerActivity.EXTRA_START_POSITION, 0L);
        if (miniMediaUrl == null || miniMediaUrl.isEmpty()) return;

        try {
            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
            if (miniUserAgent != null && !miniUserAgent.isEmpty()) {
                httpFactory.setUserAgent(miniUserAgent);
            }

            Map<String, String> headers = new HashMap<>();
            if (miniPageUrl != null && !miniPageUrl.isEmpty()) {
                headers.put("Referer", miniPageUrl);
                try {
                    Uri page = Uri.parse(miniPageUrl);
                    if (page.getScheme() != null && page.getHost() != null) {
                        headers.put("Origin", page.getScheme() + "://" + page.getHost());
                    }
                } catch (Exception ignored) {
                }
            }
            if (miniCookies != null && !miniCookies.isEmpty()) {
                headers.put("Cookie", miniCookies);
            }
            if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

            miniPlayer = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                            .setDataSourceFactory(httpFactory))
                    .build();
            miniPlayerView.setPlayer(miniPlayer);

            MediaItem.Builder item = new MediaItem.Builder().setUri(miniMediaUrl);
            String lower = miniMediaUrl.toLowerCase();
            if (lower.contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if (lower.contains(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
            miniPlayer.setMediaItem(item.build());
            if (startPosition > 0L) miniPlayer.seekTo(startPosition);
            miniPlayer.prepare();
            miniPlayer.play();
            miniResumePlayback = true;

            miniTitleView.setText(
                    miniTitle == null || miniTitle.isEmpty() ? "Video" : miniTitle
            );
            miniPlayerCard.setVisibility(View.VISIBLE);
            updateBanner.setVisibility(View.GONE);
        } catch (Exception e) {
            stopMiniPlayer();
            Toast.makeText(this,
                    "Couldn't start the mini-player.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void reopenMiniPlayer() {
        if (miniPlayer == null || miniMediaUrl == null) return;
        haptic(miniPlayerCard);
        long position = miniPlayer.getCurrentPosition();

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, miniMediaUrl);
        intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, miniPageUrl);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, miniTitle);
        intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, miniUserAgent);
        intent.putExtra(PlayerActivity.EXTRA_COOKIES, miniCookies);
        intent.putExtra(PlayerActivity.EXTRA_START_POSITION, position);
        stopMiniPlayer();
        startActivityForResult(intent, NATIVE_PLAYER);
    }

    private void stopMiniPlayer() {
        miniResumePlayback = false;
        if (miniPlayerView != null) miniPlayerView.setPlayer(null);
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
        if (miniPlayerCard != null) miniPlayerCard.setVisibility(View.GONE);
        miniMediaUrl = null;
        miniPageUrl = null;
        miniTitle = null;
        miniUserAgent = null;
        miniCookies = null;
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            try {
                CustomTabsIntent customTab = new CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build();
                customTab.launchUrl(this, uri);
                return;
            } catch (Exception ignored) {
            }
        }
        openSystemIntent(uri);
    }

    private void openSystemIntent(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "No app can open this link.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppMenu() {
        haptic(menuButton);
        BottomSheetDialog sheet = new BottomSheetDialog(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(28));
        content.setBackgroundColor(Color.rgb(18, 18, 21));

        TextView handle = new TextView(this);
        handle.setBackground(rounded(Color.rgb(88, 88, 96), dp(3)));
        LinearLayout.LayoutParams handleParams =
                new LinearLayout.LayoutParams(dp(42), dp(5));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(14));
        content.addView(handle, handleParams);

        TextView title = sheetText("CrazyShit Jeremy Edition", 22, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(6), 0, dp(6), dp(4));
        content.addView(title);

        TextView subtitle = sheetText("Jeremy Edition • Quick controls", 13, Color.rgb(255, 122, 69));
        subtitle.setPadding(dp(6), 0, dp(6), dp(8));
        content.addView(subtitle);

        addSheetSection(content, "Browsing");
        addSheetAction(content, "Home", "Go to the CrazyShit homepage", () -> {
            probingNativeVideo = false;
            webView.loadUrl(HOME);
            sheet.dismiss();
        });
        addSheetAction(content, "Refresh", "Reload the current page", () -> {
            probingNativeVideo = false;
            swipeRefresh.setRefreshing(true);
            webView.reload();
            sheet.dismiss();
        });
        addSheetAction(content, "Share page", currentUrl(), () -> {
            shareText(currentUrl());
            sheet.dismiss();
        });
        addSheetAction(content,
                "Open in browser",
                "Open the current page outside the app",
                () -> {
                    openExternal(Uri.parse(currentUrl()));
                    sheet.dismiss();
                });
        addSheetAction(content,
                "Watch Later",
                "Open locally saved video pages",
                () -> {
                    startActivityForResult(
                            new Intent(this, FavoritesActivity.class),
                            FAVORITES
                    );
                    sheet.dismiss();
                });

        addSheetSection(content, "Video");
        addSheetSwitch(
                content,
                "Native video player",
                "Open compatible streams in the dedicated player",
                "native_player_enabled",
                true,
                null
        );
        addSheetAction(content,
                "Play current page",
                "Try to detect a video on this page",
                () -> {
                    probingNativeVideo = true;
                    lastDetectedMediaUrl = null;
                    probeNativeVideo(currentUrl(), 0, true);
                    sheet.dismiss();
                });

        addSheetSection(content, "Privacy");
        addSheetSwitch(
                content,
                "Block ads & pop-ups",
                "Stop ad hosts, popunders and redirect hijacks",
                "ad_blocking_enabled",
                true,
                webView::reload
        );

        addSheetSection(content, "App");
        addSheetAction(content,
                "Settings",
                "Playback, privacy, haptics and Watch Later",
                () -> {
                    startActivity(new Intent(this, SettingsActivity.class));
                    sheet.dismiss();
                });
        addSheetAction(content,
                "Check for updates",
                "Look for a newer GitHub release",
                () -> {
                    checkForUpdates(true);
                    sheet.dismiss();
                });
        addSheetAction(content,
                "Clear site data",
                "Sign out and clear website data",
                () -> {
                    sheet.dismiss();
                    confirmClearSiteData();
                });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        sheet.setContentView(scroll);
        sheet.show();
    }

    private void addSheetSection(LinearLayout root, String label) {
        TextView view = sheetText(label.toUpperCase(), 11, Color.rgb(155, 155, 166));
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(dp(8), dp(18), dp(8), dp(6));
        root.addView(view);
    }

    private void addSheetAction(
            LinearLayout root,
            String title,
            String subtitle,
            Runnable action
    ) {
        MaterialCardView card = sheetCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(12), dp(14), dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(sheetText(title, 16, Color.WHITE));
        TextView sub = sheetText(subtitle, 12, Color.rgb(170, 170, 180));
        sub.setMaxLines(1);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setPadding(0, dp(3), 0, 0);
        copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = sheetText("›", 26, Color.rgb(190, 190, 198));
        row.addView(arrow);
        card.addView(row);
        root.addView(card, sheetCardParams());
    }

    private void addSheetSwitch(
            LinearLayout root,
            String title,
            String subtitle,
            String key,
            boolean defaultValue,
            Runnable afterChange
    ) {
        MaterialCardView card = sheetCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(12), dp(10), dp(12));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(sheetText(title, 16, Color.WHITE));
        TextView sub = sheetText(subtitle, 12, Color.rgb(170, 170, 180));
        sub.setPadding(0, dp(3), 0, 0);
        copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean(key, checked)
                    .apply();
            haptic(button);
            if (afterChange != null) afterChange.run();
        });
        row.addView(toggle);
        card.addView(row);
        root.addView(card, sheetCardParams());
    }

    private MaterialCardView sheetCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(28, 28, 32));
        card.setRadius(dp(18));
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(Color.rgb(48, 48, 55));
        return card;
    }

    private LinearLayout.LayoutParams sheetCardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private TextView sheetText(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void confirmClearSiteData() {
        new AlertDialog.Builder(this)
                .setTitle("Clear site data?")
                .setMessage("This signs you out and clears CrazyShit.com cookies, local storage, cache, and browsing history inside the app.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    CookieManager.getInstance().removeAllCookies(value -> {
                        CookieManager.getInstance().flush();
                        WebStorage.getInstance().deleteAllData();
                        webView.clearCache(true);
                        webView.clearHistory();
                        webView.loadUrl(HOME);
                        Toast.makeText(this,
                                "Site data cleared.",
                                Toast.LENGTH_SHORT).show();
                    });
                })
                .show();
    }

    private void shareText(String text) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "Share"));
    }

    private void copyText(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Link", text));
        Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show();
    }

    private String currentUrl() {
        String url = webView.getUrl();
        return url == null || url.isEmpty() ? HOME : url;
    }

    private void showAgeWarning() {
        new AlertDialog.Builder(this)
                .setTitle("18+ / Graphic Content")
                .setMessage(
                        "CrazyShit Jeremy Edition opens CrazyShit.com, which contains adult and graphic material. " +
                        "Continue only if you are 18 or older and want to view that type of content.\n\n" +
                        "This community app is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com."
                )
                .setCancelable(false)
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .setPositiveButton("Continue", (dialog, which) -> {
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("age_warning_accepted", true)
                            .apply();
                    String requestedStart = getIntent().getStringExtra("start_url");
                    webView.loadUrl(requestedStart == null || requestedStart.isEmpty() ? HOME : requestedStart);
                })
                .show();
    }

    private void showError() {
        progress.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
        errorView.setVisibility(View.VISIBLE);
    }

    private void startDownload(PendingDownload download) {
        if (Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {
            pendingDownload = download;
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION
            );
            return;
        }
        enqueueDownload(download);
    }

    private void enqueueDownload(PendingDownload download) {
        try {
            String name = URLUtil.guessFileName(
                    download.url,
                    download.disposition,
                    download.mime
            );
            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(download.url));
            request.setTitle(name);
            request.setDescription("Downloading from CrazyShit");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    name
            );

            if (download.mime != null && !download.mime.isEmpty()) {
                request.setMimeType(download.mime);
            }
            if (download.userAgent != null) {
                request.addRequestHeader("User-Agent", download.userAgent);
            }

            String cookies = CookieManager.getInstance().getCookie(download.url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);

            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
            Toast.makeText(this,
                    "Downloading " + name,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Couldn't start the download.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void leaveVideo() {
        if (customView == null) return;
        videoFrame.removeView(customView);
        videoFrame.setVisibility(View.GONE);
        webFrame.setVisibility(View.VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();

        customView = null;
        customViewCallback = null;
        configurePip(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        setFullscreenUi(false);
        updateBackCallback();
    }

    private void setFullscreenUi(boolean enabled) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.hide(WindowInsets.Type.statusBars() |
                            WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    controller.show(WindowInsets.Type.statusBars() |
                            WindowInsets.Type.navigationBars());
                }
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(enabled
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private boolean pipSupported() {
        return Build.VERSION.SDK_INT >= 26 &&
                getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_PICTURE_IN_PICTURE
                );
    }

    private void configurePip(boolean videoActive) {
        if (!pipSupported()) return;
        try {
            PictureInPictureParams.Builder builder =
                    new PictureInPictureParams.Builder()
                            .setAspectRatio(new Rational(16, 9));
            if (Build.VERSION.SDK_INT >= 31) {
                builder.setAutoEnterEnabled(videoActive);
            }
            setPictureInPictureParams(builder.build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (customView != null &&
                pipSupported() &&
                Build.VERSION.SDK_INT < 31) {
            try {
                enterPictureInPictureMode(
                        new PictureInPictureParams.Builder()
                                .setAspectRatio(new Rational(16, 9))
                                .build()
                );
            } catch (Exception ignored) {
            }
        }
    }

    private void configureBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleInAppBack;
            updateBackCallback();
        }
    }

    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33 || backCallback == null) return;
        boolean shouldIntercept =
                customView != null || (webView != null && webView.canGoBack());
        OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();

        if (shouldIntercept && !backCallbackRegistered) {
            dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backCallback
            );
            backCallbackRegistered = true;
        } else if (!shouldIntercept && backCallbackRegistered) {
            dispatcher.unregisterOnBackInvokedCallback(backCallback);
            backCallbackRegistered = false;
        }
    }

    private void handleInAppBack() {
        probingNativeVideo = false;
        lastDetectedMediaUrl = null;
        if (customView != null) {
            leaveVideo();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT >= 33) {
            super.onBackPressed();
            return;
        }
        if (customView != null || webView.canGoBack()) handleInAppBack();
        else super.onBackPressed();
    }

    private void restoreKnownUpdate() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String version = prefs.getString("available_version", null);
        String url = prefs.getString("available_release_url", RELEASE_PAGE);
        if (version != null && compareVersions(version, currentVersion()) > 0) {
            showUpdateBanner(version, url);
        }
    }

    private void checkForUpdates(boolean userInitiated) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastCheck = prefs.getLong("last_update_check", 0L);

        if (!userInitiated && now - lastCheck < UPDATE_CHECK_INTERVAL_MS) return;
        if (userInitiated) {
            Toast.makeText(this,
                    "Checking for updates…",
                    Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty(
                        "User-Agent",
                        "CrazyShit-Unofficial-Android"
                );

                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("GitHub returned " + status);
                }

                StringBuilder jsonText = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8
                        ))) {
                    String line;
                    while ((line = reader.readLine()) != null) jsonText.append(line);
                }

                JSONObject json = new JSONObject(jsonText.toString());
                String latest = normalizeVersion(json.optString("tag_name", ""));
                String releaseUrl = json.optString("html_url", RELEASE_PAGE);
                if (latest.isEmpty()) {
                    throw new IllegalStateException("No version returned");
                }

                boolean newer = compareVersions(latest, currentVersion()) > 0;
                SharedPreferences.Editor editor =
                        prefs.edit().putLong("last_update_check", now);
                if (newer) {
                    editor.putString("available_version", latest)
                            .putString("available_release_url", releaseUrl);
                } else {
                    editor.remove("available_version")
                            .remove("available_release_url");
                }
                editor.apply();

                runOnUiThread(() -> {
                    if (newer) {
                        if (miniPlayerCard == null ||
                                miniPlayerCard.getVisibility() != View.VISIBLE) {
                            showUpdateBanner(latest, releaseUrl);
                        }
                    } else {
                        updateBanner.setVisibility(View.GONE);
                        if (userInitiated) {
                            Toast.makeText(this,
                                    "You're up to date.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (Exception e) {
                if (userInitiated) {
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            "Couldn't check for updates.",
                            Toast.LENGTH_SHORT
                    ).show());
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void showUpdateBanner(String version, String releaseUrl) {
        updateBanner.setText("Update v" + version + " available  •  Tap to view");
        updateBanner.setOnClickListener(v -> openExternal(Uri.parse(releaseUrl)));
        updateBanner.setVisibility(View.VISIBLE);
    }

    private String currentVersion() {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 33) {
                info = getPackageManager().getPackageInfo(
                        getPackageName(),
                        PackageManager.PackageInfoFlags.of(0)
                );
            } else {
                info = getPackageManager().getPackageInfo(getPackageName(), 0);
            }
            return normalizeVersion(info.versionName == null
                    ? "0.0.0"
                    : info.versionName);
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    private String normalizeVersion(String version) {
        String value = version == null ? "" : version.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        int dash = value.indexOf('-');
        if (dash >= 0) value = value.substring(0, dash);
        return value;
    }

    private int compareVersions(String left, String right) {
        String[] a = normalizeVersion(left).split("\\.");
        String[] b = normalizeVersion(right).split("\\.");
        int count = Math.max(a.length, b.length);

        for (int i = 0; i < count; i++) {
            int av = i < a.length ? numericVersionPart(a[i]) : 0;
            int bv = i < b.length ? numericVersionPart(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private int numericVersionPart(String value) {
        try {
            String digits = value.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            );
            fileCallback = null;
            return;
        }

        if (requestCode == NATIVE_PLAYER) {
            if (data != null &&
                    data.getBooleanExtra(PlayerActivity.EXTRA_MINIMIZED, false)) {
                startMiniPlayer(data);
                return;
            }
            if (data != null) {
                String fallbackPage =
                        data.getStringExtra(PlayerActivity.EXTRA_FALLBACK_PAGE);
                if (fallbackPage != null && !fallbackPage.isEmpty()) {
                    probingNativeVideo = false;
                    webView.loadUrl(fallbackPage);
                }
            }
            return;
        }

        if (requestCode == FAVORITES &&
                resultCode == RESULT_OK &&
                data != null) {
            String selectedUrl =
                    data.getStringExtra(FavoritesActivity.EXTRA_SELECTED_URL);
            if (selectedUrl != null && !selectedUrl.isEmpty()) {
                webView.loadUrl(selectedUrl);
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] results
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_PERMISSION && pendingDownload != null) {
            if (results.length > 0 &&
                    results[0] == PackageManager.PERMISSION_GRANTED) {
                enqueueDownload(pendingDownload);
            } else {
                Toast.makeText(this,
                        "Storage permission is needed for downloads.",
                        Toast.LENGTH_LONG).show();
            }
            pendingDownload = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (miniPlayer != null && miniResumePlayback) miniPlayer.play();
    }

    @Override
    protected void onStop() {
        if (miniPlayer != null) {
            miniResumePlayback = miniPlayer.isPlaying();
            miniPlayer.pause();
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 &&
                backCallbackRegistered &&
                backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallbackRegistered = false;
        }

        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

        stopMiniPlayer();

        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private void haptic(View view) {
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class PendingDownload {
        final String url;
        final String userAgent;
        final String disposition;
        final String mime;

        PendingDownload(
                String url,
                String userAgent,
                String disposition,
                String mime
        ) {
            this.url = url;
            this.userAgent = userAgent;
            this.disposition = disposition;
            this.mime = mime;
        }
    }
}
