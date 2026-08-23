# CrazyShit v2.3.2

CrazyShit 2.3.2 simplifies the unified swipe-down mini-player polish after the on-device flicker seen in 2.3.1.

## Event-driven minimize polish

- Removes the 16ms visual guard loop that could fight the normal minimize animation
- The swipe container now exposes a secondary visual observer that receives the real drag and release events without replacing the unified player's own gesture listener
- Detail text, buttons, comments and related content fade during actual drag progress instead of being forced every frame by a separate hierarchy scanner
- Cancelled swipes restore the detail content smoothly

## Stable mini-player video layer

- The mini-player media placeholder remains transparent so it cannot cover the live video with a black rectangle
- The live unified player is given a higher Z-order once and remains above the mini-player card
- Z-order is prepared at gesture start/release rather than being repeatedly changed while the animation is running
- The same PlayerView and ExoPlayer continue playing through full, minimize, mini and expand states

## Routing and lifecycle cleanup

- The event-driven observer is attached immediately when the legacy detail launcher is intercepted into the unified session
- A short resume/configuration retry remains only as a setup fallback, not as a continuous frame loop
- Legacy VideoDetailActivity remains available as a compatibility fallback when unified routing is unavailable

## Existing behavior kept

- Swipe thresholds and cancel-to-restore behavior remain unchanged
- Mini-player title, expand, close and progress controls remain
- Full video detail keeps Comments, Watch Later, Share, speed, Fit / Fill / Zoom, fullscreen, related videos, history and remembered position
- The floating bottom dock remains unchanged
- Chaos playback and portrait controls remain untouched

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.3.2 installs over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
