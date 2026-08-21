package com.webapp.crazyshit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URISyntaxException;

public class MainActivity extends Activity {
    private static final String HOME = "https://crazyshit.com/";
    private static final int PICK_FILE = 1001;
    private static final int STORAGE_PERMISSION = 1002;

    private FrameLayout root;
    private FrameLayout webFrame;
    private FrameLayout videoFrame;
    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> fileCallback;
    private PendingDownload pendingDownload;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(17, 17, 17));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        configureWebView();

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("age_warning_accepted", false)) {
            showAgeWarning();
        } else if (state == null || webView.restoreState(state) == null) {
            webView.loadUrl(HOME);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(17, 17, 17));

        webFrame = new FrameLayout(this);
        root.addView(webFrame, match());

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(17, 17, 17));
        webFrame.addView(webView, match());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.getProgressDrawable().setTint(Color.WHITE);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(3));
        pp.gravity = Gravity.TOP;
        webFrame.addView(progress, pp);

        errorView = makeErrorView();
        errorView.setVisibility(View.GONE);
        webFrame.addView(errorView, match());

        videoFrame = new FrameLayout(this);
        videoFrame.setBackgroundColor(Color.BLACK);
        videoFrame.setVisibility(View.GONE);
        root.addView(videoFrame, match());

        setContentView(root);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private LinearLayout makeErrorView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        box.setBackgroundColor(Color.rgb(17, 17, 17));

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
        browser.setOnClickListener(v -> openExternal(Uri.parse(HOME)));

        box.addView(title);
        box.addView(body);
        box.addView(retry);
        box.addView(browser);
        return box;
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return route(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return route(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                errorView.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showError();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse res) {
                if (req.isForMainFrame() && res.getStatusCode() >= 500) showError();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
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
                setFullscreenUi(true);
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

        webView.setDownloadListener((url, ua, disposition, mime, length) ->
                startDownload(new PendingDownload(url, ua, disposition, mime)));
    }

    private boolean route(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;
        String scheme = uri.getScheme();

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String host = uri.getHost();
            if (host != null && (host.equalsIgnoreCase("crazyshit.com") || host.toLowerCase().endsWith(".crazyshit.com"))) {
                return false;
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
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            } catch (URISyntaxException ignored) {}
            return true;
        }

        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAgeWarning() {
        new AlertDialog.Builder(this)
                .setTitle("18+ / Graphic Content")
                .setMessage("This app opens CrazyShit.com. The site contains adult and graphic material. Continue only if you are 18 or older and want to view that type of content.")
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> finish())
                .setPositiveButton("Continue", (d, w) -> {
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("age_warning_accepted", true).apply();
                    webView.loadUrl(HOME);
                })
                .show();
    }

    private void showError() {
        progress.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    private void startDownload(PendingDownload d) {
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = d;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
            return;
        }
        enqueueDownload(d);
    }

    private void enqueueDownload(PendingDownload d) {
        try {
            String name = URLUtil.guessFileName(d.url, d.disposition, d.mime);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(d.url));
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            if (d.mime != null && !d.mime.isEmpty()) r.setMimeType(d.mime);
            if (d.userAgent != null) r.addRequestHeader("User-Agent", d.userAgent);
            String cookies = CookieManager.getInstance().getCookie(d.url);
            if (cookies != null) r.addRequestHeader("Cookie", cookies);
            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't start the download.", Toast.LENGTH_SHORT).show();
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
        setFullscreenUi(false);
    }

    private void setFullscreenUi(boolean enabled) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (enabled) {
                    c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(enabled
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) leaveVideo();
        else if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_PERMISSION && pendingDownload != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) enqueueDownload(pendingDownload);
            else Toast.makeText(this, "Storage permission is needed for downloads.", Toast.LENGTH_LONG).show();
            pendingDownload = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class PendingDownload {
        final String url, userAgent, disposition, mime;
        PendingDownload(String url, String userAgent, String disposition, String mime) {
            this.url = url;
            this.userAgent = userAgent;
            this.disposition = disposition;
            this.mime = mime;
        }
    }
}
