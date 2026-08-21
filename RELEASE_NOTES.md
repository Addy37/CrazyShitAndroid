# CrazyShit Unofficial v1.3.0

A browsing-cleanup update focused on stopping intrusive ad redirects, popups, and popunders without interfering with the native video player.

## New and improved

- New **Block ads & pop-ups** setting, enabled by default
- Blocks common third-party ad-network requests inside the WebView
- Prevents JavaScript popup windows and popunders
- Removes `target=_blank` behavior so links cannot quietly spawn extra browser windows
- Blocks accidental third-party page redirects from normal taps while ad blocking is enabled
- Same-site CrazyShit.com navigation continues to work normally
- Direct MP4, HLS, DASH, WebM, and M4V media links can still hand off to the native player
- Long-press remains available when you intentionally want to open a real external link
- Ad blocking can be disabled at any time from the app's three-dot menu

## Existing v1.2 features retained

- Dedicated Android Media3 / ExoPlayer video player
- MP4, HLS, and DASH playback
- Picture-in-Picture
- Playback-speed selection
- Resume position
- Pull to refresh
- Update checker
- Custom Tabs for intentionally opened external pages
- HTTPS-only WebView traffic, Safe Browsing, and third-party-cookie blocking

## Notes

The blocker is intentionally conservative around first-party CrazyShit.com content and direct media streams. It does not modify or redistribute website media, bypass access controls, or remove server-side content.

This project remains unofficial and is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
