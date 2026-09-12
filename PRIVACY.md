# Privacy Notice

## Summary

CrazyShit for Android is a lightweight Android client with an optional native Media3 / ExoPlayer video player. The app does not operate analytics, advertising, telemetry, or required user accounts. Its optional feedback feature uses a project-operated backend as described below.

## Optional feedback

If you choose to send feedback, the app sends the following information to the project's feedback service:

- The message, category, and optional rating you submit
- App version
- Android version
- Device manufacturer and model
- The app section where feedback was opened
- A random installation ID

The service hashes the installation ID before storing it. The ID lets you view developer replies under **My feedback** without creating an account. It is not used for advertising or cross-app tracking. Feedback remains on the service until the project owner deletes it. Clearing app data or uninstalling the app can prevent you from viewing earlier submissions.

## Website data

The app loads live pages from `https://crazyshit.com/` and, when you select the EFukt source under Series, `https://efukt.com/`. Those sites and their embedded services may use cookies, local storage, analytics, advertising, authentication, or other technologies according to their own policies.

The wrapper allows normal first-party website cookies so sign-in sessions can persist. Those cookies belong to the website context and are managed by Android WebView. Third-party WebView cookies are disabled.

## Native video player

When the optional native player is used, the app may hand a media URL already exposed by the current webpage/WebView session to Android Media3 / ExoPlayer.

To request that same stream successfully, the player may reuse:

- The current WebView User-Agent
- The original page URL as a Referer
- The page origin
- Relevant cookies already present in the website session

These values are passed directly to the media server as part of playback requests. They are not sent to a separate analytics, telemetry, advertising, or project-operated backend.

The native player does not bypass DRM, encryption, paywalls, authentication, or access controls. If a usable stream is not available to the current session, the app falls back to the normal webpage.

The player may store a small local playback-position value on the device so a partially watched video can resume later. This local position data is not uploaded by the wrapper.

## Data handled by the wrapper

The wrapper may interact with:

- Website cookies and local storage needed for normal browsing and login sessions
- Media URLs and request headers needed for optional native playback
- Local playback-position values for resume support
- Files you explicitly choose through Android's file picker for website uploads
- Files you explicitly download from the website
- Links you choose to open in external apps or your browser

Except for information you intentionally submit through the optional feedback feature, the app does not send this data to a separate server operated by this project.

## Permissions

- `INTERNET`: required to load CrazyShit.com, optional EFukt.com Series pages, and network media
- `ACCESS_NETWORK_STATE`: used for normal network-aware behavior
- Legacy storage permission is declared only for Android versions where it is needed for downloads

## Third-party policy

This project is not responsible for CrazyShit.com's or EFukt.com's privacy practices or for third-party services loaded by either website. Review each site's Privacy and Terms links before using it.

## Contact

For issues caused by this Android wrapper, open an issue in this repository.

For website accounts, website content, removals, DMCA matters, or CrazyShit.com privacy questions, use the official Terms, Privacy, DMCA, and Contact links on CrazyShit.com.
