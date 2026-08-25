# CrazyShit v2.8.0

CrazyShit 2.8 is the Chaos + Foundation release. It keeps the OLED look of 2.7.2 while making Chaos the center of the app, adding native Shit Show playback, stabilizing navigation, and simplifying the UI code underneath it.

## Chaos is now the main experience

- Fresh app launches open directly to Chaos
- Chaos pulls randomized videos from Home, Trending, Videos, User Uploads, and all currently exposed site categories
- Regular source pages are sampled instead of dumping entire feeds into one batch, keeping the mix broad and unpredictable
- Shit Show is now a first-class Chaos source using the site's embedded story data and rendered playback context
- Shit Show and regular content are mixed at roughly a 50/50 target while both are available, so Shit Show normally appears about every other swipe
- Session repeat protection remembers up to 500 watched URLs and avoids already offered clips
- Completed Chaos videos restart from the beginning when you swipe back to them, while partially watched clips keep their position

## Shit Show playback

- Shit Show stories are harvested from the site's JavaScript-driven story data instead of being treated like a normal HTML feed
- Story permalinks are resolved on demand immediately before playback
- The app forwards the rendered WebView media request context into Media3, including the request headers/referrer/cookies needed by Shit Show streams
- Extensionless video endpoints are identified correctly as playable media
- Native title, views, save, comments, share, progress, swipe, and auto-advance UI remain available around Shit Show clips

## Navigation and UI stability

- The top feed header is permanently static, removing the old slow-scroll/collapsing-header glitch
- The preferred Material/OLED floating bottom navigation remains the portrait design
- The old Flash navigation polling and duplicate floating Chaos button path are disabled
- One navigation owner now keeps portrait height, margins, item transforms, and active-state geometry stable through tab changes and resume
- Horizontal mode keeps the existing left navigation rail with a clean portrait/horizontal handoff
- Portrait video controls and compact progress behavior remain intact

## Foundation cleanup

- Native UI lifecycle ordering is centralized through the foundation coordinator
- Several old compatibility/polish ownership paths were removed or detached after their behavior moved into the views/controllers that actually own it
- Chaos filtering, portrait chrome, source mixing, playback resolution, and replay behavior now live closer to the Chaos feed itself
- Responsive geometry remains the final layout authority instead of multiple controllers continuously fighting over the same views
- Pull-request builds include the 2.8 compatibility regression guard, Android lint audit, and hard APK compilation gate

## Existing features retained

- OLED Black remains the default visual theme
- Home, Series, Categories, More, Search, Continue Watching, History, and Watch Later remain available
- Cards, Grid, Posters, embedded Series/Category artwork, related videos, comments, save, and share remain intact
- Stable application ID and signing identity are unchanged, so 2.8.0 installs as an update over 2.7.2

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
