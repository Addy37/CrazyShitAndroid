# CrazyShit Unofficial v1.1.0

A native-experience update focused on video, navigation, updates, sharing, and WebView security.

## New and improved

- Pull down to refresh the current page
- Picture-in-Picture for fullscreen video when leaving the app on supported Android devices
- Better fullscreen video behavior with sensor rotation and keep-screen-awake handling
- External web links open in Android Custom Tabs instead of abruptly switching to a full browser window
- Modern Android back handling with predictive back-to-home behavior when the WebView has no page history
- Built-in GitHub Releases update checker, with a small in-app update banner when a newer version is available
- Compact three-dot app menu with Home, Refresh, Share, Open in browser, Check for updates, and Clear site data
- Long-press links and images to open, share, copy, or save images
- Improved download feedback showing the file name
- First-run warning now explicitly identifies the app as an unofficial community project

## Security and privacy changes

- Mixed HTTP content is blocked inside the WebView
- Third-party WebView cookies are disabled
- Android Safe Browsing remains enabled where supported
- CrazyShit.com itself remains first-party and keeps normal login/session cookies
- No analytics, advertising SDK, or tracking backend is added by the wrapper

## Unofficial status

This project is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com. No CrazyShit.com media is bundled in the APK. The app loads the live website at runtime.

See `README.md`, `PRIVACY.md`, and `NOTICE.md` in the repository for more information.
