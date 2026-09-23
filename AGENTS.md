# ZEROCHILL repository instructions

ZEROCHILL is the native Android app in this repository. The public product name is ZeroChill. Some legacy CrazyShit or ZeroFilter identifiers remain for compatibility and must not be renamed only for branding.

## Operating priorities

1. Follow the user's current request and product decisions.
2. Follow this file.
3. Follow repository documentation that applies to the affected subsystem.
4. Inspect the current implementation before proposing or making changes.
5. Prefer the working app and current code over older chats or stale documentation.
6. Prefer targeted changes over rewrites.

When instructions conflict, follow the higher-priority instruction and report the conflict.

## Source of truth and lineage

- Treat `rebrand/zerochill` as the ZEROCHILL source-of-truth branch until it is merged into `main`, unless the user explicitly selects another active branch.
- Read `ZEROCHILL_BUILD_LINEAGE.md` before substantial feature work, release work, or producing an APK.
- Start new work from the current complete approved lineage. Do not build new features on an older feature branch because it happens to contain one desired change.
- Keep `integration/zerochill-current` synchronized with the current approved complete product lineage when the lineage document calls for it.
- Before sharing an APK, verify that its source commit is the approved baseline or a descendant of it.
- APK filenames must identify the source branch and short commit SHA.
- Never claim a build contains work that is not in its actual source commit.

## Product ownership

The user makes product decisions. Translate requests into technical requirements and acceptance criteria when needed.

Use reasonable judgment for low-risk implementation details. Ask only when a missing choice would materially change product behavior, UX, architecture, data, cost, compatibility, security, or an irreversible outcome.

Do not broaden a request into unrelated cleanup or redesign.

## Agent and model routing

Optimize for useful completed work per unit of model usage. Do not use stronger models, higher reasoning, or extra agents without a concrete benefit.

Default routing:

- Primary coordinator: GPT-6 Sol, medium reasoning.
- `scout`: GPT-6 Luna, low reasoning, read-only. Use for repository searches, reference tracing, log scanning, documentation checks, and other bounded evidence gathering.
- `builder`: GPT-6 Sol, medium reasoning. Use for routine Android implementation, UI work, ordinary bug fixes, refactors, tests, and scoped configuration changes.
- `reviewer`: GPT-6 Sol, high reasoning, read-only. Use only for consequential changes with meaningful regression, compatibility, security, persistence, networking, or release risk.
- `architect`: GPT-6 Sol, high reasoning, read-only. Use for difficult architecture, Android lifecycle or state problems, cross-system design, hard debugging, security-sensitive work, signing or release issues, upgrade compatibility, or Supabase plus Android integration.

Routing rules:

- Do not spawn a subagent for a trivial task the coordinator can complete cleanly.
- Prefer one builder plus coordinator verification for routine features.
- For difficult bugs, prefer scout, coordinator diagnosis, builder, then targeted reviewer when needed.
- For major architecture or high-risk UI/system work, prefer architect, builder, then reviewer.
- Keep independent searches parallel only when they materially reduce time or uncertainty.
- Do not let multiple agents edit overlapping files at the same time.
- Keep final scope control, integration, and completion reporting with the primary coordinator.
- Do not default to maximum reasoning. Escalate only when the current path hits meaningful uncertainty, failure, or risk.
- Do not use GPT-6 Astra as a routine escalation path. Sol High is the normal high-reasoning tier for this repository unless the user explicitly directs otherwise.

## Compatibility protections

Protect existing installs and upgrade behavior.

- Keep `applicationId = "com.addy37.crazyshitunofficial"` unless the user explicitly requests a package migration.
- Preserve the stable signing identity. Never commit keystores, passwords, service-role keys, admin tokens, or other private credentials.
- Do not rename Java/Kotlin package paths or compatibility-sensitive internal identifiers only for branding.
- Treat persisted settings, databases, favorites, history, downloads, backups, notification preferences, and migration behavior as compatibility-sensitive.
- Preserve working playback, navigation, creator search, galleries, source pagination, downloads, favorites, history, backups, and settings unless the request requires changing them.
- Preserve state across rotation, fullscreen transitions, navigation, and process recreation where the affected flow already supports it.

## ZEROCHILL branding

Public branding is ZEROCHILL / ZeroChill.

- Background: OLED black or near-black.
- Primary accent: electric cyan.
- Supporting text: white and cool gray.
- Wordmark: `ZERO` in white and `CHILL` in cyan.
- Tagline: `NO LIMITS. ALL CONTENT.`
- Mascot: black devil face with cyan glowing horns and outline, one X eye, one angry eye, sharp teeth, and cyan tongue.

Do not restore public CrazyShit or ZeroFilter branding, the ZF logo, the pink/red concept, or old yellow/orange styling on newly branded surfaces.

Legacy internal identifiers may remain when changing them adds migration or compatibility risk.

## Content sources and remote configuration

Diagnose source failures instead of hiding or removing the source.

- Preserve other working sources when repairing one source.
- Read `REMOTE_SOURCE_CONFIG.md` before changing source domains, routes, selectors, headers, User-Agent, Referer, retries, timeouts, CDN hosts, or availability behavior.
- Prefer the existing remote configuration system for ordinary source drift when it can safely solve the problem.
- Keep known-good bundled fallbacks.
- Keep parsing algorithms, Android behavior, permissions, classes, and executable logic in the app.
- Never place executable Java, Kotlin, DEX, JavaScript, shell commands, permissions, class names, or local file paths into remote configuration.

## Supabase and security

Inspect the current Supabase implementation and repository documentation before backend changes.

- Preserve existing authentication and authorization boundaries.
- Keep service-role and private admin credentials on the backend.
- Reuse the current private admin authentication design instead of placing admin secrets in the public app.
- Use migrations under `supabase/` when the repository structure calls for them.
- Consider rollback behavior and compatibility with installed app versions.
- Treat changes spanning Android and Supabase as high-risk enough to justify Sol High architecture or review when needed.

## Implementation workflow

For bugs:

1. Reproduce or trace the failure from available code, tests, logs, screenshots, video, or source responses.
2. Identify the root cause.
3. Change the smallest responsible layer.
4. Check nearby flows that share the code path.
5. Add or update a focused regression test when it provides real protection.
6. Run relevant checks.
7. Review the diff for unrelated changes.

For features:

1. Translate the request into concrete acceptance criteria.
2. Inspect the current implementation and relevant docs.
3. Reuse existing patterns, controllers, models, resources, and dependencies where they fit.
4. Implement the smallest complete version.
5. Test the primary path and realistic failure states.
6. Check nearby flows that share changed code.
7. Review for regressions and unintended scope expansion.
8. Prepare a focused PR when the change is substantial.

Do not introduce a new dependency when the current stack already handles the need well.

## Testing and builds

Run checks that match the changed area. Never claim a test or build passed unless it actually ran and passed.

For broad app changes or release candidates, use the repository CI-equivalent command when the environment supports it:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease
```

Android lint is currently an audit:

```bash
gradle --no-daemon :app:lintDebug
```

Also:

- Run focused tests first when they exist.
- Run broader tests after changes to shared models, networking, parsing, navigation, playback, persistence, or shared branding resources.
- Use GitHub Actions when the local environment cannot provide a trustworthy Android build.
- Build and test `feedbackadmin` when that module changes.
- Validate Supabase migrations and Edge Functions with the available Supabase tooling when they change.
- Check signing and upgrade compatibility for release-affecting work.
- Documentation-only or Codex-configuration-only changes do not require an Android build unless they unexpectedly affect the build system.

## Git and pull requests

- Use a focused feature or chore branch for substantial work.
- Keep commits scoped and readable.
- Do not rewrite shared history.
- Do not mix unrelated cleanup into a feature or bug-fix PR.
- Review the complete diff against the intended target branch before opening a PR.
- A PR description should state what changed, what was tested, failures, compatibility or migration impact, backend or remote-config impact, and remaining limitations.
- Do not merge release-affecting work with known failing required checks unless the user explicitly directs it after seeing the failure.

## Completion report

A development task should report:

- What changed.
- Files or subsystems affected.
- Tests and builds actually run.
- Failures.
- Compatibility or migration impact.
- Backend or remote-config impact when applicable.
- PR or commit reference when created.
- Remaining limitations.

Carry authorized work through implementation, validation, diff review, and PR preparation when connected tools support those actions. Do not stop at a plan unless the user asked only for planning.
