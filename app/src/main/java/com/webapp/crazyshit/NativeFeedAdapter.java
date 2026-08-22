package com.webapp.crazyshit;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public final class NativeFeedAdapter extends RecyclerView.Adapter<NativeFeedAdapter.Holder> {
    public interface Listener {
        void onOpen(NativeContentItem item);
        void onLongPress(NativeContentItem item, View anchor);
    }

    private final List<NativeContentItem> items = new ArrayList<>();
    private final Listener listener;

    public NativeFeedAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void replace(List<NativeContentItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    public void append(List<NativeContentItem> next) {
        if (next == null || next.isEmpty()) return;
        int start = items.size();
        for (NativeContentItem item : next) {
            boolean duplicate = false;
            for (NativeContentItem old : items) {
                if (old.url.equals(item.url)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) items.add(item);
        }
        int added = items.size() - start;
        if (added > 0) notifyItemRangeInserted(start, added);
    }

    public int size() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).url.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int margin = dp(parent, 12);

        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setRadius(dp(parent, 20));
        card.setCardElevation(dp(parent, 2));
        card.setStrokeColor(Color.rgb(50, 50, 57));
        card.setStrokeWidth(dp(parent, 1));
        RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(-1, -2);
        cardParams.setMargins(margin, dp(parent, 7), margin, dp(parent, 7));
        card.setLayoutParams(cardParams);

        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -2));

        FrameLayout mediaFrame = new FrameLayout(parent.getContext());
        column.addView(mediaFrame, new LinearLayout.LayoutParams(-1, dp(parent, 218)));

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(11, 11, 13));
        mediaFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView play = new TextView(parent.getContext());
        play.setText("▶");
        play.setTextColor(Color.WHITE);
        play.setTextSize(27);
        play.setGravity(Gravity.CENTER);
        play.setBackground(new ColorDrawable(Color.argb(165, 0, 0, 0)));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(parent, 54), dp(parent, 54));
        playParams.gravity = Gravity.CENTER;
        mediaFrame.addView(play, playParams);

        LinearLayout copy = new LinearLayout(parent.getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(parent, 15), dp(parent, 13), dp(parent, 15), dp(parent, 15));
        column.addView(copy, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = new TextView(parent.getContext());
        meta.setTextColor(Color.rgb(170, 170, 180));
        meta.setTextSize(13);
        meta.setPadding(0, dp(parent, 7), 0, 0);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        return new Holder(card, image, title, meta);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        holder.title.setText(item.title);
        holder.meta.setText(buildMeta(item));

        if (item.imageUrl == null || item.imageUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 23)));
        } else {
            Glide.with(holder.image)
                    .load(item.imageUrl)
                    .centerCrop()
                    .placeholder(new ColorDrawable(Color.rgb(20, 20, 23)))
                    .error(new ColorDrawable(Color.rgb(20, 20, 23)))
                    .into(holder.image);
        }

        holder.card.setOnClickListener(v -> listener.onOpen(item));
        holder.card.setOnLongClickListener(v -> {
            listener.onLongPress(item, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String buildMeta(NativeContentItem item) {
        ArrayList<String> parts = new ArrayList<>();
        if (item.views != null && !item.views.isEmpty()) parts.add(item.views + " views");
        if (item.uploader != null && !item.uploader.isEmpty()) parts.add(item.uploader);
        if (item.comments != null && !item.comments.isEmpty()) parts.add(item.comments + " comments");
        return TextUtils.join("  •  ", parts);
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView title;
        final TextView meta;

        Holder(MaterialCardView card, ImageView image, TextView title, TextView meta) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
            this.meta = meta;
        }
    }
}
