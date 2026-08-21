# Privacy Notice

## Summary

CrazyShit Unofficial is a lightweight Android WebView wrapper with an optional native Media3 / ExoPlayer video player. The wrapper itself does not operate analytics, advertising, telemetry, accounts, or a remote backend.

## Website data

The app loads the live website at `https://crazyshit.com/`. CrazyShit.com and services embedded by that website may use cookies, local storage, analytics, advertising, authentication, or other technologies according to their own policies.

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

The wrapper does not send this data to a separate server operated by this project.

## Permissions

- `INTERNET`: required to load CrazyShit.com and play network media
- `ACCESS_NETWORK_STATE`: used for normal network-aware behavior
- Legacy storage permission is declared only for Android versions where it is needed for downloads

## Third-party policy

This project is not responsible for CrazyShit.com's privacy practices or for third-party services loaded by the website. Review the Privacy and Terms links in the CrazyShit.com footer before using the service.

## Contact

For issues caused by this Android wrapper, open an issue in this repository.

For website accounts, website content, removals, DMCA matters, or CrazyShit.com privacy questions, use the official Terms, Privacy, DMCA, and Contact links on CrazyShit.com.
