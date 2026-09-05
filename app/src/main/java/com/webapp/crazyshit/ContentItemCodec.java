package com.webapp.crazyshit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Compact local snapshots. Large lists stay on disk, outside saved-instance Bundles. */
final class ContentItemCodec {
    private ContentItemCodec() { }

    static JSONObject encode(NativeContentItem item) throws JSONException {
        return new JSONObject().put("kind", item.kind).put("name", item.title)
                .put("url", item.url).put("image", item.imageUrl).put("views", item.views)
                .put("referer", item.uploader).put("comments", item.comments)
                .put("description", item.description).put("query", item.searchQuery);
    }

    static NativeContentItem decode(JSONObject item) {
        return new NativeContentItem(item.optString("kind", NativeContentItem.KIND_CREATOR),
                item.optString("name"), item.optString("url"), item.optString("image"),
                item.optString("views", item.optString("rank")), item.optString("referer"),
                item.optString("comments"), item.optString("description"),
                item.optString("query", item.optString("name")));
    }

    static JSONArray encodeList(List<NativeContentItem> items, int limit) throws JSONException {
        JSONArray out = new JSONArray();
        for (NativeContentItem item : items) {
            if (item != null) out.put(encode(item));
            if (out.length() >= limit) break;
        }
        return out;
    }

    static List<NativeContentItem> decodeList(JSONArray values, int limit) {
        List<NativeContentItem> out = new ArrayList<>();
        if (values == null) return out;
        for (int i = 0; i < Math.min(limit, values.length()); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null) out.add(decode(value));
        }
        return out;
    }
}
