# ZEROCHILL build lineage

This file identifies the current approved ZEROCHILL product baseline for test APKs and future feature work.

- Source-of-truth branch: `main`
- Integration branch: `integration/zerochill-current`
- Approved release baseline commit: `8417ede035eb2fd1f47de1c75383aa7cc5b3754b`
- Signed upgrade-tested APK source commit: `6f156114b4b54dbabcc369a6712e9d41dcf80906`
- Release PRs: #123 (icon/splash), #124 (release preparation), #125 (main integration)
- Production release tag: `v3.1.0`
- Previous approved checkpoint branch: `checkpoint/zerochill-approved-95d8fa4`
- Approval date: 2026-09-23

The approved baseline contains the complete current ZEROCHILL stack, including Home/Collections glass UI, OnlyFap and OnlyHaven work, ShitTok all-source/fullscreen and preload work, creator search and bundled catalog, release orientation and action-sheet cleanup, Account Coming Soon, Memes removal from the release UI, product-level notification consolidation, and the approved OnlyFap creator-gallery optimizations from PR #115.

The approved gallery behavior includes progressive creator results, ZEROCHILL loading treatment and skeletons, fluid pinch density changes, refresh suppression during pinch, full-screen media resolution five items ahead and one behind, image warm-up, and bounded video preloading.

The previous gallery and release baseline was built from `b1a6107` and merged through PR #119 as `ed07f23`.

The approved portrait navigation icons are Home, Shows, a phone and downward feed arrow for ShitTok, a seated devil woman silhouette for OnlyFap, and the ZEROCHILL mascot for More. The device-tested signed APK was built from `02d7a5a`. PR #122 merged those icons into `rebrand/zerochill` as `95d8fa4`.

The approved devil-head launcher and themed icons and DEVIL WAKE splash came from the signed test APK built at `fcc454d`. PR #123 merged that product tree into `rebrand/zerochill` as `5035f48`. The previous approved product tree remains on the checkpoint branch for rollback.

PR #124 prepared the signed 3.1.0 release and updated the private admin companion. PR #125 merged the complete ZEROCHILL line into `main` as `8417ede`. The Android 15 CI emulator installed the previous 3.0.12 stable APK and then the signed 3.1.0 candidate in place, retaining the original first-install record. The integration pointer follows `8417ede`.

## APK rule

Before producing or sharing a ZEROCHILL test APK:

1. Verify the candidate commit is the approved product baseline commit or a descendant of it.
2. Treat `main` as the current ZEROCHILL source of truth.
3. Keep `integration/zerochill-current` synchronized with the latest approved complete ZEROCHILL lineage.
4. Start new feature work from the current complete integration lineage, not from an older feature branch.
5. Do not build from an older feature branch when a newer compatible approved lineage exists.
6. Include the source branch and short commit SHA in the APK filename.

If a newer complete build replaces this baseline, advance both current branches as appropriate, preserve a rollback checkpoint for the last approved build, and update the approved product baseline commit here.
