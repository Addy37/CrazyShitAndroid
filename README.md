# CrazyShit for Android

[![Latest APK downloads](https://img.shields.io/github/downloads/Addy37/CrazyShitAndroid/latest/CrazyShit.apk?label=latest%20APK%20downloads&labelColor=0D0D0F&color=FBF506)](https://github.com/Addy37/CrazyShitAndroid/releases/latest)

A native-first Android client for [CrazyShit.com](https://crazyshit.com/).

> **Independent project:** This app is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

## Content warning

CrazyShit.com and EFukt.com contain adult, graphic, violent, and otherwise sensitive material. This app is intended only for adults who knowingly choose to access those sites. The app shows a one-time 18+ access notice covering sensitive content, regional availability, lawful VPN use, and the project's unofficial status.

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
- Mix Home, Trending, Videos, User Uploads, Categories, Shit Show, and EFukt sources
- Prevent duplicates within the current session
- Remember up to 500 recently viewed URLs and strongly prefer unseen videos on later visits
- Save to Watch Later, open supported comments, share, or open full details
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

## Creator search and saved lists

- Type at least two characters in Search to see up to eight matching creators.
- Saved and previously seen creators appear immediately; live matches follow after a short typing pause.
- Exact and prefix matches come first. Stars save creators to **More > Favorite creators**.
- Global search shows each source as it finishes and reports sources that could not be reached.
- Search text, filters, galleries, selected media and scroll positions restore when Android recreates a screen. Large lists use bounded local cache snapshots.
- **Settings > Export backup** saves favorite creators, Watch Later and selected app settings as JSON. Import previews the counts, merges saved lists and applies included settings. Cookies, account sessions, history and downloaded media are excluded.

## Downloads and playback recovery

Supported media hosts can resume saved byte ranges after a pause or interruption. Resume checks the resource validator, size and each returned byte range before appending. Hosts without this support restart the file. At most two downloads run at once, with up to four ranges per file.

Compatible native players can refresh a failed stream link twice and resume from the previous position. Settings includes local performance counters for launch, scrolling, first video frame and buffering. These counters stay on the device and reset when the process restarts.

## Updates and installation

Stable APKs are distributed through this private repository's [Releases page](https://github.com/Addy37/CrazyShitAndroid/releases). Sign in with an account that has access, download `CrazyShit.apk`, and open it in Android's installer. Android may ask you to allow **Install unknown apps** for the app opening the APK.

This build uses manual APK updates. Settings and More open the private release page; the app does not make anonymous update checks that cannot access private releases.

Stable releases keep the application ID `com.addy37.crazyshitunofficial` and the existing signing identity so they can update an earlier stable installation without clearing its data. Debug builds use the `.dev` suffix and appear as **CrazyShit Beta**; they are separate test installations.

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
