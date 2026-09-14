# CrazyShit 3.0.9

- Allow remote source configuration to recover even when no local source-config snapshot is active.
- Make "Check source config now" contact Supabase instead of failing immediately when bundled initialization fails.
- Allow a validated remote config to become the first active snapshot and persist it as the known-good configuration.
- Show the real bundled initialization or validation error in Settings instead of only "Source config unavailable."
- Preserve the 3.0.8 source diagnostics and manual refresh controls.
