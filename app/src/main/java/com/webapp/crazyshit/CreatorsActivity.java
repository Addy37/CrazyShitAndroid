package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/** All starred creators, including favorites saved before the creator catalog existed. */
public final class CreatorsActivity extends Activity {
    private EditText input;
    private TextView empty, count;
    private RecyclerView recycler;
    private CreatorListAdapter adapter;
    private Parcelable pendingScroll;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = BrowseUi.screen(this);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.addView(BrowseUi.action(this, "‹", "Back", v -> finish()), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = BrowseUi.text(this, "Favorite creators", 20, Color.WHITE);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(BrowseUi.action(this, "+", "Find creators", v -> startActivity(SearchActivity.createBunkrSearch(this))),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);
        input = new EditText(this);
        input.setHint("Search your favorite creators");
        input.setHintTextColor(BrowseUi.MUTED);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(BrowseUi.rounded(this, BrowseUi.SURFACE, 14));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(50));
        inputParams.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(input, inputParams);
        count = BrowseUi.text(this, "", 12, BrowseUi.MUTED);
        count.setPadding(dp(16), dp(4), dp(16), dp(8));
        root.addView(count);
        empty = BrowseUi.text(this, "", 15, BrowseUi.MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.addView(empty);
        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setItemAnimator(null);
        adapter = new CreatorListAdapter(this, item -> {
            BrowseUi.hideKeyboard(this, input);
            startActivity(NativeFeedBrowserActivity.createCreatorGallery(this, item.title,
                    item.searchQuery.isEmpty() ? item.title : item.searchQuery,
                    FapelloRepository.isModelUrl(item.url) ? item.url : "",
                    CreatorGalleryPreloader.sessionId(item)));
        }, this::render);
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        input.addTextChangedListener(BrowseUi.onText(value -> render()));
        if (state != null) {
            input.setText(state.getString("query", ""));
            pendingScroll = state.getParcelable("scroll");
        }
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void render() {
        List<NativeContentItem> creators = CreatorCatalog.matching(this, input.getText().toString(), true, 5000);
        adapter.replace(creators);
        count.setText(creators.size() + (creators.size() == 1 ? " creator · A–Z" : " creators · A–Z"));
        empty.setVisibility(creators.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(input.length() == 0 ? "No favorite creators yet\n\nTap + to find creators, then tap ☆ to save them here."
                : "No favorite creators match your search.");
        if (pendingScroll != null) {
            recycler.getLayoutManager().onRestoreInstanceState(pendingScroll);
            pendingScroll = null;
        }
    }

    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("query", input.getText().toString());
        state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        super.onSaveInstanceState(state);
    }
    private int dp(int value) { return BrowseUi.dp(this, value); }
}
