package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads the site's comments without opening another Activity.
 *
 * A small in-memory cache lets a ready player warm its comment section without competing
 * with initial video startup. Requests for the same page share one hidden WebView.
 */
final class NativeCommentsLoader {
    interface Callback {
        void onLoaded(Payload payload);
        void onError(String message);
    }

    static final class Token {
        private boolean cancelled;

        void cancel() {
            cancelled = true;
        }
    }

    static final class Payload {
        final ArrayList<Comment> comments;
        final boolean loginRequired;
        final boolean canComment;

        Payload(ArrayList<Comment> comments, boolean loginRequired, boolean canComment) {
            this.comments = comments;
            this.loginRequired = loginRequired;
            this.canComment = canComment;
        }
    }

    static final class Comment {
        final String author;
        final String time;
        final String text;
        final String avatar;
        final String score;
        final int depth;
        final int siteIndex;

        Comment(String author, String time, String text, String avatar, String score, int depth, int siteIndex) {
            this.author = author;
            this.time = time;
            this.text = text;
            this.avatar = avatar;
            this.score = score;
            this.depth = depth;
            this.siteIndex = siteIndex;
        }
    }

    private static final long CACHE_TTL_MS = 10L * 60L * 1000L;
    private static final int MAX_CACHE_ENTRIES = 16;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, PendingRequest> PENDING = new HashMap<>();
    private static final LinkedHashMap<String, CacheEntry> CACHE =
            new LinkedHashMap<String, CacheEntry>(MAX_CACHE_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };

    private NativeCommentsLoader() {
    }

    static Token load(Activity activity, String pageUrl, Callback callback) {
        Token token = new Token();
        String url = clean(pageUrl);
        if (activity == null || url.isEmpty()) {
            if (callback != null) callback.onError("This comment section has no page URL.");
            return token;
        }

        Runnable start = () -> startOnMain(activity, url, token, callback);
        if (Looper.myLooper() == Looper.getMainLooper()) start.run();
        else MAIN.post(start);
        return token;
    }

    static void preload(Activity activity, String pageUrl) {
        load(activity, pageUrl, null);
    }

    static void invalidate(String pageUrl) {
        String url = clean(pageUrl);
        if (url.isEmpty()) return;
        Runnable remove = () -> CACHE.remove(url);
        if (Looper.myLooper() == Looper.getMainLooper()) remove.run();
        else MAIN.post(remove);
    }

    private static void startOnMain(Activity activity, String url, Token token, Callback callback) {
        if (token.cancelled || activity.isFinishing() || activity.isDestroyed()) return;

        CacheEntry cached = CACHE.get(url);
        if (cached != null) {
            if (SystemClock.elapsedRealtime() - cached.loadedAt <= CACHE_TTL_MS) {
                if (callback != null && !token.cancelled) callback.onLoaded(cached.payload);
                return;
            }
            CACHE.remove(url);
        }

        PendingRequest existing = PENDING.get(url);
        if (existing != null) {
            if (callback != null) existing.listeners.add(new Listener(token, callback));
            return;
        }

        PendingRequest request = new PendingRequest(activity, url);
        if (callback != null) request.listeners.add(new Listener(token, callback));
        PENDING.put(url, request);
        request.start();
    }

    private static final class PendingRequest {
        final Activity activity;
        final String url;
        final ArrayList<Listener> listeners = new ArrayList<>();
        WebView webView;
        ViewGroup host;
        int attempts;
        boolean finished;

        PendingRequest(Activity activity, String url) {
            this.activity = activity;
            this.url = url;
        }

        @SuppressLint("SetJavaScriptEnabled")
        void start() {
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
                settings.setUserAgentString(CommentsActivity.USER_AGENT);
                try {
                    CookieManager.getInstance().setAcceptCookie(true);
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
                    CookieManager.getInstance().flush();
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
                    public void onPageFinished(WebView view, String loadedUrl) {
                        if (finished) return;
                        attempts = 0;
                        try {
                            CookieManager.getInstance().flush();
                        } catch (Exception ignored) {
                        }
                        view.postDelayed(PendingRequest.this::probe, 520L);
                    }

                    @Override
                    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        completeError("The comment loader stopped unexpectedly. Tap Retry.");
                        return true;
                    }
                });
                webView.loadUrl(url);
            } catch (Exception e) {
                completeError("Comments couldn't be loaded here.");
            }
        }

        void probe() {
            if (finished || webView == null) return;
            if (activity.isFinishing() || activity.isDestroyed()) {
                completeError("Comments were closed before loading finished.");
                return;
            }
            try {
                webView.evaluateJavascript(CommentsActivity.COMMENTS_JS, raw -> {
                    if (finished) return;
                    JSONObject object = decodeObject(raw);
                    if (object != null) {
                        JSONArray array = object.optJSONArray("comments");
                        boolean loginRequired = object.optBoolean("loginRequired", false);
                        boolean canComment = object.optBoolean("canComment", false);
                        if ((array != null && array.length() > 0) || loginRequired || canComment) {
                            Payload payload = new Payload(
                                    parseComments(array == null ? new JSONArray() : array),
                                    loginRequired,
                                    canComment
                            );
                            complete(payload);
                            return;
                        }
                    }

                    attempts++;
                    if (attempts < 4 && webView != null) {
                        long delay = attempts == 1 ? 650L : attempts == 2 ? 1000L : 1450L;
                        webView.postDelayed(this::probe, delay);
                    } else {
                        completeError("No comments were found on this page.");
                    }
                });
            } catch (Exception e) {
                completeError("Comments couldn't be extracted from this page.");
            }
        }

        void complete(Payload payload) {
            if (finished) return;
            finished = true;
            CACHE.put(url, new CacheEntry(payload));
            PENDING.remove(url);
            cleanup();
            for (Listener listener : new ArrayList<>(listeners)) {
                if (!listener.token.cancelled) listener.callback.onLoaded(payload);
            }
            listeners.clear();
        }

        void completeError(String message) {
            if (finished) return;
            finished = true;
            PENDING.remove(url);
            cleanup();
            for (Listener listener : new ArrayList<>(listeners)) {
                if (!listener.token.cancelled) listener.callback.onError(message);
            }
            listeners.clear();
        }

        void cleanup() {
            if (webView == null) return;
            try {
                webView.stopLoading();
                webView.removeCallbacks(this::probe);
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

    private static ArrayList<Comment> parseComments(JSONArray comments) {
        ArrayList<Comment> parsed = new ArrayList<>();
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

            boolean handleRow = author.isEmpty() && text.matches("^@[A-Za-z0-9][A-Za-z0-9_.-]{1,39}$");
            if (handleRow && i + 1 < comments.length()) {
                JSONObject next = comments.optJSONObject(i + 1);
                if (next != null) {
                    String nextAuthor = clean(next.optString("author"));
                    String nextText = clean(next.optString("text"));
                    boolean nextIsHandle = nextText.matches("^@[A-Za-z0-9][A-Za-z0-9_.-]{1,39}$");
                    if (nextAuthor.isEmpty() && isRealCommentText(nextText) && !nextIsHandle) {
                        author = text;
                        text = nextText;
                        if (time.isEmpty()) time = clean(next.optString("time"));
                        if (avatar.isEmpty()) avatar = clean(next.optString("avatar"));
                        if (score.isEmpty()) score = clean(next.optString("score"));
                        depth = Math.min(depth, Math.max(0, Math.min(4, next.optInt("depth", depth))));
                        i++;
                    }
                }
            }

            if (!isRealCommentText(text)) continue;
            if (text.matches("^@[A-Za-z0-9][A-Za-z0-9_.-]{1,39}$")) continue;
            String key = (author + "|" + text).toLowerCase(Locale.ROOT);
            if (!rendered.add(key)) continue;
            parsed.add(new Comment(author, time, text, avatar, score, depth, i));
        }
        return parsed;
    }

    private static boolean isRealCommentText(String text) {
        String value = clean(text);
        if (value.length() < 5 || value.length() > 2600) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("top") || lower.equals("bottom") || lower.equals("comments") || lower.equals("comment")) {
            return false;
        }
        if (lower.contains("please login to view all comments") ||
                lower.contains("please log in to view all comments")) return false;
        return !value.matches("^[+\\-]?\\d+(?:\\s+[+\\-]?\\d+)*$");
    }

    private static JSONObject decodeObject(String raw) {
        if (raw == null || raw.equals("null")) return null;
        try {
            Object outer = new JSONTokener(raw).nextValue();
            if (!(outer instanceof String)) return null;
            return new JSONObject((String) outer);
        } catch (Exception e) {
            return null;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static final class Listener {
        final Token token;
        final Callback callback;

        Listener(Token token, Callback callback) {
            this.token = token;
            this.callback = callback;
        }
    }

    private static final class CacheEntry {
        final Payload payload;
        final long loadedAt = SystemClock.elapsedRealtime();

        CacheEntry(Payload payload) {
            this.payload = payload;
        }
    }
}
