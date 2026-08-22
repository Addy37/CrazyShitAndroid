# CrazyShit Jeremy Edition v2

v2 moves the primary browsing experience from WebView to native Android UI while keeping the existing WebView as a compatibility fallback.

## Native surfaces

- Home feed with pagination
- Trending feed
- Categories and category feeds
- Search results
- Material bottom navigation
- Existing Watch Later and Settings
- Direct Media3 player handoff when a content page exposes a playable stream

## Fallback surfaces

The existing WebView remains available for login/account/upload/comment flows and for pages the native parser cannot handle. The package name and signing identity remain unchanged so v2 installs as an update over v1.x.

## Data source

The native client reads the public CrazyShit.com HTML at runtime with Jsoup. It does not introduce a proxy, unofficial server, or fabricated API. If the site's markup changes, the app can fall back to the WebView instead of becoming unusable.
