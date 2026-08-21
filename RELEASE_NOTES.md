# CrazyShit Unofficial v1.4.0

A major native-polish update focused on making the app look and behave more like a purpose-built modern Android app while keeping the lightweight CrazyShit.com WebView foundation.

## Modern UI

- Material 3 dark theme across the native app surfaces
- Replaces the old popup app menu with a modern bottom-sheet control panel
- New dedicated **Settings** screen
- Rounded cards, improved spacing, typography, dialogs, switches, and haptic feedback
- Smooth first-page fade-in
- New adaptive launcher icon with Android 13+ monochrome themed-icon support

## Native video player improvements

- Redesigned translucent top bar with the current video title
- Double-tap left or right to seek backward or forward 10 seconds
- Horizontal swipe to seek through the video
- Vertical swipe on the left side to adjust brightness
- Vertical swipe on the right side to adjust media volume
- On-screen gesture feedback
- Fit, Fill, and Zoom video sizing modes
- Current playback resolution shown in the player menu when available
- Playback-speed controls retained
- Picture-in-Picture retained and now toggleable from Settings
- Resume-position behavior is toggleable from Settings
- Optional **Minimize player on Back** behavior

## In-app mini-player

- Minimizing native playback returns to the website with a compact video bar above the browser controls
- Playback resumes from the same position in the mini-player
- Tap the preview, title, or expand button to return to fullscreen native playback
- Close button ends mini-player playback
- The mini-player pauses when the browsing Activity leaves the foreground and resumes when it returns

## Watch Later

- Long-press a normal video/page link and choose **Save for later**
- Native player can save or remove the current page from Watch Later
- New **Watch Later** screen with locally stored page titles and URLs
- Saved items remain on the device only and can be removed individually or cleared from Settings

## Settings

New toggles and actions include:

- Native video player
- Picture-in-Picture
- Remember playback position
- Minimize player on Back
- Ad and pop-up blocking
- Haptic feedback
- Watch Later library
- Clear Watch Later
- Check for updates
- Clear site data

## Existing protections retained

- Ad and pop-up blocking remains enabled by default
- JavaScript popups/popunders and common ad-network requests remain blocked
- Third-party cookies remain disabled
- Mixed HTTP content remains blocked
- Android Safe Browsing remains enabled where supported
- Direct compatible media streams can still hand off to the native player

## Privacy and unofficial status

The wrapper adds no analytics, advertising SDK, telemetry backend, or account system. Watch Later and playback preferences are stored locally on the device.

This project remains unofficial and is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com. No CrazyShit.com media is bundled with the APK.
