package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class CreatorBackupTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private NativeContentItem creator(String name) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                "https://fapello.com/" + name.toLowerCase().replace(" ", "-") + "/", "", "", "", "", "", name);
    }

    @Test public void legacyFavoritesSurviveAndAreSortedOffline() {
        context.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", new HashSet<>(Arrays.asList("zoe", "anna"))).commit();
        List<NativeContentItem> items = CreatorCatalog.matching(context, "", true, 8);
        assertEquals(2, items.size()); assertEquals("anna", items.get(0).title);
        assertTrue(CreatorFavoriteStore.contains(context, items.get(1)));
    }

    @Test public void exactMatchOutranksAFavoritedPartialMatch() {
        CreatorCatalog.remember(context, Arrays.asList(creator("Anna"), creator("Annabelle")));
        CreatorFavoriteStore.toggle(context, creator("Annabelle"));
        assertEquals("Anna", CreatorCatalog.matching(context, "anna", false, 8).get(0).title);
    }

    @Test public void roundTripMergesSavedItemsAndRestoresSettings() throws Exception {
        CreatorFavoriteStore.toggle(context, creator("Anna"));
        FavoriteStore.add(context, "First", "https://crazyshit.com/video/first");
        context.getSharedPreferences("app_prefs", 0).edit().putBoolean("haptics_enabled", false)
                .putString("private_cookie", "must-not-be-exported").commit();
        JSONObject backup = AppBackupStore.export(context);
        assertFalse(backup.toString().contains("private_cookie"));
        CreatorFavoriteStore.toggle(context, creator("Zoe"));
        FavoriteStore.add(context, "Second", "https://crazyshit.com/video/second");
        context.getSharedPreferences("app_prefs", 0).edit().putBoolean("haptics_enabled", true).commit();
        AppBackupStore.restore(context, backup);
        AppBackupStore.restore(context, backup);
        assertEquals(2, CreatorFavoriteStore.names(context).size());
        assertEquals(2, FavoriteStore.load(context).size());
        assertFalse(context.getSharedPreferences("app_prefs", 0).getBoolean("haptics_enabled", true));
        assertEquals("must-not-be-exported", context.getSharedPreferences("app_prefs", 0).getString("private_cookie", ""));
    }

    @Test public void invalidImportLeavesAllExistingDataAlone() throws Exception {
        CreatorFavoriteStore.toggle(context, creator("Anna"));
        JSONObject backup = AppBackupStore.export(context);
        backup.getJSONObject("settings").put("private_cookie", "bad");
        try { AppBackupStore.restore(context, backup); fail("Import should fail"); } catch (java.io.IOException expected) { }
        assertEquals(1, CreatorFavoriteStore.names(context).size());
        assertFalse(context.getSharedPreferences("app_prefs", 0).contains("private_cookie"));
    }

    @Test public void invalidWebLinksAndFutureSchemasAreRejected() throws Exception {
        CreatorFavoriteStore.toggle(context, creator("Anna"));
        JSONObject backup = AppBackupStore.export(context);
        backup.getJSONArray("creators").getJSONObject(0).put("url", "file:///etc/passwd");
        try { BackupDocument.validate(backup); fail(); } catch (java.io.IOException expected) { }
        backup = AppBackupStore.export(context).put("version", 2);
        try { BackupDocument.validate(backup); fail(); } catch (java.io.IOException expected) { }
    }

    @Test public void oversizedFilesAreRejectedBeforeParsing() throws Exception {
        byte[] bytes = new byte[BackupDocument.MAX_BYTES + 1];
        try { BackupDocument.read(new java.io.ByteArrayInputStream(bytes)); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("large")); }
    }
}
