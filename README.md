# CrazyShit Unofficial for Android

An unofficial Android wrapper for [CrazyShit.com](https://crazyshit.com/), built as a lightweight native WebView app for sideloading.

> **Unofficial project:** This app is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

## Content warning

CrazyShit.com contains adult, graphic, violent, and otherwise sensitive material. This app is intended only for adults who knowingly choose to access that site. The app shows a one-time 18+ / graphic-content warning before loading the website.

## Features

- Dedicated native Media3/ExoPlayer video player for compatible video pages and direct media links
- Automatic MP4, HLS, and DASH playback when a usable stream is exposed by the page
- Native-player Picture-in-Picture, playback-speed control, resume position, sharing, and normal-page fallback
- Native player forwards the current site User-Agent, Referer, and relevant cookies when needed for playback
- Optional **Open videos in native player** toggle, enabled by default
- Manual **Play current page in native player** action for pages that do not auto-detect
- Pull to refresh
- Picture-in-Picture for legacy fullscreen WebView video on supported Android devices
- Fullscreen HTML5 video with sensor rotation and keep-screen-awake handling
- Persistent first-party login cookies and sessions
- Modern Android back handling
- File uploads
- Downloads to the Android Downloads folder with native feedback
- Outside web links open in Android Custom Tabs
- Built-in GitHub Releases update checker
- Small three-dot app menu for Home, Refresh, Share, Browser, Native Player, Updates, and Clear site data
- Long-press links and images to open, share, copy, or save
- Native loading progress
- Native connection/error screen
- Android launcher icon and splash screen
- HTTPS-only app traffic with mixed HTTP content blocked
- Third-party WebView cookies disabled
- Android Safe Browsing enabled where supported
- No analytics, advertising SDKs, or tracking added by this wrapper

## Native video player

When you tap a same-site link, the app can inspect the resulting page for a usable HTML5 video stream. If it finds a compatible MP4, HLS, or DASH source, playback moves into the dedicated Android player. If the page does not expose a usable stream, the normal webpage remains available.

The player does not bypass DRM, encryption, paywalls, authentication, or access controls. It only plays media URLs that the page or WebView session already exposes to the device.

Native-player features include:

- Android Media3 / ExoPlayer playback
- MP4, HLS, and DASH support
- Picture-in-Picture
- Resume from the last playback position
- Playback-speed selection
- Share the original page
- Return to the normal webpage if the stream cannot be played

You can disable automatic native playback from the app's three-dot menu at any time.

## Download

Public releases are distributed through GitHub Releases.

- Latest release page: `https://github.com/Addy37/CrazyShitAndroid/releases/latest`
- Latest APK asset: `https://github.com/Addy37/CrazyShitAndroid/releases/latest/download/CrazyShit-Unofficial.apk`

Android may ask you to allow **Install unknown apps** for your browser or file manager before sideloading.

## Updates

The app checks GitHub Releases periodically. If a newer version is available, a small native banner appears with a link to the release page. You can also use the app's three-dot menu and choose **Check for updates**.

The app never silently installs updates.

## Privacy

This wrapper itself does not operate an account system, analytics service, ad network, or tracking backend. It loads the live CrazyShit.com website, so the website's own first-party cookies, analytics, ads, privacy terms, and other third-party services may still apply.

Third-party cookies are disabled inside the wrapper. When the native player is used, the app may pass the existing site session's User-Agent, Referer, and relevant cookies directly to the media server so the same stream can play outside the WebView. Those values are not sent to an app-operated analytics or tracking service.

See [PRIVACY.md](PRIVACY.md) for details.

## Credits and ownership

- Website, website content, branding, user submissions, and associated rights: **CrazyShit.com and their respective owners**.
- CrazyShit.com: https://crazyshit.com/
- CrazyShit.com Terms, Privacy, 2257, DMCA, and Contact links are available from the footer of the website.
- Android platform and WebView technology: Android Open Source Project / Google.
- Native video playback: AndroidX Media3 / ExoPlayer by the Android Open Source Project / Google.
- Community Android wrapper source: **Addy37**, licensed under the MIT License.

No CrazyShit.com videos, images, user uploads, or other website media are bundled with this repository or APK. The app loads the live website at runtime.

## Legal / trademark notice

The use of the name **CrazyShit** in this project's title is descriptive, identifying the website the wrapper opens. This project makes no claim to the CrazyShit.com name, logo, website content, or other third-party intellectual property.

If a rights holder has a concern about this wrapper, see [NOTICE.md](NOTICE.md) for contact/removal information.

## Building

GitHub Actions validates the Android project on pushes and pull requests. A separate release workflow builds a signed release APK when the repository signing secret is configured.

Toolchain:

- Android API 35
- Android Gradle Plugin 8.6.1
- Gradle 8.7
- Java 17
- AndroidX Browser / Custom Tabs
- AndroidX SwipeRefreshLayout
- AndroidX Media3 / ExoPlayer

## License

The original Android wrapper code in this repository is licensed under the [MIT License](LICENSE).

That license does **not** apply to CrazyShit.com, its branding, its site content, user-submitted media, or any other third-party material.
