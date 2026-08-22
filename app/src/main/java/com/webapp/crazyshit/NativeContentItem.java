package com.webapp.crazyshit;

public final class NativeContentItem {
    public static final String KIND_MEDIA = "media";
    public static final String KIND_CATEGORY = "category";

    public final String kind;
    public final String title;
    public final String url;
    public final String imageUrl;
    public final String views;
    public final String uploader;
    public final String comments;

    public NativeContentItem(
            String kind,
            String title,
            String url,
            String imageUrl,
            String views,
            String uploader,
            String comments
    ) {
        this.kind = kind == null ? KIND_MEDIA : kind;
        this.title = title == null ? "" : title;
        this.url = url == null ? "" : url;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.views = views == null ? "" : views;
        this.uploader = uploader == null ? "" : uploader;
        this.comments = comments == null ? "" : comments;
    }

    public boolean isCategory() {
        return KIND_CATEGORY.equals(kind);
    }

    public NativeContentItem merge(NativeContentItem other) {
        if (other == null) return this;
        return new NativeContentItem(
                kind,
                choose(title, other.title),
                choose(url, other.url),
                choose(imageUrl, other.imageUrl),
                choose(views, other.views),
                choose(uploader, other.uploader),
                choose(comments, other.comments)
        );
    }

    private static String choose(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }
}
