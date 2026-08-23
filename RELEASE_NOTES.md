# CrazyShit v2.2.4

CrazyShit 2.2.4 finishes the swipe-down mini-player handoff so the video lands more naturally on top of the feed instead of disappearing and letting the mini-player pop in afterward.

## Feed-visible handoff

- A committed swipe now exits the detail Activity earlier so Home is already visible behind the final part of the minimize animation
- The exact on-screen bounds of the shrinking video are carried back to the feed
- The captured video frame continues moving on top of Home from those bounds into the real mini-player video slot
- The final travel uses the actual laid-out mini-player PlayerView position rather than an estimated destination
- The last part of the motion uses a faster soft deceleration so the video settles into the dock instead of drifting

## Mini-player landing

- The mini-player card starts transparent during a direct handoff
- Its background, title and action buttons fade in around the traveling video while the frame is still moving
- The traveling frame remains over the destination until the mini-player ExoPlayer reaches READY
- Once playback is ready, the bridge frame fades out to reveal the live video without an extra black flash
- Existing fallback behavior remains available if a frame snapshot or geometry cannot be captured

## Existing behavior kept

- Swipe-down gesture thresholds and cancel-to-restore behavior remain
- Mini-player reopen, close, history, Continue Watching and progress tracking remain
- The 2.2.2 floating dock remains unchanged
- Chaos playback and portrait controls remain untouched

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.2.4 installs over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
