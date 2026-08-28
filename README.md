# CrazyShit for Android

[![Latest APK downloads](https://img.shields.io/github/downloads/Addy37/CrazyShitAndroid/latest/CrazyShit.apk?label=latest%20APK%20downloads&labelColor=0D0D0F&color=FBF506)](https://github.com/Addy37/CrazyShitAndroid/releases/latest)

A native-first Android client for [CrazyShit.com](https://crazyshit.com/).

> **Independent project:** This app is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

## Content warning

CrazyShit.com contains adult, graphic, violent, and otherwise sensitive material. This app is intended only for adults who knowingly choose to access that site. The app shows a one-time 18+ / graphic-content warning.

## CrazyShit 2.0

Version 2.0 replaces the old WebView-first experience with a native Android interface while retaining a compatibility browser for site flows that need it.

### Main navigation

- **Home** with native cards, pagination, grid/list view, comments, and related content
- **Series** with a remembered CrazyShit / EFukt source selector and native series feeds
- **Trending** with its own live page and retained scroll position
- **Chaos** as the featured center tab with a randomized vertical video feed
- **Memes** as a native static-image feed and image viewer
- **More** for Library, Categories, My Profile, Settings, website fallback, and updates

Home, Trending, Chaos, and Memes are kept alive in a real ViewPager2 pager, so horizontal swipes follow your finger and each tab keeps its own state.

## Chaos

Chaos is the app's full-screen random video feed.

- Swipe up for the next video
- Swipe down to return to previous videos in the current stack
- Autoplay the active item and pause offscreen items
- Resolve upcoming videos ahead of time for quicker transitions
- Mix Home and Trending sources
- Prevent duplicates within the current session
- Remember up to 180 recently viewed URLs and strongly prefer unseen videos on later visits
- Save to Watch Later, open comments, share, or open full details
- Feed Chaos playback into normal History and Continue Watching
- Rotate to landscape for immersive fullscreen playback
- Keep vertical next/previous swiping active while landscape
- Rotate back to restore the normal app interface

## Memes

Memes are treated as images, not videos.

- Native meme feed
- Full-image fit instead of video-thumbnail cropping
- Dedicated static image viewer
- Share and Open Page actions
- No Media3 player or fake video controls

## Library

Library is available from More and contains three live swipeable pages:

- Continue Watching
- History
- Watch Later

Library cards include thumbnails, playback progress, timestamps, and local remove/delete controls. Watch Later and playback history are stored locally on the device.

## Native video experience

Selecting a compatible video opens a native video detail screen with Media3 / ExoPlayer playback, metadata, comments, and related videos.

Playback features include:

- Picture-in-Picture
- Resume playback position
- In-app mini-player
- Swipe down to minimize
- Playback speeds from 0.5x to 2x
- Double-tap left/right to seek 10 seconds
- Horizontal swipe seeking
- Left-side brightness gesture
- Right-side volume gesture
- Fit, Fill, and Zoom modes
- Fullscreen rotation
- Share and Watch Later

The app only attempts to play media URLs already exposed by CrazyShit.com or EFukt.com to the current device/session. It does not bypass DRM, encryption, paywalls, authentication, or access controls.

## Comments and account

- Native comment list and reply threading
- Native comment composer when the site session allows posting
- Dedicated sign-in flow that shares the website cookies used by the app
- My Profile opens the profile associated with the signed-in site session
- Website fallback remains available for unsupported account or interactive flows

## Compatibility browser

The fallback WebView is retained for pages that cannot be represented reliably by the native parser or need full website behavior.

It includes first-party cookies, file chooser support, HTML5 fullscreen video, popup suppression, cutout-safe layouts, and external-link handling.

## Updates

CrazyShit can update itself from GitHub Releases.

- Manual **Check for updates** remains available in Settings and More
- **Automatic updates** are enabled by default and can be disabled in Settings
- The app checks its current stable or beta channel, downloads a newer matching APK, verifies the package, then opens Android's installer
- Android still shows its required final sideload installation confirmation

Public releases use the existing application ID `com.addy37.crazyshitunofficial` and the existing signing identity so CrazyShit 2.0 can upgrade earlier stable releases.

Beta/debug builds use the `.dev` application ID suffix and appear as **CrazyShit Beta**, allowing them to remain installed beside stable CrazyShit.

## Download

Public releases are distributed through GitHub Releases.

- Latest release page: `https://github.com/Addy37/CrazyShitAndroid/releases/latest`
- Latest APK: `https://github.com/Addy37/CrazyShitAndroid/releases/latest/download/CrazyShit.apk`

Android may ask you to allow **Install unknown apps** for the app used to install the APK.

## Privacy

The Android client does not operate its own analytics, telemetry, ad network, account backend, or proxy. It connects directly to CrazyShit.com and media hosts used by the website, so the website's own cookies, analytics, advertising, privacy terms, and third-party services may still apply.

See [PRIVACY.md](PRIVACY.md) for more information.

## Credits and ownership

- Website, site content, branding, and user submissions: CrazyShit.com, EFukt.com, and their respective owners
- Android platform and WebView: Android Open Source Project / Google
- Material Components: Google / Android Open Source ecosystem
- Media playback: AndroidX Media3 / ExoPlayer
- Native HTML parsing: Jsoup
- Image loading: Glide
- Community Android client source: Addy37, licensed under the MIT License

No CrazyShit.com videos or user uploads are bundled with this repository or APK. Content is loaded from the live website at runtime.

## Legal / trademark notice

The use of the name **CrazyShit** identifies the website this client connects to. This project makes no claim to CrazyShit.com branding, site content, or other third-party intellectual property.

If a rights holder has a concern about this client, see [NOTICE.md](NOTICE.md).

## Building

GitHub Actions validates the Android project on pushes and pull requests. A separate workflow builds, signs, verifies, hashes, and publishes the stable APK.

Toolchain:

- Android API 35
- Gradle 8.7
- Java 17
- Material Components
- AndroidX RecyclerView
- AndroidX ViewPager2
- AndroidX SwipeRefreshLayout
- AndroidX Browser / Custom Tabs
- AndroidX Media3 / ExoPlayer
- Jsoup
- Glide

## License

Original Android client code in this repository is licensed under the [MIT License](LICENSE). That license does not apply to CrazyShit.com, its branding, its site content, user-submitted media, or other third-party material.
