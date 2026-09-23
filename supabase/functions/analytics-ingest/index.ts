import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { validAndroidVersion, validDeviceModel } from "./validation.ts";

const allowedMetrics = new Set(["app_open", "app_version", "section", "source", "creator", "device_model", "device_manufacturer", "android_version"]);
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
const maxBodyBytes = 8 * 1024;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed." }, 405);

  try {
    const contentLength = Number(request.headers.get("content-length") ?? "0");
    if (Number.isFinite(contentLength) && contentLength > maxBodyBytes) {
      return json({ error: "Request too large." }, 413);
    }

    // This endpoint intentionally accepts anonymous app clients. Supabase anon/publishable keys are
    // public client credentials and should not be treated as an analytics authentication secret.
    // Safety comes from the strict allowlists below and from storing only aggregate counters plus
    // rotating, one-way uniqueness keys.
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
    if ((metric === "device_model" || metric === "device_manufacturer") && !validDeviceModel(body.value)) {
      return json({ error: "Invalid device model." }, 400);
    }
    if (metric === "android_version" && !validAndroidVersion(body.value)) {
      return json({ error: "Invalid Android version." }, 400);
    }

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

    return new Response(null, {
      status: 204,
      headers: { "cache-control": "no-store" },
    });
  } catch (error) {
    console.error(error);
    return json({ error: "Unable to record analytics." }, 500);
  }
});
