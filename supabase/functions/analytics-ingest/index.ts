import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const allowedMetrics = new Set(["app_open", "app_version", "section", "source", "creator"]);
const allowedSections = new Set([
  "home", "collections", "chaos", "categories", "search", "favorites",
  "downloads", "settings", "profile", "creator_gallery"
]);
const allowedSources = new Set([
  "crazyshit", "efukt", "fapzone", "fapello", "bunkr", "wikifeet", "wikifeetx"
]);
const hexKey = /^[0-9a-f]{64}$/;
const versionValue = /^[A-Za-z0-9._+\-]{1,40}$/;
const safeLabel = /^[^\u0000-\u001F\u007F]{1,120}$/u;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed." }, 405);

  try {
    const expectedKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const suppliedKey = request.headers.get("apikey") ?? "";
    if (expectedKey && suppliedKey !== expectedKey) return json({ error: "Unauthorized." }, 401);

    const body = await request.json();
    const metric = String(body.metric ?? "");
    const value = String(body.value ?? "").trim();
    const dayKey = String(body.day_key ?? "");
    const weekKey = String(body.week_key ?? "");
    const monthKey = String(body.month_key ?? "");

    if (!allowedMetrics.has(metric)) return json({ error: "Unsupported metric." }, 400);
    if (!hexKey.test(dayKey) || !hexKey.test(weekKey) || !hexKey.test(monthKey)) {
      return json({ error: "Invalid anonymous key." }, 400);
    }
    if (metric === "app_open" && value !== "all") return json({ error: "Invalid app metric." }, 400);
    if (metric === "app_version" && !versionValue.test(value)) return json({ error: "Invalid app version." }, 400);
    if (metric === "section" && !allowedSections.has(value)) return json({ error: "Invalid section." }, 400);
    if (metric === "source" && !allowedSources.has(value)) return json({ error: "Invalid source." }, 400);
    if (metric === "creator" && !safeLabel.test(value)) return json({ error: "Invalid creator label." }, 400);

    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !serviceKey) return json({ error: "Service unavailable." }, 503);
    const db = createClient(url, serviceKey, { auth: { persistSession: false } });
    const { error } = await db.rpc("record_analytics_metric", {
      p_metric: metric,
      p_value: value,
      p_day_key: dayKey,
      p_week_key: weekKey,
      p_month_key: monthKey,
    });
    if (error) throw error;
    return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
  } catch (error) {
    console.error(error);
    return json({ error: "Unable to record analytics." }, 500);
  }
});
