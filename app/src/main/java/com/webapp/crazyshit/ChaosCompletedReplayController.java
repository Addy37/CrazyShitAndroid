package com.webapp.crazyshit;

import android.view.View;
import android.view.ViewGroup;

import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makes a completed Chaos clip behave like a fresh clip when the user swipes back to it.
 *
 * Partially watched clips are untouched. If the currently selected holder still owns a player in
 * STATE_ENDED, selecting that page again seeks to 0 and starts playback. Recycled holders already
 * create a fresh player at 0, so no extra persisted state is needed.
 */
final class ChaosCompletedReplayController {
    private static final Map<ChaosFeedView, State> STATES = new WeakHashMap<>();

    private ChaosCompletedReplayController() {
    }

    static void attachSoon(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> attachFound(activity));
        decor.postDelayed(() -> attachFound(activity), 260L);
        decor.postDelayed(() -> attachFound(activity), 900L);
    }

    static void detach(NativeMainActivity activity) {
        if (activity == null) return;
        List<ChaosFeedView> remove = new ArrayList<>();
        for (Map.Entry<ChaosFeedView, State> entry : STATES.entrySet()) {
            ChaosFeedView view = entry.getKey();
            State state = entry.getValue();
            if (view == null || state == null || view.getContext() == activity) {
                if (state != null) state.detach();
                remove.add(view);
            }
        }
        for (ChaosFeedView view : remove) STATES.remove(view);
    }

    private static void attachFound(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        List<ChaosFeedView> feeds = new ArrayList<>();
        collect(activity.findViewById(android.R.id.content), ChaosFeedView.class, feeds);
        for (ChaosFeedView feed : feeds) {
            if (feed == null || STATES.containsKey(feed)) continue;
            State state = new State(feed);
            if (state.attach()) STATES.put(feed, state);
        }
    }

    private static final class State {
        final ChaosFeedView feed;
        ViewPager2 pager;
        ViewPager2.OnPageChangeCallback callback;

        State(ChaosFeedView feed) {
            this.feed = feed;
        }

        boolean attach() {
            pager = findFirst(feed, ViewPager2.class);
            if (pager == null) return false;
            callback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    pager.postDelayed(() -> restartIfCompleted(position), 90L);
                }
            };
            pager.registerOnPageChangeCallback(callback);
            return true;
        }

        void detach() {
            if (pager != null && callback != null) {
                try {
                    pager.unregisterOnPageChangeCallback(callback);
                } catch (Exception ignored) {
                }
            }
            callback = null;
            pager = null;
        }

        void restartIfCompleted(int position) {
            if (pager == null || pager.getChildCount() == 0) return;
            View child = pager.getChildAt(0);
            if (!(child instanceof RecyclerView)) return;
            RecyclerView recycler = (RecyclerView) child;
            RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
            if (holder == null) return;
            PlayerView playerView = findFirst(holder.itemView, PlayerView.class);
            if (playerView == null) return;
            Player player = playerView.getPlayer();
            if (player == null || player.getPlaybackState() != Player.STATE_ENDED) return;
            try {
                player.seekTo(0L);
                player.play();
            } catch (Exception ignored) {
            }
        }
    }

    private static <T> T findFirst(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T found = findFirst(group.getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }

    private static <T> void collect(View view, Class<T> type, List<T> out) {
        if (view == null) return;
        if (type.isInstance(view)) out.add(type.cast(view));
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collect(group.getChildAt(i), type, out);
        }
    }
}