package com.webapp.crazyshit;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZeroChillReleaseEndpointsTest {
    @Test
    public void stableUpdateChecksPreferRenamedRepoThenLegacyFallback() {
        List<String> endpoints = ZeroChillReleaseEndpoints.stableApis();

        assertEquals(2, endpoints.size());
        assertEquals(
                "https://api.github.com/repos/Addy37/ZEROCHILL/releases/latest",
                endpoints.get(0)
        );
        assertEquals(
                "https://api.github.com/repos/Addy37/CrazyShitAndroid/releases/latest",
                endpoints.get(1)
        );
    }

    @Test
    public void releaseListChecksUseSameTransitionOrder() {
        List<String> endpoints = ZeroChillReleaseEndpoints.releasesApis();

        assertEquals(2, endpoints.size());
        assertTrue(endpoints.get(0).contains("/Addy37/ZEROCHILL/"));
        assertTrue(endpoints.get(1).contains("/Addy37/CrazyShitAndroid/"));
        assertTrue(endpoints.get(0).endsWith("releases?per_page=100"));
        assertTrue(endpoints.get(1).endsWith("releases?per_page=100"));
    }
}
