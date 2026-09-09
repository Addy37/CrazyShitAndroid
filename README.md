<p align="center">
  <img src="app/src/main/res/drawable-nodpi/splash_wordmark_transparent.webp" alt="CrazyShit for Android" width="720">
</p>

<h1 align="center">CrazyShit for Android</h1>

<p align="center">
  A fast, native Android client for CrazyShit, EFukt, Bunkr, Fapello, and WikiFeet content.
</p>

<p align="center">
  <a href="https://github.com/Addy37/CrazyShitAndroid/releases/latest/download/CrazyShit.apk"><img src="https://img.shields.io/badge/Download-Latest_APK-FBF506?style=for-the-badge&logo=android&logoColor=0D0D0F&labelColor=0D0D0F" alt="Download latest APK"></a>
  <a href="https://github.com/Addy37/CrazyShitAndroid/releases"><img src="https://img.shields.io/badge/Release-Latest-F97316?style=for-the-badge&logo=github&logoColor=white&labelColor=0D0D0F" alt="Latest release"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=black" alt="Android 8.0 or newer">
  <img src="https://img.shields.io/badge/UI-Native_Android-FBF506?style=flat-square&labelColor=0D0D0F" alt="Native Android interface">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-F97316?style=flat-square&labelColor=0D0D0F" alt="MIT License"></a>
</p>

> [!WARNING]
> This project is intended for adults 18 and older. Connected sources may contain explicit, graphic, violent, or sensitive material. Content availability depends on your region, provider, and each source's current status.

> [!NOTE]
> This is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com, EFukt.com, Bunkr, Fapello, WikiFeet, or their owners.

## Android browsing without the browser clutter

CrazyShit combines native feeds, galleries, search, playback, downloads, and personal lists in one OLED-friendly Android interface. A compatibility browser remains available only for account actions and pages that require the original website.

| Experience | What you get |
| --- | --- |
| **Home** | Native cards, pagination, list and grid layouts, comments, related media, and state retention |
| **Chaos** | Random vertical video feed with autoplay, session history, preloading, and next or previous swiping |
| **Fapzone** | Combined creator and gallery browsing across Bunkr, Fapello, WikiFeet, and WikiFeet X |
| **Player** | Media3 playback, picture-in-picture, mini-player, resume, speed controls, seeking, brightness, volume, and zoom modes |
| **Library** | Continue Watching, History, Watch Later, favorite creators, and local backups |
| **Downloads** | Resumable downloads for supported hosts with validation before interrupted files continue |

## What changed in 3.0

- Faster startup, feed rendering, thumbnail loading, scrolling, and player preparation
- Restored classic Home presentation with the current performance work retained
- Expanded Fapzone coverage with Bunkr, Fapello, WikiFeet, and WikiFeet X
- Source icons on gallery thumbnails so mixed galleries remain easy to scan
- Portrait-locked phone browsing with sensor-triggered fullscreen for active Chaos videos and opened Fapzone media
- Rotation that keeps playback, gallery position, page state, and image gestures intact
- Local performance counters for launch, scrolling, first frame, and buffering
- Baseline profile coverage for common startup, browsing, search, gallery, and playback paths

## Sources

| Source | Available in the app |
| --- | --- |
| **CrazyShit** | Home, categories, search, comments, related media, profiles, and Chaos |
| **EFukt** | Collections, search results, native playback, and Chaos |
| **Bunkr** | Creator search, albums, mixed image and video galleries, and downloads |
| **Fapello** | Creator search and combined creator galleries |
| **WikiFeet / WikiFeet X** | Creator search and image galleries with source identification |

Source response times vary. The app shows each search source as it finishes and reports sources it cannot reach.

## Chaos

Chaos is the featured full-screen feed.

- Swipe up for the next video and down for earlier videos in the current stack
- Autoplay only the active item and pause offscreen players
- Prepare upcoming media before you reach it
- Mix supported feeds while limiting duplicates
- Prefer media you have not recently watched
- Save to Watch Later, share, open comments, or view full details
- Continue vertical swiping after rotating into horizontal fullscreen
- Return to the same feed position after rotation or leaving fullscreen

## Galleries and playback

Fapzone galleries support mixed photos and videos without sending you to a separate browser page.

- Swipe left and right through gallery media
- Pinch or double-tap to zoom images
- Show source and video indicators directly on thumbnails
- Download supported videos
- Return to the same search, creator, gallery, and scroll position

The native player supports picture-in-picture, playback resume, a mini-player, playback speeds from 0.5x to 2x, double-tap seeking, horizontal seek gestures, brightness and volume gestures, and Fit, Fill, or Zoom display modes.

## Search, favorites, and backup

- Predictive creator matches begin after two characters
- Exact and prefix matches appear first
- Favorite creators live under **More > Favorite creators**
- Search results appear as each source responds
- **Settings > Export backup** saves favorite creators, Watch Later, and selected settings as JSON
- Import previews the backup counts before merging saved lists and applying settings

Backups exclude cookies, account sessions, history, and downloaded media.

## Install and update

Stable APKs are distributed publicly through GitHub Releases. A GitHub account is not required.

1. Download [`CrazyShit.apk`](https://github.com/Addy37/CrazyShitAndroid/releases/latest/download/CrazyShit.apk).
2. Open the APK and approve **Install unknown apps** if Android asks.

The app checks the public GitHub release feed for newer stable versions when update alerts are enabled. You can also check manually from the app.

Stable builds keep the application ID `com.addy37.crazyshitunofficial` and the existing signing identity, so a newer stable APK installs over an older stable version without clearing app data. Debug builds use the `.dev` suffix and install separately as **CrazyShit Beta**.

## Privacy and access

The Android client does not run its own analytics, telemetry, advertising network, account backend, or media proxy. It connects directly to supported websites and their media hosts. Their cookies, analytics, advertising, privacy terms, and third-party services may still apply.

The app does not bypass DRM, encryption, paywalls, authentication, or source access controls. It only requests media URLs exposed to the current device and session.

Read [PRIVACY.md](PRIVACY.md) for the complete privacy notes and [NOTICE.md](NOTICE.md) for rights-holder contact information.

<details>
<summary><strong>Building from source</strong></summary>

### Requirements

- Android API 35
- Java 17
- Gradle 8.7

### Main libraries

- AndroidX Media3 / ExoPlayer
- Material Components
- RecyclerView and ViewPager2
- SwipeRefreshLayout
- AndroidX Browser / Custom Tabs
- Jsoup
- Glide

GitHub Actions validates pushes and pull requests. Release workflows build, sign, verify, hash, and attach the stable APK.

</details>

<details>
<summary><strong>Credits, ownership, and license</strong></summary>

- Website content, branding, and user submissions belong to their respective source owners.
- Android, WebView, Material Components, and AndroidX come from Google and the Android Open Source Project.
- Media playback uses AndroidX Media3 / ExoPlayer.
- HTML parsing uses Jsoup. Image loading uses Glide.
- The community Android client source is maintained by Addy37.

No website videos or user uploads are bundled with this repository or APK. Live sources provide content at runtime.

Original Android client code is licensed under the [MIT License](LICENSE). That license does not apply to third-party branding, websites, media, or user submissions.

</details>
