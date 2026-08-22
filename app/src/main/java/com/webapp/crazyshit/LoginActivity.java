package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.json.JSONTokener;

public final class LoginActivity extends Activity {
    public static final String EXTRA_RETURN_URL = "return_url";

    private static final String LOGIN_URL = CrazyShitRepository.BASE + "login/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final String AUTH_JS =
            "(() => {" +
            "const pwd=!!document.querySelector('input[type=password]');" +
            "const form=!!document.querySelector('form[action*=login],form[id*=login],form[class*=login]');" +
            "const logout=!!document.querySelector('a[href*=logout],a[href*=signout],form[action*=logout]');" +
            "const account=!!document.querySelector('a[href*=account],a[href*=profile],a[href*=member]');" +
            "return JSON.stringify({pwd:pwd,form:form,logout:logout,account:account,path:location.pathname||''});" +
            "})()";

    private WebView webView;
    private ProgressBar progress;
    private TextView status;
    private boolean sawLoginForm;
    private boolean finishingSuccess;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        configureWebView();

        if (state == null || webView.restoreState(state) == null) {
            webView.loadUrl(LOGIN_URL);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 13, 15));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(13, 13, 15));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
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
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(6), dp(10), dp(6));
        top.setBackgroundColor(Color.rgb(17, 17, 20));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(this);
        title.setText("Sign in");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(title);

        status = new TextView(this);
        status.setText("CrazyShit account • returns automatically");
        status.setTextColor(Color.rgb(165, 165, 174));
        status.setTextSize(11);
        labels.addView(status);

        TextView cancel = new TextView(this);
        cancel.setText("CANCEL");
        cancel.setTextColor(Color.rgb(255, 112, 60));
        cancel.setTextSize(12);
        cancel.setTypeface(null, android.graphics.Typeface.BOLD);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(8), dp(8), dp(8), dp(8));
        cancel.setOnClickListener(v -> finish());
        top.addView(cancel, new LinearLayout.LayoutParams(dp(76), -1));

        TextView privacy = new TextView(this);
        privacy.setText("Sign in directly on CrazyShit.com. The app shares the website session cookie but does not store your password.");
        privacy.setTextColor(Color.rgb(190, 190, 198));
        privacy.setTextSize(12);
        privacy.setPadding(dp(14), dp(10), dp(14), dp(10));
        privacy.setBackgroundColor(Color.rgb(25, 25, 28));
        shell.addView(privacy, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout browser = new FrameLayout(this);
        shell.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1f));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 13, 15));
        if (Build.VERSION.SDK_INT >= 26) {
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        }
        browser.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.getProgressDrawable().setTint(Color.rgb(255, 90, 31));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(3));
        pp.gravity = Gravity.TOP;
        browser.addView(progress, pp);

        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(USER_AGENT);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

        try {
            CookieManager manager = CookieManager.getInstance();
            manager.setAcceptCookie(true);
            manager.setAcceptThirdPartyCookies(webView, false);
        } catch (Exception ignored) {
        }

        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                try {
                    CookieManager.getInstance().flush();
                } catch (Exception ignored) {
                }
                view.postDelayed(LoginActivity.this::probeAuthentication, 350L);
            }
        });
    }

    private void probeAuthentication() {
        if (finishingSuccess || webView == null) return;
        try {
            webView.evaluateJavascript(AUTH_JS, raw -> {
                JSONObject state = decodeObject(raw);
                if (state == null) return;
                boolean hasLogin = state.optBoolean("pwd") || state.optBoolean("form");
                boolean loggedEvidence = state.optBoolean("logout") || state.optBoolean("account");
                String path = state.optString("path", "").toLowerCase();

                if (hasLogin) {
                    sawLoginForm = true;
                    status.setText("Enter your CrazyShit credentials");
                    return;
                }

                boolean leftLoginPage = !path.contains("login");
                if (loggedEvidence || (sawLoginForm && leftLoginPage)) {
                    finishSuccessfulLogin();
                }
            });
        } catch (Exception ignored) {
        }
    }

    private JSONObject decodeObject(String raw) {
        if (raw == null || raw.equals("null")) return null;
        try {
            Object outer = new JSONTokener(raw).nextValue();
            if (!(outer instanceof String)) return null;
            return new JSONObject((String) outer);
        } catch (Exception e) {
            return null;
        }
    }

    private void finishSuccessfulLogin() {
        if (finishingSuccess) return;
        finishingSuccess = true;
        status.setText("Signed in • returning to the app");
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {
        }
        setResult(RESULT_OK);
        webView.postDelayed(this::finish, 180L);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.loadUrl("about:blank");
                webView.stopLoading();
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
