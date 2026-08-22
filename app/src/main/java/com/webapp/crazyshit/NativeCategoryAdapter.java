package com.webapp.crazyshit;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public final class NativeCategoryAdapter extends RecyclerView.Adapter<NativeCategoryAdapter.Holder> {
    public interface Listener {
        void onOpen(NativeContentItem item);
    }

    private final List<NativeContentItem> items = new ArrayList<>();
    private final Listener listener;

    public NativeCategoryAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void replace(List<NativeContentItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).url.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(Color.rgb(27, 27, 31));
        card.setRadius(dp(parent, 18));
        card.setStrokeWidth(dp(parent, 1));
        card.setStrokeColor(Color.rgb(55, 55, 62));
        card.setCardElevation(0f);

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(parent, 96));
        params.setMargins(dp(parent, 7), dp(parent, 7), dp(parent, 7), dp(parent, 7));
        card.setLayoutParams(params);

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(parent, 10), dp(parent, 10), dp(parent, 10), dp(parent, 10));
        card.addView(title, new MaterialCardView.LayoutParams(-1, -1));

        return new Holder(card, title);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        holder.title.setText(item.title);
        holder.card.setOnClickListener(v -> listener.onOpen(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title;

        Holder(MaterialCardView card, TextView title) {
            super(card);
            this.card = card;
            this.title = title;
        }
    }
}
