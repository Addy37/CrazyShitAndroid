package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;

/** Full-screen native viewer for static meme images. No Media3/player behavior. */
public final class MemeViewerActivity extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_IMAGE_URL = "image_url";

    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private String title;
    private String pageUrl;
    private String imageUrl;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        title = clean(getIntent().getStringExtra(EXTRA_TITLE));
        pageUrl = clean(getIntent().getStringExtra(EXTRA_PAGE_URL));
        imageUrl = clean(getIntent().getStringExtra(EXTRA_IMAGE_URL));
        if (title.isEmpty()) title = "Meme";

        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.rgb(14, 14, 16));

        TextView back = action("‹", 30);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(17);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setPadding(dp(8), 0, dp(8), 0);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView share = action("↗", 22);
        share.setContentDescription("Share meme page");
        share.setOnClickListener(v -> share());
        top.addView(share, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView web = action("⋮", 26);
        web.setContentDescription("Open meme page");
        web.setOnClickListener(v -> openPage());
        top.addView(web, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.BLACK);
        stage.addView(image, new FrameLayout.LayoutParams(-1, -1));

        ProgressBar loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        loadingParams.gravity = Gravity.CENTER;
        stage.addView(loading, loadingParams);

        TextView failure = new TextView(this);
        failure.setText("Couldn't load this image.\nTap to open the meme page.");
        failure.setTextColor(Color.rgb(190, 190, 198));
        failure.setTextSize(14);
        failure.setGravity(Gravity.CENTER);
        failure.setPadding(dp(28), dp(28), dp(28), dp(28));
        failure.setVisibility(View.GONE);
        failure.setOnClickListener(v -> openPage());
        stage.addView(failure, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        if (imageUrl.isEmpty()) {
            loading.setVisibility(View.GONE);
            failure.setVisibility(View.VISIBLE);
            return;
        }

        Glide.with(image)
                .load(withSiteHeaders(imageUrl, pageUrl))
                .fitCenter()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.BLACK))
                .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean first) {
                        loading.setVisibility(View.GONE);
                        failure.setVisibility(View.VISIBLE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource source, boolean first) {
                        loading.setVisibility(View.GONE);
                        failure.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(image);
    }

    private void share() {
        String value = pageUrl.isEmpty() ? imageUrl : pageUrl;
        if (value.isEmpty()) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, value);
        share.putExtra(Intent.EXTRA_SUBJECT, title);
        startActivity(Intent.createChooser(share, "Share meme"));
    }

    private void openPage() {
        if (pageUrl.isEmpty()) return;
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, pageUrl);
        startActivity(intent);
    }

    private GlideUrl withSiteHeaders(String url, String referer) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", referer.isEmpty() ? SITE : referer)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if ((cookies == null || cookies.trim().isEmpty()) && !referer.isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(referer);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(url, headers.build());
    }

    private TextView action(String label, int size) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
