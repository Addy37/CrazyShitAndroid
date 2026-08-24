# CrazyShit v2.7.0

CrazyShit 2.7 is the Immersive UI release. It keeps the navigation and features from 2.6 while giving the native app a much more dynamic visual presentation.

## Immersive feed presentation

- Adds an ambient background glow sampled from the artwork closest to the center of the feed
- Smoothly transitions the ambient color as you browse instead of switching abruptly
- Makes feed containers transparent so the artwork-driven background can actually show through
- Adds subtle scroll depth to media cards with small scale, opacity and elevation changes
- Adds a tiny thumbnail parallax effect while scrolling
- Gives media cards a softer translucent surface, larger radius and cleaner outline

## Collapsing chrome

- Home, Series, Categories and native collection feeds shrink the header while you scroll down
- The header expands again when you return toward the top
- The app icon and subtitle reduce naturally with the header instead of simply disappearing
- The effect stays out of the way of the existing rotated/full-screen UI

## Floating navigation

- Restyles the bottom navigation as a rounded floating surface
- Adds a subtle active-tab indicator and softer ripple treatment
- Keeps the featured Chaos button and all existing tab behavior

## Video detail refresh

- Replaces the heavy action row with smaller pill actions for Comments, Later and Share
- Removes the duplicate large comments card from the video details page
- Gives the details area a subtle warm gradient instead of a flat black surface
- Gives the player a short expansion animation when opening a video
- Keeps player controls, predictive back and auto-hiding overlay buttons intact
- Refines Related video cards to match the new 2.7 surface treatment

## Appearance controls

Settings now includes independent switches for:

- Ambient feed glow
- Scroll depth motion
- Collapsing header

All three default to on and can be disabled separately.

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
