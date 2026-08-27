# CrazyShit v2.8.1

CrazyShit 2.8.1 is the fast-start and interface release. It keeps the playback, Chaos mixing, navigation, and replay behavior from 2.8.0 while improving startup, comments, related videos, player controls, menus, and downloads.

## Faster startup

- Replaces the old circular splash artwork with the transparent CrazyShit wordmark
- Uses an OLED-black splash with restrained yellow glow and light effects
- Keeps the splash visible until the first real Chaos player reaches a ready state
- Fades directly into the first Chaos video and hides the loading spinner during the handoff when possible
- Retains a hard timeout so a slow or failed stream cannot trap the app on the splash
- Starts Chaos from a small prepared batch while the wider feed continues loading

## Updated interface

- Reworks the adaptive launcher icon colors and Android mask fit
- Changes the app accent color from orange to the yellow used by the CrazyShit logo
- Refines the floating bottom navigation pill and top header sizing
- Redesigns More as a native bottom sheet with clearer Library, Account, Settings, browse, display, and app sections
- Adds launcher shortcuts for Chaos, Continue Watching, Search, and Watch Later

## Comments and related videos

- Opens comments in an in-place bottom sheet instead of a separate screen
- Keeps portrait Chaos videos in place while moving horizontal videos upward to make room for comments
- Keeps the surrounding Chaos interface at full size during the comments handoff
- Improves related-video thumbnail loading and caching
- Preserves the related-video history stack so Back returns through each previously opened video
- Adds Android predictive-back support to related-video navigation

Comment posting still depends on the website accepting the signed-in session and request. Comment viewing remains available when the site exposes the thread.

## Player and menu polish

- Replaces the old popup player menu with a consistent native action sheet
- Refreshes play, pause, seek, timeline, title, fullscreen, speed, comments, save, share, and download controls
- Reduces control-show and control-hide stutter
- Adds playback retry handling and shareable playback reports for failed clips
- Keeps Picture-in-Picture, mini-player, gestures, resume position, fullscreen rotation, and completed-video replay behavior

## Downloads

- Adds Download actions to Chaos and regular video menus
- Adds a Downloads section under More for progress, retry, playback, and removal
- Saves new downloads in the device's public Downloads folder
- Uses up to four parallel byte-range transfers when the media host supports them
- Falls back to one reliable transfer when parallel ranges are unavailable
- Keeps active downloads running through an Android foreground service

## Existing behavior retained

- Chaos source mixing and frequency remain unchanged
- Shit Show playback remains unchanged
- Home, Series, Categories, Search, Continue Watching, History, and Watch Later remain available
- Stable application ID and signing identity remain unchanged, so 2.8.1 installs over 2.8.0

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
