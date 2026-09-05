package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Debounced live lookups with immediate local matches and stale-response rejection. */
final class CreatorSuggestionsController {
    private final Activity activity;
    private final EditText input;
    private final LinearLayout panel;
    private final TextView hint;
    private final CreatorListAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private Future<?> request;
    private int generation;
    private boolean closed;
    private boolean active = true;
    private Runnable debounce;
    interface Lookup { List<FapelloRepository.Model> find(android.content.Context context, String query) throws Exception; }
    private final Lookup lookup;

    CreatorSuggestionsController(Activity activity, EditText input, LinearLayout panel,
                                 Consumer<NativeContentItem> open) {
        this(activity, input, panel, open, (context, query) -> new FapelloRepository().searchConfirmedModels(context, query, 8));
    }

    CreatorSuggestionsController(Activity activity, EditText input, LinearLayout panel,
                                 Consumer<NativeContentItem> open, Lookup lookup) {
        this.activity = activity; this.input = input; this.panel = panel; this.lookup = lookup;
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(android.graphics.Color.BLACK);
        hint = BrowseUi.text(activity, "Type at least 2 characters to find creators", 13, BrowseUi.MUTED);
        hint.setPadding(dp(16), dp(8), dp(16), dp(8));
        panel.addView(hint);
        RecyclerView recycler = new RecyclerView(activity);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        recycler.setItemAnimator(null);
        adapter = new CreatorListAdapter(activity, open, this::refreshLocal);
        recycler.setAdapter(adapter);
        panel.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        input.addTextChangedListener(BrowseUi.onText(value -> show(value)));
        input.setOnFocusChangeListener((v, focused) -> { if (focused) show(input.getText().toString()); });
    }

    void show(String value) {
        if (closed) return;
        active = true;
        panel.setVisibility(View.VISIBLE);
        cancel();
        String query = value.trim();
        int token = generation;
        if (query.length() < 2) {
            adapter.replace(new ArrayList<>());
            hint.setText("Type at least 2 characters to find creators");
            return;
        }
        refreshLocal();
        hint.setText("Creators · Checking for matches…");
        debounce = () -> request = io.submit(() -> {
            String error = null;
            try {
                List<NativeContentItem> results = new ArrayList<>();
                for (FapelloRepository.Model model : lookup.find(activity.getApplicationContext(), query)) {
                    results.add(CreatorCatalog.fromModel(model));
                }
                if (!Thread.currentThread().isInterrupted()) CreatorCatalog.remember(activity, results);
            } catch (Exception failure) { error = "Live suggestions unavailable"; }
            String finalError = error;
            main.post(() -> {
                if (closed || !active || token != generation || activity.isFinishing()
                        || activity.isDestroyed() || !query.equals(input.getText().toString().trim())) return;
                refreshLocal();
                hint.setText(finalError != null
                        ? finalError + (adapter.getItemCount() > 0 ? " · Saved matches shown" : " · You can still tap Search")
                        : adapter.getItemCount() > 0 ? "Creators · Tap to open a gallery"
                        : "No creator suggestions · Tap Search to search by name");
            });
        });
        main.postDelayed(debounce, 280L);
    }

    void refreshLocal() {
        if (closed || !active) return;
        String query = input.getText().toString().trim();
        adapter.replace(query.length() < 2 ? new ArrayList<>()
                : CreatorCatalog.matching(activity, query, false, 8));
    }

    void hide() { active = false; cancel(); panel.setVisibility(View.GONE); }
    boolean isShowing() { return active; }

    private void cancel() {
        generation++;
        if (debounce != null) main.removeCallbacks(debounce);
        if (request != null) request.cancel(true);
    }

    void close() { closed = true; cancel(); main.removeCallbacksAndMessages(null); io.shutdownNow(); }
    private int dp(int value) { return BrowseUi.dp(activity, value); }
}
