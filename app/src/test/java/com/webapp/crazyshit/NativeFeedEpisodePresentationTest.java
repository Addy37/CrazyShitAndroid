package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NativeFeedEpisodePresentationTest {
    private Context context;

    @Before
    public void setUp() {
        context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                R.style.Theme_CrazyShit
        );
        context.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void episodeModeCreatesWideEpisodeRowAndPlayAction() {
        NativeFeedAdapter adapter = adapter();
        adapter.setViewMode(NativeFeedAdapter.VIEW_EPISODES);

        RecyclerView parent = new RecyclerView(context);
        NativeFeedAdapter.Holder holder =
                adapter.onCreateViewHolder(parent, NativeFeedAdapter.VIEW_EPISODES);

        assertEquals(NativeFeedAdapter.VIEW_EPISODES, adapter.getViewMode());
        assertNotNull(holder.episodeAction);
        assertEquals("Play", holder.episodeAction.getText().toString());
        assertTrue(holder.image.getParent() instanceof FrameLayout);
        FrameLayout media = (FrameLayout) holder.image.getParent();
        assertTrue(media.getLayoutParams() instanceof LinearLayout.LayoutParams);
        LinearLayout.LayoutParams mediaParams =
                (LinearLayout.LayoutParams) media.getLayoutParams();
        assertTrue(mediaParams.width > mediaParams.height);
        assertTrue(mediaParams.width / (float) mediaParams.height > 1.6f);

        adapter.close();
    }

    @Test
    public void episodeActionTracksContinueAndWatchedState() {
        String pageUrl = "https://crazyshit.com/video/episode-test/";
        PlaybackHistoryStore.record(
                context,
                "Episode Test",
                pageUrl,
                "https://cdn.example.com/episode.jpg",
                45_000L,
                120_000L,
                false,
                true
        );

        NativeFeedAdapter adapter = adapter();
        adapter.setViewMode(NativeFeedAdapter.VIEW_EPISODES);
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Episode Test",
                pageUrl,
                "file:///does-not-exist.jpg",
                "",
                "",
                "",
                "Episode description"
        );
        adapter.replace(Collections.singletonList(item));

        RecyclerView parent = new RecyclerView(context);
        NativeFeedAdapter.Holder holder =
                adapter.onCreateViewHolder(parent, NativeFeedAdapter.VIEW_EPISODES);
        adapter.onBindViewHolder(holder, 0);

        assertEquals("Continue · 0:45", holder.episodeAction.getText().toString());

        PlaybackHistoryStore.record(
                context,
                "Episode Test",
                pageUrl,
                "",
                120_000L,
                120_000L,
                true,
                true
        );
        adapter.refreshPlaybackState();
        adapter.onBindViewHolder(holder, 0);

        assertEquals("Replay", holder.episodeAction.getText().toString());

        adapter.close();
    }

    private NativeFeedAdapter adapter() {
        return new NativeFeedAdapter(context, new NativeFeedAdapter.Listener() {
            @Override
            public void onOpen(NativeContentItem item) {
            }

            @Override
            public void onLongPress(NativeContentItem item, android.view.View anchor) {
            }

            @Override
            public void onComments(NativeContentItem item) {
            }
        });
    }
}
