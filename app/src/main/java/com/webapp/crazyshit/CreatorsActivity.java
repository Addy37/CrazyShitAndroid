package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** All starred creators, including favorites saved before the creator catalog existed. */
public final class CreatorsActivity extends Activity {
    private EditText input;
    private TextView empty, count;
    private RecyclerView recycler;
    private CreatorGridAdapter adapter;
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
        recycler.setLayoutManager(new GridLayoutManager(this, 3));
        recycler.setItemAnimator(null);
        recycler.setClipToPadding(false);
        recycler.setPadding(dp(8), dp(2), dp(8), dp(20));
        adapter = new CreatorGridAdapter();
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

    private void openCreator(NativeContentItem item) {
        BrowseUi.hideKeyboard(this, input);
        startActivity(NativeFeedBrowserActivity.createCreatorGallery(
                this,
                item.title,
                item.searchQuery.isEmpty() ? item.title : item.searchQuery,
                NativeFeedBrowserActivity.creatorProfileHint(item),
                CreatorGalleryPreloader.sessionId(this, item)
        ));
    }

    private final class CreatorGridAdapter
            extends RecyclerView.Adapter<CreatorGridAdapter.Holder> {
        private final List<NativeContentItem> items = new ArrayList<>();

        void replace(List<NativeContentItem> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout wrapper = new LinearLayout(CreatorsActivity.this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
            wrapper.setPadding(dp(4), dp(7), dp(4), dp(8));
            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(dp(2), dp(2), dp(2), dp(4));
            wrapper.setLayoutParams(params);
            wrapper.setFocusable(true);
            wrapper.setClickable(true);
            ZeroChillMotion.installPressFeedback(wrapper);

            FrameLayout avatarFrame = new FrameLayout(CreatorsActivity.this);
            wrapper.addView(avatarFrame, new LinearLayout.LayoutParams(dp(86), dp(86)));

            MaterialCardView avatarCard = new MaterialCardView(CreatorsActivity.this);
            avatarCard.setRadius(dp(43));
            avatarCard.setCardElevation(0f);
            avatarCard.setStrokeWidth(0);
            avatarCard.setCardBackgroundColor(Color.rgb(19, 23, 27));
            avatarFrame.addView(avatarCard, new FrameLayout.LayoutParams(-1, -1));

            ImageView avatar = new ImageView(CreatorsActivity.this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackgroundColor(Color.rgb(19, 23, 27));
            avatarCard.addView(avatar, new MaterialCardView.LayoutParams(-1, -1));

            TextView favorite = BrowseUi.text(CreatorsActivity.this, "★", 18, UiPalette.PRIMARY);
            favorite.setGravity(Gravity.CENTER);
            favorite.setBackground(BrowseUi.rounded(
                    CreatorsActivity.this,
                    Color.argb(190, 0, 0, 0),
                    14
            ));
            favorite.setClickable(true);
            favorite.setFocusable(true);
            FrameLayout.LayoutParams favoriteParams =
                    new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.END);
            favoriteParams.setMargins(0, dp(1), dp(1), 0);
            avatarFrame.addView(favorite, favoriteParams);

            TextView name = BrowseUi.text(CreatorsActivity.this, "", 12.5f, Color.WHITE);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(-1, -2);
            nameParams.setMargins(dp(2), dp(7), dp(2), 0);
            wrapper.addView(name, nameParams);

            return new Holder(wrapper, avatar, name, favorite);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            NativeContentItem item = items.get(position);
            holder.bound = item;
            holder.name.setText(item.title);
            holder.itemView.setContentDescription("Open " + item.title);
            holder.favorite.setContentDescription("Remove " + item.title + " from favorite creators");

            holder.itemView.setOnClickListener(v -> openCreator(item));
            View.OnClickListener remove = v -> {
                CreatorFavoriteStore.toggle(CreatorsActivity.this, item);
                render();
            };
            holder.favorite.setOnClickListener(remove);
            holder.itemView.setOnLongClickListener(v -> {
                remove.onClick(v);
                return true;
            });

            CreatorGalleryPreloader.warm(
                    CreatorsActivity.this,
                    item,
                    position < 6
                            ? CreatorGalleryPreloader.PRIORITY_HIGH
                            : CreatorGalleryPreloader.PRIORITY_NORMAL
            );

            Glide.with(holder.avatar).clear(holder.avatar);
            holder.avatar.setImageDrawable(new ColorDrawable(Color.rgb(19, 23, 27)));
            if (!item.imageUrl.isEmpty()) {
                GlideUrl url = new GlideUrl(
                        item.imageUrl,
                        new LazyHeaders.Builder()
                                .addHeader(
                                        "Referer",
                                        item.uploader.isEmpty() ? item.url : item.uploader
                                )
                                .addHeader(
                                        "User-Agent",
                                        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139.0 Mobile Safari/537.36"
                                )
                                .build()
                );
                Glide.with(holder.avatar)
                        .load(url)
                        .circleCrop()
                        .dontAnimate()
                        .placeholder(new ColorDrawable(Color.rgb(19, 23, 27)))
                        .error(R.drawable.ic_more_account)
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_more_account);
            }
        }

        @Override
        public void onViewRecycled(Holder holder) {
            CreatorGalleryPreloader.cancelQueued(holder.bound);
            holder.bound = null;
            Glide.with(holder.avatar).clear(holder.avatar);
            super.onViewRecycled(holder);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView name;
            final TextView favorite;
            NativeContentItem bound;

            Holder(View itemView, ImageView avatar, TextView name, TextView favorite) {
                super(itemView);
                this.avatar = avatar;
                this.name = name;
                this.favorite = favorite;
            }
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
