package com.webapp.crazyshit;

import java.util.Locale;

/** Maps creator-based OnlyFap clips to the clean creator label used by ShitTok. */
final class ShitTokCreatorMetadata {
    private ShitTokCreatorMetadata() {
    }

    static String creatorName(NativeContentItem item) {
        if (item == null) return "";

        if (FapelloRepository.isFapelloUrl(item.url)) {
            String creator = clean(item.uploader);
            return isGenericCreatorLabel(creator) ? "" : creator;
        }

        String uploader = clean(item.uploader);
        String description = clean(item.description).toLowerCase(Locale.US);
        boolean onlyHaven = "onlyhaven".equalsIgnoreCase(uploader)
                || description.contains("onlyhaven");
        if (!onlyHaven) return "";

        String creator = clean(item.title);
        return isGenericCreatorLabel(creator) ? "" : creator;
    }

    static boolean hasCreator(NativeContentItem item) {
        return !creatorName(item).isEmpty();
    }

    private static boolean isGenericCreatorLabel(String value) {
        if (value.isEmpty()) return true;
        String lower = value.toLowerCase(Locale.US);
        return "fapello".equals(lower)
                || "onlyfap".equals(lower)
                || "onlyhaven".equals(lower)
                || "onlyhaven media".equals(lower)
                || "random video".equals(lower);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
