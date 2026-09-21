# Remote source configuration

ZeroChill keeps source logic in the APK. Supabase can publish validated data values that change
domains, routes, approved headers, selectors, bounded timeout/retry values, CDN hosts, fallbacks,
and per-source availability.

## Known-good baseline

- Baseline commit: `04a12ff15fdce3def897df71ee569232b531d7d4` (`v3.0.5`)
- Fapello repair commit present: `d055a59` (PR #85)
- Main-branch Android build run `34773591621`: passed before this branch was created
- Main-branch signed release run `34773591644`: passed before this branch was created
- The local baseline command could not download Maven dependencies in the restricted workspace, so
  GitHub Actions is the dependency-backed test and APK build runner for this work.

## Runtime order

1. Parse `app/src/main/assets/source_config_defaults.json`.
2. Replace it with a newer validated local cache when one exists.
3. Start the normal application flow.
4. Check the `app-config` Edge Function on a background thread, at most once every 45 minutes while
   the process is running.
5. Activate and cache only a newer config that passes Android validation.

The previous valid snapshot remains in local storage for rollback. A failed refresh never deletes
the active cache.

## Backend

- `source_config_versions` stores immutable history and one active version.
- `publish_source_config` serializes publishes with a database lock and requires increasing versions.
- `app-config` accepts a Supabase publishable key and returns only the active document.
- `app-config-admin` uses the existing private admin token hash and a backend service credential.
- Rollback copies an older document into a new higher version so phones with a newer cache accept it.
- Direct table access remains closed to `anon` and `authenticated` roles.

## Remote fields

| Source | Remote values |
|---|---|
| Global | Source kill switches on/off, fallback-domain use on/off |
| Fapello | Enabled, base/fallback domains, search/creator/listing/popular-video routes, User-Agent, Referer override, approved headers, request/ajax timeouts, retry count, creator/media/video/image/next/playable selectors, bounded parser regex, CDN hosts |
| Bunkr | Enabled, Balbums index, page/fallback origins, legacy API endpoints, signing URL, download root, User-Agent, Referer override, approved headers, request/sign timeouts, retry count, album/video/image selectors, CDN hosts |
| WikiFeet | Enabled, base/fallback domains, picture/thumbnail hosts, search route/selector, User-Agent, Referer override, approved headers, request/ajax timeouts, retry count |
| WikiFeet X | Same supported values as WikiFeet, with an independent configuration |
| Kaotic | Enabled, base/fallback domains, feed routes, User-Agent, Referer override, approved headers, request timeout/retry count, card/playable selectors, bounded page/media regex |
| TheYNC | Same supported values as Kaotic, with an independent configuration |
| ItemFix | Same supported values as Kaotic, with an independent configuration |
| OnlyHaven (cum.st) | Enabled, base/fallback domains, creator-search UI/API, creator-page/posts routes, User-Agent, Referer override, approved headers, request timeout/retry count, creator/media/playable selectors, bounded creator/media regex |

## Values that remain compiled

- Repository control flow and parsing algorithms
- WebView challenge fallback behavior
- Creator/gallery state and pagination state machines
- Database and SharedPreferences structure
- Player, thumbnail, download, and file behavior
- Android components, permissions, classes, and local paths
- Admin authentication and credential storage behavior
- Header allowlist, URL protocol rules, size limits, schema support, and validation limits

Remote Java, Kotlin, DEX, JavaScript, commands, class names, permissions, and local file paths are not
accepted by the schema.

## One-time deployment

The commands require the Supabase CLI and access to project `fketutffusxgjxjlckci`.

```bash
supabase link --project-ref fketutffusxgjxjlckci
supabase db push
supabase functions deploy app-config
supabase functions deploy app-config-admin
```

The hosted functions receive `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, and publishable-key data
from Supabase. Keep the service-role value in Supabase only. Keep the existing
`FEEDBACK_ADMIN_TOKEN_HASH` function secret because `app-config-admin` uses the same private admin
token as feedback management. Register the public client key that the read-only function accepts:

```bash
supabase secrets set APP_CONFIG_PUBLISHABLE_KEYS='["<the project sb_publishable key>"]'
```

Add these GitHub Actions secrets:

```text
SOURCE_CONFIG_ENDPOINT=https://fketutffusxgjxjlckci.supabase.co/functions/v1/app-config
SUPABASE_PUBLISHABLE_KEY=<the project's sb_publishable key>
ADMIN_SOURCE_CONFIG_ENDPOINT=https://fketutffusxgjxjlckci.supabase.co/functions/v1/app-config-admin
```

Do not add a secret/service-role Supabase key to GitHub Android-build secrets.

Build and install the private admin APK. Open **Source Control** and publish a validated document
newer than the app's bundled version 4 configuration. Version 4 keeps Kaotic, TheYNC, and ItemFix,
and adds the current OnlyHaven creator-search API route used to resolve cum.st creators reliably.
Until a newer compatible document is published, ZeroChill keeps the bundled version 4 defaults and
ignores older remote documents.

## Publishing and rollback

1. Open **Source Control** in ZeroChill Admin.
2. Edit supported JSON values.
3. Tap **Validate**. The app assigns the next version and current UTC time.
4. Tap **Publish** only after validation succeeds.
5. Use **History** and **Roll back** to restore an older known-good document as a new version.

The main app normally receives a publish within 45 minutes. Restarting does not force repeated
requests inside the refresh interval.

When this source-expansion change is deployed, deploy the updated `app-config-admin` function before
publishing a configuration that contains the expanded source objects or the OnlyHaven
`creatorSearchApi` route. No database migration is required for these source fields because the
configuration document remains stored as JSON.
