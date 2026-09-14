import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

function response(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(status === 304 ? null : JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store, max-age=0",
      "pragma": "no-cache",
      ...headers,
    },
  });
}

function parseDictionary(name: string): Record<string, string> {
  try {
    const value = JSON.parse(Deno.env.get(name) ?? "{}");
    if (!value || typeof value !== "object" || Array.isArray(value)) return {};
    return Object.fromEntries(
      Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === "string" && !!entry[1]),
    );
  } catch {
    return {};
  }
}

function parseArray(name: string): string[] {
  try {
    const value = JSON.parse(Deno.env.get(name) ?? "[]");
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string" && !!item) : [];
  } catch {
    return [];
  }
}

function acceptsPublishableKey(request: Request) {
  const supplied = request.headers.get("apikey") ?? "";
  if (!supplied) return false;

  const legacy = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  if (legacy && supplied === legacy) return true;

  const currentKeys = Object.values(parseDictionary("SUPABASE_PUBLISHABLE_KEYS"));
  if (currentKeys.includes(supplied)) return true;

  const customKeys = parseArray("APP_CONFIG_PUBLISHABLE_KEYS");
  return customKeys.includes(supplied);
}

function adminKey() {
  const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (legacy) return legacy;
  const keys = parseDictionary("SUPABASE_SECRET_KEYS");
  return keys.default ?? Object.values(keys)[0] ?? "";
}

Deno.serve(async (request) => {
  if (request.method !== "GET") return response({ error: "Method not allowed." }, 405, { Allow: "GET" });
  if (!acceptsPublishableKey(request)) return response({ error: "Unauthorized." }, 401);

  try {
    const url = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceKey = adminKey();
    if (!url || !serviceKey) return response({ error: "Service unavailable." }, 503);

    const db = createClient(url, serviceKey, { auth: { persistSession: false } });
    const { data, error } = await db.from("source_config_versions")
      .select("config_version,config").eq("is_active", true).maybeSingle();
    if (error) throw error;
    if (!data) return response({ error: "No configuration has been published." }, 404);

    const version = Number(data.config_version);
    const etag = `\"${version}\"`;
    const current = Number(new URL(request.url).searchParams.get("currentVersion") ?? "0");
    if (request.headers.get("if-none-match") === etag || current >= version) {
      return response(null, 304, { ETag: etag });
    }

    return response(data.config, 200, { ETag: etag });
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to load app configuration." }, 500);
  }
});
