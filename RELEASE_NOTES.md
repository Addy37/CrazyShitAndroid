# CrazyShit v2.0.11

CrazyShit 2.0.11 prioritizes reliable portrait Chaos controls and temporarily shelves the ambient blur experiment.

## Chaos portrait fix

- While CrazyShit itself is in portrait orientation, the Chaos title, metadata, Save, Comments, Share, More, and mute controls now stay visible continuously
- The normal 2.2-second Chaos chrome-hide callback is actively canceled in portrait so the controls do not fade away unexpectedly
- The thin scrubbable Chaos progress bar keeps its own independent inactivity fade behavior
- Landscape Chaos keeps the existing auto-fading control behavior
- The experimental ambient/blur background is disabled for now so it cannot interfere with the portrait control fix

## Existing behavior kept

- Hold for 2x playback
- Swipe up/down between Chaos clips
- Auto-advance when a clip ends
- Remember mute state
- Not interested filtering
- Preloading and recent-view avoidance
- Native video detail controls on Home, Trending, Library, and related videos remain hidden until the user taps the player

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.0.11 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
