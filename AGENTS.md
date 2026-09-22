# ZEROCHILL agent instructions

This repository contains ZEROCHILL, a native Android app maintained by Addy37. The public product name is ZeroChill. Some internal package names, application IDs, file names, and historical documentation still use CrazyShit or ZeroFilter because changing them may affect upgrade compatibility.

## Mission

Complete the user's requested change with the smallest safe scope. Preserve working behavior outside that scope. Fix root causes instead of hiding failures, removing content, or replacing working code with an older implementation.

## Instruction priority

1. The user's current request.
2. This `AGENTS.md`.
3. Repository documentation that applies to the files being changed.
4. Existing project patterns and tested implementations.

When instructions conflict, follow the higher-priority instruction and report the conflict.

## Source of truth and version safety

The verified HEAD of the active ZEROCHILL working branch is the source of truth.

Before editing code, building an APK, or preparing a PR:

1. Read this file.
2. Check the active branch.
3. Check `git status`.
4. Record the current HEAD SHA.
5. Inspect the relevant existing implementation before changing it.

Never silently substitute an older state of the app.

- Do not restore, copy from, build from, or deliver an earlier commit, APK, artifact, branch, cached workspace, or previous task state unless the user explicitly requests that exact rollback.
- Do not use an old APK as a source of truth.
- Do not reset the branch to solve a bug.
- Do not discard unrelated uncommitted work.
- If the branch or remote state changes during the task, verify HEAD again before continuing.
- If a previous implementation is useful as a reference, port only the required idea into the current HEAD.
- When the requested behavior already exists in another current screen or component, reuse the current implementation rather than restoring an older one.

If the current state is ambiguous, inspect Git history, the active PR, and the diff. Do not guess which build is newest.

## Scope discipline

Change only what the task requires.

- Do not perform opportunistic refactors, cleanup, renames, dependency swaps, or redesigns during a focused task.
- Do not change unrelated screens while fixing one screen.
- Do not remove a feature or source merely because it is difficult to repair.
- Do not change navigation, persistence, playback, source selection, or layout behavior as collateral damage.
- If a shared component must change, make the smallest safe shared change and verify every known consumer that could be affected.
- If the user marks a surface, behavior, geometry, animation, or implementation as approved or locked, treat it as immutable unless the current request explicitly changes it.
- Preserve all existing functionality not named in the acceptance criteria.

A failed experiment should be reverted at the experiment's scope. Never recover from a failed change by rolling the whole app back to an older revision.

## ZEROCHILL project map

- `app/`: main ZEROCHILL Android application.
- `feedbackadmin/`: private developer feedback and source-control administration app.
- `baselineprofile/`: Android baseline profile module.
- `supabase/`: database migrations and Edge Functions used by feedback and remote source configuration.
- `.github/workflows/`: CI, APK builds, performance checks, beta/release publishing, and upgrade checks.
- `REMOTE_SOURCE_CONFIG.md`: remote source configuration design and deployment notes.
- `SIGNING.md`: release signing rules.
- `PERFORMANCE_PHASE_1.md`: existing performance work and measurements.

## Compatibility rules

- Keep `applicationId = "com.addy37.crazyshitunofficial"` unless the user explicitly requests a package migration.
- Keep the existing stable signing identity.
- Never commit a keystore, signing password, service-role key, admin token, API secret, or private credential.
- Debug builds may use the existing `.dev` suffix so they install separately from stable builds.
- Do not rename Java package paths solely for branding.
- Treat settings, databases, backups, downloads, favorites, history, playback state, saved page slots, and upgrade installs as compatibility-sensitive.
- Preserve navigation IDs and persisted preferences unless the task explicitly changes them.

## ZEROCHILL branding

The public brand is ZEROCHILL / ZeroChill.

- Main background: black or near-black OLED.
- Primary accent: electric blue `#0892D0` unless the current implementation defines a shared theme token for it.
- Supporting text: white and cool gray.
- Wordmark: `ZERO` in white and `CHILL` in electric blue.
- Tagline: `NO LIMITS. ALL CONTENT.`
- Mascot direction: black devil face, cyan/electric-blue glowing horns and outline, one X eye, one angry eye, sharp teeth, cyan/electric-blue tongue.
- Do not restore ZeroFilter `ZF` branding.
- Do not restore the previous pink/red concept.
- Do not reintroduce old yellow/orange CrazyShit styling into newly branded surfaces.
- Preserve compatibility-sensitive internal names unless the user explicitly requests a migration.

Do not perform broad branding cleanup during unrelated tasks.

## Approved UI and interaction preservation

For UI work, screenshots and user-provided videos are acceptance evidence.

- Preserve geometry, spacing, positions, labels, source order, navigation order, and interaction behavior that the request does not mention.
- Preserve approved Home, Shows, ShitTok, OnlyFap, More, and bottom-navigation behavior unless the task explicitly targets them.
- Do not replace a working current component with an older visual implementation.
- Prefer the existing shared glass, typography, icon, animation, and media components when they already match the requested result.
- Keep touch feedback, transitions, and animation fluid without blocking navigation or media gestures.
- Visual polish must not break swipe, scroll, tap, back, rotation, fullscreen, source switching, or playback behavior.

## Media and navigation regression contract

When a change touches shared UI, player code, navigation, lifecycle handling, or media loading, verify the relevant flows below:

- Bottom navigation switches tabs correctly.
- ShitTok can swipe immediately after launch and after returning from another tab.
- ShitTok content can remain behind the floating navbar where the current design allows it.
- Portrait and landscape transitions restore the correct layout.
- Leaving fullscreen does not leave headers or content inset incorrectly.
- Related-video navigation keeps the current transition behavior.
- Home and Shows retain their source rails and feed behavior.
- OnlyFap retains source selection, search, galleries, image loading, video playback, and creator navigation.
- Search remains responsive and does not regress thumbnails or results.
- Back navigation returns to the expected state without recreating an older UI state.

Run only the checks relevant to the changed shared code, but treat regressions in these flows as blockers when the changed code can affect them.

## Content source rules

ZEROCHILL aggregates multiple sources. Source failures should remain visible and diagnosable.

- Do not remove or hide a broken source merely to make the UI appear healthy.
- Keep working sources functional while repairing another source.
- Preserve source-specific headers, attribution, pagination, thumbnails, media URLs, and routing unless the task changes them.
- For Fapello, Bunkr, WikiFeet, WikiFeet X, or other remotely configurable sources, read `REMOTE_SOURCE_CONFIG.md` before hard-coding domains, routes, approved headers, selectors, timeout values, retries, CDN hosts, or source availability.
- Prefer supported remote configuration for ordinary source drift when the current architecture already supports it.
- Keep parsing algorithms, Android behavior, permissions, classes, local paths, and executable logic compiled into the APK.
- Do not add remote executable Java, Kotlin, DEX, JavaScript, shell commands, class names, permissions, or local file paths to the source-config schema.
- Preserve known-good bundled source defaults as fallback when remote configuration is missing or invalid.

## Supabase rules

- Read `REMOTE_SOURCE_CONFIG.md` before changing source-config schema, migrations, Edge Functions, publish logic, or rollback behavior.
- Keep direct source-config table access closed to public client roles unless the user explicitly changes that architecture.
- Keep service-role credentials on the backend only.
- Reuse the existing private admin authentication design instead of placing admin secrets in the public Android app.
- Add a migration under `supabase/` when the current Supabase layout requires one.

## Implementation style

- Inspect the relevant current implementation before editing it.
- Prefer targeted changes over broad rewrites.
- Reuse existing controllers, models, helpers, resources, animations, and patterns when they fit.
- Do not introduce a dependency for something the current stack already handles well.
- Keep UI state across rotation, fullscreen transitions, navigation, and process recreation where the affected screen already supports it.
- Keep media playback, downloads, favorites, history, backups, creator search, galleries, pagination, and source state from regressing during unrelated work.
- Do not claim a path was tested unless it was actually tested.
- Do not mask exceptions or network failures solely to make a test pass.
- Add focused regression coverage when it meaningfully protects the reported behavior.

## Testing and build checks

Start with the narrowest useful checks, then widen only when the changed code can affect broader behavior.

For broad app changes or release candidates, use the repository's CI-equivalent app command when the environment supports it:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease
```

Android lint is currently an audit rather than a hard CI gate:

```bash
gradle --no-daemon :app:lintDebug
```

Also follow these rules:

- Run a focused test first when one exists.
- Run broader app tests after changes to shared models, networking, parsing, navigation, player behavior, persistence, theme resources, or shared UI components.
- Use GitHub Actions when local dependency or Android SDK access blocks a trustworthy build.
- For `feedbackadmin` changes, build and test that module too.
- For Supabase changes, validate migrations and Edge Functions using available Supabase tooling.
- For upgrade/rebrand work, verify the upgrade workflow and stable package/signing compatibility.
- Do not report success while a relevant required check is known to be failing.

## APK and artifact provenance

Every APK presented as the result of a task must be traceable to the intended current code.

Before delivering an APK:

1. Verify the branch used for the build.
2. Verify the commit SHA used for the build.
3. Verify the build completed successfully for that SHA.
4. Confirm the artifact belongs to that build, not an earlier workflow run or cached output.

Prefer artifact names that include the build date and short commit SHA when the workflow permits it, for example:

`ZEROCHILL-2026-09-22-c962297.apk`

Never present an older APK as the result of a newer task.

If CI produced the APK, confirm the workflow run's commit SHA matches the intended commit before sharing the artifact.

## Bug-fix workflow

1. Reproduce or trace the reported failure using available video, screenshots, logs, code, tests, or source responses.
2. Identify the root cause.
3. Change the smallest responsible layer.
4. Check other flows sharing that code path.
5. Add or update focused regression coverage when useful.
6. Run appropriate checks.
7. Review the final diff for unrelated changes.
8. Verify current HEAD before building or delivering an artifact.

## Feature workflow

1. Translate the user's request into concrete acceptance criteria.
2. Inspect the current implementation.
3. Reuse current working patterns where practical.
4. Implement the smallest complete change.
5. Test the primary path and realistic failure states.
6. Check nearby flows sharing changed code.
7. Review the final diff for scope creep.
8. Verify current HEAD before building or delivering an artifact.

## Git and pull requests

- Use a focused branch for substantial work.
- Keep commits scoped and readable.
- Do not mix unrelated cleanup into a feature or bug-fix PR.
- Never force-push, hard-reset, delete a branch, or rewrite shared history unless the user explicitly requests it.
- Before opening or updating a PR, review the full diff against its target branch.
- In the PR description, include the user-visible change, technical scope, tests run, compatibility impact, backend impact, and remaining follow-up.
- Do not merge a release-affecting change with known failing required checks unless the user explicitly directs that action after seeing the failure.

## Agent autonomy

When the user asks for a change, carry the authorized task through investigation, implementation, testing, diff review, and PR or artifact preparation when the connected tools support those actions.

Use reasonable assumptions for low-risk details. Ask only when a missing choice would materially change product behavior, data, public API, cost, security, or an irreversible outcome.

For long tasks, keep one clear owner for the final implementation. Parallel investigation is fine when independent work can be reconciled safely.

## Completion report

A coding task is complete only after the agent reports:

- What changed.
- Files or subsystems affected.
- Tests and builds actually run, including failures.
- Branch and commit SHA used.
- Compatibility or migration impact.
- Backend or remote-config impact when applicable.
- PR reference when one exists.
- APK or artifact provenance when one was produced.
- Any known limitation that remains.
