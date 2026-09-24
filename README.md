<p align="center">
  <img src="docs/brand/zerochill-hero.svg" alt="ZEROCHILL devil mascot. NO LIMITS. ALL CONTENT." width="720">
</p>

<h1 align="center">ZEROCHILL for Android</h1>

<p align="center">Native feeds, creator galleries, video, and personal lists on an OLED black interface.</p>

<p align="center">
  <a href="https://github.com/Addy37/CrazyShitAndroid/releases/latest">Download the latest signed APK</a> ·
  <a href="PRIVACY.md">Privacy</a> ·
  <a href="NOTICE.md">Rights and ownership</a>
</p>

> [!WARNING]
> For adults 18 and older. Connected sources can contain explicit, graphic, violent, or sensitive content. Availability depends on source and region.

## What you can do

| Area | Features |
| --- | --- |
| **Home and Shows** | Native browsing, Collections, search, comments, related media, and pagination |
| **ShitTok** | Vertical video feed with preloading, autoplay, history, and fullscreen playback |
| **OnlyFap** | Creator search and mixed photo and video galleries from supported sources |
| **Player** | Media3 playback, picture-in-picture, resume, speed and gesture controls |
| **More** | Favorites, Watch Later, history, downloads, settings, and backup tools |

ZEROCHILL connects to supported third-party sources, including CrazyShit, EFukt, Bunkr, Fapello, and WikiFeet. Source availability can change. Account sign-in is still marked **Coming Soon** in this release.

## Install or update

1. Download **[ZeroChill.apk](https://github.com/Addy37/CrazyShitAndroid/releases/latest/download/ZeroChill.apk)** from the [latest release](https://github.com/Addy37/CrazyShitAndroid/releases/latest).
2. Open the APK and allow installation from your browser or file manager if Android asks.

Install the signed stable APK over your existing CrazyShit app. It keeps the same Android application ID and signing key, so Android treats ZEROCHILL as an update and retains your app data, favorites, history, downloads, and settings. Do not uninstall the old app first. The `.dev` debug package installs separately and does not update stable installs.

**Requirements:** Android 8.0 (API 26) or newer. New stable releases publish one signed install file, `ZeroChill.apk`, plus its checksum on [GitHub Releases](https://github.com/Addy37/CrazyShitAndroid/releases). Older versioned releases may still contain legacy APK aliases.

## Privacy and ownership

ZEROCHILL sends aggregate usage events to its project service, including app opens, version, section, source, and creator interest. It uses rotating hashed keys for daily, weekly, and monthly counts. Optional feedback sends the details you submit and device/app context. Read [PRIVACY.md](PRIVACY.md) for what the app sends and stores locally.

This is an independent community client. It is not affiliated with or endorsed by the connected websites. Their content and branding belong to their owners. The app does not bundle their media or bypass access controls. See [NOTICE.md](NOTICE.md) and the [MIT License](LICENSE).

## Build from source

Use Java 17, Gradle 8.7, and Android SDK 35. The app module is `app`; the private feedback and analytics companion is `feedbackadmin`.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease
gradle --no-daemon :feedbackadmin:assembleRelease
```

Local release builds need the private signing variables described in [SIGNING.md](SIGNING.md). An unsigned or debug-signed build cannot update stable installations. Production APKs are built and verified by the repository release workflow. No signing keys or admin tokens belong in the repository.
