# CrazyShit 3.0.10

- Fix Android source-config validation rejecting route templates such as `{query}`, `{page}`, and `{slug}`.
- Restore bundled source configuration initialization on-device.
- Allow remote source configuration to validate and activate normally again.
- Keep the manual source diagnostics and recovery controls added in 3.0.8 and 3.0.9.
- Add a regression test so route token validation cannot break silently again.
