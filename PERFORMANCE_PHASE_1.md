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

The app profile and generator cover:

- cold startup and splash handoff
- Home and Chaos scrolling
- global search
- Fapzone creator lists and creator galleries
- gallery media selection
- video detail and Chaos playback

The release build consumes `app/src/main/baseline-prof.txt`. The generator can refresh the profile from live flows through the `:baselineprofile` module.

## Verification

Final CI run details, tests, benchmark output, and the release APK are recorded in the pull request.
