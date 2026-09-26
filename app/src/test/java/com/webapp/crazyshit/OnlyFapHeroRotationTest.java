package com.webapp.crazyshit;

import android.app.Application;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class OnlyFapHeroRotationTest {
    @Test public void lateSecondHeroStartsRotationTimer() throws Exception {
        android.content.Context themedContext = new android.view.ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                R.style.Theme_CrazyShit
        );
        OnlyFapHubView hub = new OnlyFapHubView(
                themedContext,
                new OnlyFapHubView.Listener() {
                    @Override public void onOpenCreator(NativeContentItem creator) {}
                    @Override public void onSearch() {}
                    @Override public void onMore() {}
                    @Override public void onViewAllFavorites() {}
                }
        );

        Class<?> heroClass = Class.forName(
                "com.webapp.crazyshit.OnlyFapHubView$HeroCandidate"
        );
        Constructor<?> constructor = heroClass.getDeclaredConstructor(
                NativeContentItem.class,
                String.class,
                String.class
        );
        constructor.setAccessible(true);

        NativeContentItem firstCreator = creator("First Creator", "first");
        NativeContentItem secondCreator = creator("Second Creator", "second");
        Object first = constructor.newInstance(
                firstCreator,
                "https://cdn.example.com/first.jpg",
                "https://example.com/first"
        );
        Object second = constructor.newInstance(
                secondCreator,
                "https://cdn.example.com/second.jpg",
                "https://example.com/second"
        );

        Field heroItemsField = OnlyFapHubView.class.getDeclaredField("heroItems");
        heroItemsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> heroItems = (List<Object>) heroItemsField.get(hub);
        heroItems.add(first);

        Field heroItemField = OnlyFapHubView.class.getDeclaredField("heroItem");
        heroItemField.setAccessible(true);
        heroItemField.set(hub, first);

        Field heroIndexField = OnlyFapHubView.class.getDeclaredField("heroIndex");
        heroIndexField.setAccessible(true);
        heroIndexField.setInt(hub, 0);

        hub.setActive(true);

        Method addHero = OnlyFapHubView.class.getDeclaredMethod(
                "addHeroCandidate",
                heroClass
        );
        addHero.setAccessible(true);
        addHero.invoke(hub, second);

        assertEquals(2, heroItems.size());
        assertEquals(0, heroIndexField.getInt(hub));

        shadowOf(Looper.getMainLooper()).idleFor(13, TimeUnit.SECONDS);

        assertEquals(1, heroIndexField.getInt(hub));
        hub.close();
    }

    private static NativeContentItem creator(String title, String slug) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                title,
                "https://example.com/" + slug,
                "https://cdn.example.com/" + slug + ".jpg",
                "",
                "https://example.com/" + slug,
                "",
                "",
                slug
        );
    }
}
