package com.webapp.crazyshit;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class BundledCreatorIndexTest {
    private BundledCreatorIndex index(String rows) throws Exception {
        return BundledCreatorIndex.parse(new BufferedReader(new StringReader(rows)));
    }

    private NativeContentItem creator(String name, String url, String avatar) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name, url,
                avatar, "", "", "", "", name);
    }

    @Test public void aliasesRankWithExactNamesAndAccents() throws Exception {
        BundledCreatorIndex index = index("Annabelle\tanna-belle\nMía Rose\tmia_rose|rose mia\nAnna\n");
        assertEquals(3, index.size());
        assertEquals("Anna", index.matching("anna", 4).get(0).title);
        assertEquals("Mía Rose", index.matching("rose mia", 4).get(0).title);
        assertEquals("Mía Rose", index.matching("mia rose", 4).get(0).title);
    }

    @Test public void largeIndexLimitsResultsAndRepeatedQueries() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 25000; i++) {
            rows.append("Creator ").append(i);
            if (i == 17777) rows.append("\tfar_away_alias");
            rows.append('\n');
        }
        BundledCreatorIndex index = index(rows.toString());
        assertEquals(25000, index.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(12, index.matching("creator", 12).size());
            assertEquals("Creator 24999", index.matching("creator 24999", 12).get(0).title);
            assertEquals("Creator 17777", index.matching("away alias", 12).get(0).title);
            assertEquals("Creator 17777", index.matching("ator 17777", 12).get(0).title);
        }
    }

    @Test public void liveMetadataEnrichesSameIdentityWithoutRemovingLocalMatches() {
        NativeContentItem saved = creator("Mía Rose", "", "");
        NativeContentItem live = creator("Mia-Rose", "https://example.org/profile", "https://example.org/avatar");
        List<NativeContentItem> result = OnlyFapCreatorResults.merge(
                Arrays.asList(saved, creator("Zoe", "", "")), Collections.singletonList(live), 10);
        assertEquals(2, result.size());
        assertEquals("Mía Rose", result.get(0).title);
        assertEquals(live.imageUrl, result.get(0).imageUrl);
        assertEquals(OnlyFapCreatorResults.key(saved), OnlyFapCreatorResults.key(result.get(0)));
        assertEquals("Zoe", result.get(1).title);
    }
}
