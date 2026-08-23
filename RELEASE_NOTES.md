# CrazyShit v2.3.0

CrazyShit 2.3.0 replaces the old cross-Activity minimize handoff with one unified native video session inside the main app shell.

## One continuous player

- Normal video detail now lives inside `NativeMainActivity` instead of remaining a separate visible Activity
- Full video, swipe-down minimization, mini-player playback and expand-back-up all share the same `PlayerView` and the same ExoPlayer instance
- Minimizing no longer depends on a screenshot bridge, a second decoder, or recreating playback at the destination
- Playback position, buffering state and the live frame remain attached to the same player during minimize and expand

## Real swipe-to-mini motion

- The video follows the finger continuously toward the real mini-player slot
- The detail layer fades away during the drag so the current feed is already visible underneath
- The mini-player card fades in under the moving live video
- Releasing a committed swipe settles the live player directly into the mini-player position
- Canceling the gesture reverses the same transform back to full video

## Expand back to full video

- Tapping the mini-player video, title or expand action reverses the exact motion
- The same live player grows back into the full video area while the detail page fades in around it
- No playback restart is required when switching between full and mini states

## Native video detail kept

- Title and metadata
- Comments
- Watch Later
- Share
- Playback speed
- Fit / Fill / Zoom
- Fullscreen landscape playback
- Related videos
- Playback history and remembered position
- Native playback failure fallback

## Existing UI kept

- v2.2.2 floating bottom dock and circular Chaos button remain unchanged
- Feed polish, collapsing header, compact metadata and More redesign remain
- Legacy video Activities remain available as a fallback path, but normal video launches are routed into the unified session

## Chaos playback kept untouched

- Persistent portrait title and action controls
- Touch passthrough in empty lower overlay space
- Hold for 2x
- Swipe navigation and auto-advance
- Remember mute state
- Not interested filtering
- Scrubbable progress bar and preload behavior
- Ambient blur remains disabled

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.3.0 installs over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
