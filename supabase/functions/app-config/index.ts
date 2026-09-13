import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

function response(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(status === 304 ? null : JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...headers },
  });
}

function acceptsPublishableKey(request: Request) {
  const supplied = request.headers.get("apikey") ?? "";
  if (!supplied) return false;
  const legacy = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  if (supplied === legacy) return true;
  try {
    const keys = JSON.parse(Deno.env.get("APP_CONFIG_PUBLISHABLE_KEYS") ?? "[]");
    return Array.isArray(keys) && keys.some((key) => key === supplied);
  } catch { return false; }
}

Deno.serve(async (request) => {
  if (request.method !== "GET") return response({ error: "Method not allowed." }, 405, { Allow: "GET" });
  if (!acceptsPublishableKey(request)) return response({ error: "Unauthorized." }, 401);
  try {
    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
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
      return response(null, 304, { ETag: etag, "cache-control": "public, max-age=1800" });
    }
    return response(data.config, 200, { ETag: etag, "cache-control": "public, max-age=1800" });
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to load app configuration." }, 500);
  }
});
