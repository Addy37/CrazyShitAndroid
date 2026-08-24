# CrazyShit v2.7.0

CrazyShit 2.7 is the Immersive OLED UI release. It keeps the navigation and features from 2.6 while moving the native app to a calmer true-black visual system.

## OLED immersive presentation

- True black OLED backgrounds are now the default across native screens
- Media cards use near-black surfaces with softer borders instead of stacked gray layers
- The top header and bottom navigation blend into the black background more naturally
- Orange remains the primary accent color
- Artwork-driven ambient color is now a very faint glow instead of a full-screen tint
- Ambient transitions are slower and less reactive while scrolling
- Scroll depth, scaling and thumbnail parallax are substantially reduced for smoother browsing
- The collapsing header is slower and less dramatic

## Floating navigation

- Keeps the rounded floating bottom navigation
- Uses a black OLED surface with a much softer outline and shadow
- Keeps the featured Chaos button and all existing tab behavior

## Video detail refresh

- Keeps compact Comments, Later and Share pill actions
- Removes the duplicate large comments card
- Uses true black details surfaces with only a tiny optional warm glow near the player
- Uses near-black Related video cards and action pills
- Reduces the player/detail entrance movement for a smoother transition
- Keeps player controls, predictive back and auto-hiding overlay buttons intact

## Appearance controls

Settings includes independent switches for:

- OLED black
- Ambient feed glow
- Motion effects
- Collapsing header

OLED black defaults to on. Each visual effect can still be disabled independently.

## Existing behavior kept

- List remains the default feed view
- Cards, Grid and Posters remain available
- Global native Search and Search thumbnails remain unchanged
- Context-aware Related videos remain unchanged
- Series and Categories keep their embedded artwork
- Continue Watching, History and Watch Later remain intact
- Chaos playback and rotated full-screen behavior remain intact
- Predictive back remains enabled on supported Android versions

## Compatibility

The stable application ID and signing identity remain unchanged, so the eventual stable CrazyShit v2.7.0 release will install over v2.6.0 while retaining app data and watch history.

Beta builds use the separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
