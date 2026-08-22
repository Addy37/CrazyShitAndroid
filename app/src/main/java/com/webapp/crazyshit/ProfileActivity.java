package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
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

/** Opens the currently signed-in CrazyShit profile using the shared website session. */
public final class ProfileActivity extends Activity {
    private static final int LOGIN_REQUEST = 5101;
    private static final String HOME = CrazyShitRepository.BASE;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final String PROFILE_JS =
            "(() => {" +
            "const abs=a=>{try{return a?new URL(a.href||a.getAttribute('href')||'',document.baseURI).href:''}catch(e){return ''}};" +
            "const valid=u=>/https?:\\/\\/(?:[^.]+\\.)?crazyshit\\.com\\/profile\\//i.test(u);" +
            "const selectors=['header','nav','.header','.navbar','.topbar','#header','#nav','.account','.user-menu','.member-menu'];" +
            "let profile='';" +
            "for(const s of selectors){for(const root of document.querySelectorAll(s)){for(const a of root.querySelectorAll('a[href]')){let u=abs(a);if(valid(u)){profile=u;break;}}if(profile)break;}if(profile)break;}" +
            "const logout=!!document.querySelector('a[href*=logout],a[href*=signout],form[action*=logout],button[name*=logout]');" +
            "const login=!!document.querySelector('a[href*=login],input[type=password],form[action*=login]');" +
            "if(!profile&&logout){for(const a of document.querySelectorAll('a[href*=\\\"/profile/\\\"]')){let u=abs(a);if(valid(u)){profile=u;break;}}}" +
            "return JSON.stringify({profile:profile,logged:logout||!!profile,login:login,path:location.pathname||''});" +
            "})()";

    private WebView webView;
    private ProgressBar progress;
    private TextView status;
    private TextView signIn;
    private boolean locating = true;
    private boolean redirected;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        configureWebView();

        if (state == null || webView.restoreState(state) == null) {
            webView.loadUrl(HOME);
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
        top.setPadding(dp(8), dp(6), dp(10), dp(6));
        top.setBackgroundColor(Color.rgb(17, 17, 20));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        TextView back = textButton("‹", "Back");
        back.setTextSize(32);
        back.setOnClickListener(v -> handleBack());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(6), 0, 0, 0);
        top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(this);
        title.setText("Profile");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(title);

        status = new TextView(this);
        status.setText("Finding your signed-in CrazyShit profile…");
        status.setTextColor(Color.rgb(165, 165, 174));
        status.setTextSize(11);
        labels.addView(status);

        signIn = textButton("SIGN IN", "Sign in");
        signIn.setTextSize(12);
        signIn.setTextColor(Color.rgb(255, 112, 60));
        signIn.setTypeface(null, android.graphics.Typeface.BOLD);
        signIn.setVisibility(View.GONE);
        signIn.setOnClickListener(v -> startActivityForResult(new Intent(this, LoginActivity.class), LOGIN_REQUEST));
        top.addView(signIn, new LinearLayout.LayoutParams(dp(82), dp(48)));

        FrameLayout browser = new FrameLayout(this);
        shell.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1f));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 13, 15));
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
                try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
                String lower = url == null ? "" : url.toLowerCase();
                if (lower.contains("crazyshit.com/profile/")) {
                    locating = false;
                    redirected = true;
                    signIn.setVisibility(View.GONE);
                    status.setText("Signed-in profile");
                    return;
                }
                if (locating) view.postDelayed(ProfileActivity.this::locateProfile, 300L);
            }
        });
    }

    private void locateProfile() {
        if (!locating || webView == null) return;
        try {
            webView.evaluateJavascript(PROFILE_JS, raw -> {
                JSONObject result = decode(raw);
                if (result == null) return;
                String profile = result.optString("profile", "");
                boolean logged = result.optBoolean("logged", false);

                if (!profile.isEmpty()) {
                    locating = false;
                    redirected = true;
                    signIn.setVisibility(View.GONE);
                    status.setText("Opening your profile…");
                    webView.loadUrl(profile);
                    return;
                }

                if (!logged) {
                    locating = false;
                    signIn.setVisibility(View.VISIBLE);
                    status.setText("You're not signed in. Sign in to view your profile.");
                } else {
                    locating = false;
                    signIn.setVisibility(View.GONE);
                    status.setText("Signed in. Use the account/profile link shown on the page.");
                }
            });
        } catch (Exception ignored) {
        }
    }

    private JSONObject decode(String raw) {
        if (raw == null || raw.equals("null")) return null;
        try {
            Object outer = new JSONTokener(raw).nextValue();
            if (!(outer instanceof String)) return null;
            return new JSONObject((String) outer);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != LOGIN_REQUEST || resultCode != RESULT_OK) return;
        try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
        locating = true;
        redirected = false;
        signIn.setVisibility(View.GONE);
        status.setText("Finding your signed-in CrazyShit profile…");
        webView.loadUrl(HOME);
    }

    private void handleBack() {
        if (redirected && webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
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

    private TextView textButton(String text, String description) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
