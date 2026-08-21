# CrazyShit Unofficial v1.2.0

A video-focused update that adds a dedicated native Android player while keeping the normal website as a safe fallback.

## New native video player

- Adds a dedicated Android Media3 / ExoPlayer player
- Compatible video-page taps can automatically hand off to native playback
- Supports direct MP4, HLS, DASH, WebM, and M4V links when exposed by the page/session
- Looks for the page's active HTML5 video source and common video metadata
- Briefly retries extraction for JavaScript-loaded players
- Watches the WebView session for direct media requests as a fallback when the DOM does not expose a source
- Passes the current User-Agent, Referer, Origin, and relevant site cookies to the media request when needed
- Falls back to the normal webpage if native playback fails
- Does not bypass DRM, encryption, paywalls, authentication, or access controls

## Player experience

- Picture-in-Picture on supported Android devices
- Full-sensor rotation
- Keeps the screen awake during playback
- Remembers playback position and resumes later
- Playback speed choices from 0.5x to 2x
- Restart video action
- Share the original video page
- Open the normal webpage at any time

## Controls

- **Open videos in native player** toggle in the three-dot app menu, enabled by default
- **Play current page in native player** for a manual one-off attempt
- Direct compatible media links can open straight in the player

## Existing v1.1 features retained

- Pull to refresh
- WebView Picture-in-Picture/fullscreen support
- Android Custom Tabs for external links
- Modern Android back handling
- Built-in update checker
- Long-press link/image actions
- Downloads, file uploads, Safe Browsing, first-party sessions, and HTTPS-only traffic

## Privacy

The wrapper adds no analytics, advertising SDK, or tracking backend. The native player may reuse the existing site's User-Agent, Referer, and relevant cookies strictly to request the same media stream the current WebView session already exposes.

## Unofficial status

This project is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com. No CrazyShit.com media is bundled in the APK. The app loads the live website at runtime.

See `README.md`, `PRIVACY.md`, and `NOTICE.md` in the repository for more information.
