# CrazyShit v2.0.10

CrazyShit 2.0.10 is a hotfix for the portrait Chaos treatment and native video-player controls.

## Fixes

- Fixed the Chaos ambient effect so it fills the unused black area around portrait clips instead of being hidden underneath the PlayerView letterbox
- Ambient video frames are still sampled from the existing player at low resolution, so no second decoder/player is created
- Portrait-looking clips embedded inside wider streams can now be detected from the sampled frame and receive the ambient treatment too
- Portrait Chaos title, metadata, Save, Comments, Share, More, and mute controls remain visible while playback continues
- The thin scrubbable Chaos progress bar still fades away after inactivity
- Fixed native video detail pages opened from Home, Trending, related videos, and Library so Media3 controls no longer pop up automatically when playback starts
- Standard player controls remain available when the user deliberately taps the video

## Existing Chaos behavior

- Hold for 2x playback
- Swipe up/down between clips
- Auto-advance when a clip ends
- Remember mute state
- Not interested filtering
- Preloading and recent-view avoidance

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.0.10 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
