import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const allowedTypes = new Set(["feature_request", "bug_report", "general_feedback"]);
const encoder = new TextEncoder();

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

async function installationHash(value: string) {
  const salt = Deno.env.get("FEEDBACK_ID_SALT") ?? "";
  const bytes = encoder.encode(`${salt}:${value}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((part) => part.toString(16).padStart(2, "0"))
    .join("");
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return response({ error: "Method not allowed." }, 405);

  try {
    const body = await request.json();
    const installationId = String(body.installation_id ?? "");
    if (!/^[0-9a-f-]{36}$/i.test(installationId)) {
      return response({ error: "Invalid installation ID." }, 400);
    }

    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !serviceKey) return response({ error: "Service unavailable." }, 503);
    const db = createClient(url, serviceKey, { auth: { persistSession: false } });
    const hash = await installationHash(installationId);

    if (body.action === "list") {
      const { data, error } = await db
        .from("app_feedback")
        .select("id,type,message,rating,status,developer_reply,created_at,updated_at")
        .eq("installation_hash", hash)
        .order("created_at", { ascending: false })
        .limit(50);
      if (error) throw error;
      return response({ items: data ?? [] });
    }

    if (body.action !== "submit") return response({ error: "Invalid action." }, 400);
    const type = String(body.type ?? "");
    const message = String(body.message ?? "").trim();
    const rating = body.rating == null ? null : Number(body.rating);
    if (!allowedTypes.has(type)) return response({ error: "Invalid feedback type." }, 400);
    if (message.length < 5 || message.length > 2000) {
      return response({ error: "Message must contain 5 to 2000 characters." }, 400);
    }
    if (rating != null && (!Number.isInteger(rating) || rating < 1 || rating > 5)) {
      return response({ error: "Rating must be from 1 to 5." }, 400);
    }

    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
    const { count, error: countError } = await db
      .from("app_feedback")
      .select("id", { count: "exact", head: true })
      .eq("installation_hash", hash)
      .gte("created_at", oneHourAgo);
    if (countError) throw countError;
    if ((count ?? 0) >= 5) return response({ error: "Please wait before sending more feedback." }, 429);

    const { data, error } = await db.from("app_feedback").insert({
      installation_hash: hash,
      type,
      message,
      rating,
      app_version: String(body.app_version ?? "unknown").slice(0, 80),
      android_version: String(body.android_version ?? "unknown").slice(0, 80),
      device: String(body.device ?? "unknown").slice(0, 160),
      section: String(body.section ?? "unknown").slice(0, 80),
    }).select("id").single();
    if (error) throw error;
    return response({ id: data.id }, 201);
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to process feedback." }, 500);
  }
});

