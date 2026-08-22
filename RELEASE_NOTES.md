# CrazyShit v2.0.0

CrazyShit 2.0 is a major rebuild of the Android client. The primary browsing experience is now native Android, with the live website retained as a compatibility fallback for flows that need it.

## Highlights

- Native Home and Trending feeds with pagination and retained tab state
- Real-time horizontal swiping between Home, Trending, Chaos, and Memes
- **Chaos**, a featured vertical random-video feed with autoplay, next/previous swiping, preloading, duplicate prevention, and recent-view avoidance
- Chaos landscape mode with immersive fullscreen playback while keeping vertical next/previous swipes active
- Native **Memes** feed and static image viewer
- Native Library with Continue Watching, History, Watch Later, thumbnails, progress, and swipeable Library tabs
- Native video detail screen with Media3 playback, comments, related videos, sharing, and Watch Later
- In-app mini-player, swipe-down minimize, resume position, PiP, speed controls, seek gestures, brightness/volume gestures, and Fit/Fill/Zoom modes
- Native comments and reply composer using the current CrazyShit.com session
- Dedicated sign-in flow and **My Profile** entry for the logged-in site account
- Native Categories and Search with WebView fallback when the site cannot be represented reliably
- Manual updater plus **Automatic updates**, enabled by default
- Automatic update downloads are package-verified before Android opens the required installer confirmation
- Material dark interface with Chaos emphasized as the center navigation tab

## Native architecture

The app reads the public CrazyShit.com HTML directly at runtime with Jsoup. It does not use a project-operated proxy or fabricated remote API. Media playback only uses stream URLs already exposed by the website to the current device/session.

The compatibility WebView remains available for unsupported interactive pages, site changes, or account flows that need the full website.

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.0.0 can install over earlier signed stable releases such as v1.4.4 while retaining their app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit. Beta-only local data does not automatically transfer to the stable package.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
