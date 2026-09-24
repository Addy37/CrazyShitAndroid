package com.webapp.crazyshit;

import java.util.Arrays;
import java.util.List;

/** GitHub release endpoints kept compatible across the ZEROCHILL repository rename. */
final class ZeroChillReleaseEndpoints {
    static final String PRIMARY_REPOSITORY = "Addy37/ZEROCHILL";
    static final String LEGACY_REPOSITORY = "Addy37/CrazyShitAndroid";

    private ZeroChillReleaseEndpoints() {
    }

    static List<String> stableApis() {
        return Arrays.asList(
                api(PRIMARY_REPOSITORY, "releases/latest"),
                api(LEGACY_REPOSITORY, "releases/latest")
        );
    }

    static List<String> releasesApis() {
        return Arrays.asList(
                api(PRIMARY_REPOSITORY, "releases?per_page=100"),
                api(LEGACY_REPOSITORY, "releases?per_page=100")
        );
    }

    private static String api(String repository, String path) {
        return "https://api.github.com/repos/" + repository + "/" + path;
    }
}
