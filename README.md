# CrazyShit Unofficial for Android

An unofficial Android wrapper for [CrazyShit.com](https://crazyshit.com/), built as a lightweight native WebView app for sideloading.

> **Unofficial project:** This app is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

## Content warning

CrazyShit.com contains adult, graphic, violent, and otherwise sensitive material. This app is intended only for adults who knowingly choose to access that site. The app shows a one-time 18+ / graphic-content warning before loading the website.

## Features

- Pull to refresh
- Picture-in-Picture for fullscreen video on supported Android devices
- Fullscreen HTML5 video with sensor rotation and keep-screen-awake handling
- Persistent first-party login cookies and sessions
- Modern Android back handling
- File uploads
- Downloads to the Android Downloads folder with native feedback
- Outside web links open in Android Custom Tabs
- Built-in GitHub Releases update checker
- Small three-dot app menu for Home, Refresh, Share, Browser, Updates, and Clear site data
- Long-press links and images to open, share, copy, or save
- Native loading progress
- Native connection/error screen
- Android launcher icon and splash screen
- HTTPS-only app traffic with mixed HTTP content blocked
- Third-party WebView cookies disabled
- Android Safe Browsing enabled where supported
- No analytics, advertising SDKs, or tracking added by this wrapper

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

Third-party cookies are disabled inside the wrapper. See [PRIVACY.md](PRIVACY.md) for details.

## Credits and ownership

- Website, website content, branding, user submissions, and associated rights: **CrazyShit.com and their respective owners**.
- CrazyShit.com: https://crazyshit.com/
- CrazyShit.com Terms, Privacy, 2257, DMCA, and Contact links are available from the footer of the website.
- Android platform and WebView technology: Android Open Source Project / Google.
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

## License

The original Android wrapper code in this repository is licensed under the [MIT License](LICENSE).

That license does **not** apply to CrazyShit.com, its branding, its site content, user-submitted media, or any other third-party material.
