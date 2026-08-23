# CrazyShit v2.2.2

CrazyShit 2.2.2 is a focused navigation cleanup based on real-device testing. It removes the competing selection treatments from 2.2.1 and gives the bottom dock one clear visual hierarchy.

## Simpler floating dock

- Reduced the portrait dock height again so more of the feed stays visible
- Increased side breathing room and softened the dock outline to a near-neutral dark border
- Removed the large orange selected-tab pill
- Normal tabs now use a small orange underline that glides between destinations
- Inactive labels are slightly smaller and dimmer so the selected destination reads cleanly
- Navigation touch ripples are quieter and less visually busy

## True Chaos center button

- Chaos now uses a separate 54dp circular floating button layered above the dock instead of stretching the built-in nav icon
- The original center icon is hidden while the Chaos label remains in the dock
- The floating button uses a white Chaos glyph, orange surface and subtle elevation
- Selecting Chaos gives the button a short spring response and a slightly brighter treatment
- Tapping the floating Chaos button still routes through the existing navigation logic

## Existing polish kept

- Compact view counts and tighter comments metadata
- Two-line compact titles
- Collapsing header with subtitle-first fade
- Animated skeleton loading on normal feeds
- Feed depth motion
- Mini-player entrance and live progress line
- Video-detail transition
- Animated splash artwork
- Grouped More screen

## Chaos playback kept untouched

- Portrait title and action controls stay visible
- Empty lower-overlay space passes taps and long presses through to the video
- Hold for 2x playback
- Swipe up/down between clips
- Auto-advance
- Remember mute state
- Not interested filtering
- Scrubbable progress bar with inactivity fade
- Preloading and recent-view avoidance
- Ambient/blur background remains disabled

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.2.2 installs over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
