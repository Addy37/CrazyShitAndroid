# CrazyShit Unofficial for Android

An unofficial Android wrapper for [CrazyShit.com](https://crazyshit.com/), built as a lightweight WebView app with a more native Android browsing and video experience.

> **Unofficial project:** This app is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

## Content warning

CrazyShit.com contains adult, graphic, violent, and otherwise sensitive material. This app is intended only for adults who knowingly choose to access that site. The app shows a one-time 18+ / graphic-content warning before loading the website.

## Highlights

- Material 3 dark native UI
- Modern bottom-sheet quick controls
- Dedicated Settings screen
- Dedicated Media3 / ExoPlayer video player
- Automatic MP4, HLS, DASH, WebM, and M4V handoff when a usable stream is exposed
- Double-tap ±10-second seek
- Horizontal swipe seeking
- Vertical swipe brightness and volume controls
- Fit, Fill, and Zoom video sizing
- Native Picture-in-Picture
- Optional in-app mini-player while browsing
- Local Watch Later library
- Playback-position resume
- Playback-speed controls
- Pull to refresh
- Built-in ad and pop-up blocking, enabled by default
- Persistent first-party website login cookies
- File uploads and Android Downloads integration
- Modern Android back handling
- GitHub Releases update checker
- Adaptive launcher icon and Android 13+ themed monochrome icon
- HTTPS-only WebView traffic with mixed HTTP content blocked
- Third-party WebView cookies disabled
- Android Safe Browsing enabled where supported
- No analytics, advertising SDKs, or tracking added by this wrapper

## Native video player

When you tap a same-site video link, the app can inspect the resulting page for a usable HTML5 video stream. If it finds a compatible direct stream, playback moves into the dedicated Android Media3 / ExoPlayer player. If the page does not expose a usable stream, the normal webpage remains available.

The player does not bypass DRM, encryption, paywalls, authentication, or access controls. It only plays media URLs already exposed to the device by the website or current WebView session.

Player features include:

- MP4, HLS, DASH, WebM, and M4V playback when supported by the exposed stream
- Picture-in-Picture
- Resume from the last playback position
- Playback speeds from 0.5× to 2×
- Double-tap left/right to seek 10 seconds
- Horizontal swipe to seek
- Left-side vertical swipe for brightness
- Right-side vertical swipe for volume
- Fit, Fill, and Zoom modes
- Current playback resolution in the player menu when available
- Share the original page
- Save/remove the page from Watch Later
- Return to the normal webpage if playback fails

The native player can forward the current User-Agent, Referer, Origin, and relevant existing cookies when required for the same exposed stream to play outside the WebView.

## Mini-player

With **Minimize player on Back** enabled, leaving the full native player returns you to the website with a compact in-app video bar.

- Playback resumes from the same position
- Continue browsing while the mini-player remains visible
- Tap the video preview, title, or expand button to restore fullscreen playback
- Close the mini-player to stop playback
- Mini-player playback pauses when the browsing Activity leaves the foreground and resumes when it returns

## Watch Later

Watch Later is stored only on the Android device. No project-operated account or server is involved.

You can:

- Long-press a normal page/video link and choose **Save for later**
- Save or remove the current page from the native player menu
- Open **Watch Later** from the bottom-sheet app menu or Settings
- Remove individual items or clear the library

Only the page title, URL, and saved timestamp are stored by the wrapper.

## Ad and pop-up blocking

The app includes a lightweight WebView blocker intended mainly to stop intrusive ad pages from hijacking video taps.

With **Block ads & pop-ups** enabled, the app:

- disables JavaScript-created popup windows
- keeps `target=_blank` links from spawning separate windows
- blocks common third-party ad-network hosts inside the WebView
- blocks normal third-party page redirects from taps so popunder ads do not replace the intended page
- continues to allow CrazyShit.com pages and compatible direct media streams used by the native player

The blocker is enabled by default and can be changed from the bottom-sheet quick controls or Settings. If you intentionally want to open a legitimate external link while blocking is enabled, long-press the link and choose **Open**.

No blocker can guarantee every future ad or redirect will be caught because websites and ad providers can change.

## Settings

The Material 3 Settings screen includes controls for:

- Native video player
- Picture-in-Picture
- Remember playback position
- Minimize player on Back
- Ad and pop-up blocking
- Haptic feedback
- Watch Later
- Clear Watch Later
- Check for updates
- Clear site data

## Download

Public releases are distributed through GitHub Releases.

- Latest release page: `https://github.com/Addy37/CrazyShitAndroid/releases/latest`
- Latest APK asset: `https://github.com/Addy37/CrazyShitAndroid/releases/latest/download/CrazyShit-Unofficial.apk`

Android may ask you to allow **Install unknown apps** for your browser or file manager before sideloading.

## Updates

The app checks GitHub Releases periodically. If a newer version is available, a native banner appears with a link to the release page. You can also choose **Check for updates** from the app controls or Settings.

The app never silently installs updates.

## Privacy

This wrapper itself does not operate analytics, an advertising network, telemetry, a remote account system, or a tracking backend. It loads the live CrazyShit.com website, so the website's own first-party cookies, analytics, ads, privacy terms, and third-party services may still apply.

Third-party cookies are disabled inside the wrapper. Watch Later and playback preferences are stored locally on the device. When the native or mini-player is used, the wrapper may pass the existing site session's User-Agent, Referer, Origin, and relevant cookies directly to the media server so the same exposed stream can play outside the WebView.

See [PRIVACY.md](PRIVACY.md) for details.

## Credits and ownership

- Website, website content, branding, user submissions, and associated rights: **CrazyShit.com and their respective owners**.
- CrazyShit.com: https://crazyshit.com/
- CrazyShit.com Terms, Privacy, 2257, DMCA, and Contact links are available from the footer of the website.
- Android platform and WebView technology: Android Open Source Project / Google.
- Material Design components: Google / Android Open Source ecosystem.
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
- Material Components / Material 3
- AndroidX Browser / Custom Tabs
- AndroidX SwipeRefreshLayout
- AndroidX Media3 / ExoPlayer

## License

The original Android wrapper code in this repository is licensed under the [MIT License](LICENSE).

That license does **not** apply to CrazyShit.com, its branding, its site content, user-submitted media, or any other third-party material.
