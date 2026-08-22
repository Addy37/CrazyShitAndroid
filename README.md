# CrazyShit Jeremy Edition for Android

An unofficial native-first Android client for [CrazyShit.com](https://crazyshit.com/).

> **Unofficial project:** This app is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

## Content warning

CrazyShit.com contains adult, graphic, violent, and otherwise sensitive material. This app is intended only for adults who knowingly choose to access that site. The app shows a one-time 18+ / graphic-content warning.

## Native v2

Starting with v2.0.0, the primary browsing experience is native Android rather than a full-page WebView wrapper.

Native screens include:

- Home feed
- Trending feed
- Categories and category feeds
- Search results
- Infinite-scroll pagination
- Material bottom navigation
- Watch Later
- Settings
- Native Media3 / ExoPlayer playback
- In-app mini-player

The client reads the public CrazyShit.com HTML directly at runtime with Jsoup. It does not use a project-operated proxy or fabricated remote API. When a page cannot be represented natively, a dedicated in-app compatibility WebView opens that exact page instead.

## Native video player

When a native media card is selected, the app first inspects the public content page for a usable exposed stream. Compatible MP4, HLS, DASH, WebM, and M4V sources can open directly in Android Media3 / ExoPlayer.

Player features include:

- Picture-in-Picture
- Resume playback position
- Playback speeds from 0.5x to 2x
- Double-tap left/right to seek 10 seconds
- Horizontal swipe seeking
- Left-side brightness gesture
- Right-side volume gesture
- Fit, Fill, and Zoom modes
- Current playback resolution when available
- Share page
- Watch Later
- Mini-player on return to browsing

The app does not bypass DRM, encryption, paywalls, authentication, or access controls. It only attempts to play media URLs already exposed by the website to the current device/session.

## Compatibility browser

WebView is still included as a fallback for flows that are better left to the website, including account/login pages, uploads, some interactive pages, and pages that the native parser cannot understand after a site change.

The fallback browser includes:

- first-party cookies
- file chooser support
- HTML5 fullscreen video
- system-bar/cutout safe areas
- HTTPS-only mixed-content policy
- external links through Android browser/Custom Tabs
- popup suppression

## Other features

- CrazyShit Jeremy Edition launcher and splash branding
- Material 3 dark UI with Jeremy orange accent
- Lightweight ad and pop-up blocking
- Local Watch Later library
- Pull to refresh
- GitHub Releases update checker
- Adaptive/themed launcher icon
- Modern Android back handling
- Android status-bar, display-cutout, and gesture-area handling
- No analytics or project-operated tracking backend

## Download

Public releases are distributed through GitHub Releases.

- Latest release page: `https://github.com/Addy37/CrazyShitAndroid/releases/latest`
- Latest APK: `https://github.com/Addy37/CrazyShitAndroid/releases/latest/download/CrazyShit-Jeremy-Edition.apk`

Android may ask you to allow **Install unknown apps** for your browser or file manager before sideloading.

## Updates and compatibility

The application ID remains `com.addy37.crazyshitunofficial` and public releases continue to use the same signing identity. This lets supported releases install over earlier versions while preserving local app preferences/data.

PR/debug builds use a `.dev` application ID suffix so experimental native builds can be installed beside the public Jeremy Edition app without replacing it.

## Privacy

This client does not operate analytics, telemetry, an ad network, or a remote account backend. It connects directly to the live CrazyShit.com website and media hosts used by that website, so the site's own cookies, analytics, privacy terms, advertising, and third-party services may still apply.

Watch Later and playback preferences are stored locally on the Android device.

See [PRIVACY.md](PRIVACY.md) for more information.

## Credits and ownership

- Website, site content, branding, and user submissions: CrazyShit.com and their respective owners.
- Android platform and WebView: Android Open Source Project / Google.
- Material Components: Google / Android Open Source ecosystem.
- Media playback: AndroidX Media3 / ExoPlayer.
- Native HTML parsing: Jsoup.
- Image loading: Glide.
- Community Android client source: Addy37, licensed under the MIT License.

No CrazyShit.com videos or user uploads are bundled with this repository or APK. Content is loaded from the live website at runtime.

## Legal / trademark notice

The use of the name **CrazyShit** in this project's title is descriptive, identifying the website this client connects to. This project makes no claim to CrazyShit.com branding, site content, or other third-party intellectual property.

If a rights holder has a concern about this client, see [NOTICE.md](NOTICE.md).

## Building

GitHub Actions validates the Android project on pushes and pull requests. A separate workflow builds and verifies the signed release APK.

Toolchain:

- Android API 35
- Android Gradle Plugin 8.6.1
- Gradle 8.7
- Java 17
- Material Components / Material 3
- AndroidX RecyclerView
- AndroidX SwipeRefreshLayout
- AndroidX Browser / Custom Tabs
- AndroidX Media3 / ExoPlayer
- Jsoup
- Glide

## License

Original Android client code in this repository is licensed under the [MIT License](LICENSE). That license does not apply to CrazyShit.com, its branding, its site content, user-submitted media, or other third-party material.
