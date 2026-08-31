package com.webapp.crazyshit;

public final class NativeContentItem {
    public static final String KIND_MEDIA = "media";
    public static final String KIND_IMAGE = "image";
    public static final String KIND_MEME = "meme";
    public static final String KIND_CATEGORY = "category";
    public static final String KIND_SERIES = "series";
    public static final String KIND_SECTION = "section";

    public final String kind;
    public final String title;
    public final String url;
    public final String imageUrl;
    public final String views;
    public final String uploader;
    public final String comments;
    public final String description;

    public NativeContentItem(
            String kind,
            String title,
            String url,
            String imageUrl,
            String views,
            String uploader,
            String comments
    ) {
        this(kind, title, url, imageUrl, views, uploader, comments, "");
    }

    public NativeContentItem(
            String kind,
            String title,
            String url,
            String imageUrl,
            String views,
            String uploader,
            String comments,
            String description
    ) {
        this.kind = kind == null ? KIND_MEDIA : kind;
        this.title = title == null ? "" : title;
        this.url = url == null ? "" : url;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.views = views == null ? "" : views;
        this.uploader = uploader == null ? "" : uploader;
        this.comments = comments == null ? "" : comments;
        this.description = description == null ? "" : description;
    }

    public boolean isCategory() {
        return KIND_CATEGORY.equals(kind);
    }

    public boolean isSeries() {
        return KIND_SERIES.equals(kind);
    }

    public boolean isMeme() {
        return KIND_MEME.equals(kind);
    }

    public boolean isImage() {
        return KIND_IMAGE.equals(kind) || KIND_MEME.equals(kind);
    }

    public boolean isVideo() {
        return KIND_MEDIA.equals(kind);
    }

    public boolean isSection() {
        return KIND_SECTION.equals(kind);
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
                choose(comments, other.comments),
                choose(description, other.description)
        );
    }

    private static String choose(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }
}
