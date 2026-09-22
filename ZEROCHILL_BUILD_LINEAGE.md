# ZEROCHILL build lineage

This file identifies the current approved ZEROCHILL product baseline for test APKs and future feature work.

- Source-of-truth branch: `rebrand/zerochill`
- Integration branch: `integration/zerochill-current`
- Approved product baseline commit: `4b9e3938cb775acdc985f225f128e63059604aef`
- Approved checkpoint branch: `checkpoint/zerochill-approved-4b9e393`
- CI build: ZeroChill Signed Upgrade Candidate #84
- Approval date: 2026-09-22

The approved baseline contains the current complete ZEROCHILL stack, including Home/Collections glass UI, OnlyFap and OnlyHaven work, ShitTok all-source/fullscreen and preload work, notification and gesture-guide polish, portrait video seek bar, the approved glass navigation treatment, GPU-reactive reflection on supported Android versions, synchronized capsule/icon/label motion during pager swipes, the approved instant OnlyFap creator search treatment, and the 3,395-creator bundled catalog.

The checkpoint branch preserves the exact device-tested product commit before this documentation update.


## APK rule

Before producing or sharing a ZEROCHILL test APK:

1. Verify the candidate commit is the approved product baseline commit or a descendant of it.
2. Treat `rebrand/zerochill` as the current ZEROCHILL source of truth until it is merged into `main`.
3. Keep `integration/zerochill-current` synchronized with the latest approved complete ZEROCHILL lineage.
4. Do not build from an older feature branch when a newer compatible approved lineage exists.
5. Include the source branch and short commit SHA in the APK filename.

If a newer complete build replaces this baseline, advance both current branches as appropriate, preserve a rollback checkpoint for the last approved build, and update the approved product baseline commit here.
