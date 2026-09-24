# ZEROCHILL AI development workflow

This file describes the recommended way to use ChatGPT, Codex, GitHub, and Supabase while building ZEROCHILL.

## Which tool to use

### ChatGPT Chat

Use Chat for product decisions, bug triage, architecture discussion, release planning, prompt drafting, code review discussion, and interpreting screenshots or test results.

Good examples:

- "ZEROCHILL: I want creator search to feel faster. Review the current behavior and propose the smallest useful improvement."
- "Compare these two UI approaches and tell me which one fits the current app better."
- "Explain this crash in simple terms and tell me what part of the app probably owns it."

### Codex

Use Codex for repository work. It can inspect the codebase, edit files, run commands, run tests, review diffs, and prepare commits or pull requests.

A good task contains the problem, the desired result, any behavior that must stay unchanged, and the level of testing you expect.

Example:

> ZEROCHILL: Fapello creator galleries load correctly, but creator search is slow. Diagnose the delay, keep all other sources working, make the smallest safe fix, run the relevant tests and Android builds, review the final diff, and prepare a PR. Follow AGENTS.md.

### ChatGPT Work

Use Work for longer multi-step tasks that involve research, files, connected apps, reports, or browser actions. For code changes, Codex remains the better default.

### Supabase

Use the connected Supabase tools for database, migration, Edge Function, and remote source-config work. Keep backend secrets out of prompts, commits, screenshots, and public Android resources.

## Recommended ChatGPT Project

Create one ChatGPT Project named `ZEROCHILL` and keep app-related chats inside it.

Suggested project instructions:

```text
You are helping build ZEROCHILL, the Android app in GitHub repository Addy37/ZEROCHILL.

Treat the current repository and AGENTS.md as the technical source of truth. Use the active ZEROCHILL branch or the branch named by the user, not an older branding branch, when inspecting current implementation.

For coding work, prefer Codex and GitHub-backed inspection over assumptions from old chat history. For Supabase work, inspect the current backend state and repository migrations before making changes.

Protect upgrade compatibility, the existing stable application ID, signing identity, persisted user data, working content sources, and bundled source fallbacks unless the user explicitly requests a migration.

Translate nontechnical feature requests into concrete acceptance criteria. Prefer small complete changes over large rewrites. Diagnose root causes instead of hiding failures.

When work changes code, report what changed, what was tested, any build or test failure, compatibility impact, backend impact, and the PR or commit reference.
```

## Recommended chat organization

Keep one main Project, then use separate chats for substantial workstreams. Examples include:

- ZEROCHILL release planning
- Rebrand and UI polish
- Fapello and source repairs
- Remote source configuration
- Downloads and playback
- Creator search and favorites
- Feedback Admin
- Supabase backend
- Performance
- Release and signing

A new chat is useful when the goal changes substantially. Continue an existing chat when you are still fixing or refining the same feature.

## Prompt pattern for coding tasks

Use this format when you want Codex to take a task from report to PR:

```text
ZEROCHILL task: <problem or feature>

Goal:
<what you want the user to experience>

Preserve:
<working behavior that must stay unchanged>

Check:
<screens, sources, flows, or regressions that matter>

Completion:
Diagnose the current implementation, make the smallest safe change, run appropriate tests/builds, review the diff, and prepare a PR. Follow AGENTS.md.
```

You do not need to fill every section for simple tasks. Plain language is fine.

## Working with screenshots and videos

For UI problems, attach the screenshot or video and describe what feels wrong. Pair visual evidence with a Codex task when code changes are needed.

Useful wording:

> This is the current ZEROCHILL behavior. Find the code responsible for what is shown here, compare it with the intended behavior, fix it, and test related states.

## Releases

Treat release work as a separate task from ordinary feature development.

Before a stable release:

- Review the accumulated diff from the last stable version.
- Run debug and release unit tests and APK builds.
- Check upgrade compatibility with the existing application ID and signing identity.
- Review public branding and release asset names.
- Review Supabase or remote-config dependencies introduced by the release.
- Check release notes.
- Use the signed release workflow documented in `SIGNING.md`.

## Source breakages

For a content source failure, first decide if the break can be repaired with the supported remote source configuration. Domains, routes, supported headers, selectors, bounded timeouts/retries, CDN hosts, fallbacks, and source availability may be remotely configurable. Parsing algorithms and executable app logic still require an APK update.

When a remote fix is possible, prefer the backend path so users do not need a new APK. When the parser or app logic changed, use a normal code PR and keep the bundled defaults current.

## Review habit

For meaningful changes, ask Codex to review its own diff before the PR. For larger releases, start a separate review task against the final branch so a fresh coding context checks for regressions, stale branding, data migration risk, security mistakes, and missing tests.
