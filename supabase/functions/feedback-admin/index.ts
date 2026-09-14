import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const encoder = new TextEncoder();
const allowedStatuses = new Set(["submitted", "reviewing", "planned", "completed"]);

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(value));
  return Array.from(new Uint8Array(digest)).map((part) => part.toString(16).padStart(2, "0")).join("");
}

function constantTimeEqual(left: string, right: string) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let i = 0; i < left.length; i++) difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return difference === 0;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return response({ error: "Method not allowed." }, 405);
  const suppliedToken = request.headers.get("x-admin-token") ?? "";
  const expectedHash = Deno.env.get("FEEDBACK_ADMIN_TOKEN_HASH") ?? "";
  if (!suppliedToken || !expectedHash || !constantTimeEqual(await sha256(suppliedToken), expectedHash)) {
    return response({ error: "Unauthorized." }, 401);
  }

  try {
    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !serviceKey) return response({ error: "Service unavailable." }, 503);
    const db = createClient(url, serviceKey, { auth: { persistSession: false } });
    const body = await request.json();

    if (body.action === "list") {
      const { data, error } = await db.from("app_feedback")
        .select("id,type,message,rating,status,developer_reply,app_version,android_version,device,section,created_at,updated_at")
        .order("created_at", { ascending: false }).limit(200);
      if (error) throw error;
      return response({ items: data ?? [] });
    }

    if (body.action === "update") {
      const id = String(body.id ?? "");
      const status = String(body.status ?? "");
      const developerReply = String(body.developer_reply ?? "").trim();
      if (!/^[0-9a-f-]{36}$/i.test(id)) return response({ error: "Invalid feedback ID." }, 400);
      if (!allowedStatuses.has(status)) return response({ error: "Invalid status." }, 400);
      if (developerReply.length > 2000) return response({ error: "Reply is too long." }, 400);
      const { data, error } = await db.from("app_feedback")
        .update({ status, developer_reply: developerReply || null })
        .eq("id", id)
        .select("id,status,developer_reply,updated_at").single();
      if (error) throw error;
      return response({ item: data });
    }

    if (body.action === "analytics") {
      const { data, error } = await db.rpc("analytics_dashboard");
      if (error) throw error;
      return response({ analytics: data ?? {} });
    }

    return response({ error: "Invalid action." }, 400);
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to manage feedback." }, 500);
  }
});
