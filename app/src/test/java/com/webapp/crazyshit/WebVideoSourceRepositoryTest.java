package com.webapp.crazyshit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class WebVideoSourceRepositoryTest {
    @Test
    public void kaoticCategoriesUseOnlySameHostCategoryHeadings() {
        Document document = Jsoup.parse(
                "<html><body>"
                        + "<nav><a href='/featured/'>Featured</a></nav>"
                        + "<h2><a href='/category/fight/'>Fight</a></h2>"
                        + "<h2><a href='/category/wtf/'>WTF</a></h2>"
                        + "<h2><a href='https://example.com/category/nope/'>External</a></h2>"
                        + "</body></html>",
                "https://kaotic.com/categories/"
        );

        List<NativeContentItem> items =
                new WebVideoSourceRepository().parseKaoticCategories(document);

        assertEquals(2, items.size());
        assertEquals(NativeContentItem.KIND_CATEGORY, items.get(0).kind);
        assertEquals("Fight", items.get(0).title);
        assertEquals("https://kaotic.com/category/fight/", items.get(0).url);
        assertEquals("WTF", items.get(1).title);
        assertEquals("Kaotic", items.get(1).uploader);
    }
}
