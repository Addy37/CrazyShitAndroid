# ZEROCHILL build lineage

This file identifies the minimum approved ZEROCHILL product baseline for test APKs.

- Integration branch: `integration/zerochill-current`
- Minimum baseline commit: `8dad094162bef8b537daa9474e8cd647f55e910b`
- Baseline build: Build Android APK #957
- Baseline date: 2026-09-22

The baseline contains the approved modern ZEROCHILL stack, including the Home/Collections glass UI, OnlyFap and OnlyHaven work, ShitTok all-source/fullscreen changes, notification and gesture-guide polish, and portrait video seek bar.

## APK rule

Before producing or sharing a ZEROCHILL test APK:

1. Verify the candidate commit is the baseline commit or a descendant of it.
2. Do not use `rebrand/zerochill` directly as a test-APK base unless the user explicitly requests that historical baseline.
3. Keep `integration/zerochill-current` pointed at the latest user-approved complete build.
4. Include the source branch and short commit SHA in the APK filename.

If a newer complete build replaces this baseline, advance `integration/zerochill-current` and update the minimum baseline commit here.
