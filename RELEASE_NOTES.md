# CrazyShit v2.4.0

CrazyShit 2.4.0 retires the in-app mini-player experiment and replaces it with a simpler, more useful watch-state experience directly in the feeds.

## Watched and Continue indicators

- Partially watched videos show a compact **Continue** badge with the saved timestamp
- A thin orange progress line across the thumbnail shows how far you watched
- Videos that reach the completion threshold show a **✓ Watched** badge and a subtle thumbnail dim
- Watch-state indicators appear in Large, Compact and Grid feed layouts
- Feed cards refresh automatically when playback history changes
- Very short accidental plays under the existing history threshold are not marked as Continue

## Simpler video return flow

- The experimental unified mini-player routing is no longer used for normal video pages
- Swipe-down-to-minimize is disabled for existing and new installs
- Back no longer minimizes a video into an in-app floating player
- Returning from video playback reveals the existing feed where you left it instead of creating a mini-player
- Saved playback position remains available so unfinished videos can resume close to where you stopped

## Settings cleanup

- Removes the retired **Minimize player on Back** setting
- Removes the retired **Swipe down to minimize** setting
- Keeps Picture-in-Picture as a separate Android playback option
- Watch history can still be cleared from Settings, which also clears feed Watched/Continue state

## Existing behavior kept

- Native video detail still includes Comments, Watch Later, Share, playback speed, Fit / Fill / Zoom, fullscreen, related videos, history and remembered position
- Home, Trending, Memes, Library, the floating bottom dock and visual polish remain unchanged
- Chaos playback and portrait controls remain untouched

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.4.0 installs over earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
