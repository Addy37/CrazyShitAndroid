# ZEROCHILL build lineage

This file identifies the current approved ZEROCHILL product baseline for test APKs and future feature work.

- Source-of-truth branch: `rebrand/zerochill`
- Integration branch: `integration/zerochill-current`
- Approved product baseline commit: `ed07f23c1245a24826012191b64898807e981295`
- Device-tested APK source commit: `b1a61075306ed3ea69903a9d91676c6ff464f03d`
- Integration PR: #119
- Approved checkpoint branch: `checkpoint/zerochill-approved-ed07f23`
- Approval date: 2026-09-23

The approved baseline contains the complete current ZEROCHILL stack, including Home/Collections glass UI, OnlyFap and OnlyHaven work, ShitTok all-source/fullscreen and preload work, creator search and bundled catalog, release orientation and action-sheet cleanup, Account Coming Soon, Memes removal from the release UI, product-level notification consolidation, and the approved OnlyFap creator-gallery optimizations from PR #115.

The approved gallery behavior includes progressive creator results, ZEROCHILL loading treatment and skeletons, fluid pinch density changes, refresh suppression during pinch, full-screen media resolution five items ahead and one behind, image warm-up, and bounded video preloading.

The device-tested APK was built from `b1a6107`. PR #119 merged that candidate into `rebrand/zerochill` as `ed07f23`. The merge commit has the same product tree as the tested candidate, so `ed07f23` is the promoted approved product baseline.

The checkpoint branch preserves the exact promoted product commit before this documentation update.

## APK rule

Before producing or sharing a ZEROCHILL test APK:

1. Verify the candidate commit is the approved product baseline commit or a descendant of it.
2. Treat `rebrand/zerochill` as the current ZEROCHILL source of truth until it is merged into `main`.
3. Keep `integration/zerochill-current` synchronized with the latest approved complete ZEROCHILL lineage.
4. Start new feature work from the current complete integration lineage, not from an older feature branch.
5. Do not build from an older feature branch when a newer compatible approved lineage exists.
6. Include the source branch and short commit SHA in the APK filename.

If a newer complete build replaces this baseline, advance both current branches as appropriate, preserve a rollback checkpoint for the last approved build, and update the approved product baseline commit here.
