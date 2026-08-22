package com.webapp.crazyshit;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

public final class WebFallbackActivity extends Activity {
    public static final String EXTRA_URL = "url";
    private static final int PICK_FILE = 4101;

    private FrameLayout root;
    private FrameLayout browserFrame;
    private FrameLayout fullscreenFrame;
    private WebView webView;
    private ProgressBar progress;
    private TextView titleView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        configureWebView();

        if (state == null || webView.restoreState(state) == null) {
            String url = getIntent().getStringExtra(EXTRA_URL);
            if (url == null || url.trim().isEmpty()) url = CrazyShitRepository.HOME;
            webView.loadUrl(url);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 13, 15));

        browserFrame = new FrameLayout(this);
        browserFrame.setOnApplyWindowInsetsListener((view, insets) -> {
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
        root.addView(browserFrame, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        browserFrame.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.rgb(18, 18, 21));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView back = button("‹", "Back");
        back.setTextSize(32);
        back.setOnClickListener(v -> handleBack());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        titleView = new TextView(this);
        titleView.setText("Website fallback");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setPadding(dp(8), 0, dp(8), 0);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView external = button("↗", "Open in browser");
        external.setTextSize(20);
        external.setOnClickListener(v -> openExternal(Uri.parse(currentUrl())));
        top.addView(external, new LinearLayout.LayoutParams(dp(48), dp(48)));

        FrameLayout webContainer = new FrameLayout(this);
        shell.addView(webContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 13, 15));
        webContainer.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.getProgressDrawable().setTint(Color.rgb(255, 90, 31));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1, dp(3));
        progressParams.gravity = Gravity.TOP;
        webContainer.addView(progress, progressParams);

        fullscreenFrame = new FrameLayout(this);
        fullscreenFrame.setBackgroundColor(Color.BLACK);
        fullscreenFrame.setVisibility(View.GONE);
        root.addView(fullscreenFrame, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isSameSite(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (isSameSite(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String title = view.getTitle();
                titleView.setText(title == null || title.trim().isEmpty()
                        ? "Website fallback" : title);
                installPopupGuard();
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
                fullscreenFrame.addView(view, new FrameLayout.LayoutParams(-1, -1));
                fullscreenFrame.setVisibility(View.VISIBLE);
                browserFrame.setVisibility(View.GONE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                setFullscreen(true);
            }

            @Override
            public void onHideCustomView() {
                leaveFullscreen();
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(WebFallbackActivity.this,
                            "No file picker is available.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
    }

    private void installPopupGuard() {
        webView.evaluateJavascript(
                "(function(){if(window.__jeremyNativeFallback)return;" +
                        "window.__jeremyNativeFallback=true;window.open=function(){return null;};" +
                        "document.querySelectorAll('a[target]').forEach(function(a){a.removeAttribute('target');});" +
                        "})()",
                null
        );
    }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        try {
            CustomTabsIntent tab = new CustomTabsIntent.Builder().setShowTitle(true).build();
            tab.launchUrl(this, uri);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isSameSite(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        return host != null &&
                (host.equalsIgnoreCase("crazyshit.com") || host.toLowerCase().endsWith(".crazyshit.com"));
    }

    private String currentUrl() {
        String url = webView == null ? null : webView.getUrl();
        return url == null || url.isEmpty() ? CrazyShitRepository.HOME : url;
    }

    private TextView button(String text, String description) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void handleBack() {
        if (customView != null) {
            leaveFullscreen();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    private void leaveFullscreen() {
        if (customView == null) return;
        fullscreenFrame.removeView(customView);
        fullscreenFrame.setVisibility(View.GONE);
        browserFrame.setVisibility(View.VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customView = null;
        customViewCallback = null;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        setFullscreen(false);
    }

    private void setFullscreen(boolean enabled) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || fileCallback == null) return;
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
