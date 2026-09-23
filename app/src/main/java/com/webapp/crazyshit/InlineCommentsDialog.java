package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TikTok-style, in-place comment surface shared by feeds and video pages. */
@SuppressLint("SetTextI18n")
final class InlineCommentsDialog extends BottomSheetDialog {
    interface ResizeListener {
        void onSheetTopChanged(int topOnScreen);
        void onSheetClosed();
    }

    private static final int SORT_SITE = 0;
    private static final int SORT_TOP = 1;
    private static final int SORT_NEWEST = 2;
    private static final Pattern SCORE_NUMBER = Pattern.compile("[-+]?\\d+");
    private static final Pattern RELATIVE_TIME = Pattern.compile(
            "(\\d+)\\s*(second|seconds|sec|secs|s|minute|minutes|min|mins|m|hour|hours|hr|hrs|h|day|days|d|week|weeks|wk|wks|w|month|months|mo|mos|year|years|yr|yrs|y)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Activity activity;
    private final String pageUrl;
    private final String pageTitle;
    private final String count;
    private final ResizeListener resizeListener;
    private final ArrayList<NativeCommentsLoader.Comment> loadedComments = new ArrayList<>();

    private LinearLayout commentsContainer;
    private TextView headerTitle;
    private TextView sortButton;
    private EditText composerInput;
    private TextView composerSend;
    private TextView replyContext;
    private ScrollView scrollView;
    private NativeCommentsLoader.Token loadToken;
    private NativeCommentsLoader.Comment replyTarget;
    private BottomSheetBehavior<FrameLayout> behavior;
    private FrameLayout bottomSheet;
    private int sortMode = SORT_SITE;
    private boolean loginRequired;
    private boolean commentingAvailable;
    private boolean loadingStarted;
    private boolean closeDispatched;
    private boolean loginLaunched;
    private boolean posting;

    InlineCommentsDialog(
            Activity activity,
            String pageUrl,
            String pageTitle,
            String count,
            ResizeListener resizeListener
    ) {
        super(activity);
        this.activity = activity;
        this.pageUrl = clean(pageUrl);
        this.pageTitle = clean(pageTitle).isEmpty() ? "Comments" : clean(pageTitle);
        this.count = clean(count);
        this.resizeListener = resizeListener;

        setCancelable(true);
        setCanceledOnTouchOutside(true);
        setDismissWithAnimation(true);
        setContentView(buildContent());
        setOnDismissListener(dialog -> dispatchClosed());
    }

    @Override
    protected void onStart() {
        super.onStart();
        configureWindow();
        configureSheet();
        if (!loadingStarted) {
            loadingStarted = true;
            loadComments(false);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || !loginLaunched || !isShowing()) return;
        loginLaunched = false;
        NativeCommentsLoader.invalidate(pageUrl);
        loadComments(true);
    }

    private View buildContent() {
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(roundedTop(Color.rgb(16, 16, 18), 24));

        FrameLayout handleRow = new FrameLayout(activity);
        shell.addView(handleRow, new LinearLayout.LayoutParams(-1, dp(18)));
        View handle = new View(activity);
        handle.setBackground(roundRect(Color.rgb(91, 91, 98), 3));
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(38), dp(4));
        handleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = dp(7);
        handleRow.addView(handle, handleParams);

        FrameLayout header = new FrameLayout(activity);
        header.setPadding(dp(12), 0, dp(8), 0);
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));

        headerTitle = new TextView(activity);
        headerTitle.setText(headerText());
        headerTitle.setTextColor(Color.rgb(239, 239, 242));
        headerTitle.setTextSize(14);
        headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        headerTitle.setGravity(Gravity.CENTER);
        headerTitle.setSingleLine(true);
        headerTitle.setContentDescription(pageTitle);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -1);
        titleParams.setMargins(dp(72), 0, dp(96), 0);
        header.addView(headerTitle, titleParams);

        LinearLayout headerActions = new LinearLayout(activity);
        headerActions.setOrientation(LinearLayout.HORIZONTAL);
        headerActions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        FrameLayout.LayoutParams actionsParams = new FrameLayout.LayoutParams(-2, -1);
        actionsParams.gravity = Gravity.END;
        header.addView(headerActions, actionsParams);

        sortButton = headerAction("⇅", "Sort comments");
        sortButton.setVisibility(View.GONE);
        sortButton.setOnClickListener(this::showSortMenu);
        headerActions.addView(sortButton, new LinearLayout.LayoutParams(dp(48), -1));

        TextView close = headerAction("×", "Close comments");
        close.setTextSize(27);
        close.setOnClickListener(v -> dismiss());
        headerActions.addView(close, new LinearLayout.LayoutParams(dp(48), -1));

        View divider = new View(activity);
        divider.setBackgroundColor(Color.rgb(38, 38, 42));
        shell.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        shell.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        commentsContainer = new LinearLayout(activity);
        commentsContainer.setOrientation(LinearLayout.VERTICAL);
        commentsContainer.setPadding(dp(12), dp(7), dp(12), dp(18));
        scrollView.addView(commentsContainer, new ScrollView.LayoutParams(-1, -2));
        showSkeletons();

        shell.addView(buildComposer(), new LinearLayout.LayoutParams(-1, -2));
        return shell;
    }

    private View buildComposer() {
        LinearLayout composer = new LinearLayout(activity);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(12), dp(5), dp(12), dp(9));
        composer.setBackgroundColor(Color.rgb(20, 20, 23));

        replyContext = new TextView(activity);
        replyContext.setTextColor(UiPalette.PRIMARY);
        replyContext.setTextSize(11);
        replyContext.setGravity(Gravity.CENTER_VERTICAL);
        replyContext.setPadding(dp(46), 0, dp(8), 0);
        replyContext.setVisibility(View.GONE);
        replyContext.setClickable(true);
        replyContext.setFocusable(true);
        replyContext.setOnClickListener(v -> cancelReply());
        composer.addView(replyContext, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        composer.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView avatar = new TextView(activity);
        avatar.setText("C");
        avatar.setTextColor(Color.rgb(25, 25, 27));
        avatar.setTextSize(13);
        avatar.setTypeface(null, android.graphics.Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(circle(UiPalette.PRIMARY));
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));

        composerInput = new EditText(activity);
        composerInput.setHint("Add comment…");
        composerInput.setHintTextColor(Color.rgb(157, 157, 166));
        composerInput.setTextColor(Color.WHITE);
        composerInput.setTextSize(13);
        composerInput.setGravity(Gravity.CENTER_VERTICAL);
        composerInput.setPadding(dp(14), 0, dp(12), 0);
        composerInput.setSingleLine(false);
        composerInput.setMinLines(1);
        composerInput.setMaxLines(3);
        composerInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composerInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composerInput.setBackground(roundRect(Color.rgb(34, 34, 38), 20));
        composerInput.setContentDescription("Add a comment");
        composerInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus || !loginRequired) return;
            composerInput.clearFocus();
            openLogin();
        });
        composerInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            submitComposer();
            return true;
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        inputParams.setMargins(dp(10), 0, 0, 0);
        row.addView(composerInput, inputParams);

        composerSend = new TextView(activity);
        composerSend.setText("↑");
        composerSend.setTextColor(Color.rgb(24, 24, 26));
        composerSend.setTextSize(22);
        composerSend.setTypeface(null, android.graphics.Typeface.BOLD);
        composerSend.setGravity(Gravity.CENTER);
        composerSend.setBackground(circle(UiPalette.PRIMARY));
        composerSend.setContentDescription("Post comment");
        composerSend.setClickable(true);
        composerSend.setFocusable(true);
        composerSend.setOnClickListener(v -> submitComposer());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        sendParams.setMargins(dp(8), 0, 0, 0);
        row.addView(composerSend, sendParams);
        return composer;
    }

    private TextView headerAction(String text, String description) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.rgb(221, 221, 226));
        view.setTextSize(20);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void configureWindow() {
        Window window = getWindow();
        if (window == null) return;
        window.setDimAmount(0.18f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setNavigationBarColor(Color.BLACK);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getDecorView().setSystemUiVisibility(0);
    }

    private void configureSheet() {
        bottomSheet = findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;
        bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
        params.height = desiredHeight();
        bottomSheet.setLayoutParams(params);

        behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setFitToContents(true);
        behavior.setHideable(true);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(true);
        behavior.setPeekHeight(dp(72), false);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(View sheet, int newState) {
                notifySheetTop();
                if (newState == BottomSheetBehavior.STATE_HIDDEN) dismiss();
            }

            @Override
            public void onSlide(View sheet, float slideOffset) {
                notifySheetTop();
            }
        });
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheet.post(() -> {
            if (!isShowing() || behavior == null) return;
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            notifySheetTop();
        });
    }

    private int desiredHeight() {
        View content = activity.findViewById(android.R.id.content);
        int available = content == null ? 0 : content.getHeight();
        if (available <= 0) available = activity.getResources().getDisplayMetrics().heightPixels;
        boolean landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        float fraction = landscape ? 0.72f : 0.64f;
        int desired = Math.round(available * fraction);
        int minimum = Math.min(dp(360), available);
        int maximum = Math.max(minimum, available - dp(92));
        return Math.max(minimum, Math.min(maximum, desired));
    }

    private void notifySheetTop() {
        if (resizeListener == null || bottomSheet == null || !isShowing()) return;
        int[] location = new int[2];
        bottomSheet.getLocationOnScreen(location);
        resizeListener.onSheetTopChanged(location[1]);
    }

    private void dispatchClosed() {
        if (closeDispatched) return;
        closeDispatched = true;
        if (loadToken != null) loadToken.cancel();
        hideKeyboard();
        if (resizeListener != null) resizeListener.onSheetClosed();
    }

    private void loadComments(boolean force) {
        if (pageUrl.isEmpty()) {
            showError("This video doesn't have a comment page.");
            return;
        }
        if (loadToken != null) loadToken.cancel();
        if (force) NativeCommentsLoader.invalidate(pageUrl);
        showSkeletons();
        loadToken = NativeCommentsLoader.load(activity, pageUrl, new NativeCommentsLoader.Callback() {
            @Override
            public void onLoaded(NativeCommentsLoader.Payload payload) {
                loadedComments.clear();
                loadedComments.addAll(payload.comments);
                loginRequired = payload.loginRequired && !payload.canComment;
                commentingAvailable = payload.canComment;
                updateComposerState();
                renderComments();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private void showSkeletons() {
        if (commentsContainer == null) return;
        commentsContainer.removeAllViews();
        sortButton.setVisibility(View.GONE);
        for (int i = 0; i < 6; i++) {
            commentsContainer.addView(skeletonRow(i), rowParams(0));
        }
    }

    private View skeletonRow(int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(2), dp(9), dp(2), dp(8));

        View avatar = new View(activity);
        avatar.setAlpha(0.68f);
        avatar.setBackground(circle(Color.rgb(48, 48, 53)));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout lines = new LinearLayout(activity);
        lines.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams linesParams = new LinearLayout.LayoutParams(0, -2, 1f);
        linesParams.setMargins(dp(10), dp(2), 0, 0);
        row.addView(lines, linesParams);
        lines.addView(skeletonLine(index % 2 == 0 ? 82 : 116, 8), new LinearLayout.LayoutParams(dp(index % 2 == 0 ? 82 : 116), dp(8)));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(-1, dp(10));
        secondParams.setMargins(0, dp(10), dp(index % 3 == 0 ? 54 : 18), 0);
        lines.addView(skeletonLine(0, 10), secondParams);
        LinearLayout.LayoutParams thirdParams = new LinearLayout.LayoutParams(dp(76), dp(8));
        thirdParams.setMargins(0, dp(8), 0, 0);
        lines.addView(skeletonLine(76, 8), thirdParams);
        return row;
    }

    private View skeletonLine(int width, int height) {
        View line = new View(activity);
        line.setAlpha(0.68f);
        line.setBackground(roundRect(Color.rgb(48, 48, 53), Math.max(3, height / 2)));
        return line;
    }

    private void renderComments() {
        commentsContainer.removeAllViews();

        if (loginRequired) commentsContainer.addView(loginRow(), rowParams(0));

        if (sortMode == SORT_TOP && !hasScoreData()) sortMode = SORT_SITE;
        if (sortMode == SORT_NEWEST && !hasTimeData()) sortMode = SORT_SITE;

        ArrayList<NativeCommentsLoader.Comment> display = new ArrayList<>(loadedComments);
        if (sortMode == SORT_TOP) {
            display.sort((a, b) -> {
                int scoreCompare = Integer.compare(scoreValue(b.score), scoreValue(a.score));
                return scoreCompare != 0 ? scoreCompare : Integer.compare(a.siteIndex, b.siteIndex);
            });
        } else if (sortMode == SORT_NEWEST) {
            display.sort((a, b) -> {
                int timeCompare = Long.compare(relativeAgeSeconds(a.time), relativeAgeSeconds(b.time));
                return timeCompare != 0 ? timeCompare : Integer.compare(a.siteIndex, b.siteIndex);
            });
        }

        for (NativeCommentsLoader.Comment item : display) {
            commentsContainer.addView(commentRow(item), rowParams(item.depth));
        }

        if (loadedComments.isEmpty() && !loginRequired) {
            if (commentingAvailable) {
                commentsContainer.addView(message("No comments yet. Start the conversation."), rowParams(0));
            } else {
                showError("No comments were found on this page.");
                return;
            }
        }
        if (loadedComments.isEmpty()) {
            commentsContainer.addView(message("Log in to load the full comment section."), rowParams(0));
        }

        if (count.isEmpty() && !loadedComments.isEmpty()) {
            headerTitle.setText(loadedComments.size() + (loadedComments.size() == 1 ? " comment" : " comments"));
        }
        sortButton.setVisibility(loadedComments.size() > 1 && (hasScoreData() || hasTimeData())
                ? View.VISIBLE : View.GONE);
        scrollView.scrollTo(0, 0);
    }

    private View commentRow(NativeCommentsLoader.Comment item) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(2), dp(9), dp(2), dp(9));

        View avatar = avatar(item);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, -2, 1f);
        contentParams.setMargins(dp(10), 0, 0, 0);
        row.addView(content, contentParams);

        TextView identity = new TextView(activity);
        String author = item.author.isEmpty() ? "CrazyShit user" : item.author;
        identity.setText(item.time.isEmpty() ? author : author + "  ·  " + item.time);
        identity.setTextColor(Color.rgb(171, 171, 180));
        identity.setTextSize(12);
        identity.setTypeface(null, android.graphics.Typeface.BOLD);
        identity.setSingleLine(true);
        content.addView(identity, new LinearLayout.LayoutParams(-1, -2));

        TextView copy = new TextView(activity);
        copy.setText(item.text);
        copy.setTextColor(Color.rgb(239, 239, 242));
        copy.setTextSize(14);
        copy.setLineSpacing(0f, 1.08f);
        copy.setPadding(0, dp(4), 0, 0);
        copy.setTextIsSelectable(true);
        content.addView(copy, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(26));
        actionParams.topMargin = dp(2);
        content.addView(actions, actionParams);

        TextView reply = smallAction("Reply");
        reply.setContentDescription("Reply to " + author);
        reply.setClickable(true);
        reply.setFocusable(true);
        reply.setOnClickListener(v -> {
            if (loginRequired) openLogin();
            else startReply(item);
        });
        actions.addView(reply, new LinearLayout.LayoutParams(-2, -1));

        if (!item.score.isEmpty() && item.score.length() <= 28) {
            TextView score = smallAction("♡  " + item.score);
            LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(-2, -1);
            scoreParams.setMargins(dp(18), 0, 0, 0);
            actions.addView(score, scoreParams);
        }
        return row;
    }

    private View avatar(NativeCommentsLoader.Comment item) {
        if (!item.avatar.isEmpty()) {
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(circle(Color.rgb(43, 43, 48)));
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            try {
                Glide.with(image).load(item.avatar).circleCrop().into(image);
            } catch (Exception ignored) {
            }
            return image;
        }

        TextView fallback = new TextView(activity);
        String author = item.author.isEmpty() ? "C" : item.author;
        fallback.setText(author.substring(0, 1).toUpperCase(Locale.US));
        fallback.setTextColor(Color.rgb(28, 28, 30));
        fallback.setTextSize(13);
        fallback.setTypeface(null, android.graphics.Typeface.BOLD);
        fallback.setGravity(Gravity.CENTER);
        fallback.setBackground(circle(Color.rgb(183, 181, 80)));
        fallback.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return fallback;
    }

    private TextView smallAction(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.rgb(139, 139, 148));
        view.setTextSize(11);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private View loginRow() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundRect(Color.rgb(37, 36, 17), 14));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openLogin());

        TextView title = new TextView(activity);
        title.setText("Log in to load every comment");
        title.setTextColor(UiPalette.PRIMARY);
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView copy = new TextView(activity);
        copy.setText("Tap to sign in, then open comments again.");
        copy.setTextColor(Color.rgb(194, 194, 201));
        copy.setTextSize(12);
        copy.setPadding(0, dp(4), 0, 0);
        card.addView(copy);
        return card;
    }

    private TextView message(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.rgb(180, 180, 188));
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(30), dp(20), dp(30));
        return view;
    }

    private void showError(String text) {
        commentsContainer.removeAllViews();
        sortButton.setVisibility(View.GONE);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(20), dp(34), dp(20), dp(24));
        panel.addView(message(text), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView retry = actionPill("Retry");
        retry.setOnClickListener(v -> loadComments(true));
        actions.addView(retry, new LinearLayout.LayoutParams(dp(112), dp(48)));

        TextView web = actionPill("Website");
        web.setOnClickListener(v -> openWebsite());
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(dp(112), dp(48));
        webParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(web, webParams);
        commentsContainer.addView(panel, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView actionPill(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(UiPalette.PRIMARY);
        view.setTextSize(12);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundRect(Color.rgb(34, 34, 38), 19));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private LinearLayout.LayoutParams rowParams(int depth) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(Math.min(3, Math.max(0, depth)) * 14), 0, 0, dp(2));
        return params;
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, SORT_SITE, 0, "Site order");
        if (hasScoreData()) menu.getMenu().add(0, SORT_TOP, 1, "Top rated");
        if (hasTimeData()) menu.getMenu().add(0, SORT_NEWEST, 2, "Newest first");
        menu.setOnMenuItemClickListener(item -> {
            sortMode = item.getItemId();
            renderComments();
            return true;
        });
        menu.show();
    }

    private boolean hasScoreData() {
        for (NativeCommentsLoader.Comment item : loadedComments) {
            if (scoreValue(item.score) != Integer.MIN_VALUE) return true;
        }
        return false;
    }

    private boolean hasTimeData() {
        for (NativeCommentsLoader.Comment item : loadedComments) {
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
        String lower = value.toLowerCase(Locale.US);
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
        String unit = matcher.group(2).toLowerCase(Locale.US);
        if (unit.startsWith("s")) return amount;
        if (unit.equals("m") || unit.startsWith("min")) return amount * 60L;
        if (unit.equals("h") || unit.startsWith("hr") || unit.startsWith("hour")) return amount * 3600L;
        if (unit.equals("d") || unit.startsWith("day")) return amount * 86400L;
        if (unit.equals("w") || unit.startsWith("wk") || unit.startsWith("week")) return amount * 604800L;
        if (unit.startsWith("mo") || unit.startsWith("month")) return amount * 2592000L;
        if (unit.equals("y") || unit.startsWith("yr") || unit.startsWith("year")) return amount * 31536000L;
        return Long.MAX_VALUE / 2;
    }

    private void openLogin() {
        posting = false;
        setComposerEnabled(true);
        if (composerInput != null) composerInput.clearFocus();
        hideKeyboard();
        AccountComingSoonDialog.show(activity);
    }

    private void startReply(NativeCommentsLoader.Comment item) {
        if (posting || item == null || composerInput == null) return;
        if (loginRequired) {
            openLogin();
            return;
        }
        replyTarget = item;
        updateComposerState();
        composerInput.requestFocus();
        composerInput.setSelection(composerInput.length());
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(composerInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void cancelReply() {
        replyTarget = null;
        updateComposerState();
    }

    private void submitComposer() {
        if (posting || composerInput == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (loginRequired) {
            openLogin();
            return;
        }
        String message = composerInput.getText() == null
                ? ""
                : composerInput.getText().toString().trim();
        if (message.isEmpty()) {
            composerInput.setError("Write something first.");
            return;
        }
        if (message.length() > 2400) {
            composerInput.setError("Keep the comment under 2,400 characters.");
            return;
        }

        NativeCommentsLoader.Comment submittedReply = replyTarget;
        posting = true;
        setComposerEnabled(false);

        NativeCommentPoster.post(activity, pageUrl, message, submittedReply, new NativeCommentPoster.Callback() {
            @Override
            public void onPosted() {
                posting = false;
                if (composerInput != null) composerInput.setText("");
                replyTarget = null;
                setComposerEnabled(true);
                updateComposerState();
                hideKeyboard();
                Toast.makeText(activity,
                        submittedReply == null ? "Comment posted." : "Reply posted.",
                        Toast.LENGTH_SHORT).show();
                NativeCommentsLoader.invalidate(pageUrl);
                if (isShowing()) loadComments(true);
            }

            @Override
            public void onLoginRequired() {
                posting = false;
                loginRequired = true;
                setComposerEnabled(true);
                updateComposerState();
                openLogin();
            }

            @Override
            public void onError(String message) {
                posting = false;
                setComposerEnabled(true);
                if (!isShowing() || composerInput == null) {
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    return;
                }
                composerInput.setError(message);
            }
        });
    }

    private String displayAuthor(NativeCommentsLoader.Comment item) {
        if (item == null || item.author == null || item.author.isEmpty()) return "this comment";
        return item.author;
    }

    private void updateComposerState() {
        if (composerInput == null || composerSend == null || replyContext == null) return;
        if (replyTarget == null) {
            replyContext.setVisibility(View.GONE);
        } else {
            replyContext.setText("Replying to " + displayAuthor(replyTarget) + "  ×");
            replyContext.setContentDescription("Cancel reply to " + displayAuthor(replyTarget));
            replyContext.setVisibility(View.VISIBLE);
        }
        String hint = loginRequired
                ? "Log in to comment"
                : replyTarget == null ? "Add comment…" : "Reply to " + displayAuthor(replyTarget) + "…";
        composerInput.setHint(hint);
        composerInput.setContentDescription(hint);
        composerSend.setContentDescription(replyTarget == null ? "Post comment" : "Post reply");
    }

    private void setComposerEnabled(boolean enabled) {
        if (composerInput != null) composerInput.setEnabled(enabled);
        if (composerSend == null) return;
        composerSend.setEnabled(enabled);
        composerSend.setAlpha(enabled ? 1f : 0.52f);
        composerSend.setText(enabled ? "↑" : "…");
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) focused = composerInput;
        if (focused == null) return;
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }

    private void openWebsite() {
        dismiss();
        Intent intent = new Intent(activity, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, pageUrl);
        activity.startActivity(intent);
    }

    private String headerText() {
        if (count.isEmpty()) return "Comments";
        String lower = count.toLowerCase(Locale.US);
        return lower.contains("comment") ? count : count + " comments";
    }

    private GradientDrawable roundedTop(int color, int radiusDp) {
        float radius = dp(radiusDp);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        return drawable;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
