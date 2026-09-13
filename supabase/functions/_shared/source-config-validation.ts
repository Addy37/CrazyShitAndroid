const ROOT_FIELDS = new Set(["schemaVersion", "configVersion", "updatedAt", "global", "sources"]);
const SOURCE_IDS = ["fapello", "bunkr", "wikifeet", "wikifeetx"];
const FAPELLO_FIELDS = new Set(["enabled", "baseUrl", "fallbackDomains", "userAgent",
  "requestHeaders", "ajaxHeaders", "refererOverride", "requestTimeoutMs", "ajaxTimeoutMs", "retryCount",
  "routes", "selectors", "patterns", "cdnHosts"]);
const BUNKR_FIELDS = new Set(["enabled", "indexUrl", "pageOrigins", "fallbackOrigins",
  "apiEndpoints", "signUrl", "downloadRoot", "userAgent", "requestHeaders",
  "refererOverride", "requestTimeoutMs", "signTimeoutMs", "retryCount", "selectors", "cdnHosts"]);
const WIKI_FIELDS = new Set(["enabled", "baseUrl", "fallbackDomains", "pictureHost",
  "thumbnailHost", "userAgent", "requestHeaders", "ajaxHeaders", "refererOverride", "requestTimeoutMs",
  "ajaxTimeoutMs", "retryCount", "searchRoute", "searchSelector"]);
const FAPELLO_ROUTES = new Set(["search", "creatorMedia", "creatorProfileFirst",
  "creatorProfilePage", "listingNewFirst", "listingNewPage", "listingHotFirst",
  "listingHotPage", "listingPopularFirst", "listingPopularPage", "popularVideosFirst",
  "popularVideosPage"]);
const FAPELLO_SELECTORS = new Set(["creatorLinks", "mediaLinks", "videoSources", "images",
  "nextPageLinks", "playableVideo", "playableImage"]);
const FAPELLO_PATTERNS = new Set(["postPath", "contentUrl", "scriptMediaUrl"]);
const BUNKR_SELECTORS = new Set(["albumLinks", "directVideo", "directImage"]);
const ALLOWED_HEADERS = new Set(["Accept", "Accept-Encoding", "Accept-Language", "Cache-Control",
  "Pragma", "DNT", "Origin", "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site",
  "Upgrade-Insecure-Requests", "X-Requested-With"]);
const MAX_BYTES = 256 * 1024;

export type ValidationResult = { valid: true } | { valid: false; reason: string };

function object(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function https(value: unknown) {
  if (typeof value !== "string" || value.length > 2048) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password && !url.hash;
  } catch { return false; }
}

function validateHeaders(value: unknown): string | null {
  if (!object(value) || Object.keys(value).length > 16) return "headers must be a small object";
  for (const [name, headerValue] of Object.entries(value)) {
    if (!ALLOWED_HEADERS.has(name)) return `unsupported header ${name}`;
    if (typeof headerValue !== "string" || !headerValue || headerValue.length > 1024 || /[\r\n]/.test(headerValue)) {
      return `invalid header ${name}`;
    }
    if (name === "Origin" && !https(headerValue)) return "Origin header must use HTTPS";
  }
  return null;
}

function validateHost(value: unknown) {
  return typeof value === "string" && value.length <= 253 &&
    /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i.test(value);
}

function requireFields(value: Record<string, unknown>, fields: Set<string>, path: string) {
  const missing = [...fields].find((field) => !(field in value));
  return missing ? `${path}.${missing} is required` : null;
}

function validateRoute(value: unknown, tokens: Set<string>, path: string) {
  if (typeof value !== "string" || value.length > 300 || value.includes("://") ||
    value.includes("..") || value.includes("\\")) return `${path} is invalid`;
  const withoutTokens = value.replace(/\{([a-zA-Z][a-zA-Z0-9]*)\}/g, (_match, token) => {
    if (!tokens.has(token)) throw new Error(`unsupported token ${token}`);
    return "";
  });
  if (/[{}]/.test(withoutTokens)) return `${path} contains a malformed token`;
  return null;
}

function unknown(value: unknown, allowed: Set<string>, path: string): string | null {
  if (!object(value)) return `${path} must be an object`;
  const field = Object.keys(value).find((key) => !allowed.has(key));
  return field ? `unknown field ${path}.${field}` : null;
}

function validateSource(id: string, value: unknown): string | null {
  if (!object(value) || typeof value.enabled !== "boolean") return `${id} must contain enabled`;
  const expectedFields = id === "fapello" ? FAPELLO_FIELDS : id === "bunkr" ? BUNKR_FIELDS : WIKI_FIELDS;
  const fieldError = unknown(value, expectedFields, id) ?? requireFields(value, expectedFields, id);
  if (fieldError) return fieldError;
  if (typeof value.userAgent !== "string" || !value.userAgent || value.userAgent.length > 512) {
    return `${id}.userAgent is invalid`;
  }
  if (typeof value.refererOverride !== "string" ||
    (value.refererOverride !== "" && !https(value.refererOverride))) {
    return `${id}.refererOverride must be empty or an HTTPS URL`;
  }
  const timeoutKeys = ["requestTimeoutMs", "ajaxTimeoutMs", "signTimeoutMs"];
  for (const key of timeoutKeys) if (key in value) {
    const amount = value[key];
    if (!Number.isInteger(amount) || Number(amount) < 1000 || Number(amount) > 30000) {
      return `${id}.${key} is outside 1000..30000`;
    }
  }
  if (!Number.isInteger(value.retryCount) || Number(value.retryCount) < 0 || Number(value.retryCount) > 3) {
    return `${id}.retryCount is outside 0..3`;
  }
  for (const [key, item] of Object.entries(value)) {
    if (/Url$|Domain$|Root$/.test(key) && typeof item === "string" && !https(item)) {
      return `${id}.${key} must use HTTPS`;
    }
    if (/Domains$|Origins$|Endpoints$/.test(key)) {
      if (!Array.isArray(item) || item.length > 16 || item.some((entry) => !https(entry))) {
        return `${id}.${key} must contain at most 16 HTTPS URLs`;
      }
    }
    if (/Headers$/.test(key)) {
      const headerError = validateHeaders(item);
      if (headerError) return `${id}.${headerError}`;
    }
    if (/Selector$/.test(key) && (typeof item !== "string" || !item || item.length > 1000)) {
      return `${id}.${key} is not a valid selector value`;
    }
  }
  for (const hostKey of ["pictureHost", "thumbnailHost"]) if (hostKey in value && !validateHost(value[hostKey])) {
    return `${id}.${hostKey} must be a hostname`;
  }
  if ("cdnHosts" in value && (!Array.isArray(value.cdnHosts) || value.cdnHosts.length > 16 ||
    value.cdnHosts.some((entry) => !validateHost(entry)))) return `${id}.cdnHosts must contain hostnames`;
  if (id === "fapello") {
    const routeError = unknown(value.routes, FAPELLO_ROUTES, `${id}.routes`);
    const selectorError = unknown(value.selectors, FAPELLO_SELECTORS, `${id}.selectors`);
    const patternError = unknown(value.patterns, FAPELLO_PATTERNS, `${id}.patterns`);
    if (routeError || selectorError || patternError) return routeError ?? selectorError ?? patternError;
    const missing = requireFields(value.routes as Record<string, unknown>, FAPELLO_ROUTES, `${id}.routes`) ??
      requireFields(value.selectors as Record<string, unknown>, FAPELLO_SELECTORS, `${id}.selectors`) ??
      requireFields(value.patterns as Record<string, unknown>, FAPELLO_PATTERNS, `${id}.patterns`);
    if (missing) return missing;
  }
  if (id === "bunkr") {
    const selectorError = unknown(value.selectors, BUNKR_SELECTORS, `${id}.selectors`);
    if (selectorError) return selectorError;
    const missing = requireFields(value.selectors as Record<string, unknown>, BUNKR_SELECTORS, `${id}.selectors`);
    if (missing) return missing;
    if (!Array.isArray(value.pageOrigins) || value.pageOrigins.length === 0 ||
      !Array.isArray(value.apiEndpoints) || value.apiEndpoints.length === 0) {
      return `${id}.pageOrigins and apiEndpoints must not be empty`;
    }
    if (value.pageOrigins.length + (Array.isArray(value.fallbackOrigins) ? value.fallbackOrigins.length : 0) > 16) {
      return `${id} origins list is too large`;
    }
  }
  const routeTokens: Record<string, Set<string>> = {
    search: new Set(["query", "limit", "offset"]), creatorMedia: new Set(["slug", "page"]),
    creatorProfileFirst: new Set(["slug", "page"]), creatorProfilePage: new Set(["slug", "page"]),
    listingNewFirst: new Set(["page"]), listingNewPage: new Set(["page"]),
    listingHotFirst: new Set(["page"]), listingHotPage: new Set(["page"]),
    listingPopularFirst: new Set(["page"]), listingPopularPage: new Set(["page"]),
    popularVideosFirst: new Set(["page"]), popularVideosPage: new Set(["page"]),
  };
  for (const [key, route] of Object.entries(object(value.routes) ? value.routes : {})) {
    try {
      const error = validateRoute(route, routeTokens[key] ?? new Set(), `${id}.routes.${key}`);
      if (error) return error;
    } catch (error) { return `${id}.routes.${key} ${(error as Error).message}`; }
  }
  if ("searchRoute" in value) {
    try {
      const error = validateRoute(value.searchRoute, new Set(["query"]), `${id}.searchRoute`);
      if (error) return error;
    } catch (error) { return `${id}.searchRoute ${(error as Error).message}`; }
  }
  if (object(value.selectors)) for (const [key, selector] of Object.entries(value.selectors)) {
    if (typeof selector !== "string" || !selector || selector.length > 1000 || /[{};]/.test(selector)) {
      return `${id}.selectors.${key} is invalid`;
    }
  }
  if (object(value.patterns)) for (const [key, pattern] of Object.entries(value.patterns)) {
    if (typeof pattern !== "string" || !pattern || pattern.length > 1500) return `${id}.patterns.${key} is invalid`;
    if (/\\[1-9]/.test(pattern) || /\([^)]*[+*][^)]*\)\s*(?:[+*]|\{)/.test(pattern)) {
      return `${id}.patterns.${key} contains an unsafe regex construct`;
    }
    try {
      const flags = pattern.startsWith("(?is)") ? "is" : pattern.startsWith("(?i)") ? "i" : "";
      const body = flags ? pattern.replace(/^\(\?[ism]+\)/, "") : pattern;
      new RegExp(body, flags);
    } catch { return `${id}.patterns.${key} does not compile`; }
  }
  return null;
}

export function validateSourceConfig(value: unknown): ValidationResult {
  let encoded = "";
  try { encoded = JSON.stringify(value); } catch { return { valid: false, reason: "configuration is not JSON" }; }
  if (new TextEncoder().encode(encoded).length > MAX_BYTES) return { valid: false, reason: "configuration exceeds 256 KB" };
  if (!object(value)) return { valid: false, reason: "configuration must be an object" };
  for (const field of Object.keys(value)) if (!ROOT_FIELDS.has(field)) {
    return { valid: false, reason: `unknown root field ${field}` };
  }
  if (value.schemaVersion !== 1) return { valid: false, reason: "unsupported schemaVersion" };
  if (!Number.isSafeInteger(value.configVersion) || Number(value.configVersion) < 1) {
    return { valid: false, reason: "configVersion must be a positive integer" };
  }
  if (typeof value.updatedAt !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value.updatedAt) ||
    Number.isNaN(Date.parse(value.updatedAt))) {
    return { valid: false, reason: "updatedAt must be ISO-8601" };
  }
  if (!object(value.global) || Object.keys(value.global).some((key) =>
    key !== "sourceKillSwitchesEnabled" && key !== "fallbacksEnabled") ||
    typeof value.global.sourceKillSwitchesEnabled !== "boolean" ||
    typeof value.global.fallbacksEnabled !== "boolean") {
    return { valid: false, reason: "global feature flags must be boolean" };
  }
  if (!object(value.sources)) return { valid: false, reason: "sources must be an object" };
  for (const id of Object.keys(value.sources)) if (!SOURCE_IDS.includes(id)) {
    return { valid: false, reason: `unsupported source ${id}` };
  }
  for (const id of SOURCE_IDS) {
    const error = validateSource(id, value.sources[id]);
    if (error) return { valid: false, reason: error };
  }
  return { valid: true };
}
