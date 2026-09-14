package com.webapp.crazyshit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.select.Selector;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Immutable, validated data-only configuration used by source repositories. */
final class SourceConfig {
    static final int SUPPORTED_SCHEMA_VERSION = 1;
    static final int MAX_JSON_BYTES = 256 * 1024;
    private static final int MIN_TIMEOUT_MS = 1_000;
    private static final int MAX_TIMEOUT_MS = 30_000;
    private static final int MAX_ROUTE_LENGTH = 300;
    private static final int MAX_SELECTOR_LENGTH = 1_000;
    private static final int MAX_REGEX_LENGTH = 1_500;
    private static final int MAX_LIST_SIZE = 16;
    private static final int MAX_HEADERS = 16;
    private static final Set<String> ALLOWED_HEADERS = set(
            "Accept", "Accept-Encoding", "Accept-Language", "Cache-Control", "Pragma", "DNT",
            "Origin", "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site",
            "Upgrade-Insecure-Requests", "X-Requested-With"
    );

    final int schemaVersion;
    final long configVersion;
    final String updatedAt;
    final boolean sourceKillSwitchesEnabled;
    final boolean fallbacksEnabled;
    final Fapello fapello;
    final Bunkr bunkr;
    final WikiFeet wikiFeet;
    final WikiFeet wikiFeetX;
    private final String serialized;

    private SourceConfig(
            int schemaVersion,
            long configVersion,
            String updatedAt,
            boolean sourceKillSwitchesEnabled,
            boolean fallbacksEnabled,
            Fapello fapello,
            Bunkr bunkr,
            WikiFeet wikiFeet,
            WikiFeet wikiFeetX,
            String serialized
    ) {
        this.schemaVersion = schemaVersion;
        this.configVersion = configVersion;
        this.updatedAt = updatedAt;
        this.sourceKillSwitchesEnabled = sourceKillSwitchesEnabled;
        this.fallbacksEnabled = fallbacksEnabled;
        this.fapello = fapello;
        this.bunkr = bunkr;
        this.wikiFeet = wikiFeet;
        this.wikiFeetX = wikiFeetX;
        this.serialized = serialized;
    }

    String serialized() { return serialized; }

    static SourceConfig parseAndValidate(String json) throws ValidationException {
        if (json == null || json.trim().isEmpty()) throw invalid("Configuration was empty");
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw invalid("Configuration exceeded 256 KB");
        }
        try {
            JSONObject root = new JSONObject(json);
            rejectUnknown(root, set("schemaVersion", "configVersion", "updatedAt", "global", "sources"), "root");
            int schemaVersion = requiredInt(root, "schemaVersion");
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                throw invalid("Unsupported schemaVersion " + schemaVersion);
            }
            long configVersion = requiredLong(root, "configVersion");
            if (configVersion < 1L) throw invalid("configVersion must be positive");
            String updatedAt = requiredString(root, "updatedAt", 80);
            try { Instant.parse(updatedAt); } catch (Exception error) {
                throw invalid("updatedAt must be an ISO-8601 instant");
            }

            JSONObject global = requiredObject(root, "global");
            rejectUnknown(global, set("sourceKillSwitchesEnabled", "fallbacksEnabled"), "global");
            boolean sourceKillSwitchesEnabled = requiredBoolean(global, "sourceKillSwitchesEnabled");
            boolean fallbacksEnabled = requiredBoolean(global, "fallbacksEnabled");

            JSONObject sources = requiredObject(root, "sources");
            rejectUnknown(sources, set("fapello", "bunkr", "wikifeet", "wikifeetx"), "sources");
            Fapello fapello = parseFapello(requiredObject(sources, "fapello"));
            Bunkr bunkr = parseBunkr(requiredObject(sources, "bunkr"));
            WikiFeet wikiFeet = parseWikiFeet(requiredObject(sources, "wikifeet"), "wikifeet");
            WikiFeet wikiFeetX = parseWikiFeet(requiredObject(sources, "wikifeetx"), "wikifeetx");
            String canonical = root.toString();
            return new SourceConfig(schemaVersion, configVersion, updatedAt,
                    sourceKillSwitchesEnabled, fallbacksEnabled,
                    fapello, bunkr, wikiFeet, wikiFeetX, canonical);
        } catch (ValidationException error) {
            throw error;
        } catch (JSONException error) {
            throw new ValidationException("Malformed JSON", error);
        }
    }

    private static Fapello parseFapello(JSONObject value) throws ValidationException, JSONException {
        rejectUnknown(value, set(
                "enabled", "baseUrl", "fallbackDomains", "userAgent", "requestHeaders",
                "ajaxHeaders", "refererOverride", "requestTimeoutMs", "ajaxTimeoutMs", "retryCount", "routes",
                "selectors", "patterns", "cdnHosts"
        ), "sources.fapello");
        JSONObject routes = requiredObject(value, "routes");
        rejectUnknown(routes, set(
                "search", "creatorMedia", "creatorProfileFirst", "creatorProfilePage",
                "listingNewFirst", "listingNewPage", "listingHotFirst", "listingHotPage",
                "listingPopularFirst", "listingPopularPage", "popularVideosFirst", "popularVideosPage"
        ), "sources.fapello.routes");
        JSONObject selectors = requiredObject(value, "selectors");
        rejectUnknown(selectors, set(
                "creatorLinks", "mediaLinks", "videoSources", "images", "nextPageLinks",
                "playableVideo", "playableImage"
        ), "sources.fapello.selectors");
        JSONObject patterns = requiredObject(value, "patterns");
        rejectUnknown(patterns, set("postPath", "contentUrl", "scriptMediaUrl"),
                "sources.fapello.patterns");
        return new Fapello(
                requiredBoolean(value, "enabled"),
                httpsBase(value, "baseUrl"),
                httpsList(value, "fallbackDomains"),
                userAgent(value),
                headers(value, "requestHeaders"),
                headers(value, "ajaxHeaders"),
                optionalHttpsUrl(value, "refererOverride"),
                timeout(value, "requestTimeoutMs"),
                timeout(value, "ajaxTimeoutMs"),
                retryCount(value),
                route(routes, "search", set("query", "limit", "offset")),
                route(routes, "creatorMedia", set("slug", "page")),
                route(routes, "creatorProfileFirst", set("slug", "page")),
                route(routes, "creatorProfilePage", set("slug", "page")),
                route(routes, "listingNewFirst", set("page")),
                route(routes, "listingNewPage", set("page")),
                route(routes, "listingHotFirst", set("page")),
                route(routes, "listingHotPage", set("page")),
                route(routes, "listingPopularFirst", set("page")),
                route(routes, "listingPopularPage", set("page")),
                route(routes, "popularVideosFirst", set("page")),
                route(routes, "popularVideosPage", set("page")),
                selector(selectors, "creatorLinks"),
                selector(selectors, "mediaLinks"),
                selector(selectors, "videoSources"),
                selector(selectors, "images"),
                selector(selectors, "nextPageLinks"),
                selector(selectors, "playableVideo"),
                selector(selectors, "playableImage"),
                regex(patterns, "postPath"),
                regex(patterns, "contentUrl"),
                regex(patterns, "scriptMediaUrl"),
                hosts(value, "cdnHosts")
        );
    }

    private static Bunkr parseBunkr(JSONObject value) throws ValidationException, JSONException {
        rejectUnknown(value, set(
                "enabled", "indexUrl", "pageOrigins", "fallbackOrigins", "apiEndpoints",
                "signUrl", "downloadRoot", "userAgent", "requestHeaders", "refererOverride", "requestTimeoutMs",
                "signTimeoutMs", "retryCount", "selectors", "cdnHosts"
        ), "sources.bunkr");
        JSONObject selectors = requiredObject(value, "selectors");
        rejectUnknown(selectors, set("albumLinks", "directVideo", "directImage"),
                "sources.bunkr.selectors");
        List<String> origins = httpsList(value, "pageOrigins");
        List<String> fallbacks = httpsList(value, "fallbackOrigins");
        if (origins.isEmpty()) throw invalid("sources.bunkr.pageOrigins must not be empty");
        if (origins.size() + fallbacks.size() > MAX_LIST_SIZE) {
            throw invalid("sources.bunkr origins list was too large");
        }
        List<String> apiEndpoints = httpsList(value, "apiEndpoints");
        if (apiEndpoints.isEmpty()) throw invalid("sources.bunkr.apiEndpoints must not be empty");
        return new Bunkr(
                requiredBoolean(value, "enabled"), httpsBase(value, "indexUrl"), origins, fallbacks,
                apiEndpoints, httpsUrl(value, "signUrl"),
                httpsBase(value, "downloadRoot"), userAgent(value), headers(value, "requestHeaders"),
                optionalHttpsUrl(value, "refererOverride"),
                timeout(value, "requestTimeoutMs"), timeout(value, "signTimeoutMs"), retryCount(value),
                selector(selectors, "albumLinks"), selector(selectors, "directVideo"),
                selector(selectors, "directImage"), hosts(value, "cdnHosts")
        );
    }

    private static WikiFeet parseWikiFeet(JSONObject value, String id)
            throws ValidationException, JSONException {
        rejectUnknown(value, set(
                "enabled", "baseUrl", "fallbackDomains", "pictureHost", "thumbnailHost",
                "userAgent", "requestHeaders", "ajaxHeaders", "refererOverride", "requestTimeoutMs", "ajaxTimeoutMs",
                "retryCount", "searchRoute", "searchSelector"
        ), "sources." + id);
        return new WikiFeet(
                requiredBoolean(value, "enabled"), httpsBase(value, "baseUrl"),
                httpsList(value, "fallbackDomains"), host(value, "pictureHost"),
                host(value, "thumbnailHost"), userAgent(value), headers(value, "requestHeaders"),
                headers(value, "ajaxHeaders"), optionalHttpsUrl(value, "refererOverride"),
                timeout(value, "requestTimeoutMs"),
                timeout(value, "ajaxTimeoutMs"), retryCount(value),
                route(value, "searchRoute", set("query")), selector(value, "searchSelector")
        );
    }

    private static String requiredString(JSONObject object, String key, int max)
            throws ValidationException {
        Object raw = object.opt(key);
        if (!(raw instanceof String)) throw invalid(key + " must be a string");
        String value = ((String) raw).trim();
        if (value.isEmpty() || value.length() > max) throw invalid(key + " had an invalid length");
        return value;
    }

    private static JSONObject requiredObject(JSONObject object, String key) throws ValidationException {
        JSONObject value = object.optJSONObject(key);
        if (value == null) throw invalid(key + " must be an object");
        return value;
    }

    private static boolean requiredBoolean(JSONObject object, String key) throws ValidationException {
        Object value = object.opt(key);
        if (!(value instanceof Boolean)) throw invalid(key + " must be a boolean");
        return (Boolean) value;
    }

    private static int requiredInt(JSONObject object, String key) throws ValidationException {
        Object value = object.opt(key);
        if (!(value instanceof Number)) throw invalid(key + " must be an integer");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) ||
                number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalid(key + " must be an integer");
        }
        return (int) number;
    }

    private static long requiredLong(JSONObject object, String key) throws ValidationException {
        Object value = object.opt(key);
        if (!(value instanceof Number)) throw invalid(key + " must be an integer");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < 1d ||
                number > 9_007_199_254_740_991d) throw invalid(key + " must be a safe integer");
        return ((Number) value).longValue();
    }

    private static int timeout(JSONObject object, String key) throws ValidationException {
        int value = requiredInt(object, key);
        if (value < MIN_TIMEOUT_MS || value > MAX_TIMEOUT_MS) {
            throw invalid(key + " must be between 1000 and 30000 ms");
        }
        return value;
    }

    private static int retryCount(JSONObject object) throws ValidationException {
        int value = requiredInt(object, "retryCount");
        if (value < 0 || value > 3) throw invalid("retryCount must be between 0 and 3");
        return value;
    }

    private static String userAgent(JSONObject object) throws ValidationException {
        return requiredString(object, "userAgent", 512);
    }

    private static String httpsBase(JSONObject object, String key) throws ValidationException {
        String value = httpsUrl(object, key);
        return value.endsWith("/") ? value : value + "/";
    }

    private static String httpsUrl(JSONObject object, String key) throws ValidationException {
        String value = requiredString(object, key, 2_048);
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                    uri.getUserInfo() != null || uri.getFragment() != null) {
                throw invalid(key + " must be a safe HTTPS URL");
            }
            return uri.toASCIIString();
        } catch (ValidationException error) {
            throw error;
        } catch (Exception error) {
            throw invalid(key + " must be a valid HTTPS URL");
        }
    }

    private static String optionalHttpsUrl(JSONObject object, String key) throws ValidationException {
        Object raw = object.opt(key);
        if (!(raw instanceof String)) throw invalid(key + " must be a string");
        if (((String) raw).trim().isEmpty()) return "";
        return httpsUrl(object, key);
    }

    private static String host(JSONObject object, String key) throws ValidationException {
        String value = requiredString(object, key, 253).toLowerCase(java.util.Locale.US);
        if (!value.matches("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) {
            throw invalid(key + " must be a hostname");
        }
        return value;
    }

    private static List<String> httpsList(JSONObject object, String key)
            throws ValidationException, JSONException {
        JSONArray values = object.optJSONArray(key);
        if (values == null) throw invalid(key + " must be a list");
        if (values.length() > MAX_LIST_SIZE) throw invalid(key + " list was too large");
        ArrayList<String> result = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject wrapper = new JSONObject().put(key, values.opt(index));
            result.add(httpsUrl(wrapper, key));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> hosts(JSONObject object, String key)
            throws ValidationException, JSONException {
        JSONArray values = object.optJSONArray(key);
        if (values == null) throw invalid(key + " must be a list");
        if (values.length() > MAX_LIST_SIZE) throw invalid(key + " list was too large");
        ArrayList<String> result = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject wrapper = new JSONObject().put(key, values.opt(index));
            result.add(host(wrapper, key));
        }
        return Collections.unmodifiableList(result);
    }

    private static Map<String, String> headers(JSONObject object, String key)
            throws ValidationException {
        JSONObject headers = object.optJSONObject(key);
        if (headers == null) throw invalid(key + " must be an object");
        if (headers.length() > MAX_HEADERS) throw invalid(key + " contained too many headers");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Iterator<String> keys = headers.keys();
        while (keys.hasNext()) {
            String name = keys.next();
            if (!ALLOWED_HEADERS.contains(name)) throw invalid("Unsupported header " + name);
            Object raw = headers.opt(name);
            if (!(raw instanceof String)) throw invalid("Invalid header value for " + name);
            String value = (String) raw;
            if (value.isEmpty() || value.length() > 1_024 || value.contains("\r") || value.contains("\n")) {
                throw invalid("Invalid header value for " + name);
            }
            if ("Origin".equals(name)) {
                JSONObject wrapper = new JSONObject();
                try { wrapper.put("origin", value); }
                catch (JSONException error) { throw invalid("Invalid Origin header"); }
                httpsUrl(wrapper, "origin");
            }
            result.put(name, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static String route(JSONObject object, String key, Set<String> supportedTokens)
            throws ValidationException {
        Object raw = object.opt(key);
        if (!(raw instanceof String)) throw invalid(key + " must be a string");
        String value = ((String) raw).trim();
        if (value.length() > MAX_ROUTE_LENGTH) throw invalid(key + " had an invalid length");
        if (value.contains("://") || value.contains("\\") || value.contains("..")) {
            throw invalid(key + " must be a relative route template");
        }
        java.util.regex.Matcher tokens = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)\\}").matcher(value);
        while (tokens.find()) if (!supportedTokens.contains(tokens.group(1))) {
            throw invalid(key + " contained unsupported token " + tokens.group(1));
        }
        if (value.matches(".*[{}].*") && !value.replaceAll("\\{[a-zA-Z][a-zA-Z0-9]*\\}", "").matches("[^{}]*")) {
            throw invalid(key + " contained a malformed token");
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static String selector(JSONObject object, String key) throws ValidationException {
        String value = requiredString(object, key, MAX_SELECTOR_LENGTH);
        try {
            org.jsoup.nodes.Document.createShell("https://example.invalid").select(value);
        } catch (Selector.SelectorParseException error) {
            throw invalid(key + " contained invalid CSS");
        }
        return value;
    }

    private static Pattern regex(JSONObject object, String key) throws ValidationException {
        String value = requiredString(object, key, MAX_REGEX_LENGTH);
        if (Pattern.compile("\\\\[1-9]").matcher(value).find() ||
                Pattern.compile("\\([^)]*[+*][^)]*\\)\\s*(?:[+*]|\\{)").matcher(value).find()) {
            throw invalid(key + " contained an unsafe regex construct");
        }
        try { return Pattern.compile(value); }
        catch (PatternSyntaxException error) { throw invalid(key + " contained invalid regex"); }
    }

    private static void rejectUnknown(JSONObject object, Set<String> allowed, String path)
            throws ValidationException {
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!allowed.contains(key)) throw invalid("Unknown field " + path + "." + key);
        }
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(message);
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    static final class Fapello {
        final boolean enabled;
        final String baseUrl, userAgent, refererOverride;
        final List<String> fallbackDomains, cdnHosts;
        final Map<String, String> requestHeaders, ajaxHeaders;
        final int requestTimeoutMs, ajaxTimeoutMs, retryCount;
        final String searchRoute, creatorMediaRoute, creatorProfileFirstRoute, creatorProfilePageRoute;
        final String listingNewFirstRoute, listingNewPageRoute, listingHotFirstRoute, listingHotPageRoute;
        final String listingPopularFirstRoute, listingPopularPageRoute;
        final String popularVideosFirstRoute, popularVideosPageRoute;
        final String creatorLinksSelector, mediaLinksSelector, videoSourcesSelector, imagesSelector;
        final String nextPageLinksSelector, playableVideoSelector, playableImageSelector;
        final Pattern postPathPattern, contentUrlPattern, scriptMediaUrlPattern;

        Fapello(boolean enabled, String baseUrl, List<String> fallbackDomains, String userAgent,
                 Map<String, String> requestHeaders, Map<String, String> ajaxHeaders,
                 String refererOverride,
                 int requestTimeoutMs, int ajaxTimeoutMs, int retryCount, String searchRoute,
                 String creatorMediaRoute, String creatorProfileFirstRoute, String creatorProfilePageRoute,
                 String listingNewFirstRoute, String listingNewPageRoute, String listingHotFirstRoute,
                 String listingHotPageRoute, String listingPopularFirstRoute, String listingPopularPageRoute,
                 String popularVideosFirstRoute, String popularVideosPageRoute, String creatorLinksSelector,
                 String mediaLinksSelector, String videoSourcesSelector, String imagesSelector,
                 String nextPageLinksSelector, String playableVideoSelector, String playableImageSelector,
                 Pattern postPathPattern, Pattern contentUrlPattern, Pattern scriptMediaUrlPattern,
                 List<String> cdnHosts) {
            this.enabled = enabled; this.baseUrl = baseUrl; this.fallbackDomains = fallbackDomains;
            this.userAgent = userAgent; this.requestHeaders = requestHeaders; this.ajaxHeaders = ajaxHeaders;
            this.refererOverride = refererOverride;
            this.requestTimeoutMs = requestTimeoutMs; this.ajaxTimeoutMs = ajaxTimeoutMs; this.retryCount = retryCount;
            this.searchRoute = searchRoute; this.creatorMediaRoute = creatorMediaRoute;
            this.creatorProfileFirstRoute = creatorProfileFirstRoute; this.creatorProfilePageRoute = creatorProfilePageRoute;
            this.listingNewFirstRoute = listingNewFirstRoute; this.listingNewPageRoute = listingNewPageRoute;
            this.listingHotFirstRoute = listingHotFirstRoute; this.listingHotPageRoute = listingHotPageRoute;
            this.listingPopularFirstRoute = listingPopularFirstRoute; this.listingPopularPageRoute = listingPopularPageRoute;
            this.popularVideosFirstRoute = popularVideosFirstRoute; this.popularVideosPageRoute = popularVideosPageRoute;
            this.creatorLinksSelector = creatorLinksSelector; this.mediaLinksSelector = mediaLinksSelector;
            this.videoSourcesSelector = videoSourcesSelector; this.imagesSelector = imagesSelector;
            this.nextPageLinksSelector = nextPageLinksSelector; this.playableVideoSelector = playableVideoSelector;
            this.playableImageSelector = playableImageSelector; this.postPathPattern = postPathPattern;
            this.contentUrlPattern = contentUrlPattern; this.scriptMediaUrlPattern = scriptMediaUrlPattern;
            this.cdnHosts = cdnHosts;
        }
    }

    static final class Bunkr {
        final boolean enabled;
        final String indexUrl, signUrl, downloadRoot, userAgent, refererOverride;
        final List<String> pageOrigins, fallbackOrigins, apiEndpoints, cdnHosts;
        final Map<String, String> requestHeaders;
        final int requestTimeoutMs, signTimeoutMs, retryCount;
        final String albumLinksSelector, directVideoSelector, directImageSelector;

        Bunkr(boolean enabled, String indexUrl, List<String> pageOrigins, List<String> fallbackOrigins,
              List<String> apiEndpoints, String signUrl, String downloadRoot, String userAgent,
              Map<String, String> requestHeaders, String refererOverride,
              int requestTimeoutMs, int signTimeoutMs,
              int retryCount, String albumLinksSelector, String directVideoSelector,
              String directImageSelector, List<String> cdnHosts) {
            this.enabled = enabled; this.indexUrl = indexUrl; this.pageOrigins = pageOrigins;
            this.fallbackOrigins = fallbackOrigins; this.apiEndpoints = apiEndpoints; this.signUrl = signUrl;
            this.downloadRoot = downloadRoot; this.userAgent = userAgent; this.requestHeaders = requestHeaders;
            this.refererOverride = refererOverride;
            this.requestTimeoutMs = requestTimeoutMs; this.signTimeoutMs = signTimeoutMs;
            this.retryCount = retryCount; this.albumLinksSelector = albumLinksSelector;
            this.directVideoSelector = directVideoSelector; this.directImageSelector = directImageSelector;
            this.cdnHosts = cdnHosts;
        }
    }

    static final class WikiFeet {
        final boolean enabled;
        final String baseUrl, pictureHost, thumbnailHost, userAgent, refererOverride, searchRoute, searchSelector;
        final List<String> fallbackDomains;
        final Map<String, String> requestHeaders, ajaxHeaders;
        final int requestTimeoutMs, ajaxTimeoutMs, retryCount;

        WikiFeet(boolean enabled, String baseUrl, List<String> fallbackDomains, String pictureHost,
                 String thumbnailHost, String userAgent, Map<String, String> requestHeaders,
                 Map<String, String> ajaxHeaders, String refererOverride,
                 int requestTimeoutMs, int ajaxTimeoutMs,
                 int retryCount, String searchRoute, String searchSelector) {
            this.enabled = enabled; this.baseUrl = baseUrl; this.fallbackDomains = fallbackDomains;
            this.pictureHost = pictureHost; this.thumbnailHost = thumbnailHost; this.userAgent = userAgent;
            this.requestHeaders = requestHeaders; this.ajaxHeaders = ajaxHeaders;
            this.refererOverride = refererOverride;
            this.requestTimeoutMs = requestTimeoutMs; this.ajaxTimeoutMs = ajaxTimeoutMs;
            this.retryCount = retryCount; this.searchRoute = searchRoute; this.searchSelector = searchSelector;
        }
    }

    static final class ValidationException extends Exception {
        ValidationException(String message) { super(message); }
        ValidationException(String message, Throwable cause) { super(message, cause); }
    }
}
