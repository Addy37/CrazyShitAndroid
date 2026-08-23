# CrazyShit v2.0.12

CrazyShit 2.0.12 fixes an invisible touch-blocking area in the lower part of Chaos videos.

## Chaos touch fix

- The full-width transparent lower Chaos chrome container no longer acts as a long-press or click target
- Empty space around the title and action stack now passes touches through to the video underneath
- Tap-to-play/pause and hold-for-2x work across much more of the lower half of the Chaos screen
- Actual controls remain interactive, including title/meta, Save, Comments, Share, More, mute, and the scrubbable progress bar
- Portrait Chaos title and action controls still stay visible continuously
- The thin scrub bar still uses its own inactivity fade behavior
- Landscape Chaos keeps the existing auto-fading control behavior

## Existing behavior kept

- Swipe up/down between Chaos clips
- Auto-advance when a clip ends
- Remember mute state
- Not interested filtering
- Preloading and recent-view avoidance
- Native video detail controls on Home, Trending, Library, and related videos remain hidden until the user taps the player
- The ambient/blur experiment remains disabled for now

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.0.12 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
