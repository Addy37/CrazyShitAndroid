# CrazyShit v2.2.3

CrazyShit 2.2.3 focuses on the swipe-down video minimize experience and the native mini-player handoff.

## Swipe-to-mini-player handoff

- Swipe-down now makes the playing video follow the gesture instead of barely shrinking and then disappearing
- Releasing a committed swipe animates the video directly toward the mini-player video slot at the bottom of the feed
- Video controls and detail content fade away progressively during the gesture so the transition feels like one continuous action
- Canceling the gesture smoothly restores the full video page and controls
- The Activity close animation remains suppressed so Android does not add a second competing transition

## Decoder handoff polish

- A tiny frame snapshot is captured from the playing TextureView when the minimize gesture commits
- The snapshot is passed through the existing activity result and displayed over the mini-player video surface while its ExoPlayer resumes
- The snapshot fades away as soon as the mini-player reaches READY, reducing black flashes during the player-to-player handoff
- Temporary snapshot files are removed from app cache after the transition

## Mini-player cleanup

- Mini-player height reduced slightly for a cleaner floating-card shape
- Video area increased to 132 x 74dp so the minimized video reads more clearly
- Title and action spacing tightened
- Mini-player now sits closer to the 2.2.2 floating dock while keeping a visible gap between the two layers
- Direct swipe handoffs skip the old slide-in animation because the outgoing video already lands at the destination
- Normal mini-player launches still keep a subtle entrance animation

## Existing UI kept

- 2.2.2 floating dock with small selected underline
- Separate circular Chaos center button
- Compact feed metadata and two-line titles
- Collapsing header and skeleton loading
- Mini-player progress line
- Video-detail page controls and related videos

## Chaos playback kept untouched

- Portrait Chaos title and action controls remain visible
- Empty lower-overlay space passes gestures through to the video
- Hold for 2x, swipe navigation, auto-advance, mute memory, scrubbing and preload behavior are unchanged
- Ambient/blur background remains disabled

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.2.3 installs over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
