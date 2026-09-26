package com.webapp.crazyshit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OnlyFapHeroPolicyTest {
    @Test public void newCreatorsLeadAndVisibleOrFavoriteCreatorsNeverEnterHero() {
        NativeContentItem newOne = creator("New One");
        NativeContentItem newTwo = creator("New Two");
        NativeContentItem visible = creator("Trending One");
        NativeContentItem saved = creator("Saved One");
        NativeContentItem backup = creator("Backup One");
        List<NativeContentItem> selected = OnlyFapHeroPolicy.select(
                Arrays.asList(newOne, newOne, visible, saved, newTwo),
                Arrays.asList(visible, creator("Hot One"), creator("Popular One"),
                        backup, backup),
                new HashSet<>(Collections.singletonList(CreatorFavoriteStore.key(saved))),
                Collections.singletonList(visible),
                Collections.singletonList(creator("Hot One")),
                Collections.singletonList(creator("Popular One")),
                5);
        assertEquals(3, selected.size());
        assertEquals("New One", selected.get(0).title);
        assertEquals("New Two", selected.get(1).title);
        assertEquals("Backup One", selected.get(2).title);
    }

    @Test public void artworkUsesFullImageThenHeaderThenPreviewThenAvatar() {
        List<OnlyFapHeroPolicy.Artwork> choices = new ArrayList<>();
        NativeContentItem image = media(NativeContentItem.KIND_IMAGE,
                "https://img.example/full.jpg", "https://img.example/thumb.jpg");
        NativeContentItem video = media(NativeContentItem.KIND_MEDIA,
                "https://img.example/video.mp4", "https://img.example/poster.webp");
        List<NativeContentItem> media = Arrays.asList(video, image);
        OnlyFapHeroPolicy.addDirectImages(choices, media, "gallery");
        choices.add(new OnlyFapHeroPolicy.Artwork(
                "https://img.example/header.webp", "profile", false));
        OnlyFapHeroPolicy.addPreviews(choices, media, "gallery");
        choices.add(new OnlyFapHeroPolicy.Artwork(
                "https://img.example/avatar.webp", "profile", true));
        List<OnlyFapHeroPolicy.Artwork> unique = OnlyFapHeroPolicy.distinctArtwork(choices);
        assertEquals("https://img.example/full.jpg", unique.get(0).url);
        assertEquals("https://img.example/header.webp", unique.get(1).url);
        assertEquals("https://img.example/poster.webp", unique.get(2).url);
        assertTrue(unique.get(unique.size() - 1).avatar);
        assertFalse(unique.get(0).avatar);
    }

    @Test public void portraitHeroRequiresClearlyVerticalDimensions() {
        assertTrue(OnlyFapHeroPolicy.isGoodPortraitDimensions(1080, 1920));
        assertTrue(OnlyFapHeroPolicy.isGoodPortraitDimensions(1200, 1500));
        assertFalse(OnlyFapHeroPolicy.isGoodPortraitDimensions(1000, 1100));
        assertFalse(OnlyFapHeroPolicy.isGoodPortraitDimensions(1200, 1200));
        assertFalse(OnlyFapHeroPolicy.isGoodPortraitDimensions(1920, 1080));
        assertFalse(OnlyFapHeroPolicy.isGoodPortraitDimensions(0, 1920));
    }

    @Test public void missingHeaderStillPicksPortraitMediaBeforeAvatar() {
        List<OnlyFapHeroPolicy.Artwork> choices = new ArrayList<>();
        choices.add(new OnlyFapHeroPolicy.Artwork("", "profile", false));
        OnlyFapHeroPolicy.addMedia(choices, Collections.singletonList(media(
                NativeContentItem.KIND_IMAGE, "https://img.example/portrait.jpg",
                "https://img.example/portrait.jpg")), "gallery");
        choices.add(new OnlyFapHeroPolicy.Artwork("https://img.example/avatar.webp", "profile", true));
        List<OnlyFapHeroPolicy.Artwork> unique = OnlyFapHeroPolicy.distinctArtwork(choices);
        assertEquals(2, unique.size());
        assertEquals("https://img.example/portrait.jpg", unique.get(0).url);
        assertTrue(unique.get(1).avatar);
    }

    private static NativeContentItem creator(String title) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, title,
                "https://example.com/" + title.replace(' ', '-'), "", "", "", "", "", title);
    }

    private static NativeContentItem media(String kind, String url, String preview) {
        return new NativeContentItem(kind, "Media", url, preview, "", "", "", "");
    }
}
