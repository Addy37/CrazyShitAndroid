package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Set;

public class SourceConfigRouteTokenTest {
    @Test
    public void routeTokensCompileAndValidateOnAndroid() throws Exception {
        Method route = SourceConfig.class.getDeclaredMethod(
                "route", JSONObject.class, String.class, Set.class);
        route.setAccessible(true);

        JSONObject value = new JSONObject()
                .put("route", "creator/{slug}/page-{page}/?q={query}");

        String parsed = (String) route.invoke(
                null,
                value,
                "route",
                Set.of("slug", "page", "query")
        );

        assertEquals("creator/{slug}/page-{page}/?q={query}", parsed);
    }
}
