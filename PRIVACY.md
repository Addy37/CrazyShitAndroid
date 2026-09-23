# Privacy Notice

## Summary

ZeroChill for Android is a native Android client with Media3 / ExoPlayer playback. It sends aggregate usage events and optional feedback to a project-operated service. It does not require a project account or include an advertising network.

## Aggregate usage analytics

The app reports app opens, app version, the section and content source you open, and the names of creator galleries you view. These events go to the project's analytics endpoint and contribute to aggregate counts in the private admin dashboard.

To count usage by day, week, and month, the app derives separate one-way keys from a local installation ID, the metric and value, and the time period. The installation ID itself is not included in analytics requests. The keys change across metrics, values, and time periods. The analytics dashboard does not expose a per-install viewing history. The app does not send video playback history, downloaded files, or favorite lists to this analytics endpoint.

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

These values are passed directly to the media server as part of playback requests. They are not sent to the project analytics or feedback services.

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

The project service also receives the aggregate usage events described above. Other website and playback data listed here stays on the device or goes to the connected source as required for browsing and playback.

## Permissions

- `INTERNET`: required to load CrazyShit.com, optional EFukt.com Series pages, and network media
- `ACCESS_NETWORK_STATE`: used for normal network-aware behavior
- `POST_NOTIFICATIONS`: optional update, content, and download alerts on supported Android versions
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`: keep active downloads running
- `REQUEST_INSTALL_PACKAGES`: hand an APK update to Android's installer when you choose to update
- Legacy storage permission is declared only for Android versions where it is needed for downloads

## Third-party policy

This project is not responsible for CrazyShit.com's or EFukt.com's privacy practices or for third-party services loaded by either website. Review each site's Privacy and Terms links before using it.

## Contact

For issues caused by this Android wrapper, open an issue in this repository.

For website accounts, website content, removals, DMCA matters, or CrazyShit.com privacy questions, use the official Terms, Privacy, DMCA, and Contact links on CrazyShit.com.
