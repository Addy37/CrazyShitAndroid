# CrazyShit 3.0 Performance Phase 1

## Scope

This phase audits and improves startup, feed rendering, thumbnails, image caching, adapter invalidation, duplicate requests, state retention, pagination, Media3 lifecycle, and memory use. It keeps the existing UI, navigation, and feature set.

The app uses Android Views and RecyclerView, not Jetpack Compose. Compose recomposition does not apply. The matching cost in this codebase is unnecessary full adapter rebinding.

## Measurement setup

- Benchmark module: `:baselineprofile`
- AndroidX Benchmark: 1.4.1
- Device in CI: Pixel 6 profile, API 35, x86_64 emulator, animations disabled
- Compilation mode: none
- Startup: five cold iterations
- Scrolling and search: three warm iterations per flow
- Metrics: time to initial display, time to full display, frame duration, frame overrun, and last-sample memory
- Before-change app commit: `b7ba2127689a15a20248a94cb29f7cb4a7d47592`
- Isolated benchmark runner commit: `3bad2a5979088f689d60adc4930b15c88196502b`

Emulator results are a repeatable regression baseline for this repository. Release decisions should also use a fixed physical device because Android recommends physical hardware for representative benchmark values.

## Baseline measurements

These are the pre-change `benchmarkRelease` medians from the API 35 CI emulator. Startup uses five iterations. The other flows use three. Frame figures are CPU duration percentiles, and memory is the final sample from each run.

| Flow | Startup display | Frame P50 | Frame P95 | Heap | Anonymous RSS |
| --- | ---: | ---: | ---: | ---: | ---: |
| Cold startup, uncompiled | 2,990.5 ms | n/a | n/a | 19.6 MiB | 141.1 MiB |
| Home scroll | n/a | 340.5 ms | 1,003.8 ms | 27.6 MiB | 193.3 MiB |
| Chaos scroll | n/a | 321.6 ms | 465.6 ms | 38.8 MiB | 196.9 MiB |
| Search | n/a | 217.2 ms | 910.6 ms | 28.4 MiB | 184.0 MiB |

The app does not call `reportFullyDrawn`, so StartupTimingMetric reports time to initial display only. CI emulator timing is noisy and should be used for relative regression checks, not device-level performance claims.

## Post-change measurements

| Flow | Startup display | Frame P50 | Frame P95 | Heap | Anonymous RSS |
| --- | ---: | ---: | ---: | ---: | ---: |
| Cold startup, uncompiled | 1,982.7 ms (-33.7%) | n/a | n/a | 11.2 MiB (-42.9%) | 127.4 MiB (-9.7%) |
| Home scroll | n/a | 322.0 ms (-5.4%) | 755.6 ms (-24.7%) | 9.8 MiB (-64.4%) | 147.7 MiB (-23.6%) |
| Chaos scroll | n/a | 324.3 ms (+0.8%) | 440.1 ms (-5.5%) | 9.9 MiB (-74.4%) | 146.6 MiB (-25.6%) |
| Search | n/a | 316.8 ms (+45.9%) | 1,459.3 ms (+60.2%) | 11.1 MiB (-61.1%) | 151.8 MiB (-17.5%) |

Lower values are better. Startup and memory improved in this run. Home and Chaos tail frame time also improved. Search frame time regressed, while its memory fell. Search uses eight live network sources, so this emulator result includes server and response-order variance. It remains a recorded regression signal for a fixed-device follow-up rather than proof of a rendering gain.

The creator profile, gallery, and playback flow was also added as a separate Macrobenchmark. Its first recorded run completed with a 405.6 ms frame P50, 1,697.9 ms frame P95, 10.1 MiB heap, and 148.9 MiB anonymous RSS. It has no pre-change comparison because it was added after the initial baseline snapshot.

## Audit findings and changes

| Area | Finding | Phase 1 action |
| --- | --- | --- |
| Startup | `MainPagerAdapter` loaded Home, Collections, and Categories during construction even though only one page was active. | Load each non-Chaos page on first selection. Existing page instances remain retained. |
| Startup | App preference migration wrote the same values on every process start. | Skip the editor and disk scheduling after migration is complete. |
| Startup | Launcher shortcuts were republished every process start. | Publish once per app version. |
| Thumbnails | Every native feed adapter eagerly created two WebViews. The retired browse-art fallback also created an unused WebView during main-screen construction. | Create a WebView only after static thumbnail extraction fails or a browse-art request is made. |
| Duplicate requests | Video cards with valid direct artwork still scheduled page scraping and rendered-page fallback work. Search had the same issue. | Use fallback resolution only after direct artwork is absent or Glide reports a failure. |
| Image caching | Glide already supplies memory and disk caches, while resolved thumbnail URLs and generated local frames are reused. The main waste was bypassing those direct-image paths with speculative page requests. | Keep Glide behavior and remove speculative fallback traffic. |
| Feed rendering | Playback history changes called `notifyDataSetChanged`, rebinding text, listeners, and thumbnails for the full feed. | Dispatch a playback payload and bind only watch state. |
| Pagination | Appending a page scanned the existing list once for every incoming item. | Maintain a URL set so deduplication is linear. |
| Search state | A completed search restored its snapshot after recreation and then issued all eight source requests again. Each source completion also rewrote the full snapshot. | Reuse completed snapshots and save once when the source batch finishes. Partial searches still restart to complete missing data. |
| Main screen state | The four primary page views and their adapters are retained by the existing pager. | Kept as-is. Lazy loading does not discard page state. |
| Gallery state | Creator and Bunkr gallery session stores already retain items, cursor, page, resolved URLs, and scroll position. | Kept as-is. |
| Chaos startup | Splash preloading resolved a starter stream, but Chaos could resolve the same page again. | Transfer the preloaded `StreamInfo` into the Chaos stream cache when available. |
| Chaos memory | The resolved-stream map grew for the full session. Several visible holders could keep ExoPlayer instances. | Cap stream metadata at 32 entries, release players beyond the adjacent page, and release visible players when Chaos is inactive or the host pauses. |
| Chaos persistence | Every swipe serialized up to 500 recent URLs and scheduled a preference write. | Debounce writes for 750 ms and flush on pause or close. |
| Video lifecycle | `VideoDetailActivity` pauses in `onStop` and releases Media3, recovery work, thumbnail resolvers, image targets, and executors in `onDestroy`. Bunkr gallery playback releases in `onPause` and `onDestroy`. | Kept existing detail-player behavior. Chaos received the tighter lifecycle because it can own several players. |

## Audited items left unchanged

- The branded splash has an intentional 850 ms minimum. Changing it would change visible startup behavior.
- The primary pager retains every main screen to keep tab state and horizontal paging behavior.
- `VideoDetailActivity` keeps one paused player through a short background stop and releases it at destruction. Rebuilding it on every stop would add playback risk.
- Repository-wide response caching or request coalescing would change freshness and cancellation semantics. Phase 1 removes confirmed duplicate thumbnail and Chaos starter requests instead.
- The feed and Chaos item lists remain available for backward scrolling. Phase 1 bounds derived stream/player state rather than trimming user-visible history.

## Baseline Profile coverage

The checked-in app profile covers:

- cold startup and splash handoff
- Home and Chaos scrolling
- global search
- Fapzone creator lists and creator galleries
- gallery media selection
- video detail and Chaos playback

The release build consumes `app/src/main/baseline-prof.txt`. The generator validates startup profile collection through the `:baselineprofile` module. Separate Macrobenchmarks exercise Home, Chaos, search, creator profiles, galleries, and playback without combining every live network flow into one profile-capture process.

## Verification

- Debug unit tests: 36 passed, 0 failed
- Release unit tests: 36 passed, 0 failed
- Debug APK: assembled
- Release APK: assembled as `CrazyShit-3.0-phase1-release.apk`
- Android lint: the existing `NotificationCoordinator` notification-permission finding remains; no Phase 1 file added a lint error
- GitHub artifact uploads: blocked by the repository's full Actions artifact quota, so the assembled APK is not downloadable from this run

Final CI run links and benchmark output are recorded in the pull request.

## Phase 1.1 Home thumbnail regression

The live Home markup places `https://static.crazyshit.com/static/images/blank-tile.png`
before each real `/thumbs/...jpg` image. The feed parser accepted that sizing tile as a
thumbnail. Phase 1 then trusted the nonblank URL and correctly skipped its heavier page
resolver, leaving the card image black.

Phase 1.1 rejects the specific `blank-tile` sizing asset during feed parsing. The parser
then maps the real thumbnail from the same Home response. Glide keeps using its shared
memory and disk caches, view-sized decoding, lifecycle cancellation, and direct
asynchronous request. No delay, retry loop, extra page request, or adapter-wide refresh
was added.

The regression fixture mirrors the current Home card markup and verifies that the sizing
tile is skipped in favor of the real thumbnail. Debug and release suites now contain 36
tests each.
