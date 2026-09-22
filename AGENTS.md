# ZEROCHILL agent instructions

This repository contains ZEROCHILL, a native Android app maintained by Addy37. The public product name is ZeroChill. Some internal package names, application IDs, file names, and historical documentation still use CrazyShit or ZeroFilter because they are tied to upgrade compatibility or unfinished rebrand work.

## Primary goal

Make requested changes with the smallest safe scope. Preserve working behavior outside the requested area. Diagnose the cause of bugs instead of hiding failures or removing affected sources.

## Instruction priority

1. Follow the user's current request.
2. Follow this file.
3. Follow the repository documentation that applies to the files being changed.
4. Prefer existing project patterns over introducing new architecture without a clear need.

When instructions conflict, follow the higher item and call out the conflict in the final report.

## Repository map

- `app/`: main ZEROCHILL Android application.
- `feedbackadmin/`: private developer feedback and source-control administration app.
- `baselineprofile/`: Android baseline profile module.
- `supabase/`: database migrations and Edge Functions used by feedback and remote source configuration.
- `.github/workflows/`: CI, APK builds, performance checks, beta/release publishing, and rebrand upgrade checks.
- `REMOTE_SOURCE_CONFIG.md`: remote source configuration design and deployment notes.
- `SIGNING.md`: release signing rules.
- `PERFORMANCE_PHASE_1.md`: existing performance work and measurements.

## Compatibility rules

- Keep `applicationId = "com.addy37.crazyshitunofficial"` unless the user explicitly requests a package migration.
- Keep the existing stable signing identity. Never commit a keystore, signing passwords, service-role key, admin token, or other private credentials.
- Debug builds may use the existing `.dev` suffix so they install separately from stable builds.
- Do not rename Java package paths solely for branding. Legacy internal names can remain when changing them would add migration risk without user benefit.
- Treat persisted settings, databases, backups, downloads, favorites, history, and upgrade installs as compatibility-sensitive.

## ZEROCHILL branding

The public brand is ZEROCHILL / ZeroChill.

- Main background: black or near-black OLED.
- Primary accent: bright electric cyan.
- Supporting text: white and cool gray.
- Wordmark: `ZERO` in white and `CHILL` in cyan.
- Tagline: `NO LIMITS. ALL CONTENT.`
- Mascot direction: black devil face, cyan glowing horns and outline, one X eye, one angry eye, sharp teeth, cyan tongue.
- Do not restore the ZeroFilter `ZF` branding.
- Do not restore the prior pink/red concept.
- Do not reintroduce old yellow/orange CrazyShit styling into newly branded surfaces.
- Preserve intentionally retained internal compatibility identifiers unless the user asks to migrate them.

When a task is unrelated to branding, do not perform broad rebrand cleanup as side work.

## Content source rules

ZEROCHILL aggregates multiple sources. Source failures should remain visible and diagnosable.

- Do not remove or hide a broken source merely to make the UI appear healthy.
- Keep other working sources functional when repairing one source.
- For Fapello, Bunkr, WikiFeet, or WikiFeet X changes, check `REMOTE_SOURCE_CONFIG.md` before hard-coding domains, routes, approved headers, selectors, timeout values, retries, CDN hosts, or source availability.
- Prefer a supported remote configuration value for ordinary source drift when the current architecture already supports it.
- Keep parsing algorithms, Android behavior, permissions, classes, local paths, and executable logic compiled into the APK.
- Do not add remote executable Java, Kotlin, DEX, JavaScript, shell commands, class names, permissions, or local file paths to the source-config schema.
- Preserve the known-good bundled source defaults as the fallback when remote configuration is missing or invalid.

## Supabase rules

- Read `REMOTE_SOURCE_CONFIG.md` before changing source-config schema, migrations, Edge Functions, publish logic, or rollback behavior.
- Keep direct source-config table access closed to public client roles unless the user explicitly changes that architecture.
- Keep service-role credentials on the backend only.
- Reuse the existing private admin authentication design instead of placing admin secrets in the public Android app.
- Database changes should include a migration under `supabase/` when the repository's current Supabase layout calls for one.

## Implementation style

- Read the relevant existing implementation before editing it.
- Prefer targeted changes over broad rewrites.
- Reuse existing controllers, models, helpers, resources, and patterns when they fit.
- Do not introduce a new dependency for something the current stack already handles well.
- Keep UI state across rotation, fullscreen transitions, navigation, and process recreation where the affected screen already supports it.
- Keep media playback, downloads, favorites, history, backups, creator search, galleries, and source pagination from regressing during unrelated work.
- Do not claim a path was tested unless it was actually tested.

## Testing and build checks

For normal changes, run the checks that match the changed area. For broad app changes or release candidates, use the repository's CI-equivalent app command when the environment supports it:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease
```

Android lint is currently an audit rather than a hard CI gate:

```bash
gradle --no-daemon :app:lintDebug
```

Also use these rules:

- Run targeted unit tests first when a focused test exists.
- Run broader app tests after changes that touch shared models, networking, parsing, navigation, player behavior, persistence, or branding resources used across screens.
- Use GitHub Actions when local dependency or Android SDK access blocks a trustworthy build.
- For feedback-admin changes, build and test the `feedbackadmin` module too.
- For Supabase changes, validate migrations and Edge Functions using the available Supabase tooling before reporting the work complete.
- For upgrade/rebrand work, check the rebrand upgrade workflow and stable package/signing compatibility.

## Bug-fix workflow

1. Reproduce or trace the reported failure from available logs, screenshots, video, code, tests, or source responses.
2. Identify the root cause.
3. Change the smallest responsible layer.
4. Check related flows that share the same code path.
5. Add or update a focused regression test when it provides real protection against recurrence.
6. Run appropriate checks.
7. Review the diff for unrelated changes.

## Feature workflow

1. Translate the user's description into concrete acceptance criteria.
2. Inspect the existing implementation and reuse its patterns.
3. Implement the smallest complete version of the requested behavior.
4. Test the primary path and realistic failure states.
5. Check nearby flows that share changed code.
6. Report what changed, what was tested, and any remaining limitation.

## Build lineage guard

Before building or sharing any ZEROCHILL APK, read `ZEROCHILL_BUILD_LINEAGE.md`.

- Verify the candidate commit is the documented minimum baseline or a descendant of it.
- Use `integration/zerochill-current` as the current complete-product integration pointer unless the user explicitly selects another base.
- Do not use `rebrand/zerochill` directly for a product test APK when it is behind the documented integration baseline.
- Test APK filenames must include the source branch and short commit SHA.
- When the user approves a newer complete build, advance the integration pointer and update the lineage document.

## Git and pull requests

- Use a focused branch for substantial work.
- Keep commits scoped and readable.
- Do not mix unrelated cleanup into a feature or bug-fix PR.
- Before opening a PR, review the full diff against the target branch.
- In the PR description, list the user-visible change, technical scope, tests run, compatibility impact, backend impact, and any follow-up.
- Do not merge a release-affecting change with known failing required checks unless the user explicitly directs that action after seeing the failure.

## Agent autonomy

When the user asks for a change, carry the authorized work through implementation, testing, diff review, and PR preparation when the connected tools support those actions. Do not stop after describing what could be done.

Use reasonable assumptions for low-risk details. Ask the user only when a missing choice would materially change the product behavior, data model, public API, cost, or irreversible outcome.

Parallelize independent investigation or review work when subagents are available and doing so would improve speed or quality. Keep final ownership with the primary coding agent.

## Completion report

A coding task is complete only after the agent reports:

- What changed.
- Files or subsystems affected.
- Tests and builds actually run, including failures.
- Compatibility or migration impact.
- Backend or remote-config impact when applicable.
- PR or commit reference when one was created.
- Any known limitation that remains.