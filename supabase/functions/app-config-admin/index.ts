import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { validateSourceConfig } from "../_shared/source-config-validation.ts";

const encoder = new TextEncoder();

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
    let body: Record<string, unknown>;
    try {
      const raw = await request.text();
      if (encoder.encode(raw).length > 300 * 1024) {
        return response({ error: "Request body is too large." }, 413);
      }
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error();
      body = parsed as Record<string, unknown>;
    } catch {
      return response({ error: "Request body must be a JSON object." }, 400);
    }

    if (body.action === "current") {
      const { data, error } = await db.from("source_config_versions")
        .select("config_version,schema_version,updated_at,published_at,config")
        .eq("is_active", true).maybeSingle();
      if (error) throw error;
      return response({ item: data ?? null });
    }

    if (body.action === "history") {
      const { data, error } = await db.from("source_config_versions")
        .select("config_version,schema_version,updated_at,published_at,based_on_version,action,is_active")
        .order("config_version", { ascending: false }).limit(50);
      if (error) throw error;
      return response({ items: data ?? [] });
    }

    if (body.action === "validate" || body.action === "publish") {
      const rawConfig = body.config;
      const validation = validateSourceConfig(rawConfig);
      if (!validation.valid) return response({ valid: false, error: validation.reason }, 400);
      const config = rawConfig as Record<string, unknown>;
      if (body.action === "validate") return response({ valid: true, configVersion: config.configVersion });
      const { data: active, error: activeError } = await db.from("source_config_versions")
        .select("config_version").eq("is_active", true).maybeSingle();
      if (activeError) throw activeError;
      if (active && Number(config.configVersion) <= Number(active.config_version)) {
        return response({ error: "configVersion must be newer than the active version." }, 409);
      }
      const { data, error } = await db.rpc("publish_source_config", {
        p_config: config, p_based_on_version: active?.config_version ?? null, p_action: "publish",
      });
      if (error) throw error;
      return response({ published: true, configVersion: data }, 201);
    }

    if (body.action === "rollback") {
      const target = Number(body.configVersion);
      if (!Number.isSafeInteger(target) || target < 1) return response({ error: "Invalid rollback version." }, 400);
      const [{ data: prior, error: priorError }, { data: active, error: activeError }] = await Promise.all([
        db.from("source_config_versions").select("config").eq("config_version", target).maybeSingle(),
        db.from("source_config_versions").select("config_version").eq("is_active", true).maybeSingle(),
      ]);
      if (priorError || activeError) throw priorError ?? activeError;
      if (!prior || !active) return response({ error: "Rollback version was not found." }, 404);
      const nextVersion = Number(active.config_version) + 1;
      const config = { ...prior.config, configVersion: nextVersion, updatedAt: new Date().toISOString() };
      const validation = validateSourceConfig(config);
      if (!validation.valid) return response({ error: `Stored configuration is invalid: ${validation.reason}` }, 409);
      const { data, error } = await db.rpc("publish_source_config", {
        p_config: config, p_based_on_version: target, p_action: "rollback",
      });
      if (error) throw error;
      return response({ rolledBack: true, configVersion: data, restoredFrom: target });
    }

    return response({ error: "Invalid action." }, 400);
  } catch (error) {
    console.error(error);
    const message = error instanceof Error ? error.message : String(error);
    if (/configVersion must increase|duplicate key/i.test(message)) {
      return response({ error: "Configuration version is no longer current. Reload and try again." }, 409);
    }
    return response({ error: "Unable to manage source configuration." }, 500);
  }
});
