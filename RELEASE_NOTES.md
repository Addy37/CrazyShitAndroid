# CrazyShit v2.3.1

CrazyShit 2.3.1 cleans up the new unified video session introduced in 2.3.0, focusing on the two visual problems visible during swipe-down minimization.

## Mini-player video fix

- The live unified `PlayerView` is kept above the mini-player card while it settles into the mini slot
- The mini media placeholder becomes transparent once the live video is moving over it
- The player receives an explicit higher Z-order so the mini card cannot cover the live frame with a black rectangle
- Mini-player card elevation is reduced slightly so the video remains visually dominant while the card chrome stays underneath

## Cleaner minimize transition

- Detail text, buttons, comments and related cards fade away early during the drag instead of ghosting over the feed
- The dark detail backdrop can still fade naturally behind the moving video, so Home is revealed cleanly instead of through a double-exposure effect
- During expand, detail content returns only near the end of the reverse motion
- The same live `PlayerView` and ExoPlayer remain attached throughout the entire minimize and expand sequence

## Existing behavior kept

- Swipe-down thresholds and cancel-to-restore behavior remain unchanged
- Mini-player title, expand, close and progress controls remain unchanged
- Full video detail keeps Comments, Watch Later, Share, speed, Fit / Fill / Zoom, fullscreen, related videos, history and remembered position
- The 2.2.2 floating bottom dock remains unchanged
- Chaos playback and portrait controls remain untouched

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.3.1 installs over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
