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
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashSet;
import java.util.Set;

public class CommentsActivity extends Activity {
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_COUNT = "count";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final String COMMENTS_JS =
            "(() => {" +
            "const clean=s=>(s||'').replace(/\\s+/g,' ').trim();" +
            "const commentish=e=>/comment/i.test((e.id||'')+' '+(typeof e.className==='string'?e.className:''));" +
            "const nodes=[...document.querySelectorAll('[class*=comment],[id*=comment]')];" +
            "const out=[];const seen=new Set();" +
            "for(const e of nodes){" +
            "if(!e||['FORM','TEXTAREA','INPUT','BUTTON','SCRIPT','STYLE'].includes(e.tagName))continue;" +
            "let text=clean(e.innerText||e.textContent);if(text.length<2||text.length>2600)continue;" +
            "let nested=[...e.children].some(c=>commentish(c)&&clean(c.innerText||c.textContent).length>1);if(nested)continue;" +
            "let a=e.querySelector('.username,.user,.author,.name,[class*=username],[class*=author],a[href*=user],a[href*=member],a[href*=profile]');" +
            "let tm=e.querySelector('time,.date,.time,[class*=date],[class*=time]');" +
            "let author=clean(a&&(a.innerText||a.textContent));let time=clean(tm&&(tm.innerText||tm.textContent));" +
            "let body=text;if(author&&body.toLowerCase().startsWith(author.toLowerCase()))body=clean(body.slice(author.length));" +
            "if(time&&body.toLowerCase().startsWith(time.toLowerCase()))body=clean(body.slice(time.length));" +
            "if(body.length<2)body=text;let key=(author+'|'+body).toLowerCase();if(seen.has(key))continue;seen.add(key);" +
            "out.push({author:author,time:time,text:body});if(out.length>=100)break;" +
            "}" +
            "return JSON.stringify(out);" +
            "})()";

    private String pageUrl;
    private String pageTitle;
    private String count;
    private LinearLayout commentsContainer;
    private ProgressBar progress;
    private TextView status;
    private WebView extractor;
    private int attempts;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);

        Intent intent = getIntent();
        pageUrl = intent.getStringExtra(EXTRA_PAGE_URL);
        pageTitle = intent.getStringExtra(EXTRA_TITLE);
        count = intent.getStringExtra(EXTRA_COUNT);
        if (pageUrl == null) pageUrl = CrazyShitRepository.HOME;
        if (pageTitle == null || pageTitle.trim().isEmpty()) pageTitle = "Comments";
        if (count == null) count = "";

        buildUi();
        loadComments();
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
        top.setPadding(dp(8), dp(5), dp(12), dp(5));
        top.setBackgroundColor(Color.rgb(17, 17, 20));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(38);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), -1));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(4), 0, 0, 0);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(this);
        title.setText("Comments");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titles.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(count.isEmpty() ? pageTitle : count + " comments  •  " + pageTitle);
        subtitle.setTextColor(Color.rgb(165, 165, 174));
        subtitle.setTextSize(11);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(subtitle);

        TextView web = new TextView(this);
        web.setText("WEB");
        web.setTextColor(Color.rgb(255, 112, 60));
        web.setTextSize(12);
        web.setTypeface(null, android.graphics.Typeface.BOLD);
        web.setGravity(Gravity.CENTER);
        web.setPadding(dp(8), dp(8), dp(8), dp(8));
        web.setOnClickListener(v -> openWebsite());
        top.addView(web, new LinearLayout.LayoutParams(dp(54), -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        commentsContainer = new LinearLayout(this);
        commentsContainer.setOrientation(LinearLayout.VERTICAL);
        commentsContainer.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(commentsContainer, new ScrollView.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextColor(Color.rgb(180, 180, 188));
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), dp(30), dp(24), dp(30));
        status.setText("Loading comments…");
        commentsContainer.addView(status, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(46), dp(46));
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);

        extractor = new WebView(this);
        extractor.setAlpha(0.01f);
        WebSettings settings = extractor.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(false);
        settings.setBlockNetworkImage(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(USER_AGENT);
        try {
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(extractor, false);
        } catch (Exception ignored) {
        }
        extractor.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                attempts = 0;
                view.postDelayed(CommentsActivity.this::probeComments, 500L);
            }
        });
        FrameLayout.LayoutParams hidden = new FrameLayout.LayoutParams(1, 1);
        hidden.gravity = Gravity.BOTTOM | Gravity.END;
        root.addView(extractor, hidden);

        setContentView(root);
    }

    private void loadComments() {
        try {
            extractor.loadUrl(pageUrl);
        } catch (Exception e) {
            showFallback("Couldn't load comments natively.");
        }
    }

    private void probeComments() {
        if (extractor == null) return;
        try {
            extractor.evaluateJavascript(COMMENTS_JS, raw -> {
                JSONArray comments = decodeArray(raw);
                if (comments != null && comments.length() > 0) {
                    renderComments(comments);
                    return;
                }
                attempts++;
                if (attempts < 3) {
                    extractor.postDelayed(this::probeComments, attempts == 1 ? 700L : 1200L);
                } else {
                    showFallback("No native comments were found. Tap here to open the website comments.");
                }
            });
        } catch (Exception e) {
            showFallback("Couldn't extract comments natively. Tap here to open the website comments.");
        }
    }

    private JSONArray decodeArray(String raw) {
        if (raw == null || raw.equals("null")) return null;
        try {
            Object outer = new JSONTokener(raw).nextValue();
            if (!(outer instanceof String)) return null;
            return new JSONArray((String) outer);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderComments(JSONArray comments) {
        progress.setVisibility(View.GONE);
        commentsContainer.removeAllViews();
        Set<String> rendered = new HashSet<>();

        for (int i = 0; i < comments.length(); i++) {
            JSONObject item = comments.optJSONObject(i);
            if (item == null) continue;
            String author = clean(item.optString("author"));
            String time = clean(item.optString("time"));
            String text = clean(item.optString("text"));
            if (text.length() < 2) continue;
            String key = (author + "|" + text).toLowerCase();
            if (!rendered.add(key)) continue;
            commentsContainer.addView(commentCard(author, time, text));
        }

        if (commentsContainer.getChildCount() == 0) {
            showFallback("No native comments were found. Tap here to open the website comments.");
        }
    }

    private View commentCard(String author, String time, String text) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setStrokeColor(Color.rgb(48, 48, 54));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(15));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(13));
        card.addView(body, new MaterialCardView.LayoutParams(-1, -2));

        if (!author.isEmpty() || !time.isEmpty()) {
            LinearLayout meta = new LinearLayout(this);
            meta.setOrientation(LinearLayout.HORIZONTAL);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            body.addView(meta, new LinearLayout.LayoutParams(-1, -2));

            TextView name = new TextView(this);
            name.setText(author.isEmpty() ? "Comment" : author);
            name.setTextColor(Color.rgb(255, 112, 60));
            name.setTextSize(13);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            meta.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

            if (!time.isEmpty()) {
                TextView when = new TextView(this);
                when.setText(time);
                when.setTextColor(Color.rgb(145, 145, 154));
                when.setTextSize(11);
                meta.addView(when, new LinearLayout.LayoutParams(-2, -2));
            }
        }

        TextView copy = new TextView(this);
        copy.setText(text);
        copy.setTextColor(Color.rgb(235, 235, 238));
        copy.setTextSize(14);
        copy.setLineSpacing(0f, 1.08f);
        copy.setPadding(0, dp(7), 0, 0);
        body.addView(copy, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(cp);
        return card;
    }

    private void showFallback(String message) {
        progress.setVisibility(View.GONE);
        commentsContainer.removeAllViews();
        status = new TextView(this);
        status.setText(message);
        status.setTextColor(Color.rgb(195, 195, 202));
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), dp(44), dp(24), dp(44));
        status.setOnClickListener(v -> openWebsite());
        commentsContainer.addView(status, new LinearLayout.LayoutParams(-1, -2));
    }

    private void openWebsite() {
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, pageUrl);
        startActivity(intent);
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    @Override
    protected void onDestroy() {
        if (extractor != null) {
            try {
                extractor.stopLoading();
                extractor.destroy();
            } catch (Exception ignored) {
            }
            extractor = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
