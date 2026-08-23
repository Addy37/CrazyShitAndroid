# CrazyShit v2.0.9

CrazyShit 2.0.9 focuses on portrait videos inside Chaos, giving vertical clips a richer background and keeping the useful social controls visible.

## What's new

- Portrait Chaos videos now use a live ambient background sampled from the playing video instead of plain black side space
- The ambient layer is low resolution and GPU-blurred on supported Android versions, so the app does not need to run a second video decoder
- A subtle dark overlay keeps the main portrait video, title, and controls readable over the ambient background
- Portrait clips keep the title, metadata, Save, Comments, Share, More, and mute controls visible while the video is playing
- Landscape/wide Chaos clips keep the existing auto-fading control behavior
- The thin Chaos scrub bar still fades away after inactivity and reappears when touched
- Existing Chaos gestures remain unchanged, including hold for 2x, vertical swiping, mute memory, Not interested, and auto-advance

## Existing landscape improvements

- Landscape uses a slim left-side navigation rail instead of the tall bottom navigation bar
- Home, Trending, and Memes use denser landscape grids
- The More section uses a compact scrollable landscape panel
- Chaos remains immersive in landscape and hides the app rail/header while playing fullscreen

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.0.9 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
