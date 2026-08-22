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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentsActivity extends Activity {
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_COUNT = "count";

    private static final int LOGIN_REQUEST = 4201;
    private static final int SORT_SITE = 0;
    private static final int SORT_TOP = 1;
    private static final int SORT_NEWEST = 2;

    private static final Pattern SCORE_NUMBER = Pattern.compile("[-+]?\\d+");
    private static final Pattern RELATIVE_TIME = Pattern.compile(
            "(\\d+)\\s*(second|seconds|sec|secs|s|minute|minutes|min|mins|m|hour|hours|hr|hrs|h|day|days|d|week|weeks|wk|wks|w|month|months|mo|mos|year|years|yr|yrs|y)\\b",
            Pattern.CASE_INSENSITIVE
    );

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
            "const avatarSelectors=['img.avatar','img[class*=avatar]','[class*=avatar] img','img[class*=profile]','[class*=profile] img','.user img','[class*=user] img'];" +
            "const scoreSelectors=['.score','.votes','.vote-count','.vote_count','.rating','[class*=score]','[class*=vote-count]','[class*=vote_count]','[class*=votes]','[class*=rating]'];" +
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
            "let av=null;for(const s of avatarSelectors){av=e.querySelector(s);if(av)break;}" +
            "let sc=null;for(const s of scoreSelectors){sc=e.querySelector(s);if(sc)break;}" +
            "let author=clean(a&&(a.innerText||a.textContent));" +
            "let time=clean(tm&&((tm.innerText||tm.textContent)||tm.getAttribute('datetime')||tm.getAttribute('title')));" +
            "let score=clean(sc&&(sc.innerText||sc.textContent));" +
            "if(!score)score=clean(e.getAttribute&&((e.getAttribute('data-score')||e.getAttribute('data-votes')||e.getAttribute('data-rating'))));" +
            "let avatar='';if(av){let raw=av.currentSrc||av.getAttribute('data-src')||av.getAttribute('data-original')||av.src||'';try{avatar=raw?new URL(raw,document.baseURI).href:''}catch(err){avatar='';}}" +
            "if(body===e){" +
            "if(author&&text.toLowerCase().startsWith(author.toLowerCase()))text=clean(text.slice(author.length));" +
            "if(time&&text.toLowerCase().startsWith(time.toLowerCase()))text=clean(text.slice(time.length));" +
            "}" +
            "text=text.replace(/^(?:reply|report|like|dislike)\\s+/i,'').trim();" +
            "if(text.length<5)continue;" +
            "let depth=parseInt((e.getAttribute&&((e.getAttribute('data-depth')||e.getAttribute('data-level'))))||'',10);" +
            "if(!Number.isFinite(depth)){let m=sig(e).match(/(?:depth|level)[-_ ]?(\\d+)/i);depth=m?parseInt(m[1],10):NaN;}" +
            "if(!Number.isFinite(depth)){depth=0;let p=e.parentElement;while(p&&p!==document.body&&depth<4){if(commentish(p))depth++;p=p.parentElement;}}" +
            "depth=Math.max(0,Math.min(4,depth||0));" +
            "let key=(author+'|'+text).toLowerCase();if(seen.has(key))continue;seen.add(key);" +
            "out.push({author:author,time:time,text:text,avatar:avatar,score:score,depth:depth});if(out.length>=100)break;" +
            "}" +
            "return JSON.stringify({loginRequired:loginRequired,comments:out});" +
            "})()";

    private String pageUrl;
    private String pageTitle;
    private String count;
    private LinearLayout commentsContainer;
    private LinearLayout sortBar;
    private TextView sortView;
    private ProgressBar progress;
    private TextView actionView;
    private WebView extractor;
    private int attempts;
    private boolean refreshWhenResumed;
    private boolean firstResume = true;
    private boolean lastLoginRequired;
    private int sortMode = SORT_SITE;
    private final ArrayList<CommentItem> loadedComments = new ArrayList<>();

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

        sortBar = new LinearLayout(this);
        sortBar.setOrientation(LinearLayout.HORIZONTAL);
        sortBar.setGravity(Gravity.CENTER_VERTICAL);
        sortBar.setPadding(dp(14), 0, dp(14), 0);
        sortBar.setBackgroundColor(Color.rgb(15, 15, 18));
        sortBar.setVisibility(View.GONE);
        shell.addView(sortBar, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView sortTitle = new TextView(this);
        sortTitle.setText("Comment order");
        sortTitle.setTextColor(Color.rgb(160, 160, 169));
        sortTitle.setTextSize(12);
        sortBar.addView(sortTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        sortView = new TextView(this);
        sortView.setText("Site order ▾");
        sortView.setTextColor(Color.rgb(255, 112, 60));
        sortView.setTextSize(12);
        sortView.setTypeface(null, android.graphics.Typeface.BOLD);
        sortView.setGravity(Gravity.CENTER);
        sortView.setPadding(dp(12), dp(8), dp(6), dp(8));
        sortView.setOnClickListener(this::showSortMenu);
        sortBar.addView(sortView, new LinearLayout.LayoutParams(-2, -2));

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
        sortBar.setVisibility(View.GONE);
        loadedComments.clear();
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
        loadedComments.clear();
        Set<String> rendered = new HashSet<>();
        for (int i = 0; i < comments.length(); i++) {
            JSONObject item = comments.optJSONObject(i);
            if (item == null) continue;
            String author = clean(item.optString("author"));
            String time = clean(item.optString("time"));
            String text = clean(item.optString("text"));
            String avatar = clean(item.optString("avatar"));
            String score = clean(item.optString("score"));
            int depth = Math.max(0, Math.min(4, item.optInt("depth", 0)));
            if (!isRealCommentText(text)) continue;
            String key = (author + "|" + text).toLowerCase();
            if (!rendered.add(key)) continue;
            loadedComments.add(new CommentItem(author, time, text, avatar, score, depth, i));
        }
        lastLoginRequired = loginRequired;
        renderLoadedComments();
    }

    private void renderLoadedComments() {
        progress.setVisibility(View.GONE);
        commentsContainer.removeAllViews();

        if (lastLoginRequired) {
            commentsContainer.addView(loginCard(), cardParams());
            actionView.setText("LOGIN");
            actionView.setOnClickListener(v -> openLogin());
        } else {
            actionView.setText("WEB");
            actionView.setOnClickListener(v -> openWebsite());
        }

        if (sortMode == SORT_TOP && !hasScoreData()) sortMode = SORT_SITE;
        if (sortMode == SORT_NEWEST && !hasTimeData()) sortMode = SORT_SITE;

        ArrayList<CommentItem> display = new ArrayList<>(loadedComments);
        if (sortMode == SORT_TOP) {
            display.sort((a, b) -> {
                int scoreCompare = Integer.compare(scoreValue(b.score), scoreValue(a.score));
                return scoreCompare != 0 ? scoreCompare : Integer.compare(a.siteIndex, b.siteIndex);
            });
        } else if (sortMode == SORT_NEWEST) {
            display.sort((a, b) -> {
                long ageA = relativeAgeSeconds(a.time);
                long ageB = relativeAgeSeconds(b.time);
                int timeCompare = Long.compare(ageA, ageB);
                return timeCompare != 0 ? timeCompare : Integer.compare(a.siteIndex, b.siteIndex);
            });
        }

        for (CommentItem item : display) {
            commentsContainer.addView(
                    commentCard(item.author, item.time, item.text, item.avatar, item.score),
                    commentCardParams(item.depth)
            );
        }

        if (loadedComments.isEmpty() && !lastLoginRequired) {
            showFallback("No native comments were found. Tap here to open the website comments.");
            return;
        }
        if (loadedComments.isEmpty()) {
            commentsContainer.addView(messageView(
                    "Log in to load the full comment section. You'll return here automatically after sign-in.",
                    false
            ), new LinearLayout.LayoutParams(-1, -2));
        }

        sortBar.setVisibility(loadedComments.size() > 1 ? View.VISIBLE : View.GONE);
        updateSortLabel();
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, SORT_SITE, 0, "Site order");
        if (hasScoreData()) menu.getMenu().add(0, SORT_TOP, 1, "Top rated");
        if (hasTimeData()) menu.getMenu().add(0, SORT_NEWEST, 2, "Newest first");
        menu.setOnMenuItemClickListener(item -> {
            sortMode = item.getItemId();
            renderLoadedComments();
            return true;
        });
        menu.show();
    }

    private void updateSortLabel() {
        if (sortView == null) return;
        if (sortMode == SORT_TOP) sortView.setText("Top rated ▾");
        else if (sortMode == SORT_NEWEST) sortView.setText("Newest first ▾");
        else sortView.setText("Site order ▾");
    }

    private boolean hasScoreData() {
        for (CommentItem item : loadedComments) {
            if (scoreValue(item.score) != Integer.MIN_VALUE) return true;
        }
        return false;
    }

    private boolean hasTimeData() {
        for (CommentItem item : loadedComments) {
            if (relativeAgeSeconds(item.time) < Long.MAX_VALUE / 4) return true;
        }
        return false;
    }

    private int scoreValue(String value) {
        if (value == null || value.isEmpty()) return Integer.MIN_VALUE;
        Matcher matcher = SCORE_NUMBER.matcher(value);
        if (!matcher.find()) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(matcher.group());
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private long relativeAgeSeconds(String value) {
        if (value == null || value.isEmpty()) return Long.MAX_VALUE / 2;
        String lower = value.toLowerCase();
        if (lower.contains("just now") || lower.equals("now")) return 0L;
        if (lower.contains("yesterday")) return 86400L;
        Matcher matcher = RELATIVE_TIME.matcher(lower);
        if (!matcher.find()) return Long.MAX_VALUE / 2;
        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (Exception e) {
            return Long.MAX_VALUE / 2;
        }
        String unit = matcher.group(2).toLowerCase();
        if (unit.startsWith("s") && !unit.startsWith("sec")) return amount;
        if (unit.startsWith("sec")) return amount;
        if (unit.equals("m") || unit.startsWith("min")) return amount * 60L;
        if (unit.equals("h") || unit.startsWith("hr") || unit.startsWith("hour")) return amount * 3600L;
        if (unit.equals("d") || unit.startsWith("day")) return amount * 86400L;
        if (unit.equals("w") || unit.startsWith("wk") || unit.startsWith("week")) return amount * 604800L;
        if (unit.startsWith("mo") || unit.startsWith("month")) return amount * 2592000L;
        if (unit.equals("y") || unit.startsWith("yr") || unit.startsWith("year")) return amount * 31536000L;
        return Long.MAX_VALUE / 2;
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
        copy.setText("Sign in inside the app. When login succeeds, you'll come straight back here and the full native comment section will reload.");
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

    private View commentCard(String author, String time, String text, String avatarUrl, String score) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setStrokeColor(Color.rgb(48, 48, 54));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(15));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(13));
        card.addView(body, new MaterialCardView.LayoutParams(-1, -2));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        if (!avatarUrl.isEmpty()) {
            ImageView avatar = new ImageView(this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackgroundColor(Color.rgb(43, 43, 48));
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(34), dp(34));
            avatarParams.setMargins(0, 0, dp(9), 0);
            meta.addView(avatar, avatarParams);
            try {
                Glide.with(avatar).load(avatarUrl).circleCrop().into(avatar);
            } catch (Exception ignored) {
            }
        }

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        meta.addView(identity, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = new TextView(this);
        name.setText(author.isEmpty() ? "Comment" : author);
        name.setTextColor(Color.rgb(255, 112, 60));
        name.setTextSize(13);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        identity.addView(name);

        if (!time.isEmpty()) {
            TextView when = new TextView(this);
            when.setText(time);
            when.setTextColor(Color.rgb(145, 145, 154));
            when.setTextSize(11);
            when.setSingleLine(true);
            identity.addView(when);
        }

        if (!score.isEmpty() && score.length() <= 28) {
            TextView votes = new TextView(this);
            votes.setText(score.matches(".*\\d.*") ? "▲ " + score : score);
            votes.setTextColor(Color.rgb(190, 190, 198));
            votes.setTextSize(11);
            votes.setGravity(Gravity.CENTER);
            votes.setPadding(dp(8), dp(5), dp(8), dp(5));
            votes.setBackgroundColor(Color.rgb(35, 35, 40));
            meta.addView(votes, new LinearLayout.LayoutParams(-2, -2));
        }

        TextView copy = new TextView(this);
        copy.setText(text);
        copy.setTextColor(Color.rgb(235, 235, 238));
        copy.setTextSize(14);
        copy.setLineSpacing(0f, 1.08f);
        copy.setPadding(0, dp(8), 0, 0);
        copy.setTextIsSelectable(true);
        body.addView(copy, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(9));
        return params;
    }

    private LinearLayout.LayoutParams commentCardParams(int depth) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(Math.min(4, depth) * 15), 0, 0, dp(9));
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
        sortBar.setVisibility(View.GONE);
        commentsContainer.removeAllViews();
        commentsContainer.addView(messageView(message, true), new LinearLayout.LayoutParams(-1, -2));
        actionView.setText("WEB");
        actionView.setOnClickListener(v -> openWebsite());
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_RETURN_URL, pageUrl);
        startActivityForResult(intent, LOGIN_REQUEST);
    }

    private void openWebsite() {
        refreshWhenResumed = true;
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, pageUrl);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOGIN_REQUEST && resultCode == RESULT_OK) {
            try {
                CookieManager.getInstance().flush();
            } catch (Exception ignored) {
            }
            if (extractor != null) extractor.postDelayed(this::loadComments, 180L);
        }
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

    private static final class CommentItem {
        final String author;
        final String time;
        final String text;
        final String avatar;
        final String score;
        final int depth;
        final int siteIndex;

        CommentItem(String author, String time, String text, String avatar, String score, int depth, int siteIndex) {
            this.author = author;
            this.time = time;
            this.text = text;
            this.avatar = avatar;
            this.score = score;
            this.depth = depth;
            this.siteIndex = siteIndex;
        }
    }
}
