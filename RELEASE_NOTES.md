# CrazyShit Jeremy Edition v2.0.0

A major architecture update that turns Jeremy Edition from a WebView-first wrapper into a native Android browsing app with the website retained as a compatibility fallback.

## Native v2

- New native Home feed rendered with Android RecyclerView cards.
- Native Trending feed.
- Native Categories grid and category feeds.
- Native Search results.
- Infinite feed pagination as you scroll.
- Material bottom navigation for Home, Trending, Categories, Watch Later, and More.
- Native Jeremy Edition header and search controls.
- Content cards are built from the public CrazyShit.com HTML at runtime with Jsoup. No proxy or unofficial remote API is introduced.
- Tapping a media card first tries to resolve an exposed MP4/HLS/DASH/WebM/M4V stream and opens the existing Media3 player directly.
- If the page cannot be represented natively, the app opens an in-app compatibility WebView instead of failing.
- Existing site cookies remain available for fallback login/account flows and compatible stream requests.
- Existing native mini-player behavior is retained when returning from the Media3 player.
- Existing Watch Later, playback resume, PiP, gestures, ad/pop-up protection, Settings, GitHub update checks, themed Jeremy icon, and Android system-bar handling are retained.

## Compatibility

The package name and signing identity remain unchanged, so v2.0.0 installs directly over v1.4.4 and retains the app's existing local preferences/data.

Because CrazyShit.com does not expose a documented public API for this client, the native repository parses the site's public HTML. The compatibility WebView remains available as a safety net if a future site redesign changes that markup.

This project remains unofficial and is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
