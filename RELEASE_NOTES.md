# CrazyShit 3.0.7

- Fix remote source configuration refreshes that could get stuck on bundled defaults after a failed startup request.
- Start the 45-minute refresh window only after a successful validated remote configuration fetch.
- Retry failed source configuration refreshes instead of suppressing them for 45 minutes.
- Simplify the read-only config request path and disable client-side HTTP caching for remote source updates.
- Keep bundled source defaults and the previous known-good remote snapshot as safe fallbacks.
- Preserve the existing Fapello, Bunkr, WikiFeet, and WikiFeet X behavior outside remote configuration changes.
