# ZEROCHILL build lineage

This file identifies the current approved ZEROCHILL product baseline for test APKs and future feature work.

- Source-of-truth branch: `rebrand/zerochill`
- Integration branch: `integration/zerochill-current`
- Approved product baseline commit: `0a061a0cf4964fb0cbd7d06d65d0662a0be04f8d`
- Latest production release source commit: `0a061a0cf4964fb0cbd7d06d65d0662a0be04f8d`
- Device-tested APK source commit: `643579212b5c35b59c7b7666998b518b6cbd6149`
- Latest release PR: #134
- Approval date: 2026-09-24

The approved baseline contains the complete current ZEROCHILL stack, including Home/Collections glass UI, OnlyFap and OnlyHaven work, ShitTok all-source/fullscreen and preload work, creator search and bundled catalog, release orientation and action-sheet cleanup, Account Coming Soon, Memes removal from the release UI, product-level notification consolidation, aggregate device and Android adoption analytics, and the approved OnlyFap creator-gallery optimizations from PR #115.

The approved gallery behavior includes progressive creator results, ZEROCHILL loading treatment and skeletons, fluid pinch density changes, refresh suppression during pinch, full-screen media resolution five items ahead and one behind, image warm-up, and bounded video preloading.

ZeroChill v3.1.3 was published from `0a061a0` after PR #134. It includes PR #133's ShitTok speed and preload pass, OnlyFap creator-name/gallery warmup, pinch-to-clear-display gesture, and the phone fullscreen orientation restore fix. The production release workflow verified the signed package, certificate, and upgrade path from v3.1.2. New releases now publish one public install APK, `ZeroChill.apk`, plus `SHA256SUMS.txt` instead of duplicate APK aliases.\n\nZeroChill v3.1.2 was published from `ca95579e` after PR #132. Compared with v3.1.1, the main app adds fast source failover for Home and ShitTok from PR #131. The repository also contains the distinct ZeroChill Admin launcher icon from PR #130. The v3.1.2 release workflow built and signed the stable APK, verified the package and certificate, and verified upgrade compatibility from the previous stable release.

The previous gallery and release baseline was built from `b1a6107` and merged through PR #119 as `ed07f23`.

The approved portrait navigation icons are Home, Shows, a phone and downward feed arrow for ShitTok, a seated devil woman silhouette for OnlyFap, and the ZEROCHILL mascot for More. The device-tested signed APK was built at `02d7a5a`. PR #122 merged those icons into `rebrand/zerochill` as `95d8fa4`.

The approved devil-head launcher and themed icons and DEVIL WAKE splash came from the signed test APK built at `fcc454d`. PR #123 merged that product tree into `rebrand/zerochill` as `5035f48`. The previous approved product tree remains on the checkpoint branch for rollback.

## APK rule

Before producing or sharing a ZEROCHILL test APK:

1. Verify the candidate commit is the approved product baseline commit or a descendant of it.
2. Treat `rebrand/zerochill` as the current ZEROCHILL source of truth until it is merged into `main`.
3. Keep `integration/zerochill-current` synchronized with the latest approved complete ZEROCHILL lineage.
4. Start new feature work from the current complete integration lineage, not from an older feature branch.
5. Do not build from an older feature branch when a newer compatible approved lineage exists.
6. Include the source branch and short commit SHA in the APK filename.

If a newer complete build replaces this baseline, advance both current branches as appropriate, preserve a rollback checkpoint for the last approved build, and update the approved product baseline commit here.
