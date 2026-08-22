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
            "const pageText=clean(document.body?document.body.innerText:'');" +
            "const loginRequired=/please\\s+(?:log\\s*in|login)\\s+to\\s+view\\s+all\\s+comments/i.test(pageText);" +
            "const sig=e=>((e&&e.id)||'')+' '+((e&&typeof e.className==='string')?e.className:'');" +
            "const commentish=e=>{" +
            "if(!e)return false;" +
            "if(e.hasAttribute&&e.hasAttribute('data-comment-id'))return true;" +
            "let s=sig(e).toLowerCase();" +
            "return /(?:^|[\\s_-])comment(?:[\\s_-]|$)/.test(s)||/commentitem|comment-item|comment_row|comment-row|media-comment/.test(s);" +
            "};" +
            "const bodySelectors=['[itemprop=commentText]','.comment-text','.comment_text','.comment-body','.comment_body','.comment-content','.comment_content','[class*=commentText]','[class*=comment-text]','[class*=comment_body]','[class*=comment-body]','.message','[class*=message]'];" +
            "const authorSelectors=['.username','.user-name','.user_name','.author','.comment-author','[class*=username]','[class*=author]','a[href*=user]','a[href*=member]','a[href*=profile]'];" +
            "const timeSelectors=['time','.date','.time','.comment-date','.comment-time','[class*=date]','[class*=time]'];" +
            "let roots=[...document.querySelectorAll('div,li,article,section')].filter(commentish);" +
            "if(!roots.length){roots=[...document.querySelectorAll('[data-comment-id],[id^=comment-],[id^=comment_]')];}" +
            "const out=[];const seen=new Set();" +
            "for(const e of roots){" +
            "if(!e||!e.isConnected)continue;" +
            "let explicit=null;for(const s of bodySelectors){let x=e.matches&&e.matches(s)?e:e.querySelector(s);if(x){explicit=x;break;}}" +
            "if(!explicit){let nested=[...e.querySelectorAll('div,li,article,section')].filter(x=>x!==e&&commentish(x));if(nested.length)continue;}" +
            "let body=explicit||e;let text=clean(body.innerText||body.textContent);" +
            "if(text.length<5||text.length>2600)continue;" +
            "if(/^(?:top|bottom|newest|oldest|best|worst|comments?)$/i.test(text))continue;" +
            "if(/^[+\\-]?\\d+(?:\\s+[+\\-]?\\d+)*$/.test(text))continue;" +
            "if(/please\\s+(?:log\\s*in|login)\\s+to\\s+view\\s+all\\s+comments/i.test(text))continue;" +
            "let a=null;for(const s of authorSelectors){a=e.querySelector(s);if(a)break;}" +
            "let tm=null;for(const s of timeSelectors){tm=e.querySelector(s);if(tm)break;}" +
            "let author=clean(a&&(a.innerText||a.textContent));let time=clean(tm&&(tm.innerText||tm.textContent));" +
            "if(body===e){" +
            "if(author&&text.toLowerCase().startsWith(author.toLowerCase()))text=clean(text.slice(author.length));" +
            "if(time&&text.toLowerCase().startsWith(time.toLowerCase()))text=clean(text.slice(time.length));" +
            "}" +
            "text=text.replace(/^(?:reply|report|like|dislike)\\s+/i,'').trim();" +
            "if(text.length<5)continue;" +
            "let key=(author+'|'+text).toLowerCase();if(seen.has(key))continue;seen.add(key);" +
            "out.push({author:author,time:time,text:text});if(out.length>=100)break;" +
            "}" +
            "return JSON.stringify({loginRequired:loginRequired,comments:out});" +
            "})()";

    private String pageUrl;
    private String pageTitle;
    private String count;
    private LinearLayout commentsContainer;
    private ProgressBar progress;
    private TextView actionView;
    private WebView extractor;
    private int attempts;
    private boolean refreshWhenResumed;
    private boolean firstResume = true;

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

        actionView = new TextView(this);
        actionView.setText("WEB");
        actionView.setTextColor(Color.rgb(255, 112, 60));
        actionView.setTextSize(12);
        actionView.setTypeface(null, android.graphics.Typeface.BOLD);
        actionView.setGravity(Gravity.CENTER);
        actionView.setPadding(dp(8), dp(8), dp(8), dp(8));
        actionView.setOnClickListener(v -> openWebsite());
        top.addView(actionView, new LinearLayout.LayoutParams(dp(64), -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        commentsContainer = new LinearLayout(this);
        commentsContainer.setOrientation(LinearLayout.VERTICAL);
        commentsContainer.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(commentsContainer, new ScrollView.LayoutParams(-1, -2));

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
                try {
                    CookieManager.getInstance().flush();
                } catch (Exception ignored) {
                }
                view.postDelayed(CommentsActivity.this::probeComments, 650L);
            }
        });
        FrameLayout.LayoutParams hidden = new FrameLayout.LayoutParams(1, 1);
        hidden.gravity = Gravity.BOTTOM | Gravity.END;
        root.addView(extractor, hidden);

        setContentView(root);
    }

    private void loadComments() {
        if (extractor == null) return;
        progress.setVisibility(View.VISIBLE);
        commentsContainer.removeAllViews();
        commentsContainer.addView(messageView("Loading comments…", false), new LinearLayout.LayoutParams(-1, -2));
        actionView.setText("WEB");
        actionView.setOnClickListener(v -> openWebsite());
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {
        }
        try {
            String current = extractor.getUrl();
            if (pageUrl.equals(current)) extractor.reload();
            else extractor.loadUrl(pageUrl);
        } catch (Exception e) {
            showFallback("Couldn't load comments natively.");
        }
    }

    private void probeComments() {
        if (extractor == null) return;
        try {
            extractor.evaluateJavascript(COMMENTS_JS, raw -> {
                JSONObject payload = decodeObject(raw);
                if (payload != null) {
                    JSONArray comments = payload.optJSONArray("comments");
                    boolean loginRequired = payload.optBoolean("loginRequired", false);
                    if ((comments != null && comments.length() > 0) || loginRequired) {
                        renderComments(comments == null ? new JSONArray() : comments, loginRequired);
                        return;
                    }
                }
                attempts++;
                if (attempts < 4) {
                    long delay = attempts == 1 ? 700L : attempts == 2 ? 1100L : 1600L;
                    extractor.postDelayed(this::probeComments, delay);
                } else {
                    showFallback("No native comments were found. Tap here to open the website comments.");
                }
            });
        } catch (Exception e) {
            showFallback("Couldn't extract comments natively. Tap here to open the website comments.");
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

    private void renderComments(JSONArray comments, boolean loginRequired) {
        progress.setVisibility(View.GONE);
        commentsContainer.removeAllViews();
        Set<String> rendered = new HashSet<>();

        if (loginRequired) {
            commentsContainer.addView(loginCard(), cardParams());
            actionView.setText("LOGIN");
            actionView.setOnClickListener(v -> openLogin());
        } else {
            actionView.setText("WEB");
            actionView.setOnClickListener(v -> openWebsite());
        }

        for (int i = 0; i < comments.length(); i++) {
            JSONObject item = comments.optJSONObject(i);
            if (item == null) continue;
            String author = clean(item.optString("author"));
            String time = clean(item.optString("time"));
            String text = clean(item.optString("text"));
            if (!isRealCommentText(text)) continue;
            String key = (author + "|" + text).toLowerCase();
            if (!rendered.add(key)) continue;
            commentsContainer.addView(commentCard(author, time, text), cardParams());
        }

        if (rendered.isEmpty() && !loginRequired) {
            showFallback("No native comments were found. Tap here to open the website comments.");
        } else if (rendered.isEmpty()) {
            commentsContainer.addView(messageView(
                    "Log in to CrazyShit to load the full comment section, then come back here and it will refresh automatically.",
                    false
            ), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private boolean isRealCommentText(String text) {
        if (text == null) return false;
        String value = clean(text);
        if (value.length() < 5 || value.length() > 2600) return false;
        String lower = value.toLowerCase();
        if (lower.equals("top") || lower.equals("bottom") || lower.equals("comments") || lower.equals("comment")) {
            return false;
        }
        if (lower.contains("please login to view all comments") ||
                lower.contains("please log in to view all comments")) return false;
        return !value.matches("^[+\\-]?\\d+(?:\\s+[+\\-]?\\d+)*$");
    }

    private View loginCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(35, 25, 22));
        card.setStrokeColor(Color.rgb(110, 53, 32));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openLogin());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.addView(body, new MaterialCardView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("Log in to load all comments");
        title.setTextColor(Color.rgb(255, 112, 60));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(title);

        TextView copy = new TextView(this);
        copy.setText("CrazyShit only exposes part of the comment section while logged out. Sign in once through the website, then return here and the native comments will refresh using the same session.");
        copy.setTextColor(Color.rgb(205, 205, 211));
        copy.setTextSize(12);
        copy.setLineSpacing(0f, 1.08f);
        copy.setPadding(0, dp(6), 0, dp(10));
        body.addView(copy);

        TextView button = new TextView(this);
        button.setText("LOG IN");
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.rgb(108, 48, 30));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return card;
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
        copy.setPadding(0, (!author.isEmpty() || !time.isEmpty()) ? dp(7) : 0, 0, 0);
        body.addView(copy, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(9));
        return params;
    }

    private TextView messageView(String message, boolean clickable) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(Color.rgb(195, 195, 202));
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(24), dp(44), dp(24), dp(44));
        if (clickable) view.setOnClickListener(v -> openWebsite());
        return view;
    }

    private void showFallback(String message) {
        progress.setVisibility(View.GONE);
        commentsContainer.removeAllViews();
        commentsContainer.addView(messageView(message, true), new LinearLayout.LayoutParams(-1, -2));
        actionView.setText("WEB");
        actionView.setOnClickListener(v -> openWebsite());
    }

    private void openLogin() {
        refreshWhenResumed = true;
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, CrazyShitRepository.BASE + "login/");
        startActivity(intent);
    }

    private void openWebsite() {
        refreshWhenResumed = true;
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, pageUrl);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (refreshWhenResumed) {
            refreshWhenResumed = false;
            try {
                CookieManager.getInstance().flush();
            } catch (Exception ignored) {
            }
            if (extractor != null) extractor.postDelayed(this::loadComments, 250L);
        }
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
